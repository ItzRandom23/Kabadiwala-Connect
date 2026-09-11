package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.remote.VerifyHandoverRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.VerifiedHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerHandoverConfirmRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecyclerOrdersState(
    val loading: Boolean = true,
    val handovers: List<HandoverDto> = emptyList(),
    val error: Boolean = false
)

class RecyclerOrdersViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerOrdersState())
    val state: StateFlow<RecyclerOrdersState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            runCatching { api.getRecyclerHandovers().requireData() }
                .onSuccess { items -> _state.value = RecyclerOrdersState(loading = false, handovers = items) }
                .onFailure { _state.value = RecyclerOrdersState(loading = false, error = true) }
        }
    }
}

data class RecyclerScanState(
    val checking: Boolean = false,
    val confirming: Boolean = false,
    val verified: VerifiedHandoverDto? = null,
    val confirmed: Boolean = false,
    val error: Boolean = false
)

class RecyclerScanViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerScanState())
    val state: StateFlow<RecyclerScanState> = _state.asStateFlow()

    fun verify(qrCodeData: String) {
        if (qrCodeData.isBlank() || _state.value.checking) return
        viewModelScope.launch {
            _state.value = RecyclerScanState(checking = true)
            runCatching { api.verifyHandover(VerifyHandoverRequestDto(qrCodeData.trim())).requireData() }
                .onSuccess { result -> _state.value = RecyclerScanState(verified = result) }
                .onFailure { _state.value = RecyclerScanState(error = true) }
        }
    }

    fun confirm(actualWeight: Double, materialMatch: Boolean, notes: String?) {
        val handover = _state.value.verified ?: return
        if (actualWeight <= 0 || _state.value.confirming) return
        viewModelScope.launch {
            _state.value = _state.value.copy(confirming = true, error = false)
            runCatching {
                api.confirmRecyclerHandover(
                    handover.handoverId,
                    RecyclerHandoverConfirmRequestDto(actualWeight, materialMatch, notes = notes?.trim()?.takeIf(String::isNotEmpty))
                ).requireData()
            }.onSuccess {
                _state.value = _state.value.copy(confirming = false, confirmed = true)
            }.onFailure {
                _state.value = _state.value.copy(confirming = false, error = true)
            }
        }
    }

    fun reset() { _state.value = RecyclerScanState() }
}
