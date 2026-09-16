package com.irinteractivestudios.kabadiwalaconnect.data.repository

import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeForLot(lotId: String): Flow<List<Quote>>
    suspend fun submitRequest(lot: Lot, recycler: Recycler, nowEpochMs: Long = System.currentTimeMillis()): List<Quote>
    suspend fun submitBatchRequest(lot: Lot, recyclers: List<Recycler>, nowEpochMs: Long = System.currentTimeMillis()): List<Quote> = recyclers.flatMap { submitRequest(lot, it, nowEpochMs) }
    /** Refresh server-owned offers while keeping Room as the UI source of truth. */
    suspend fun refresh(lot: Lot, recycler: Recycler? = null): List<Quote> = emptyList()
    suspend fun accept(quoteId: String): Boolean
    suspend fun reject(quoteId: String): Boolean
}

object QuoteExpiry {
    fun isExpired(quote: com.irinteractivestudios.kabadiwalaconnect.domain.model.Quote, nowEpochMs: Long): Boolean = quote.status == com.irinteractivestudios.kabadiwalaconnect.domain.model.QuoteStatus.PENDING && quote.expiresAtEpochMs <= nowEpochMs
}
