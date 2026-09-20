package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.*
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteBatchRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteExpiry
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import com.google.gson.JsonObject

@Entity(tableName = "quotes")
data class QuoteEntity(@PrimaryKey val id: String, val recyclerId: String, val lotId: String, val amountRupees: Double, val recyclerName: String, val pricePerKg: Double, val marketRatePerKg: Double, val distanceKm: Double, val pickupAvailable: Boolean, val createdAtEpochMs: Long, val expiresAtEpochMs: Long, val status: String, val deliveryState: String)
@Dao interface QuoteDao {
    @Query("SELECT * FROM quotes WHERE lotId = :lotId ORDER BY pricePerKg DESC") fun observeForLot(lotId: String): Flow<List<QuoteEntity>>
    @Query("SELECT q.* FROM quotes q INNER JOIN lots l ON q.lotId = l.id WHERE q.lotId = :lotId AND (l.collectorId = :accountId OR q.recyclerId = :accountId) ORDER BY q.pricePerKg DESC") fun observeForLotForAccount(lotId: String, accountId: String): Flow<List<QuoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<QuoteEntity>)
    @Query("UPDATE quotes SET status = :status WHERE id = :id") suspend fun updateStatus(id: String, status: String): Int
    @Query("UPDATE quotes SET status = :status WHERE id = :id AND EXISTS (SELECT 1 FROM lots WHERE lots.id = quotes.lotId AND (lots.collectorId = :accountId OR quotes.recyclerId = :accountId))") suspend fun updateStatusForAccount(id: String, status: String, accountId: String): Int
    @Query("DELETE FROM quotes WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM quotes WHERE id = :id AND EXISTS (SELECT 1 FROM lots WHERE lots.id = quotes.lotId AND (lots.collectorId = :accountId OR quotes.recyclerId = :accountId))") suspend fun deleteForAccount(id: String, accountId: String): Int
    @Query("DELETE FROM quotes") suspend fun clearAll()
}

class RoomQuoteRepository(private val dao: QuoteDao, private val accountId: () -> String? = { null }) : QuoteRepository {
    override fun observeForLot(lotId: String): Flow<List<Quote>> = (accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForLotForAccount(lotId, it) } ?: flowOf(emptyList())).map { list -> list.map { it.toDomain() }.map { if (QuoteExpiry.isExpired(it, System.currentTimeMillis())) it.copy(status = QuoteStatus.EXPIRED) else it } }
    override suspend fun submitRequest(lot: Lot, recycler: Recycler, nowEpochMs: Long): List<Quote> {
        val base = recycler.offeredRatePerKg
        val prices = listOf(base, base * 0.94, base * 1.06)
        val result = prices.mapIndexed { index, price -> Quote("Q-${nowEpochMs}-${index + 1}", recycler.id, lot.id, price * lot.weightKg, recycler.name, price, lot.estimatedValueRupees?.div(lot.weightKg).takeUnless { it == 0.0 } ?: price, recycler.distanceKm ?: 0.0, recycler.pickupAvailable, nowEpochMs, nowEpochMs + 24L * 60L * 60L * 1000L, QuoteStatus.PENDING, QuoteDeliveryState.RESPONSE_RECEIVED) }
        dao.insertAll(result.map { it.toEntity() })
        return result
    }
    override suspend fun accept(quoteId: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quoteId, QuoteStatus.ACCEPTED.name, it) > 0 } ?: false
    override suspend fun reject(quoteId: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quoteId, QuoteStatus.REJECTED.name, it) > 0 } ?: false
}

/** Live quote integration used outside the debug preview. Room remains the
 * read cache so the comparison screen stays usable when the request response
 * has already been received and the next screen is opened offline. */
