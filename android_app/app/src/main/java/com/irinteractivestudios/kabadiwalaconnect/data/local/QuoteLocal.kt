package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.*
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteExpiry
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "quotes")
data class QuoteEntity(@PrimaryKey val id: String, val recyclerId: String, val lotId: String, val amountRupees: Double, val recyclerName: String, val pricePerKg: Double, val marketRatePerKg: Double, val distanceKm: Double, val pickupAvailable: Boolean, val createdAtEpochMs: Long, val expiresAtEpochMs: Long, val status: String, val deliveryState: String)
@Dao interface QuoteDao {
    @Query("SELECT * FROM quotes WHERE lotId = :lotId ORDER BY pricePerKg DESC") fun observeForLot(lotId: String): Flow<List<QuoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<QuoteEntity>)
    @Query("UPDATE quotes SET status = :status WHERE id = :id") suspend fun updateStatus(id: String, status: String): Int
    @Query("DELETE FROM quotes") suspend fun clearAll()
}

class RoomQuoteRepository(private val dao: QuoteDao) : QuoteRepository {
    override fun observeForLot(lotId: String): Flow<List<Quote>> = dao.observeForLot(lotId).map { list -> list.map { it.toDomain() }.map { if (QuoteExpiry.isExpired(it, System.currentTimeMillis())) it.copy(status = QuoteStatus.EXPIRED) else it } }
    override suspend fun submitRequest(lot: Lot, recycler: Recycler, nowEpochMs: Long): List<Quote> {
        val base = recycler.offeredRatePerKg
        val prices = listOf(base, base * 0.94, base * 1.06)
        val result = prices.mapIndexed { index, price -> Quote("Q-${nowEpochMs}-${index + 1}", recycler.id, lot.id, price * lot.weightKg, recycler.name, price, lot.estimatedValueRupees?.div(lot.weightKg).takeUnless { it == 0.0 } ?: price, recycler.distanceKm ?: 0.0, recycler.pickupAvailable, nowEpochMs, nowEpochMs + 24L * 60L * 60L * 1000L, QuoteStatus.PENDING, QuoteDeliveryState.RESPONSE_RECEIVED) }
        dao.insertAll(result.map { it.toEntity() })
        return result
    }
    override suspend fun accept(quoteId: String) = dao.updateStatus(quoteId, QuoteStatus.ACCEPTED.name) > 0
    override suspend fun reject(quoteId: String) = dao.updateStatus(quoteId, QuoteStatus.REJECTED.name) > 0
}

/** Live quote integration used outside the debug preview. Room remains the
 * read cache so the comparison screen stays usable when the request response
 * has already been received and the next screen is opened offline. */
class RemoteQuoteRepository(private val dao: QuoteDao, private val api: ApiService) : QuoteRepository {
    override fun observeForLot(lotId: String): Flow<List<Quote>> = dao.observeForLot(lotId).map { list -> list.map { it.toDomain() }.map { if (QuoteExpiry.isExpired(it, System.currentTimeMillis())) it.copy(status = QuoteStatus.EXPIRED) else it } }

    override suspend fun submitRequest(lot: Lot, recycler: Recycler, nowEpochMs: Long): List<Quote> {
        api.requestQuote(QuoteRequestDto(lot.id, recycler.id)).requireData()
        val quotes = api.getPendingQuotes(lot.id).requireData().map { it.toDomain(lot, recycler) }
        dao.insertAll(quotes.map { it.toEntity() })
        return quotes
    }

    override suspend fun accept(quoteId: String): Boolean {
        val quote = api.acceptQuote(quoteId).requireData()
        return dao.updateStatus(quote.id, QuoteStatus.ACCEPTED.name) > 0
    }

    override suspend fun reject(quoteId: String): Boolean {
        val quote = api.rejectQuote(quoteId).requireData()
        return dao.updateStatus(quote.id, QuoteStatus.REJECTED.name) > 0
    }
}

private fun com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteDto.toDomain(lot: Lot, fallback: Recycler): Quote {
    val created = createdAt?.let(::parseQuoteTimestamp) ?: System.currentTimeMillis()
    val expires = validUntil?.let(::parseQuoteTimestamp) ?: created + 24L * 60L * 60L * 1000L
    return Quote(
        id = id,
        recyclerId = recyclerId,
        lotId = lotId,
        amountRupees = totalQuotedPrice ?: totalPrice ?: pricePerKg * lot.weightKg,
        recyclerName = recycler?.name ?: fallback.name,
        pricePerKg = pricePerKg,
        marketRatePerKg = lot.estimatedValueRupees?.div(lot.weightKg)?.takeUnless { it == 0.0 } ?: pricePerKg,
        distanceKm = recycler?.distanceKm ?: fallback.distanceKm ?: 0.0,
        pickupAvailable = recycler?.pickupAvailability == "TODAY" || recycler?.pickupAvailability == "THIS_WEEK" || fallback.pickupAvailable,
        createdAtEpochMs = created,
        expiresAtEpochMs = expires,
        status = runCatching { QuoteStatus.valueOf(status) }.getOrDefault(QuoteStatus.PENDING),
        deliveryState = QuoteDeliveryState.RESPONSE_RECEIVED
    )
}

private fun parseQuoteTimestamp(value: String): Long? = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssX"
).firstNotNullOfOrNull { pattern ->
    runCatching { java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(value)?.time }.getOrNull()
}
private fun QuoteEntity.toDomain() = Quote(id, recyclerId, lotId, amountRupees, recyclerName, pricePerKg, marketRatePerKg, distanceKm, pickupAvailable, createdAtEpochMs, expiresAtEpochMs, runCatching { QuoteStatus.valueOf(status) }.getOrDefault(QuoteStatus.PENDING), runCatching { QuoteDeliveryState.valueOf(deliveryState) }.getOrDefault(QuoteDeliveryState.SAVED_LOCALLY))
private fun Quote.toEntity() = QuoteEntity(id, recyclerId, lotId, amountRupees, recyclerName, pricePerKg, marketRatePerKg, distanceKm, pickupAvailable, createdAtEpochMs, expiresAtEpochMs, status.name, deliveryState.name)
