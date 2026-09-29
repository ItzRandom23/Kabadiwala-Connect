package com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AccountProfileUpdate
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionSnapshot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AccountProfileUiState(
    val profile: AccountProfile? = null,
    val saving: Boolean = false,
    val saveError: String? = null
)

/** One account-scoped state owner for Household, Collector, and Recycler profile editing. */
class AccountProfileViewModel(
    sessionSnapshots: StateFlow<SessionSnapshot>,
    private val updateProfile: suspend (AccountProfileUpdate) -> AccountProfile?
) : ViewModel() {
    private val _state = MutableStateFlow(AccountProfileUiState(sessionSnapshots.value.account))
    val state: StateFlow<AccountProfileUiState> = _state.asStateFlow()
    private var saveGeneration = 0L

    init {
        viewModelScope.launch {
            sessionSnapshots.collect { snapshot ->
                val incoming = snapshot.account
                val current = _state.value
                if (incoming?.id != current.profile?.id) {
                    saveGeneration++
                    _state.value = AccountProfileUiState(incoming)
                } else if (!current.saving && incoming != null) {
                    _state.value = current.copy(profile = incoming)
                }
            }
        }
    }

    fun save(draft: ProfileEditDraft) {
        val original = _state.value.profile ?: return
        if (_state.value.saving) return
        val generation = ++saveGeneration
        val sameLocation = draft.areaName.trim() == original.areaName.orEmpty().trim() &&
            draft.address.trim() == original.address.orEmpty().trim()
        val update = AccountProfileUpdate(
            displayName = draft.displayName.trim(),
            email = draft.email.trim(),
            areaName = draft.areaName.trim(),
            address = draft.address.trim(),
            latitude = original.latitude.takeIf { sameLocation },
            longitude = original.longitude.takeIf { sameLocation },
            clearCoordinates = !sameLocation
        )
        _state.value = AccountProfileUiState(
            profile = original.copy(
                displayName = update.displayName,
                businessName = if (original.role == AccountRole.RECYCLER) update.displayName else original.businessName,
                email = update.email.orEmpty(),
                address = update.address,
                latitude = update.latitude,
                longitude = update.longitude
            ),
            saving = true
        )
        viewModelScope.launch {
            try {
                val confirmed = updateProfile(update)
                if (generation == saveGeneration && _state.value.profile?.id == original.id) {
                    _state.value = AccountProfileUiState(confirmed ?: original)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == saveGeneration && _state.value.profile?.id == original.id) {
                    _state.value = AccountProfileUiState(original, saveError = "Could not save account details. Please try again.")
                }
            }
        }
    }
}
