package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first sync queue for server-dependent collector actions.
 *
 * Server-dependent actions (lot creation, payments, quote requests,
 * handovers — are recorded here when offline and uploaded by WorkManager once
 * connectivity returns. The backend currently accepts lot and payment
 * operations; other actions remain local until their server contracts exist.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0L,
    /** One of SyncOperation names, e.g. "CREATE_LOT". */
    val operation: String,
    /** JSON payload describing the operation (schema per operation). */
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attempts: Int = 0
)

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItemEntity): Long

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<SyncQueueItemEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    @Query("DELETE FROM sync_queue WHERE uid = :uid")
    suspend fun remove(uid: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1 WHERE uid = :uid")
    suspend fun incrementAttempts(uid: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
