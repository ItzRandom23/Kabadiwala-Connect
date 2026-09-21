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
import kotlinx.coroutines.flow.flowOf
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
    val serverUpdatedAtEpochMs: Long? = null,
    /** Optimistic-concurrency version mirrored from the backend lot. */
    val version: Int = 1,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val localPhotoPathsJson: String = "[]"
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
    @Query("UPDATE lots SET status = 'CANCELLED', updatedAtEpochMs = :updatedAt WHERE id = :id AND collectorId = :collectorId AND status = 'SAVED'")
    suspend fun cancelForCollector(id: String, updatedAt: Long, collectorId: String): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun lock(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND collectorId = :collectorId AND status = 'SAVED'")
    suspend fun lockForCollector(id: String, updatedAt: Long, collectorId: String): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'LOCKED'")
    suspend fun reopenQuote(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'LOCKED', updatedAtEpochMs = :updatedAt WHERE id = :id AND collectorId = :collectorId AND status = 'LOCKED'")
    suspend fun reopenQuoteForCollector(id: String, updatedAt: Long, collectorId: String): Int
    @Query("UPDATE lots SET status = 'COLLECTOR_CONFIRMED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun confirm(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'COLLECTOR_CONFIRMED', updatedAtEpochMs = :updatedAt WHERE id = :id AND collectorId = :collectorId AND status = 'SAVED'")
    suspend fun confirmForCollector(id: String, updatedAt: Long, collectorId: String): Int
    @Query("UPDATE lots SET status = 'PAID', finalValueRupees = :amount, updatedAtEpochMs = :updatedAt WHERE id = :id AND status IN ('COLLECTOR_CONFIRMED', 'HANDED_OVER')")
    suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'PAID', finalValueRupees = :amount, updatedAtEpochMs = :updatedAt WHERE id = :id AND collectorId = :collectorId AND status IN ('COLLECTOR_CONFIRMED', 'HANDED_OVER')")
    suspend fun markPaidForCollector(id: String, amount: Double, updatedAt: Long, collectorId: String): Int
    @Query("UPDATE lots SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String): Int
    @Query("UPDATE lots SET synced = 1 WHERE id = :id AND collectorId = :collectorId")
    suspend fun markSyncedForCollector(id: String, collectorId: String): Int
    @Query("UPDATE lots SET synced = 1, version = :version WHERE id = :id")
    suspend fun markSyncedWithVersion(id: String, version: Int): Int
    @Query("UPDATE lots SET synced = 1, version = :version WHERE id = :id AND collectorId = :collectorId")
    suspend fun markSyncedWithVersionForCollector(id: String, version: Int, collectorId: String): Int
    @Query("DELETE FROM lots")
    suspend fun clearAll()
}

class RoomLotRepository(
    private val dao: LotDao,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: (() -> Unit)? = null,
    private val accountId: () -> String? = { null }
) : com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository, com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter {
    override fun observeLots(): Flow<List<Lot>> = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForCollector(it) }?.map { it.map(LotEntity::toDomain) } ?: flowOf(emptyList())
    override fun observeLot(id: String): Flow<Lot?> = accountId()?.takeIf { it.isNotBlank() }?.let { active -> dao.observeById(id).map { row -> row?.takeIf { it.collectorId == active }?.toDomain() } } ?: flowOf(null)
    override suspend fun save(lot: Lot) {
        check(accountId()?.takeIf { it.isNotBlank() } == lot.collectorId) { "Authenticated account required for lot changes" }
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

    override suspend fun update(lot: Lot): Boolean {
        val account = accountId()?.takeIf { it.isNotBlank() } ?: return false
        val current = dao.findByIdForCollector(lot.id, account) ?: return false
        // The backend only permits edits while a legacy lot is still CREATED
        // (represented locally as SAVED). Never let an edit reopen a quote,
        // pickup, handover, payment, or cancelled record.
        if (current.status != LotStatus.SAVED.name) return false
        val updated = lot.copy(
            collectorId = current.collectorId,
            updatedAtEpochMs = System.currentTimeMillis(),
            synced = false,
            version = current.version
        )
        dao.save(updated.toEntity())
        syncQueue?.enqueue(
            SyncQueueItemEntity(
                operation = "UPDATE_LOT",
                payloadJson = Gson().toJson(updated.toUpdateSyncPayload(current.version)),
                createdAtEpochMs = updated.updatedAtEpochMs,
                accountId = current.collectorId
            )
        )
        requestSync?.invoke()
        return true
    }
    override suspend fun cancel(id: String, updatedAt: Long): Boolean {
        val account = accountId()?.takeIf { it.isNotBlank() } ?: return false
        val changed = dao.cancelForCollector(id, updatedAt, account)
        if (changed == 0) return false
        val local = dao.findByIdForCollector(id, account) ?: return true
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
                val queued = syncQueue?.observeForAccount(account)?.first()
                queued.orEmpty().filter { item ->
                    item.operation == "CREATE_LOT" && JsonParser.parseString(item.payloadJson).asJsonObject.get("id")?.asString == id
                }.forEach { syncQueue?.remove(it.uid, account) }
            }
        }
        return true
    }
    override suspend fun lock(id: String, updatedAt: Long): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.lockForCollector(id, updatedAt, it) > 0 } ?: false
    override suspend fun reopenQuote(id: String, updatedAt: Long): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.reopenQuoteForCollector(id, updatedAt, it) > 0 } ?: false
    override suspend fun confirm(id: String, updatedAt: Long): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.confirmForCollector(id, updatedAt, it) > 0 } ?: false
    override suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.markPaidForCollector(id, amount, updatedAt, it) > 0 } ?: false
}

