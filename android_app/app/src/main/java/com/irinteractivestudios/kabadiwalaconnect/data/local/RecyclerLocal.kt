package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Entity(tableName = "recyclers")
data class RecyclerEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val authorized: Boolean,
    val distanceKm: Double?,
    val area: String,
    val facility: String,
    val address: String,
    val acceptedMaterialsCsv: String,
    val offeredRatePerKg: Double,
    val pickupAvailable: Boolean,
    val operatingHours: String,
    val typicalHandoverHours: Int,
    val contactPhone: String,
    val latitude: Double?,
    val longitude: Double?,
    val authorizationAuthority: String? = null,
    val authorizationValidUntilEpochMs: Long? = null,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val completedHandovers: Int? = null,
    val lastUpdatedEpochMs: Long? = null
)

@Dao interface RecyclerDao {
    @Query("SELECT * FROM recyclers ORDER BY name") fun observeAll(): Flow<List<RecyclerEntity>>
    @Query("SELECT * FROM recyclers WHERE id = :id LIMIT 1") fun observeById(id: String): Flow<RecyclerEntity?>
    @Query("SELECT COUNT(*) FROM recyclers") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<RecyclerEntity>)
    @Query("DELETE FROM recyclers") suspend fun deleteAll()
    @Transaction
    suspend fun replaceAll(items: List<RecyclerEntity>) {
        deleteAll()
        insertAll(items)
    }
}

class RoomRecyclerRepository(private val dao: RecyclerDao) : RecyclerCatalogRepository {
    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Keep sample facilities isolated to the intentionally offline
            // development build. Never present them as live facilities when
            // the app is configured for the real backend.
            if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid") && dao.count() == 0) {
                dao.insertAll(MockRecyclerData.all.map { it.toEntity() })
            }
        }
    }
    override fun observeRecyclers(): Flow<List<Recycler>> = dao.observeAll().map { it.map { entity -> entity.toDomain() } }
    override fun observeRecycler(id: String): Flow<Recycler?> = dao.observeById(id).map { it?.toDomain() }
}

object MockRecyclerData {
    val all = listOf(
        Recycler("r1", "GreenLoop Recycling", true, 4.2, "Hadapsar", "E-waste processing facility", "Plot 12, Hadapsar Industrial Area", listOf("PCB / Circuit Board", "Cables", "Battery"), 320.0, true, "9:00–18:00", 24, "+91 9000000001", 18.50, 73.93, "CPCB", 1_798_000_000_000L, 4.8, 37, 128, 1_756_900_800_000L),
        Recycler("r2", "Pune Recycle Hub", true, 8.8, "Kothrud", "Authorized collection centre", "Paud Road, Kothrud", listOf("LCD Panel", "Plastic", "Cables", "Motor"), 120.0, false, "10:00–19:00", 48, "+91 9000000002", 18.51, 73.81, "CPCB", 1_795_000_000_000L, 4.5, 19, 74, 1_756_900_800_000L),
        Recycler("r3", "EcoDrop Materials", false, 12.5, "Wakad", "Material recovery facility", "Service Road, Wakad", listOf("CRT", "Plastic", "Magnet"), 55.0, true, "8:30–17:30", 72, "+91 9000000003", 18.60, 73.76),
        Recycler("r4", "Mumbai E-Cycle", true, 34.0, "Vashi", "Authorized e-waste recycler", "Turbhe MIDC, Vashi", listOf("PCB / Circuit Board", "Battery", "LCD Panel"), 340.0, true, "9:00–18:00", 24, "+91 9000000004", 19.07, 73.00)
    )
}

private fun Recycler.toEntity() = RecyclerEntity(id, name, authorized, distanceKm, area, facility, address, acceptedMaterials.joinToString(","), offeredRatePerKg, pickupAvailable, operatingHours, typicalHandoverHours, contactPhone, latitude, longitude, authorizationAuthority, authorizationValidUntilEpochMs, rating, reviewCount, completedHandovers, lastUpdatedEpochMs)
private fun RecyclerEntity.toDomain() = Recycler(id, name, authorized, distanceKm, area, facility, address, acceptedMaterialsCsv.split(",").filter { it.isNotBlank() }, offeredRatePerKg, pickupAvailable, operatingHours, typicalHandoverHours, contactPhone, latitude, longitude, authorizationAuthority, authorizationValidUntilEpochMs, rating, reviewCount, completedHandovers, lastUpdatedEpochMs)
