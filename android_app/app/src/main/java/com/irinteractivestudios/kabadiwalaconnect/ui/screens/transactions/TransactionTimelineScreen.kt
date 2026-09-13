package com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.TransactionEventDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.TransactionTimelineDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransactionTimelineState(val loading: Boolean = false, val error: Boolean = false, val data: TransactionTimelineDto? = null)

class TransactionTimelineViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(TransactionTimelineState())
    val state: StateFlow<TransactionTimelineState> = _state.asStateFlow()

    fun refresh(lotId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            runCatching { api.getTransactionTimeline(lotId).requireData() }
                .onSuccess { _state.value = TransactionTimelineState(data = it) }
                .onFailure { _state.value = TransactionTimelineState(error = true, data = _state.value.data) }
        }
    }
}

@Composable
fun TransactionTimelineScreen(lotId: String, vm: TransactionTimelineViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(lotId) { vm.refresh(lotId) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.transaction_passport_title), style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
        when {
            state.loading && state.data == null -> Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
            state.error && state.data == null -> {
                Text(stringResource(R.string.transaction_passport_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.refresh(lotId) }) { Text(stringResource(R.string.future_retry)) }
            }
            state.data != null -> TimelineContent(state.data!!, state.error, onRefresh = { vm.refresh(lotId) })
        }
    }
}

@Composable
private fun TimelineContent(data: TransactionTimelineDto, hasRefreshError: Boolean, onRefresh: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.transaction_status, data.status.replace('_', ' ')), style = MaterialTheme.typography.titleLarge)
                    data.quotedValue?.let { Text(stringResource(R.string.transaction_quoted_value, it)) }
                    data.finalValue?.let { Text(stringResource(R.string.transaction_final_value, it)) }
                    if (hasRefreshError) Text(stringResource(R.string.transaction_passport_cached), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(data.events) { event -> TimelineEvent(event) }
        item { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.transaction_refresh)) } }
    }
}

@Composable
private fun TimelineEvent(event: TransactionEventDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.type.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
            Text(event.at.replace('T', ' ').removeSuffix("Z"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            event.data.entrySet().filter { it.key in SAFE_EVENT_FIELDS }.forEach { (key, value) ->
                Text("${key.replace('_', ' ')}: ${value.asDisplayValue()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val SAFE_EVENT_FIELDS = setOf("materialCategory", "weight", "originalWeight", "sourceType", "wasteRegime", "areaName", "recyclerName", "recyclerArea", "recyclerVerified", "pricePerKg", "totalQuotedPrice", "validUntil", "actualWeight", "materialConfirmed", "amount", "method", "referenceId", "anomaly", "comparison")

private fun com.google.gson.JsonElement.asDisplayValue(): String = when {
    this is com.google.gson.JsonPrimitive -> if (isString) asString else toString()
    else -> toString()
}
