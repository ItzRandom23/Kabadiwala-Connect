package com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices

import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
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
import java.util.Locale
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * Prices tab state: cached market prices, offline-first.
 * Populated by the backend when real rates exist. Debug builds fill missing
 * areas/materials with clearly identified, generated sample rates for UI demos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PricesViewModel(
    prices: PriceRepository,
    connectivity: ConnectivityObserver,
    initialLocation: String? = null
) : ViewModel() {

    private val catalog = prices as? PriceCatalogRepository
    private val _location = MutableStateFlow(initialLocation?.trim().orEmpty())
    val selectedLocation = _location.asStateFlow()
    val locations: StateFlow<List<String>> = catalog?.observeLocations()?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        ?: MutableStateFlow(emptyList<String>()).asStateFlow()
    fun selectLocation(location: String) { _location.value = location.trim() }

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
        _location.flatMapLatest { location ->
            val samples = if (BuildConfig.DEBUG && location.isNotBlank()) samplePricesFor(location) else emptyList()
            (catalog?.observePrices(location) ?: prices.observePrices()).combine(connectivity.state) { list, connection ->
                val missingSamples = samples.filterNot { sample -> list.any { it.materialLabel.equals(sample.materialLabel, ignoreCase = true) } }
                val shown = list + missingSamples
                when {
                    list.isNotEmpty() && connection == ConnectionState.ONLINE ->
                        UiState.Success(shown)
                    list.isNotEmpty() ->
                        UiState.Offline(shown)
                    samples.isNotEmpty() ->
                        UiState.Success(samples)
                    connection == ConnectionState.ONLINE ->
                        UiState.Empty
                    else ->
                        UiState.Offline(null)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}

private fun samplePricesFor(location: String): List<Price> {
    val seed = location.trim().lowercase(Locale.ROOT).hashCode()
    val random = Random(seed)
    val materials = listOf(
        "CRT" to 42.0,
        "LCD Panel" to 115.0,
        "PCB / Circuit Board" to 310.0,
        "Cables" to 88.0,
        "Copper" to 620.0,
        "Battery" to 62.0,
        "Motor" to 105.0,
        "Magnet" to 165.0,
        "Plastic" to 28.0,
        "Other" to 25.0
    )
    return materials.map { (material, baseline) ->
        val rate = (baseline * random.nextDouble(0.78, 1.23)).roundToInt().toDouble().coerceAtLeast(1.0)
        Price(
            id = "demo-${seed}-${material.hashCode()}",
            materialLabel = material,
            ratePerKg = rate,
            updatedAtEpochMs = 0L,
            location = location.trim(),
            minRatePerKg = (rate * 0.86).roundToInt().toDouble().coerceAtLeast(1.0),
            maxRatePerKg = (rate * 1.14).roundToInt().toDouble().coerceAtLeast(rate),
            trend = "stable",
            history = emptyList(),
            source = "DEMO SAMPLE",
            qualityStatus = "DEMO_SAMPLE",
            disclaimer = "Illustrative sample only. Not a live, verified or payable market rate."
        )
    }
}
