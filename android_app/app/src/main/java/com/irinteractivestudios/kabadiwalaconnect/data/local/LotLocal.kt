package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

@Entity(tableName = "lots")
data class LotEntity(
    @androidx.room.PrimaryKey val id: String,
    val collectorId: String,
    val materialLabel: String,
    val condition: String,
    val weightKg: Double,
    val localPhotoPath: String?,
    val serverPhotoUrl: String?,
    val estimatedValueRupees: Double?,
    val quoteRupees: Double?,
    val finalValueRupees: Double?,
    val location: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val status: String,
    val notes: String,
    val synced: Boolean,
    val materialSubcategory: String? = null,
    val sourceType: String? = null,
    val wasteRegime: String = "E_WASTE",
    val originalWeight: Double? = null,
    val originalWeightUnit: String? = null,
    val imageProvenance: String? = null,
    val imageQualityStatus: String = "UNVERIFIED",
    val locationPrecision: String? = null,
    val serverUpdatedAtEpochMs: Long? = null
)

@Dao
interface LotDao {
    @Query("SELECT * FROM lots ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<LotEntity>>
    @Query("SELECT * FROM lots WHERE collectorId = :collectorId ORDER BY createdAtEpochMs DESC")
    fun observeForCollector(collectorId: String): Flow<List<LotEntity>>
    @Query("SELECT * FROM lots WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LotEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(lot: LotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(lots: List<LotEntity>)
    @Query("SELECT * FROM lots WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LotEntity?
    @Query("SELECT * FROM lots WHERE id = :id AND collectorId = :collectorId LIMIT 1")
    suspend fun findByIdForCollector(id: String, collectorId: String): LotEntity?
    @Query("UPDATE lots SET status = 'CANCELLED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun cancel(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun lock(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'LOCKED'")
    suspend fun reopenQuote(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'COLLECTOR_CONFIRMED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun confirm(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'PAID', finalValueRupees = :amount, updatedAtEpochMs = :updatedAt WHERE id = :id AND status IN ('COLLECTOR_CONFIRMED', 'HANDED_OVER')")
    suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Int
    @Query("UPDATE lots SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String): Int
    @Query("DELETE FROM lots")
    suspend fun clearAll()
}

class RoomLotRepository(
    private val dao: LotDao,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: (() -> Unit)? = null,
    private val accountId: () -> String? = { null }
) : com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository, com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter {
    override fun observeLots(): Flow<List<Lot>> = accountId()?.let { dao.observeForCollector(it) }?.map { it.map(LotEntity::toDomain) } ?: dao.observeAll().map { it.map(LotEntity::toDomain) }
    override fun observeLot(id: String): Flow<Lot?> = dao.observeById(id).map { row -> row?.takeIf { accountId().isNullOrBlank() || it.collectorId == accountId() }?.toDomain() }
    override suspend fun save(lot: Lot) {
        dao.save(lot.toEntity())
        // The lot is visible immediately. Queue the server operation separately
        // so an unavailable network never blocks the collector's workflow.
        try {
            syncQueue?.enqueue(
                SyncQueueItemEntity(
                    operation = "CREATE_LOT",
                    payloadJson = Gson().toJson(lot.toSyncPayload()),
                    createdAtEpochMs = lot.createdAtEpochMs,
                    accountId = lot.collectorId
                )
            )
            requestSync?.invoke()
        } catch (_: Exception) {
            // Local creation remains successful if queue persistence is unavailable.
        }
    }
    override suspend fun cancel(id: String, updatedAt: Long): Boolean {
        if (dao.cancel(id, updatedAt) == 0) return false
        val local = accountId()?.let { dao.findByIdForCollector(id, it) } ?: dao.findById(id) ?: return true
        if (local.synced) {
            runCatching {
                syncQueue?.enqueue(SyncQueueItemEntity(operation = "CANCEL_LOT", payloadJson = "{\"id\":\"$id\"}", createdAtEpochMs = updatedAt, accountId = local.collectorId))
                requestSync?.invoke()
            }
        } else {
            // A local lot may be cancelled before its CREATE_LOT operation has
            // reached the server. Remove that create so sync cannot resurrect
            // a lot the collector already cancelled.
            runCatching {
                syncQueue?.observeAll()?.first()?.filter { item ->
                    item.operation == "CREATE_LOT" && JsonParser.parseString(item.payloadJson).asJsonObject.get("id")?.asString == id
                }?.forEach { syncQueue.remove(it.uid) }
            }
        }
        return true
    }
    override suspend fun lock(id: String, updatedAt: Long): Boolean = dao.lock(id, updatedAt) > 0
    override suspend fun reopenQuote(id: String, updatedAt: Long): Boolean = dao.reopenQuote(id, updatedAt) > 0
    override suspend fun confirm(id: String, updatedAt: Long): Boolean = dao.confirm(id, updatedAt) > 0
    override suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Boolean = dao.markPaid(id, amount, updatedAt) > 0
}

private fun LotEntity.toDomain() = Lot(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, runCatching { LotStatus.valueOf(status) }.getOrDefault(LotStatus.SAVED), notes, synced, materialSubcategory, sourceType, wasteRegime, originalWeight, originalWeightUnit, imageProvenance, imageQualityStatus, locationPrecision, serverUpdatedAtEpochMs)
private fun Lot.toEntity() = LotEntity(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, status.name, notes, synced, materialSubcategory, sourceType, wasteRegime, originalWeight, originalWeightUnit, imageProvenance, imageQualityStatus, locationPrecision, serverUpdatedAtEpochMs)

private fun Lot.toSyncPayload() = mapOf(
    "id" to id,
    "materialCategory" to materialLabel.toBackendMaterial(),
    "condition" to condition,
    "weight" to weightKg,
    "weightUnit" to "KILOGRAM",
    "originalWeight" to originalWeight,
    "originalWeightUnit" to originalWeightUnit,
    "materialSubcategory" to materialSubcategory,
    "sourceType" to sourceType,
    "wasteRegime" to wasteRegime,
    "imageProvenance" to imageProvenance,
    "collectionLocation" to mapOf("areaName" to location, "precision" to "MANUAL"),
    "notes" to notes.takeIf { it.isNotBlank() },
    "quotedPrice" to quoteRupees,
    // Keep the original photo path in the queue so the sync worker can upload
    // it after the lot itself is created.
    "photoPath" to localPhotoPath
)

private fun String.toBackendMaterial() = when (this) {
    "LCD Panel" -> "LCD_PANEL"
    "Cables" -> "CABLE"
    "PCB / Circuit Board" -> "PCB"
    else -> uppercase()
}
