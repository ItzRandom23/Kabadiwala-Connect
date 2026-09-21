package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first sync queue for server-dependent collector actions.
 *
 * Server-dependent actions (lot creation, payments, quote requests,
 * handovers, chat, and disputes) are recorded here when offline and uploaded
 * by WorkManager once connectivity returns. Each operation is replayed through
 * its idempotent API contract or retained with an error code when rejected.
 */
@Entity(
    tableName = "sync_queue",
    // Migration 17->18 creates this index for retry scheduling. Keep it in
    // the entity schema so Room validates upgraded databases successfully.
    indices = [Index(value = ["nextAttemptAtEpochMs"])]
)
data class SyncQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0L,
    /** One of SyncOperation names, e.g. "CREATE_LOT". */
    val operation: String,
    /** JSON payload describing the operation (schema per operation). */
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attempts: Int = 0,
    /** Stable server/client error code for a permanently rejected operation. */
    val lastErrorCode: String? = null,
    /** WorkManager may run again before a transient provider failure is safe to retry. */
    val nextAttemptAtEpochMs: Long = 0L,
    val accountId: String? = null
)

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItemEntity): Long

    /** Stable idempotency keys must map to one local outbox row as well as one server mutation. */
    @Query("SELECT uid FROM sync_queue WHERE operation = :operation AND accountId = :accountId AND payloadJson LIKE '%' || :idempotencyKey || '%' LIMIT 1")
    suspend fun findUidByOperationAndIdempotencyKey(operation: String, accountId: String, idempotencyKey: String): Long?

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<SyncQueueItemEntity>>

    @Query("SELECT * FROM sync_queue WHERE accountId = :accountId ORDER BY createdAtEpochMs ASC")
    fun observeForAccount(accountId: String): Flow<List<SyncQueueItemEntity>>

    /** Rows that still need automatic processing. Rejected rows are retained for inspection/recovery, but are not retried forever. */
    @Query("SELECT * FROM sync_queue WHERE (lastErrorCode IS NULL OR attempts < 3) AND nextAttemptAtEpochMs <= CAST(strftime('%s','now') AS INTEGER) * 1000 ORDER BY createdAtEpochMs ASC")
    fun observePending(): Flow<List<SyncQueueItemEntity>>

    @Query("SELECT * FROM sync_queue WHERE accountId = :accountId AND (lastErrorCode IS NULL OR attempts < 3) AND nextAttemptAtEpochMs <= CAST(strftime('%s','now') AS INTEGER) * 1000 ORDER BY createdAtEpochMs ASC")
    fun observePendingForAccount(accountId: String): Flow<List<SyncQueueItemEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    /** Mutations are account-scoped even when a caller only has a local UID. */
    @Query("DELETE FROM sync_queue WHERE uid = :uid AND accountId = :accountId")
    suspend fun remove(uid: Long, accountId: String): Int

    /** Explicit user retry: clear the permanent-error gate for one item. */
    @Query("UPDATE sync_queue SET attempts = 0, lastErrorCode = NULL, nextAttemptAtEpochMs = 0 WHERE uid = :uid AND accountId = :accountId")
    suspend fun resetForRetry(uid: Long, accountId: String): Int

    @Query("UPDATE sync_queue SET attempts = attempts + 1, nextAttemptAtEpochMs = :nextAttemptAtEpochMs WHERE uid = :uid AND accountId = :accountId")
    suspend fun incrementAttempts(uid: Long, accountId: String, nextAttemptAtEpochMs: Long): Int

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastErrorCode = :errorCode, nextAttemptAtEpochMs = :nextAttemptAtEpochMs WHERE uid = :uid AND accountId = :accountId")
    suspend fun markFailed(uid: Long, accountId: String, errorCode: String, nextAttemptAtEpochMs: Long): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
