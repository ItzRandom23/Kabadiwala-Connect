package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
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
