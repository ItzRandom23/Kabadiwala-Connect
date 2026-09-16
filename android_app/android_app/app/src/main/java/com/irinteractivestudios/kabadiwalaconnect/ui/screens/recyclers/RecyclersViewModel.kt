package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerMatcher
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerFilterEngine
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerFilters
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerSortMode
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Recyclers tab state: cached authorized recyclers, offline-first.
 * Recycler discovery reads the Room cache populated by catalog refresh.
 */
class RecyclersViewModel(
    recyclers: RecyclerRepository,
    connectivity: ConnectivityObserver,
    private val api: ApiService? = null
) : ViewModel() {

    private var refreshCatalogs: (suspend (CurrentLocation?) -> Unit)? = null

    fun setCatalogRefresher(refresher: suspend (CurrentLocation?) -> Unit) { refreshCatalogs = refresher }
    suspend fun refreshCatalogs(location: CurrentLocation?) { refreshCatalogs?.invoke(location) }

    private val _filters = MutableStateFlow(RecyclerFilters())
    private val _matched = MutableStateFlow<List<Recycler>?>(null)
    fun clearMatches() { _matched.value = null }
    fun loadMatches(lotId: String) {
        val service = api ?: return
        viewModelScope.launch {
            runCatching { service.matchRecyclers(lotId).requireData().matches.map { it.recycler.toDomain() } }
                .onSuccess { matches -> if (matches.isNotEmpty()) _matched.value = matches }
        }
    }
    val filters = _filters.asStateFlow()
    fun setQuery(value: String) { _filters.value = _filters.value.copy(query = value) }
    fun setRadius(value: Int) { _filters.value = _filters.value.copy(radiusKm = value) }
    fun setMaterial(value: String) { _filters.value = _filters.value.copy(material = value) }
    fun setPickupOnly(value: Boolean) { _filters.value = _filters.value.copy(pickupOnly = value) }
    fun setSort(value: RecyclerSortMode) { _filters.value = _filters.value.copy(sort = value) }
    fun matchScore(recycler: Recycler) = RecyclerMatcher.score(recycler, _filters.value.material)

    val uiState: StateFlow<UiState<List<Recycler>>> =
        combine(recyclers.observeRecyclers(), _filters, connectivity.state, _matched) { list, filters, connection, matches ->
            val filtered = RecyclerFilterEngine.apply(matches ?: list, filters)
            when {
                filtered.isNotEmpty() && connection == ConnectionState.ONLINE -> UiState.Success(filtered)
                filtered.isNotEmpty() -> UiState.Offline(filtered)
                connection == ConnectionState.ONLINE ->
                    UiState.Empty
                else ->
                    UiState.Offline(null)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private fun RecyclerDto.toDomain() = Recycler(
        id = id,
        name = name,
        authorized = authorizationStatus == "VERIFIED",
        distanceKm = distanceKm,
        area = facilityLocation?.areaName.orEmpty(),
        facility = facilityLocation?.areaName.orEmpty(),
        address = facilityLocation?.areaName.orEmpty(),
        acceptedMaterials = materialsAccepted.map { it.category.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
        offeredRatePerKg = rates.firstOrNull()?.pricePerKg ?: 0.0,
        pickupAvailable = pickupAvailability == "TODAY" || pickupAvailability == "THIS_WEEK",
        operatingHours = operatingHours?.toString().orEmpty(),
        typicalHandoverHours = averageHandoverTime?.filter { it.isDigit() }?.toIntOrNull() ?: 24,
        contactPhone = contact?.phone.orEmpty(),
        latitude = facilityLocation?.latitude,
        longitude = facilityLocation?.longitude,
        authorizationAuthority = authorizationDetails?.authority,
        rating = rating,
        reviewCount = reviewCount,
        completedHandovers = completedHandovers
    )
}