class RemoteQuoteRepository(
    private val dao: QuoteDao,
    private val api: ApiService,
    private val queue: SyncQueueDao? = null,
    private val requestSync: () -> Unit = {},
    private val accountId: () -> String? = { null }
) : QuoteRepository {
    override fun observeForLot(lotId: String): Flow<List<Quote>> = (accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForLotForAccount(lotId, it) } ?: flowOf(emptyList())).map { list -> list.map { it.toDomain() }.map { if (QuoteExpiry.isExpired(it, System.currentTimeMillis())) it.copy(status = QuoteStatus.EXPIRED) else it } }

    override suspend fun refresh(lot: Lot, recycler: Recycler?): List<Quote> {
        val quotes = api.getPendingQuotes(lot.id).requireData().map { it.toDomain(lot, recycler) }
        dao.insertAll(quotes.map { it.toEntity() })
        return quotes
    }

    override suspend fun submitRequest(lot: Lot, recycler: Recycler, nowEpochMs: Long): List<Quote> {
        return try {
            api.requestQuote(QuoteRequestDto(lot.id, recycler.id)).requireData()
            val quotes = api.getPendingQuotes(lot.id).requireData().map { it.toDomain(lot, recycler) }
            dao.insertAll(quotes.map { it.toEntity() })
            quotes
        } catch (_: IOException) {
            val placeholder = Quote(
                id = "QRQ-${UUID.randomUUID()}", recyclerId = recycler.id, lotId = lot.id,
                amountRupees = lot.estimatedValueRupees ?: recycler.offeredRatePerKg * lot.weightKg,
                recyclerName = recycler.name, pricePerKg = recycler.offeredRatePerKg,
                marketRatePerKg = lot.estimatedValueRupees?.div(lot.weightKg)?.takeUnless { it == 0.0 } ?: recycler.offeredRatePerKg,
                distanceKm = recycler.distanceKm ?: 0.0, pickupAvailable = recycler.pickupAvailable,
                createdAtEpochMs = nowEpochMs, expiresAtEpochMs = nowEpochMs + 24L * 60L * 60L * 1000L,
                status = QuoteStatus.PENDING, deliveryState = QuoteDeliveryState.WAITING_TO_SEND
            )
            dao.insertAll(listOf(placeholder.toEntity()))
            enqueue("REQUEST_QUOTE", JsonObject().apply { addProperty("id", placeholder.id); addProperty("lotId", lot.id); addProperty("recyclerId", recycler.id) })
            listOf(placeholder)
        }
    }

    override suspend fun submitBatchRequest(lot: Lot, recyclers: List<Recycler>, nowEpochMs: Long): List<Quote> {
        val selected = recyclers.distinctBy { it.id }.take(10)
        if (selected.isEmpty()) return emptyList()
        return try {
            api.requestQuoteBatch(QuoteBatchRequestDto(lot.id, selected.map { it.id })).requireData()
            refresh(lot)
        } catch (_: IOException) {
            // Preserve the existing offline contract for each recipient. The
            // backend batch endpoint remains the preferred online path.
            selected.flatMap { submitRequest(lot, it, nowEpochMs) }
        }
    }

    override suspend fun accept(quoteId: String): Boolean {
        return try {
            val quote = api.acceptQuote(quoteId).requireData()
            (accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quote.id, QuoteStatus.ACCEPTED.name, it) } ?: 0) > 0
        } catch (_: IOException) {
            val updated = (accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quoteId, QuoteStatus.ACCEPTED.name, it) } ?: 0) > 0
            if (updated) enqueue("ACCEPT_QUOTE", JsonObject().apply { addProperty("id", quoteId) })
            updated
        }
    }

    override suspend fun reject(quoteId: String): Boolean {
        return try {
            val quote = api.rejectQuote(quoteId).requireData()
            (accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quote.id, QuoteStatus.REJECTED.name, it) } ?: 0) > 0
        } catch (_: IOException) {
            val updated = (accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateStatusForAccount(quoteId, QuoteStatus.REJECTED.name, it) } ?: 0) > 0
            if (updated) enqueue("REJECT_QUOTE", JsonObject().apply { addProperty("id", quoteId) })
            updated
        }
    }

    private suspend fun enqueue(operation: String, payload: JsonObject) {
        queue?.enqueue(SyncQueueItemEntity(operation = operation, payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = accountId()))
        requestSync()
    }
}

private fun com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteDto.toDomain(lot: Lot, fallback: Recycler?): Quote {
    val created = createdAt?.let(::parseQuoteTimestamp) ?: System.currentTimeMillis()
    val expires = validUntil?.let(::parseQuoteTimestamp) ?: created + 24L * 60L * 60L * 1000L
    return Quote(
        id = id,
        recyclerId = recyclerId,
        lotId = lotId,
        amountRupees = totalQuotedPrice ?: totalPrice ?: pricePerKg * lot.weightKg,
        recyclerName = recycler?.name ?: fallback?.name ?: "Recycler",
        pricePerKg = pricePerKg,
        marketRatePerKg = lot.estimatedValueRupees?.div(lot.weightKg)?.takeUnless { it == 0.0 } ?: pricePerKg,
        distanceKm = recycler?.distanceKm ?: fallback?.distanceKm ?: 0.0,
        pickupAvailable = recycler?.pickupAvailability == "TODAY" || recycler?.pickupAvailability == "THIS_WEEK" || fallback?.pickupAvailable == true,
        createdAtEpochMs = created,
        expiresAtEpochMs = expires,
        status = status.toLocalQuoteStatus(),
        deliveryState = QuoteDeliveryState.RESPONSE_RECEIVED
    )
}

private fun String?.toLocalQuoteStatus(): QuoteStatus = when (this?.uppercase()) {
    // The API calls an offer SENT; the collector UI calls the same actionable
    // state PENDING. Keep the translation at the boundary so accept/reject
    // controls remain available for live offers.
    "SENT", "PENDING" -> QuoteStatus.PENDING
    "DRAFT" -> QuoteStatus.DRAFT
    "ACCEPTED" -> QuoteStatus.ACCEPTED
    "REJECTED" -> QuoteStatus.REJECTED
    "EXPIRED" -> QuoteStatus.EXPIRED
    else -> QuoteStatus.DRAFT
}

private fun parseQuoteTimestamp(value: String): Long? = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssX"
).firstNotNullOfOrNull { pattern ->
    runCatching { java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(value)?.time }.getOrNull()
}
private fun QuoteEntity.toDomain() = Quote(id, recyclerId, lotId, amountRupees, recyclerName, pricePerKg, marketRatePerKg, distanceKm, pickupAvailable, createdAtEpochMs, expiresAtEpochMs, status.toLocalQuoteStatus(), runCatching { QuoteDeliveryState.valueOf(deliveryState) }.getOrDefault(QuoteDeliveryState.SAVED_LOCALLY))
private fun Quote.toEntity() = QuoteEntity(id, recyclerId, lotId, amountRupees, recyclerName, pricePerKg, marketRatePerKg, distanceKm, pickupAvailable, createdAtEpochMs, expiresAtEpochMs, status.name, deliveryState.name)
