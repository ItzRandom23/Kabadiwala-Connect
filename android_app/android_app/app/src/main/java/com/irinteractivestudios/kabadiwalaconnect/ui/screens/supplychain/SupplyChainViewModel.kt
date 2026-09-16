package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationSnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.Gson
import java.io.IOException

data class SupplyChainState(
    val loading: Boolean = false,
    val error: String? = null,
    val listings: List<HouseholdListingDto> = emptyList(),
    val kabadiwalas: List<KabadiwalaProfileDto> = emptyList(),
    val pickups: List<PickupRequestDto> = emptyList(),
    val inventory: List<InventoryBalanceDto> = emptyList(),
    val inventoryMovements: List<InventoryMovementDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val requirements: List<ProcurementRequirementDto> = emptyList(),
    val routeAdvantage: RouteAdvantageResponseDto? = null,
    val poolOpportunities: List<PoolOpportunityDto> = emptyList(),
    val poolSuggestions: List<PoolSuggestionDto> = emptyList(),
    val demandIntelligence: List<JsonObject> = emptyList(),
    val pools: List<PooledConsignmentDto> = emptyList(),
    val handovers: List<SupplyHandoverDto> = emptyList(),
    val passport: CollectorPassportDto? = null,
    val safety: SafetyResponseDto? = null,
    val safetyRouting: SafetyRoutingResponseDto? = null,
    val materialPassports: Map<String, MaterialPassportResponseDto> = emptyMap(),
    val anomalies: Map<String, AnomalyResponseDto> = emptyMap(),
    val showingCachedEvidence: Boolean = false,
    val cachedAtEpochMs: Long = 0L,
    val busy: Set<String> = emptySet(),
    val notice: String? = null
)

