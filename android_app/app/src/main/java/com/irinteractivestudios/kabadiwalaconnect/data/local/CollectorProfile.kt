package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "collector_profile")
data class CollectorProfileEntity(
    @androidx.room.PrimaryKey val collectorId: String,
    val phoneNumber: String,
    val preferredLanguage: String,
    val primaryLocation: String,
    val locationSource: String,
    val createdAtEpochMs: Long,
    val lastLoginEpochMs: Long
)

@Dao
interface CollectorProfileDao {
    @Query("SELECT * FROM collector_profile LIMIT 1")
    fun observe(): Flow<CollectorProfileEntity?>

    @Query("SELECT * FROM collector_profile LIMIT 1")
    suspend fun get(): CollectorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: CollectorProfileEntity)

    @Query("DELETE FROM collector_profile")
    suspend fun clear()
}
