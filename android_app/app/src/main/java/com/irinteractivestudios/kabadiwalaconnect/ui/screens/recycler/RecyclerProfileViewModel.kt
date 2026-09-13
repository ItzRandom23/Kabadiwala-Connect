package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerProfileUpdateRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateUpdateDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRatesUpdateRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerVerificationRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecyclerProfileState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val profile: RecyclerDto? = null,
    val error: String? = null,
    val saved: Boolean = false
)

class RecyclerProfileViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerProfileState())
    val state: StateFlow<RecyclerProfileState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_state.value.loading && _state.value.profile != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, saved = false)
            runCatching { api.getRecyclerProfile().requireData() }
                .onSuccess { _state.value = _state.value.copy(loading = false, profile = it, error = null) }
                .onFailure { error -> _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load recycler profile") }
        }
    }

    fun saveRates(rates: List<RecyclerRateUpdateDto>) {
        if (rates.isEmpty() || _state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null, saved = false)
            runCatching { api.updateRecyclerRates(RecyclerRatesUpdateRequestDto(rates)).requireData() }
                .onSuccess { _state.value = _state.value.copy(saving = false, profile = it, saved = true) }
                .onFailure { error -> _state.value = _state.value.copy(saving = false, error = error.message ?: "Rates could not be saved") }
        }
    }

    fun saveAvailability(value: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null, saved = false)
            runCatching { api.updateRecyclerProfile(RecyclerProfileUpdateRequestDto(pickupAvailability = value)).requireData() }
                .onSuccess { _state.value = _state.value.copy(saving = false, profile = it, saved = true) }
                .onFailure { error -> _state.value = _state.value.copy(saving = false, error = error.message ?: "Availability could not be saved") }
        }
    }

    fun submitVerification(request: RecyclerVerificationRequestDto) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null, saved = false)
            runCatching { api.submitRecyclerVerificationRequest(request).requireData() }
                .onSuccess { _state.value = _state.value.copy(saving = false, profile = it, saved = true) }
                .onFailure { error -> _state.value = _state.value.copy(saving = false, error = error.message ?: "Verification request could not be submitted") }
        }
    }
}
