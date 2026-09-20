package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecyclerMarketplaceState(
    val loading: Boolean = true,
    val lots: List<MarketplaceLot> = emptyList(),
    val submittedIds: Set<String> = emptySet(),
    val submittingIds: Set<String> = emptySet(),
    val error: String? = null
)

class RecyclerMarketplaceViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerMarketplaceState())
    val state: StateFlow<RecyclerMarketplaceState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.getRecyclerQuoteRequests().requireData() }
                .onSuccess { requests ->
                    _state.value = _state.value.copy(
                        loading = false,
                        submittedIds = requests.filter { it.status == "ACCEPTED" }.map { it.id }.toSet(),
                        lots = requests.map { request ->
                            MarketplaceLot(
                                id = request.id,
                                material = displayMaterial(request.materialCategory),
                                weight = "%.1f kg".format(request.weight),
                                range = request.estimatedValue?.let { "₹%.0f est.".format(it) } ?: "Value to be confirmed",
                                area = request.collectionLocation?.areaName ?: "Area to be confirmed",
                                distance = "",
                                requestId = request.id
                            )
                        },
                        error = null
                    )
                }
                .onFailure { error -> _state.value = _state.value.copy(loading = false, error = userFacingError(error, "Could not load requests")) }
        }
    }

    fun submitOffer(requestId: String, rate: Double) {
        if (requestId in _state.value.submittedIds || requestId in _state.value.submittingIds || rate <= 0) return
        _state.value = _state.value.copy(submittingIds = _state.value.submittingIds + requestId, error = null)
        viewModelScope.launch {
            runCatching { api.submitRecyclerQuote(com.irinteractivestudios.kabadiwalaconnect.data.remote.SubmitRecyclerQuoteRequestDto(requestId, rate)).requireData() }
                .onSuccess { _state.value = _state.value.copy(submittedIds = _state.value.submittedIds + requestId, submittingIds = _state.value.submittingIds - requestId) }
                .onFailure { error -> _state.value = _state.value.copy(submittingIds = _state.value.submittingIds - requestId, error = userFacingError(error, "Offer could not be sent")) }
        }
    }

    private fun displayMaterial(value: String) = when (value) {
        "PCB" -> "PCB / Circuit Board"
        "CABLE" -> "Copper Cable"
        "LCD_PANEL" -> "LCD Panel"
        else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
