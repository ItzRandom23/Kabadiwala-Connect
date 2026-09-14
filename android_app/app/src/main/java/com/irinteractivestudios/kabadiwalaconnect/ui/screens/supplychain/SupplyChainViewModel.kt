package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SupplyChainState(
    val loading: Boolean = false,
    val error: String? = null,
    val listings: List<HouseholdListingDto> = emptyList(),
    val kabadiwalas: List<KabadiwalaProfileDto> = emptyList(),
    val pickups: List<PickupRequestDto> = emptyList(),
    val inventory: List<InventoryBalanceDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val requirements: List<ProcurementRequirementDto> = emptyList(),
    val busy: Set<String> = emptySet(),
    val notice: String? = null
)

class SupplyChainViewModel(
    private val api: ApiService,
    private val roleProvider: () -> AccountRole? = { null }
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
        load {
        val listings = api.getKabadiwalaListings().requireData()
        val pickups = api.getKabadiwalaPickups().requireData()
        val inventory = api.getKabadiwalaInventory().requireData()
        val requirements = api.getProcurementRequirements().requireData()
        val offers = api.getKabadiwalaBulkOffers().requireData()
        val bulkLots = api.getKabadiwalaBulkLots().requireData()
        _state.value = _state.value.copy(loading = false, listings = listings, pickups = pickups, inventory = inventory, bulkLots = bulkLots, offers = offers, requirements = requirements, error = null)
        }
    }
    fun refreshRecycler() {
        if (!allowed(AccountRole.RECYCLER)) return
        load {
        val lots = api.getRecyclerBulkLots().requireData()
        val offers = api.getRecyclerBulkOffers().requireData()
        val requirements = api.getRecyclerProcurementRequirements().requireData()
        _state.value = _state.value.copy(loading = false, bulkLots = lots, offers = offers, requirements = requirements, error = null)
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
    fun createListing(input: HouseholdListingCreateDto) = action("create-listing", AccountRole.HOUSEHOLD, { api.createHouseholdListing(input).requireData(); refreshHousehold(); "Listing posted — choose a nearby Kabadiwala." })
    fun requestPickup(listingId: String, kabadiwalaId: String) = action("pickup-$listingId", AccountRole.HOUSEHOLD, { api.requestHouseholdPickup(listingId, PickupRequestCreateDto(kabadiwalaId)).requireData(); refreshHousehold(); "Pickup request sent." })
    fun cancelListing(listingId: String, reason: String? = null) = action("cancel-listing-$listingId", AccountRole.HOUSEHOLD, { api.cancelHouseholdListing(listingId, CancellationRequestDto(reason)).requireData(); refreshHousehold(); "Listing cancelled." })
    fun cancelPickup(pickupId: String, reason: String? = null) = action("cancel-pickup-$pickupId", AccountRole.HOUSEHOLD, { api.cancelHouseholdPickup(pickupId, CancellationRequestDto(reason)).requireData(); refreshHousehold(); "Pickup cancelled." })
    fun acceptListing(listingId: String) = action("accept-$listingId", AccountRole.COLLECTOR, { api.acceptHouseholdListing(listingId).requireData(); refreshKabadiwala(); "Pickup accepted." })
    fun schedulePickup(pickupId: String, iso: String) = action("schedule-$pickupId", AccountRole.COLLECTOR, { api.schedulePickup(pickupId, PickupScheduleDto(iso)).requireData(); refreshKabadiwala(); "Pickup scheduled." })
    fun pickupStatus(pickupId: String, status: String) = action("status-$pickupId", AccountRole.COLLECTOR, { api.updatePickupStatus(pickupId, PickupStatusDto(status)).requireData(); refreshKabadiwala(); "Pickup updated." })
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
    fun makeOffer(lotId: String, rate: Double) = action("offer-$lotId", AccountRole.RECYCLER, { api.makeBulkLotOffer(lotId, BulkOfferCreateDto(rate)).requireData(); refreshRecycler(); "Offer sent to the Kabadiwala." })
    fun receiveLot(lotId: String) = action("receive-$lotId", AccountRole.RECYCLER, { api.receiveBulkLot(lotId).requireData(); refreshRecycler(); "Receipt confirmed." })
    fun createRequirement(input: ProcurementRequirementCreateDto) = action("create-demand", AccountRole.RECYCLER, { api.createProcurementRequirement(input).requireData(); refreshRecycler(); "Requirement published to Kabadiwalas." })
}