private fun LotEntity.toDomain() = Lot(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, runCatching { LotStatus.valueOf(status) }.getOrDefault(LotStatus.SAVED), notes, synced, materialSubcategory, sourceType, wasteRegime, originalWeight, originalWeightUnit, imageProvenance, imageQualityStatus, locationPrecision, serverUpdatedAtEpochMs, version, locationLatitude, locationLongitude, runCatching { Gson().fromJson(localPhotoPathsJson, Array<String>::class.java).toList() }.getOrDefault(listOfNotNull(localPhotoPath)))
private fun Lot.toEntity() = LotEntity(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, status.name, notes, synced, materialSubcategory, sourceType, wasteRegime, originalWeight, originalWeightUnit, imageProvenance, imageQualityStatus, locationPrecision, serverUpdatedAtEpochMs, version, locationLatitude, locationLongitude, Gson().toJson(if (localPhotoPaths.isEmpty()) listOfNotNull(localPhotoPath) else localPhotoPaths))

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
    "collectionLocation" to mapOf(
        "areaName" to location.takeIf { it.isNotBlank() },
        "latitude" to locationLatitude,
        "longitude" to locationLongitude,
        "precision" to (locationPrecision ?: "MANUAL")
    ),
    "notes" to notes.takeIf { it.isNotBlank() },
    "quotedPrice" to quoteRupees,
    // Keep the original photo path in the queue so the sync worker can upload
    // it after the lot itself is created.
    "photoPath" to localPhotoPath,
    "photoPaths" to if (localPhotoPaths.isEmpty()) listOfNotNull(localPhotoPath) else localPhotoPaths
)

private fun Lot.toUpdateSyncPayload(clientVersion: Int) = mapOf(
    "id" to id,
    "weight" to weightKg,
    "condition" to condition,
    // Keep an explicit empty string so clearing notes is also synced (Gson
    // omits null map values from the JSON payload).
    "notes" to notes,
    "clientVersion" to clientVersion
)

private fun String.toBackendMaterial() = when (this) {
    "LCD Panel" -> "LCD_PANEL"
    "Cables" -> "CABLE"
    "PCB / Circuit Board" -> "PCB"
    else -> uppercase()
}
