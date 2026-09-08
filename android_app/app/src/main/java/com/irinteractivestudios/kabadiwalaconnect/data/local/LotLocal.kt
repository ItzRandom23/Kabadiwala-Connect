package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.gson.Gson
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val synced: Boolean
)

@Dao
interface LotDao {
    @Query("SELECT * FROM lots ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<LotEntity>>
    @Query("SELECT * FROM lots WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LotEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(lot: LotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(lots: List<LotEntity>)
    @Query("SELECT * FROM lots WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LotEntity?
    @Query("UPDATE lots SET status = 'CANCELLED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun cancel(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'COLLECTOR_CONFIRMED', updatedAtEpochMs = :updatedAt WHERE id = :id AND status = 'SAVED'")
    suspend fun confirm(id: String, updatedAt: Long): Int
    @Query("UPDATE lots SET status = 'PAID', finalValueRupees = :amount, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Int
    @Query("UPDATE lots SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String): Int
    @Query("DELETE FROM lots")
    suspend fun clearAll()
}

class RoomLotRepository(
    private val dao: LotDao,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: (() -> Unit)? = null
) : com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository, com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter {
    override fun observeLots(): Flow<List<Lot>> = dao.observeAll().map { it.map(LotEntity::toDomain) }
    override fun observeLot(id: String): Flow<Lot?> = dao.observeById(id).map { it?.toDomain() }
    override suspend fun save(lot: Lot) {
        dao.save(lot.toEntity())
        // The lot is visible immediately. Queue the server operation separately
        // so an unavailable network never blocks the collector's workflow.
        try {
            syncQueue?.enqueue(
                SyncQueueItemEntity(
                    operation = "CREATE_LOT",
                    payloadJson = Gson().toJson(lot.toSyncPayload()),
                    createdAtEpochMs = lot.createdAtEpochMs
                )
            )
            requestSync?.invoke()
        } catch (_: Exception) {
            // Local creation remains successful if queue persistence is unavailable.
        }
    }
    override suspend fun cancel(id: String, updatedAt: Long): Boolean = dao.cancel(id, updatedAt) > 0
    override suspend fun confirm(id: String, updatedAt: Long): Boolean = dao.confirm(id, updatedAt) > 0
    override suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Boolean = dao.markPaid(id, amount, updatedAt) > 0
}

private fun LotEntity.toDomain() = Lot(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, runCatching { LotStatus.valueOf(status) }.getOrDefault(LotStatus.SAVED), notes, synced)
private fun Lot.toEntity() = LotEntity(id, collectorId, materialLabel, condition, weightKg, localPhotoPath, serverPhotoUrl, estimatedValueRupees, quoteRupees, finalValueRupees, location, createdAtEpochMs, updatedAtEpochMs, status.name, notes, synced)

private fun Lot.toSyncPayload() = mapOf(
    "id" to id,
    "materialCategory" to materialLabel.toBackendMaterial(),
    "condition" to condition,
    "weight" to weightKg,
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
