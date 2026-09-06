package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Entity(tableName = "prices", primaryKeys = ["id", "location"])
data class PriceEntity(val id: String, val location: String, val materialLabel: String, val ratePerKg: Double, val minRatePerKg: Double, val maxRatePerKg: Double, val updatedAtEpochMs: Long, val trend: String, val historyCsv: String)

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE location = :location ORDER BY materialLabel")
    fun observe(location: String): Flow<List<PriceEntity>>
    @Query("SELECT DISTINCT location FROM prices ORDER BY location")
    fun observeLocations(): Flow<List<String>>
    @Query("SELECT COUNT(*) FROM prices")
    suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PriceEntity>)
    @Query("DELETE FROM prices WHERE location = :location")
    suspend fun deleteLocation(location: String)
    @Transaction
    suspend fun replaceLocation(location: String, items: List<PriceEntity>) {
        deleteLocation(location)
        insertAll(items)
    }
}

class RoomPriceRepository(private val dao: PriceDao) : PriceCatalogRepository {
    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (BuildConfig.DEBUG && dao.count() == 0) dao.insertAll(MockPriceData.all.map { it.toEntity() })
        }
    }
    override fun observePrices(location: String): Flow<List<Price>> = dao.observe(location).map { list -> list.map { it.toDomain() } }
    override fun observeLocations(): Flow<List<String>> = dao.observeLocations()
}

object MockPriceData {
    private const val UPDATED = 1_756_900_800_000L
    val all = listOf(
        price("CRT", "Pune", 42.0, 35.0, 48.0, "up", listOf(35, 36, 37, 39, 40, 42)), price("LCD Panel", "Pune", 115.0, 95.0, 128.0, "stable", listOf(112, 114, 113, 115, 115, 115)), price("PCB / Circuit Board", "Pune", 310.0, 270.0, 355.0, "up", listOf(280, 286, 290, 300, 305, 310)), price("Cables", "Pune", 88.0, 70.0, 96.0, "down", listOf(94, 93, 92, 90, 89, 88)), price("Copper", "Pune", 620.0, 570.0, 665.0, "up", listOf(580, 590, 600, 610, 615, 620)),
        price("Battery", "Pune", 62.0, 48.0, 72.0, "stable", listOf(60, 61, 60, 62, 62, 62)), price("Motor", "Pune", 105.0, 85.0, 120.0, "up", listOf(98, 100, 101, 103, 104, 105)), price("Magnet", "Pune", 165.0, 140.0, 180.0, "stable", listOf(160, 162, 165, 165, 165, 165)), price("Plastic", "Pune", 28.0, 20.0, 35.0, "down", listOf(32, 31, 30, 29, 28, 28)), price("Other", "Pune", 25.0, 15.0, 35.0, "stable", listOf(25, 25, 24, 25, 25, 25)),
        price("CRT", "Mumbai", 46.0, 38.0, 52.0, "up", listOf(38, 40, 41, 43, 44, 46)), price("LCD Panel", "Mumbai", 122.0, 100.0, 135.0, "stable", listOf(120, 121, 122, 122, 122, 122)), price("PCB / Circuit Board", "Mumbai", 325.0, 280.0, 370.0, "up", listOf(290, 295, 305, 312, 318, 325))
    )
    private fun price(m: String, l: String, r: Double, min: Double, max: Double, t: String, h: List<Int>) = Price("${l}_$m", m, r, UPDATED, l, min, max, t, h.map { it.toDouble() })
}

private fun Price.toEntity() = PriceEntity(id, location, materialLabel, ratePerKg, minRatePerKg, maxRatePerKg, updatedAtEpochMs, trend, history.joinToString(","))
private fun PriceEntity.toDomain() = Price(id, materialLabel, ratePerKg, updatedAtEpochMs, location, minRatePerKg, maxRatePerKg, trend, historyCsv.split(",").mapNotNull { it.toDoubleOrNull() })
