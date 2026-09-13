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

/**
 * Prices tab state: cached market prices, offline-first.
 * Empty on a fresh install (no fabricated prices); populated by the backend
 * catalog refresh when a session and connection are available.
 */
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
