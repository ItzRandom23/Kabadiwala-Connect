package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerProfileUpdateRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateUpdateDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRatesUpdateRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerVerificationRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecyclerProfileState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val profile: RecyclerDto? = null,
    val error: String? = null,
    val saved: Boolean = false,
    val savingSection: String? = null,
    val savedSection: String? = null,
    val materialCategories: List<String> = emptyList(),
    val materialCategoriesError: Boolean = false
)

class RecyclerProfileViewModel(private val api: ApiService, private val accountId: () -> String? = { null }) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerProfileState())
    val state: StateFlow<RecyclerProfileState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var refreshOwner: String? = null

    fun refresh() {
        if (_state.value.saving) return
        val owner = accountId()
        if (refreshOwner != owner) _state.value = RecyclerProfileState()
        if (refreshJob?.isActive == true && refreshOwner == owner) return
        refreshOwner = owner
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.profile == null, error = null)
            try {
                val profile = api.getRecyclerProfile().requireData()
                val categories = if (_state.value.materialCategories.isEmpty()) {
                    runCatching { api.materialCategories().requireData() }.getOrDefault(emptyList())
                } else _state.value.materialCategories
                if (refreshGeneration == generation && accountId() == owner) {
                    _state.value = _state.value.copy(loading = false, profile = profile, materialCategories = categories, materialCategoriesError = categories.isEmpty(), error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (refreshGeneration == generation && accountId() == owner) {
                    _state.value = _state.value.copy(loading = false, error = userFacingError(error, "Could not load recycler profile"))
                }
            }
        }
    }

    private fun stopRefreshBeforeSave() {
        refreshGeneration++
        refreshJob?.cancel()
        refreshJob = null
        _state.value = _state.value.copy(loading = false)
    }

    fun saveRates(rates: List<RecyclerRateUpdateDto>) {
        if (rates.isEmpty() || _state.value.saving) return
        stopRefreshBeforeSave()
        val owner = accountId()
        _state.value = _state.value.copy(saving = true, savingSection = "rates", savedSection = null, error = null, saved = false)
        viewModelScope.launch {
            runCatching { api.updateRecyclerRates(RecyclerRatesUpdateRequestDto(rates)).requireData() }
                .onSuccess { if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, savedSection = "rates", profile = it, saved = true) }
                .onFailure { error -> if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, error = userFacingError(error, "Rates could not be saved")) }
        }
    }

    fun saveAvailability(value: String) {
        if (_state.value.saving) return
        stopRefreshBeforeSave()
        val owner = accountId()
        _state.value = _state.value.copy(saving = true, savingSection = "availability", savedSection = null, error = null, saved = false)
        viewModelScope.launch {
            runCatching { api.updateRecyclerProfile(RecyclerProfileUpdateRequestDto(pickupAvailability = value)).requireData() }
                .onSuccess { if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, savedSection = "availability", profile = it, saved = true) }
                .onFailure { error -> if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, error = userFacingError(error, "Availability could not be saved")) }
        }
    }

    fun savePickupPricing(input: RecyclerProfileUpdateRequestDto) {
        if (_state.value.saving) return
        stopRefreshBeforeSave()
        val owner = accountId()
        _state.value = _state.value.copy(saving = true, savingSection = "pricing", savedSection = null, error = null, saved = false)
        viewModelScope.launch {
            runCatching { api.updateRecyclerProfile(input).requireData() }
                .onSuccess { if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, savedSection = "pricing", profile = it, saved = true) }
                .onFailure { error -> if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, error = userFacingError(error, "Pickup charges could not be saved")) }
        }
    }

    fun submitVerification(request: RecyclerVerificationRequestDto) {
        if (_state.value.saving) return
        stopRefreshBeforeSave()
        val owner = accountId()
        _state.value = _state.value.copy(saving = true, savingSection = "verification", savedSection = null, error = null, saved = false)
        viewModelScope.launch {
            runCatching { api.submitRecyclerVerificationRequest(request).requireData() }
                .onSuccess { if (accountId() == owner) _state.value = _state.value.copy(saving = false, savingSection = null, savedSection = "verification", profile = it, saved = true) }
                .onFailure { error ->
                    if (accountId() != owner) return@onFailure
                    val remote = error as? RemoteApiException
                    val message = if (remote?.code == "VALIDATION_ERROR" && remote.message.contains("expiry", ignoreCase = true)) {
                        "You entered an invalid expiry date. Use a future date in YYYY-MM-DD format."
                    } else {
                        userFacingError(error, "Verification request could not be submitted")
                    }
                    _state.value = _state.value.copy(saving = false, savingSection = null, error = message)
                }
        }
    }
}
