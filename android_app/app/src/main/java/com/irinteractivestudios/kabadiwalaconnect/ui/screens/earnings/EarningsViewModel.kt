package com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.EarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Earnings tab state: purely local summary, so it works fully offline.
 * The summary is local-first and is refreshed from the authoritative ledger
 * when the user asks for a remote refresh.
 */
class EarningsViewModel(earnings: EarningsRepository) : ViewModel() {

    val uiState: StateFlow<UiState<EarningsSummary>> =
        earnings.observeSummary()
            .map<EarningsSummary, UiState<EarningsSummary>> { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}
