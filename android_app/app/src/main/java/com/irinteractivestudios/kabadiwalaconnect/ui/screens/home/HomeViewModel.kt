package com.irinteractivestudios.kabadiwalaconnect.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeData(val lotCount: Int)

/**
 * Home tab state: summary of locally saved lots.
 * Survives rotation (ViewModel); offline shows cached lots when present.
 */
class HomeViewModel(
    lots: LotRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {

    val uiState: StateFlow<UiState<HomeData>> =
        combine(lots.observeLots(), connectivity.state) { list, connection ->
            val data = if (list.isNotEmpty()) HomeData(list.size) else null
            when {
                data != null && connection == ConnectionState.ONLINE ->
                    UiState.Success(data)
                data != null ->
                    UiState.Offline(data)
                connection == ConnectionState.ONLINE ->
                    UiState.Empty
                else ->
                    UiState.Offline(null)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}
