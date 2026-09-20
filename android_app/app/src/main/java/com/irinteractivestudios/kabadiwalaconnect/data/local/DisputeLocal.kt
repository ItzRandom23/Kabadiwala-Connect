package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.irinteractivestudios.kabadiwalaconnect.data.repository.DisputeRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Dispute
import com.irinteractivestudios.kabadiwalaconnect.domain.model.DisputeStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.DisputeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Entity(
    tableName = "disputes",
    indices = [Index(value = ["handoverId"])]
)
data class DisputeEntity(
    @androidx.room.PrimaryKey val id: String,
    val handoverId: String,
    val lotId: String,
    val collectorId: String,
    val recyclerId: String,
    val type: String,
    val description: String,
    val claimedWeightKg: Double?,
    val actualWeightKg: Double?,
    val status: String,
    val createdAtEpochMs: Long,
    val synced: Boolean,
    val remoteId: String?
)

@Dao
interface DisputeDao {
    @Query("SELECT * FROM disputes WHERE handoverId = :handoverId ORDER BY createdAtEpochMs DESC")
    fun observeForHandover(handoverId: String): Flow<List<DisputeEntity>>
    @Query("SELECT * FROM disputes WHERE handoverId = :handoverId AND (collectorId = :accountId OR recyclerId = :accountId) ORDER BY createdAtEpochMs DESC")
    fun observeForHandoverForAccount(handoverId: String, accountId: String): Flow<List<DisputeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DisputeEntity)

    @Query("UPDATE disputes SET synced = 1, status = 'OPEN', remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: String, remoteId: String): Int
    @Query("UPDATE disputes SET synced = 1, status = 'OPEN', remoteId = :remoteId WHERE id = :id AND (collectorId = :accountId OR recyclerId = :accountId)")
    suspend fun markSyncedForAccount(id: String, remoteId: String, accountId: String): Int
    @Query("DELETE FROM disputes")
    suspend fun clearAll()
}

class RoomDisputeRepository(private val dao: DisputeDao, private val accountId: () -> String? = { null }) : DisputeRepository {
    override fun observeForHandover(handoverId: String): Flow<List<Dispute>> =
        (accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForHandoverForAccount(handoverId, it) } ?: flowOf(emptyList())).map { list -> list.map(DisputeEntity::toDomain) }

    override suspend fun save(dispute: Dispute) {
        check(accountId()?.takeIf { it.isNotBlank() }?.let { it == dispute.collectorId || it == dispute.recyclerId } == true) { "Authenticated account required for dispute changes" }
        dao.insert(dispute.toEntity())
    }

    override suspend fun markSynced(id: String, remoteId: String): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.markSyncedForAccount(id, remoteId, it) > 0 } ?: false
}

private fun DisputeEntity.toDomain() = Dispute(
    id, handoverId, lotId, collectorId, recyclerId,
    runCatching { DisputeType.valueOf(type) }.getOrDefault(DisputeType.OTHER),
    description, claimedWeightKg, actualWeightKg,
    runCatching { DisputeStatus.valueOf(status) }.getOrDefault(DisputeStatus.SAVED_LOCALLY),
    createdAtEpochMs, synced, remoteId
)

private fun Dispute.toEntity() = DisputeEntity(
    id, handoverId, lotId, collectorId, recyclerId, type.name, description,
    claimedWeightKg, actualWeightKg, status.name, createdAtEpochMs, synced, remoteId
)
