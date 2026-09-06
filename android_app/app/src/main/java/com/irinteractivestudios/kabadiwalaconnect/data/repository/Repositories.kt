package com.irinteractivestudios.kabadiwalaconnect.data.repository

import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Dispute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository contracts (Phase 1).
 *
 * UI layers depend ONLY on these interfaces. Current implementations are
 * local-cache fakes returning empty data (a fresh install has no cache);
 * Room-backed and network-backed implementations arrive with their
 * feature phases without touching the UI.
 */

interface LotRepository {
    fun observeLots(): Flow<List<Lot>>
}

interface LotWriter {
    fun observeLot(id: String): Flow<Lot?>
    suspend fun save(lot: Lot)
    suspend fun cancel(id: String, updatedAt: Long): Boolean
    suspend fun confirm(id: String, updatedAt: Long): Boolean = false
    suspend fun markPaid(id: String, amount: Double, updatedAt: Long): Boolean = false
}

interface PriceRepository {
    fun observePrices(): Flow<List<Price>>
}

interface PriceCatalogRepository : PriceRepository {
    fun observePrices(location: String): Flow<List<Price>>
    fun observeLocations(): Flow<List<String>>
    override fun observePrices(): Flow<List<Price>> = observePrices("Pune")
}

interface RecyclerRepository {
    fun observeRecyclers(): Flow<List<Recycler>>
}

interface RecyclerCatalogRepository : RecyclerRepository {
    fun observeRecycler(id: String): Flow<Recycler?>
}

interface EarningsRepository {
    fun observeSummary(): Flow<EarningsSummary>
}

interface PaymentRepository : EarningsRepository {
    fun observePayments(): Flow<List<com.irinteractivestudios.kabadiwalaconnect.domain.model.Payment>>
    suspend fun record(payment: com.irinteractivestudios.kabadiwalaconnect.domain.model.Payment)
}

interface DisputeRepository {
    fun observeForHandover(handoverId: String): Flow<List<Dispute>>
    suspend fun save(dispute: Dispute)
    suspend fun markSynced(id: String, remoteId: String): Boolean
}

/**
 * Phase 1 fakes: always return empty local state.
 * They deliberately do NOT invent prices/recyclers/earnings — the screens
 * show the Empty state until real cached data exists.
 */
class FakeLotRepository : LotRepository {
    private val lots = MutableStateFlow<List<Lot>>(emptyList())
    override fun observeLots(): Flow<List<Lot>> = lots.asStateFlow()
}

class FakePriceRepository : PriceRepository {
    private val prices = MutableStateFlow<List<Price>>(emptyList())
    override fun observePrices(): Flow<List<Price>> = prices.asStateFlow()
}

class FakeRecyclerRepository : RecyclerRepository {
    private val recyclers = MutableStateFlow<List<Recycler>>(emptyList())
    override fun observeRecyclers(): Flow<List<Recycler>> = recyclers.asStateFlow()
}

class FakeEarningsRepository : EarningsRepository {
    private val summary = MutableStateFlow(EarningsSummary())
    override fun observeSummary(): Flow<EarningsSummary> = summary.asStateFlow()
}