class SupplyChainViewModel(
    private val api: ApiService,
    private val roleProvider: () -> AccountRole? = { null },
    private val cache: FormalisationCacheStore? = null,
    private val accountIdProvider: () -> String? = { null },
    private val idempotencyKeys: IdempotencyKeyStore? = null,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: (() -> Unit)? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SupplyChainState())
    val state: StateFlow<SupplyChainState> = _state.asStateFlow()

    private fun friendly(error: Throwable) = when ((error as? RemoteApiException)?.code) {
        "HTTP_401", "TOKEN_EXPIRED" -> "Your session expired. Please sign in again."
        "HTTP_403" -> "This action is not available for your role."
        "HTTP_409" -> "That record changed. Refresh and try again."
        "HTTP_422" -> "Check the highlighted details and try again."
        else -> "Could not reach the recycling network. Check your connection and retry."
    }

    private fun allowed(role: AccountRole): Boolean = roleProvider()?.let { it == role } ?: true

    private fun accountId() = accountIdProvider()

    private fun applyCached(snapshot: FormalisationSnapshot) {
        _state.value = _state.value.copy(
            routeAdvantage = snapshot.routeAdvantage,
            poolOpportunities = snapshot.poolOpportunities,
            pools = snapshot.pools,
            bulkLots = if (_state.value.bulkLots.isEmpty()) snapshot.bulkLots else _state.value.bulkLots,
            offers = if (_state.value.offers.isEmpty()) snapshot.offers else _state.value.offers,
            handovers = snapshot.handovers,
            passport = snapshot.passport,
            safety = snapshot.safety,
            showingCachedEvidence = true,
            cachedAtEpochMs = snapshot.cachedAtEpochMs
        )
    }

    private fun saveCache() {
        cache?.save(accountId(), FormalisationSnapshot(
            routeAdvantage = _state.value.routeAdvantage,
            poolOpportunities = _state.value.poolOpportunities,
            pools = _state.value.pools,
            bulkLots = _state.value.bulkLots,
            offers = _state.value.offers,
            handovers = _state.value.handovers,
            passport = _state.value.passport,
            safety = _state.value.safety
        ))
    }

    fun refreshHousehold() {
        if (!allowed(AccountRole.HOUSEHOLD)) return
        load {
        val listings = api.getHouseholdListings().requireData()
        val pickups = api.getHouseholdPickups().requireData()
        val kabadiwalas = api.getHouseholdKabadiwalas().requireData()
        _state.value = _state.value.copy(loading = false, listings = listings, pickups = pickups, kabadiwalas = kabadiwalas, error = null)
        }
    }
    fun refreshKabadiwala() {
        if (!allowed(AccountRole.COLLECTOR)) return
        cache?.load(accountId())?.let(::applyCached)
        load {
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (_: Exception) { partialFailure = true; fallback }
        val previous = _state.value
        val listings = optional(previous.listings) { api.getKabadiwalaListings().requireData() }
        val pickups = optional(previous.pickups) { api.getKabadiwalaPickups().requireData() }
        val inventory = optional(previous.inventory) { api.getKabadiwalaInventory().requireData() }
        val inventoryMovements = optional(previous.inventoryMovements) { api.getInventoryMovements(limit = 100).requireData() }
        val requirements = optional(previous.requirements) { api.getProcurementRequirements().requireData() }
        val offers = optional(previous.offers) { api.getKabadiwalaBulkOffers().requireData() }
        val bulkLots = optional(previous.bulkLots) { api.getKabadiwalaBulkLots().requireData() }
        val opportunities = optional(previous.poolOpportunities) { api.getPoolOpportunities().requireData() }
        val suggestions = optional(previous.poolSuggestions) { api.getPoolSuggestions().requireData() }
        val demandIntelligence = optional(previous.demandIntelligence) { api.getDemandIntelligence().requireData() }
        val pools = optional(previous.pools) { api.getKabadiwalaPools().requireData() }
        val handovers = optional(previous.handovers) { api.getKabadiwalaHandovers().requireData() }
        val passport = optional(previous.passport) { api.getCollectorPassport().requireData() }
        val safety = optional(previous.safety) { api.getSafety().requireData() }
        _state.value = _state.value.copy(loading = false, listings = listings, pickups = pickups, inventory = inventory, inventoryMovements = inventoryMovements, bulkLots = bulkLots, offers = offers, requirements = requirements, poolOpportunities = opportunities, poolSuggestions = suggestions, demandIntelligence = demandIntelligence, pools = pools, handovers = handovers, passport = passport, safety = safety, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Some saved evidence is shown because the network is unavailable." else null)
        saveCache()
        }
    }
    fun refreshRecycler() {
        if (!allowed(AccountRole.RECYCLER)) return
        cache?.load(accountId())?.let(::applyCached)
        load {
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (_: Exception) { partialFailure = true; fallback }
        val previous = _state.value
        val lots = optional(previous.bulkLots) { api.getRecyclerBulkLots().requireData() }
        val offers = optional(previous.offers) { api.getRecyclerBulkOffers().requireData() }
        val requirements = optional(previous.requirements) { api.getRecyclerProcurementRequirements().requireData() }
        val pools = optional(previous.pools) { api.getRecyclerPools().requireData() }
        val handovers = optional(previous.handovers) { api.getSupplyHandovers().requireData() }
        _state.value = _state.value.copy(loading = false, bulkLots = lots, offers = offers, requirements = requirements, pools = pools, handovers = handovers, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Some saved evidence is shown because the network is unavailable." else null)
        saveCache()
        }
    }
    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            runCatching { block() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendly(it)) }
        }
    }
    private fun action(key: String, requiredRole: AccountRole? = null, block: suspend () -> String = { "Done" }) {
        if (requiredRole != null && !allowed(requiredRole)) return
        if (key in _state.value.busy) return
        _state.value = _state.value.copy(busy = _state.value.busy + key, error = null, notice = null)
        viewModelScope.launch {
            runCatching { block() }.onSuccess { _state.value = _state.value.copy(notice = it) }.onFailure { _state.value = _state.value.copy(error = friendly(it)) }
            _state.value = _state.value.copy(busy = _state.value.busy - key)
        }
    }
    fun createListing(input: HouseholdListingCreateDto) = action("create-listing", AccountRole.HOUSEHOLD, {
        val operation = "listing-${input.materialCategory}-${input.areaName}-${input.estimatedWeight}"
        api.createHouseholdListing(input, idempotencyKeys?.getOrCreate(operation)).requireData()
        idempotencyKeys?.clear(operation)
        refreshHousehold()
        "Listing posted — choose a nearby Kabadiwala."
    })
    fun requestPickup(listingId: String, kabadiwalaId: String) = action("pickup-$listingId", AccountRole.HOUSEHOLD, {
        val operation = "pickup-$listingId-$kabadiwalaId"
        val key = idempotencyKeys?.getOrCreate(operation) ?: "pickup-$listingId-$kabadiwalaId"
        try {
            api.requestHouseholdPickup(listingId, PickupRequestCreateDto(kabadiwalaId), key).requireData()
            idempotencyKeys?.clear(operation)
            refreshHousehold()
            "Pickup request sent."
        } catch (error: Throwable) {
            val transient = error is IOException || ((error as? RemoteApiException)?.httpCode ?: 0) >= 500
            if (!transient || syncQueue == null) throw error
            val payload = com.google.gson.JsonObject().apply {
                addProperty("listingId", listingId)
                addProperty("kabadiwalaId", kabadiwalaId)
                addProperty("idempotencyKey", key)
            }
            syncQueue.enqueue(SyncQueueItemEntity(operation = "REQUEST_HOUSEHOLD_PICKUP", payloadJson = Gson().toJson(payload), createdAtEpochMs = System.currentTimeMillis(), accountId = accountId()))
            requestSync?.invoke()
            "Pickup saved offline and will sync when connected."
        }
    })
    fun cancelListing(listingId: String, reason: String? = null) = action("cancel-listing-$listingId", AccountRole.HOUSEHOLD, { api.cancelHouseholdListing(listingId, CancellationRequestDto(reason)).requireData(); refreshHousehold(); "Listing cancelled." })
    fun cancelPickup(pickupId: String, reason: String? = null) = action("cancel-pickup-$pickupId", AccountRole.HOUSEHOLD, { api.cancelHouseholdPickup(pickupId, CancellationRequestDto(reason)).requireData(); refreshHousehold(); "Pickup cancelled." })
    fun reschedulePickup(pickupId: String, scheduledSlot: String) = action("reschedule-$pickupId", AccountRole.HOUSEHOLD, { api.rescheduleHouseholdPickup(pickupId, PickupRescheduleDto(scheduledSlot)).requireData(); refreshHousehold(); "Pickup rescheduled." })
    fun decideHouseholdSettlement(pickupId: String, decision: String, reasonCode: String? = null, notes: String? = null) = action("settlement-$pickupId", AccountRole.HOUSEHOLD, { api.decideHouseholdSettlement(pickupId, SettlementDecisionDto(decision, reasonCode, null, notes)).requireData(); refreshHousehold(); "Settlement decision recorded." })
    fun acceptListing(listingId: String) = action("accept-$listingId", AccountRole.COLLECTOR, { api.acceptHouseholdListing(listingId).requireData(); refreshKabadiwala(); "Pickup accepted." })
    fun rejectPickup(pickupId: String, reason: String? = null) = action("reject-$pickupId", AccountRole.COLLECTOR, { api.rejectKabadiwalaPickup(pickupId, BulkOfferDecisionDto(reason)).requireData(); refreshKabadiwala(); "Pickup declined and returned to the network." })
    fun confirmAvailability(pickupId: String, slot: String? = null) = action("availability-$pickupId", AccountRole.COLLECTOR, { api.confirmPickupAvailability(pickupId, PickupAvailabilityDto(true, slot)).requireData(); refreshKabadiwala(); "Availability confirmed." })
    fun schedulePickup(pickupId: String, iso: String) = action("schedule-$pickupId", AccountRole.COLLECTOR, { api.schedulePickup(pickupId, PickupScheduleDto(iso)).requireData(); refreshKabadiwala(); "Pickup scheduled." })
    fun pickupStatus(pickupId: String, status: String) = action("status-$pickupId", AccountRole.COLLECTOR, { api.updatePickupStatus(pickupId, PickupStatusDto(status)).requireData(); refreshKabadiwala(); "Pickup updated." })
    fun cancelKabadiwalaPickup(pickupId: String, reason: String? = null) = action("cancel-collector-$pickupId", AccountRole.COLLECTOR, { api.cancelKabadiwalaPickup(pickupId, CancellationRequestDto(reason)).requireData(); refreshKabadiwala(); "Pickup cancelled." })
    fun reassignPickup(pickupId: String, reason: String, noShow: Boolean = false) = action("reassign-$pickupId", AccountRole.COLLECTOR, { api.reassignPickup(pickupId, PickupReassignDto(reason, noShow)).requireData(); refreshKabadiwala(); "Pickup returned to the network for reassignment." })
    fun completePickup(pickupId: String, input: PickupCompletionDto) = action("complete-$pickupId", AccountRole.COLLECTOR, { api.completePickup(pickupId, input).requireData(); refreshKabadiwala(); "Purchase completed and inventory updated." })
    fun createBulkLot(input: BulkLotCreateDto) = action("create-bulk", AccountRole.COLLECTOR) {
        // Keep the returned server record visible immediately. The follow-up
        // refresh reconciles it with the authoritative list, but a slow or
        // partially unavailable catalogue must not make a successful lot look
        // as if it disappeared.
        val created = api.createBulkLot(input).requireData()
        _state.value = _state.value.copy(bulkLots = listOf(created) + _state.value.bulkLots.filterNot { it.id == created.id })
        refreshKabadiwala()
        "Bulk lot listed for verified recyclers."
    }
    fun cancelBulkLot(lotId: String) = action("cancel-bulk-$lotId", AccountRole.COLLECTOR, { api.cancelBulkLot(lotId).requireData(); refreshKabadiwala(); "Bulk lot cancelled and stock released." })
    fun acceptOffer(offerId: String) = action("offer-$offerId", AccountRole.COLLECTOR, { api.acceptBulkOffer(offerId).requireData(); refreshKabadiwala(); "Recycler offer accepted; stock remains reserved." })
    fun rejectOffer(offerId: String, reason: String) = action("reject-offer-$offerId", AccountRole.COLLECTOR, { api.rejectBulkOffer(offerId, BulkOfferDecisionDto(reason.ifBlank { null })).requireData(); refreshKabadiwala(); "Offer rejected." })
    fun counterOffer(offerId: String, rate: Double, notes: String?) = action("counter-offer-$offerId", AccountRole.COLLECTOR, { api.counterBulkOffer(offerId, BulkOfferCounterDto(rate, notes?.ifBlank { null })).requireData(); refreshKabadiwala(); "Counter-offer sent." })
    fun makeOffer(lotId: String, rate: Double) = action("offer-$lotId", AccountRole.RECYCLER, { api.makeBulkLotOffer(lotId, BulkOfferCreateDto(rate)).requireData(); refreshRecycler(); "Offer sent to the Kabadiwala." })
    fun withdrawOffer(offerId: String, reason: String?) = action("withdraw-offer-$offerId", AccountRole.RECYCLER, { api.withdrawRecyclerOffer(offerId, BulkOfferDecisionDto(reason?.ifBlank { null })).requireData(); refreshRecycler(); "Offer withdrawn." })
    fun updateRequirement(requirementId: String, input: ProcurementRequirementUpdateDto) = action("update-demand-$requirementId", AccountRole.RECYCLER, { api.updateProcurementRequirement(requirementId, input).requireData(); refreshRecycler(); "Procurement requirement updated." })
    /**
     * Receiving is intentionally not implemented through the legacy
     * /recycler/bulk-lots/:lotId/receive compatibility guard. The production
     * workflow is the signed formal handover scanner and confirmation form.
     */
    fun receiveLot(lotId: String) = action("receive-$lotId", AccountRole.RECYCLER, { "Scan the signed handover QR to confirm receipt." })
    fun createRequirement(input: ProcurementRequirementCreateDto) = action("create-demand", AccountRole.RECYCLER, { api.createProcurementRequirement(input).requireData(); refreshRecycler(); "Requirement published to Kabadiwalas." })
    fun loadRouteAdvantage(materialCategory: String, quantityKg: Double, grade: String = "UNSPECIFIED") = action("route-advantage", AccountRole.COLLECTOR) {
        val result = api.getRouteAdvantage(materialCategory, quantityKg, grade).requireData()
        _state.value = _state.value.copy(routeAdvantage = result, showingCachedEvidence = false, cachedAtEpochMs = System.currentTimeMillis())
        saveCache()
        if (result.baseline == null) "Verified routes found, but there is not enough baseline data to claim savings." else "Route estimate ready. Confirm logistics and rate at handover."
    }
    fun loadSafetyRouting(materialCategory: String, condition: String) = action("safety-routing", AccountRole.COLLECTOR) {
        val result = api.getSafetyRouting(materialCategory, condition).requireData()
        _state.value = _state.value.copy(safetyRouting = result)
        "Safety route loaded. Follow the handling instruction before transport."
    }
    fun loadMaterialPassport(handoverId: String) = action("passport-$handoverId") {
        val result = api.getMaterialPassport(handoverId).requireData()
        _state.value = _state.value.copy(materialPassports = _state.value.materialPassports + (handoverId to result))
        "Material passport loaded."
    }
    fun loadAnomalies(handoverId: String) = action("anomalies-$handoverId") {
        val result = api.getHandoverAnomalies(handoverId).requireData()
        _state.value = _state.value.copy(anomalies = _state.value.anomalies + (handoverId to result))
        "Settlement risk review loaded."
    }
    fun decideSupplySettlement(handoverId: String, decision: String, reasonCode: String? = null, evidenceReference: String? = null, notes: String? = null) = action("supply-settlement-$handoverId") {
        api.decideSupplySettlement(handoverId, SettlementDecisionDto(decision, reasonCode, evidenceReference, notes)).requireData()
        refreshKabadiwala()
        "Settlement decision recorded."
    }
    fun createPool(requirementId: String, areaName: String) = action("pool-create-$requirementId", AccountRole.COLLECTOR) { val pool = api.createPool(PoolCreateRequestDto(requirementId, areaName)).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); refreshKabadiwala(); "Cooperative pool opened. Other Kabadiwalas can contribute reserved stock." }
    fun joinPool(poolId: String, quantityKg: Double, grade: String, expectedRatePerKg: Double?) = action("pool-join-$poolId", AccountRole.COLLECTOR) { api.joinPool(poolId, PoolJoinRequestDto(quantityKg, grade, expectedRatePerKg)).requireData(); refreshKabadiwala(); "Stock reserved in the cooperative pool." }
    fun leavePool(poolId: String) = action("pool-leave-$poolId", AccountRole.COLLECTOR) { api.leavePool(poolId).requireData(); refreshKabadiwala(); "Contribution released back to available stock." }
    fun lockPool(poolId: String) = action("pool-lock-$poolId", AccountRole.COLLECTOR) { val pool = api.lockPool(poolId).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); "Pool locked at threshold. Prepare the one-time handover QR." }
    fun preparePoolHandover(poolId: String) = action("handover-pool-$poolId", AccountRole.COLLECTOR) { val handover = api.preparePoolHandover(poolId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
    fun prepareBulkHandover(lotId: String) = action("handover-bulk-$lotId", AccountRole.COLLECTOR) { val handover = api.prepareBulkHandover(lotId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
    fun confirmCollectorHandover(handoverId: String) = action("collector-confirm-$handoverId", AccountRole.COLLECTOR) {
        val operation = "collector-handover-$handoverId"
        val handover = api.confirmCollectorHandover(handoverId, idempotencyKeys?.getOrCreate(operation)).requireData()
        idempotencyKeys?.clear(operation)
        _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id })
        saveCache()
        "Collector confirmation recorded. Recycler must scan this QR."
    }
    fun acknowledgeSafety(moduleKey: String) = action("safety-$moduleKey", AccountRole.COLLECTOR) { api.acknowledgeSafety(moduleKey).requireData(); val safety = api.getSafety().requireData(); _state.value = _state.value.copy(safety = safety); saveCache(); "Safety acknowledgement saved to your growth passport." }
}
