package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.*
import com.irinteractivestudios.kabadiwalaconnect.data.repository.HandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@Entity(tableName = "handovers")
data class HandoverEntity(
    @PrimaryKey val id: String,
    val lotId: String,
    val recyclerId: String,
    val collectorId: String,
    val recyclerName: String,
    val materialLabel: String,
    val weightKg: Double,
    val quotedPriceRupees: Double,
    val collectionLocation: String,
    val handoverLocation: String,
    val handoverLocationType: String,
    val timestampEpochMs: Long,
    val createdAtEpochMs: Long,
    val quoteId: String,
    val status: String,
    val synced: Boolean,
    val actualWeightKg: Double? = null,
    val materialConfirmed: Boolean = false,
    val collectorConfirmed: Boolean = false,
    val scalePhotoPath: String? = null,
    val evidenceUpdatedAtEpochMs: Long? = null
)
@Dao interface HandoverDao {
    @Query("SELECT * FROM handovers ORDER BY timestampEpochMs DESC") fun observeAll(): kotlinx.coroutines.flow.Flow<List<HandoverEntity>>
    @Query("SELECT * FROM handovers WHERE id = :id LIMIT 1") fun observe(id: String): kotlinx.coroutines.flow.Flow<HandoverEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: HandoverEntity)
    @Query("UPDATE handovers SET status = 'HANDED_OVER' WHERE id = :id") suspend fun markHandedOver(id: String): Int
    @Query("UPDATE handovers SET actualWeightKg = :actualWeightKg, materialConfirmed = :materialConfirmed, collectorConfirmed = :collectorConfirmed, scalePhotoPath = :scalePhotoPath, evidenceUpdatedAtEpochMs = :updatedAt WHERE id = :id") suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?, updatedAt: Long): Int
    @Query("DELETE FROM handovers") suspend fun clearAll()
}
class RoomHandoverRepository(private val dao: HandoverDao) : HandoverRepository {
    override fun observeAll() = dao.observeAll().map { it.map(HandoverEntity::toDomain) }
    override fun observe(id: String) = dao.observe(id).map { it?.toDomain() }
    override suspend fun create(lot: Lot, quote: Quote, collectorId: String, locationType: HandoverLocationType, location: String, timestampEpochMs: Long): Handover {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestampEpochMs))
        val item = Handover("HOV-$date-${Random.nextInt(100000, 999999)}", lot.id, quote.recyclerId, collectorId, quote.recyclerName, lot.materialLabel, lot.weightKg, quote.amountRupees, lot.location, location, locationType, timestampEpochMs, timestampEpochMs, quote.id)
        dao.insert(item.toEntity()); return item
    }
    override suspend fun markHandedOver(id: String) = dao.markHandedOver(id) > 0
    override suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?): Boolean = dao.updateEvidence(id, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, System.currentTimeMillis()) > 0
}
private fun HandoverEntity.toDomain() = Handover(id, lotId, recyclerId, collectorId, recyclerName, materialLabel, weightKg, quotedPriceRupees, collectionLocation, handoverLocation, runCatching { HandoverLocationType.valueOf(handoverLocationType) }.getOrDefault(HandoverLocationType.COLLECTOR_LOCATION), timestampEpochMs, createdAtEpochMs, quoteId, runCatching { HandoverStatus.valueOf(status) }.getOrDefault(HandoverStatus.SAVED_LOCALLY), synced, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, evidenceUpdatedAtEpochMs)
private fun Handover.toEntity() = HandoverEntity(id, lotId, recyclerId, collectorId, recyclerName, materialLabel, weightKg, quotedPriceRupees, collectionLocation, handoverLocation, handoverLocationType.name, timestampEpochMs, createdAtEpochMs, quoteId, status.name, synced, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, evidenceUpdatedAtEpochMs)
