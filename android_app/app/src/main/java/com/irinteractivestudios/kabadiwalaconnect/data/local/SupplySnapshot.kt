package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.gson.Gson
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkOfferDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.InventoryBalanceDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PickupRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ProcurementRequirementDto

/** Bounded, account-scoped display snapshot. Server responses remain authoritative. */
@Entity(tableName = "supply_snapshots", primaryKeys = ["accountId", "role"])
data class SupplySnapshotEntity(
    val accountId: String,
    val role: String,
    val payloadJson: String,
    val savedAtEpochMs: Long
)

data class SupplySnapshot(
    val listings: List<HouseholdListingDto> = emptyList(),
    val pickups: List<PickupRequestDto> = emptyList(),
    val inventory: List<InventoryBalanceDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val requirements: List<ProcurementRequirementDto> = emptyList()
)

@Dao
interface SupplySnapshotDao {
    @Query("SELECT * FROM supply_snapshots WHERE accountId = :accountId AND role = :role LIMIT 1")
    suspend fun find(accountId: String, role: String): SupplySnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SupplySnapshotEntity)

    @Query("DELETE FROM supply_snapshots")
    suspend fun clearAll()
}

class RoomSupplySnapshotStore(private val dao: SupplySnapshotDao) {
    private val gson = Gson()

    suspend fun load(accountId: String?, role: String): Pair<SupplySnapshot, Long>? {
        val owner = accountId?.takeIf(String::isNotBlank) ?: return null
        val row = dao.find(owner, role) ?: return null
        val snapshot = runCatching { gson.fromJson(row.payloadJson, SupplySnapshot::class.java) }.getOrNull() ?: return null
        return snapshot to row.savedAtEpochMs
    }

    suspend fun save(accountId: String?, role: String, snapshot: SupplySnapshot) {
        val owner = accountId?.takeIf(String::isNotBlank) ?: return
        // Bound storage independently of the server's history/pagination policy.
        val bounded = snapshot.copy(
            listings = snapshot.listings.take(100),
            pickups = snapshot.pickups.take(100),
            inventory = snapshot.inventory.take(100),
            bulkLots = snapshot.bulkLots.take(100),
            offers = snapshot.offers.take(100),
            requirements = snapshot.requirements.take(100)
        )
        dao.upsert(SupplySnapshotEntity(owner, role, gson.toJson(bounded), System.currentTimeMillis()))
    }
}
