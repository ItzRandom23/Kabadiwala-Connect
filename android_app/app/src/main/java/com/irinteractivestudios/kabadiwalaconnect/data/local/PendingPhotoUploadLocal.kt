package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Durable client-side state for a listing whose server record exists but whose
 * private photo upload still needs a retry.
 *
 * The path is app-private and is never sent to the backend. The backend only
 * receives the multipart bytes after the authenticated upload is retried.
 */
@Entity(
    tableName = "pending_photo_uploads",
    indices = [Index(value = ["accountId"])]
)
data class PendingPhotoUploadEntity(
    @PrimaryKey val listingId: String,
    val accountId: String,
    val localPath: String,
    val createdAtEpochMs: Long,
    val localPathsJson: String? = null
)

@Dao
interface PendingPhotoUploadDao {
    @Query("SELECT * FROM pending_photo_uploads WHERE accountId = :accountId ORDER BY createdAtEpochMs ASC LIMIT 1")
    suspend fun findForAccount(accountId: String): PendingPhotoUploadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(upload: PendingPhotoUploadEntity)

    @Query("DELETE FROM pending_photo_uploads WHERE accountId = :accountId AND listingId = :listingId")
    suspend fun remove(accountId: String, listingId: String)

    @Query("DELETE FROM pending_photo_uploads")
    suspend fun clearAll()
}
