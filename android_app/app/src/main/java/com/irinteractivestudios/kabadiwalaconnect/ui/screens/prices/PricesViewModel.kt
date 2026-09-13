package com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Prices tab state: cached market prices, offline-first.
 * Empty on a fresh install (no fabricated prices); populated by the backend
 * catalog refresh when a session and connection are available.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PricesViewModel(
    prices: PriceRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {

    private val catalog = prices as? PriceCatalogRepository
    private val _location = MutableStateFlow("Pune")
    val selectedLocation = _location.asStateFlow()
    val locations: StateFlow<List<String>> = catalog?.observeLocations()?.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("Pune"))
        ?: MutableStateFlow(listOf("Pune")).asStateFlow()
    fun selectLocation(location: String) { _location.value = location }

    private var catalogRefresher: suspend (String) -> Unit = {}
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed = _refreshFailed.asStateFlow()

    fun setCatalogRefresher(refresher: suspend (String) -> Unit) {
        catalogRefresher = refresher
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _refreshFailed.value = false
            runCatching { catalogRefresher(_location.value) }
                .onFailure { _refreshFailed.value = true }
            _refreshing.value = false
        }
    }

    val uiState: StateFlow<UiState<List<Price>>> =
        _location.flatMapLatest { location -> (catalog?.observePrices(location) ?: prices.observePrices()) }
            .combine(connectivity.state) { list, connection ->
            when {
                list.isNotEmpty() && connection == ConnectionState.ONLINE ->
                    UiState.Success(list)
                list.isNotEmpty() ->
                    UiState.Offline(list)
                connection == ConnectionState.ONLINE ->
                    UiState.Empty
                else ->
                    UiState.Offline(null)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}
