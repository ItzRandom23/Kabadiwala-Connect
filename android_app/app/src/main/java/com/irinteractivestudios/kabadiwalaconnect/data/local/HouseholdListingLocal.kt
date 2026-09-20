package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.google.gson.Gson
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import kotlinx.coroutines.flow.Flow

/**
 * Account-scoped cache for household listings. The outbox is not a UI cache:
 * keeping the draft here means a process death cannot make an offline listing
 * disappear while its CREATE_HOUSEHOLD_LISTING operation is waiting to sync.
 */
@Entity(
    tableName = "household_listings",
    indices = [Index(value = ["accountId"]), Index(value = ["accountId", "synced"])]
)
data class HouseholdListingCacheEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val payloadJson: String,
    val synced: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Dao
interface HouseholdListingCacheDao {
    @Query("SELECT * FROM household_listings WHERE accountId = :accountId ORDER BY createdAtEpochMs DESC")
    fun observeForAccount(accountId: String): Flow<List<HouseholdListingCacheEntity>>

    @Query("SELECT * FROM household_listings WHERE accountId = :accountId ORDER BY createdAtEpochMs DESC")
    suspend fun findForAccount(accountId: String): List<HouseholdListingCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HouseholdListingCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HouseholdListingCacheEntity>)

    @Query("DELETE FROM household_listings WHERE id = :listingId AND accountId = :accountId")
    suspend fun removeForAccount(listingId: String, accountId: String)

    @Query("DELETE FROM household_listings WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    @Query("DELETE FROM household_listings")
    suspend fun clearAll()
}

private val householdListingGson = Gson()

internal fun HouseholdListingDto.toCacheEntity(accountId: String, synced: Boolean, now: Long = System.currentTimeMillis()): HouseholdListingCacheEntity =
    HouseholdListingCacheEntity(
        id = id,
        accountId = accountId,
        payloadJson = householdListingGson.toJson(this),
        synced = synced,
        createdAtEpochMs = createdAt?.let(::parseListingTimestamp) ?: now,
        updatedAtEpochMs = updatedAt?.let(::parseListingTimestamp) ?: now
    )

internal fun HouseholdListingCacheEntity.toHouseholdListing(): HouseholdListingDto =
    runCatching { householdListingGson.fromJson(payloadJson, HouseholdListingDto::class.java) }
        .getOrDefault(HouseholdListingDto(id = id))

private fun parseListingTimestamp(value: String): Long = runCatching {
    java.time.Instant.parse(value).toEpochMilli()
}.getOrDefault(System.currentTimeMillis())
