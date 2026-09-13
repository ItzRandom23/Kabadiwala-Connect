package com.irinteractivestudios.kabadiwalaconnect.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class HomeNextAction { CREATE_LOT, FIND_RECYCLERS, REVIEW_QUOTES, PREPARE_HANDOVER, RECORD_PAYMENT, REVIEW_DISPUTE }

data class HomeData(
    val lotCount: Int,
    val pendingSyncCount: Int = 0,
    val nextAction: HomeNextAction = HomeNextAction.CREATE_LOT,
    val nextLotId: String? = null
)

/**
 * Home tab state: summary of locally saved lots.
 * Survives rotation (ViewModel); offline shows cached lots when present.
 */
class HomeViewModel(
    lots: LotRepository,
    connectivity: ConnectivityObserver,
    syncQueue: SyncQueueDao? = null,
    accountId: () -> String = { "" }
) : ViewModel() {

    val uiState: StateFlow<UiState<HomeData>> =
        combine(lots.observeLots(), connectivity.state, syncQueue?.observeForAccount(accountId()) ?: flowOf(emptyList())) { list, connection, pending ->
            val data = if (list.isNotEmpty()) {
                val action = resolveNextAction(list)
                HomeData(list.size, pending.size, action.first, action.second)
            } else if (pending.isNotEmpty()) {
                HomeData(0, pending.size, HomeNextAction.CREATE_LOT)
            } else null
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

    private fun resolveNextAction(lots: List<Lot>): Pair<HomeNextAction, String?> {
        val active = lots.firstOrNull { it.status !in setOf(LotStatus.PAID, LotStatus.CANCELLED) }
            ?: return HomeNextAction.CREATE_LOT to null
        return when (active.status) {
            LotStatus.SAVED, LotStatus.DRAFT -> HomeNextAction.FIND_RECYCLERS to active.id
            LotStatus.LOCKED, LotStatus.QUOTE_RECEIVED -> HomeNextAction.REVIEW_QUOTES to active.id
            LotStatus.COLLECTOR_CONFIRMED -> HomeNextAction.PREPARE_HANDOVER to active.id
            LotStatus.HANDED_OVER -> HomeNextAction.RECORD_PAYMENT to active.id
            LotStatus.DISPUTED -> HomeNextAction.REVIEW_DISPUTE to active.id
            else -> HomeNextAction.CREATE_LOT to null
        }
    }
}
