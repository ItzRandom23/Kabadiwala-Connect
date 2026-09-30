package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ConfirmedHandoverEvents
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.remote.VerifyHandoverRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.VerifiedHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerHandoverConfirmRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverConfirmRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationSnapshot
import com.irinteractivestudios.kabadiwalaconnect.util.SingleFlightGate
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecyclerOrdersState(
    val loading: Boolean = true,
    val handovers: List<SupplyHandoverDto> = emptyList(),
    val error: Boolean = false,
    val showingCachedEvidence: Boolean = false,
    val paymentBusy: Set<String> = emptySet(),
    val paymentError: String? = null
)

class RecyclerOrdersViewModel(
    private val api: ApiService,
    private val cache: FormalisationCacheStore? = null,
    private val accountIdProvider: () -> String? = { null }
) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerOrdersState())
    val state: StateFlow<RecyclerOrdersState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        viewModelScope.launch {
            ConfirmedHandoverEvents.events.collect { event ->
                if (event.accountId == accountIdProvider()) {
                    refreshGeneration++
                    refreshJob?.cancel()
                    _state.value = _state.value.copy(loading = false, error = false,
                        handovers = listOf(event.handover.copy(payments = event.handover.payments.ifEmpty { _state.value.handovers.firstOrNull { it.id == event.handover.id }?.payments.orEmpty() })) + _state.value.handovers.filterNot { it.id == event.handover.id })
                }
            }
        }
    }

    fun recordPayment(handoverId: String, input: com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyPaymentRequestDto) {
        if (handoverId in _state.value.paymentBusy) return
        val owner = accountIdProvider()
        refreshGeneration++
        refreshJob?.cancel()
        _state.value = _state.value.copy(paymentBusy = _state.value.paymentBusy + handoverId, paymentError = null)
        viewModelScope.launch {
            try {
                val payment = api.recordSupplyPayment(handoverId, input).requireData()
                if (owner != accountIdProvider()) return@launch
                _state.value = _state.value.copy(handovers = _state.value.handovers.map { if (it.id == handoverId) it.copy(payments = listOf(payment)) else it })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (owner == accountIdProvider()) _state.value = _state.value.copy(paymentError = com.irinteractivestudios.kabadiwalaconnect.util.userFacingError(error, "Payment could not be recorded"))
            } finally {
                if (owner == accountIdProvider()) {
                    _state.value = _state.value.copy(paymentBusy = _state.value.paymentBusy - handoverId)
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.paymentBusy.isNotEmpty()) return
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        val owner = accountIdProvider()
        refreshJob = viewModelScope.launch {
            fun current() = generation == refreshGeneration && accountIdProvider() == owner
            try {
                val cached = withContext(Dispatchers.IO) { cache?.load(owner)?.handovers.orEmpty() }
                if (!current()) return@launch
                val visible = _state.value.handovers.ifEmpty { cached }
                _state.value = _state.value.copy(loading = visible.isEmpty(), handovers = visible, showingCachedEvidence = visible.isNotEmpty())
                val items = api.getSupplyHandovers().requireData()
                if (!current()) return@launch
                _state.value = _state.value.copy(loading = false, handovers = items, error = false, showingCachedEvidence = false)
                withContext(Dispatchers.IO) {
                    if (current()) {
                        val prior = cache?.load(owner) ?: FormalisationSnapshot()
                        cache?.save(owner, prior.copy(handovers = items))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current()) {
                    _state.value = _state.value.copy(loading = false, error = _state.value.handovers.isEmpty(), showingCachedEvidence = _state.value.handovers.isNotEmpty())
                }
            }
        }
    }
}

data class RecyclerScanState(
    val checking: Boolean = false,
    val confirming: Boolean = false,
    val verified: VerifiedHandoverDto? = null,
    val confirmed: Boolean = false,
    val confirmation: JsonObject? = null,
    val error: Boolean = false,
    val supplyVerified: SupplyHandoverDto? = null,
    val supplyConfirmed: SupplyHandoverDto? = null,
    val supplyQueued: Boolean = false,
    val supplyFromCache: Boolean = false
)

class RecyclerScanViewModel(
    private val api: ApiService,
    private val cache: FormalisationCacheStore? = null,
    private val accountIdProvider: () -> String? = { null },
    private val idempotencyKeys: IdempotencyKeyStore? = null
) : ViewModel() {
    private val _state = MutableStateFlow(RecyclerScanState())
    val state: StateFlow<RecyclerScanState> = _state.asStateFlow()
    private val verifyGate = SingleFlightGate()
    private val confirmGate = SingleFlightGate()

    fun verify(qrCodeData: String) {
        if (qrCodeData.isBlank() || !verifyGate.tryEnter()) return
        val owner = accountIdProvider()
        _state.value = RecyclerScanState(checking = true)
        viewModelScope.launch {
            try {
                val value = qrCodeData.trim()
                if (value.startsWith("kc-supply-handover-v1.")) {
                    val cachedItems = withContext(Dispatchers.IO) { cache?.load(owner)?.handovers.orEmpty() }
                    val items = runCatching { api.getSupplyHandovers().requireData() }.getOrElse { cachedItems }
                    if (accountIdProvider() != owner) return@launch
                    val match = items.firstOrNull { it.qrCodeData == value }
                    if (match != null) _state.value = RecyclerScanState(supplyVerified = match, supplyFromCache = items === cachedItems && cachedItems.isNotEmpty())
                    else _state.value = RecyclerScanState(error = true)
                } else {
                    runCatching { api.verifyHandover(VerifyHandoverRequestDto(value)).requireData() }
                        .onSuccess { result -> if (accountIdProvider() == owner) _state.value = RecyclerScanState(verified = result) }
                        .onFailure { if (accountIdProvider() == owner) _state.value = RecyclerScanState(error = true) }
                }
            } finally {
                verifyGate.exit()
            }
        }
    }

    fun confirm(actualWeight: Double, materialMatch: Boolean, notes: String?) {
        if (actualWeight <= 0 || !confirmGate.tryEnter()) return
        val owner = accountIdProvider()
        val supply = _state.value.supplyVerified
        if (supply != null) {
            _state.value = _state.value.copy(confirming = true, error = false)
            viewModelScope.launch {
                try {
                    val request = SupplyHandoverConfirmRequestDto(supply.qrCodeData.orEmpty(), actualWeightKg = actualWeight, acceptedWeightKg = actualWeight, materialMatch = materialMatch, reasonCode = notes?.trim()?.takeIf(String::isNotEmpty))
                    val operation = "recycler-handover-${supply.id}"
                    val key = idempotencyKeys?.getOrCreate(operation) ?: operation
                    runCatching { api.confirmSupplyHandover(request, key).requireData() }
                        .onSuccess { result ->
                            if (accountIdProvider() != owner) return@onSuccess
                            idempotencyKeys?.clear(operation)
                            _state.value = _state.value.copy(confirming = false, supplyConfirmed = result, supplyVerified = result, supplyQueued = false)
                            ConfirmedHandoverEvents.publish(owner, result)
                            withContext(Dispatchers.IO) {
                                if (accountIdProvider() == owner) {
                                    val prior = cache?.load(owner) ?: FormalisationSnapshot()
                                    cache?.save(owner, prior.copy(handovers = listOf(result) + prior.handovers.filterNot { it.id == result.id }))
                                }
                            }
                        }
                        .onFailure { if (accountIdProvider() == owner) _state.value = _state.value.copy(confirming = false, supplyQueued = false, error = true) }
                } finally {
                    confirmGate.exit()
                    }
            }
            return
        }
        val handover = _state.value.verified ?: run { confirmGate.exit(); return }
        _state.value = _state.value.copy(confirming = true, error = false)
        viewModelScope.launch {
            try {
                runCatching {
                    api.confirmRecyclerHandover(
                        handover.handoverId,
                        RecyclerHandoverConfirmRequestDto(actualWeight, materialMatch, notes = notes?.trim()?.takeIf(String::isNotEmpty))
                    ).requireData()
                }.onSuccess { result ->
                    if (accountIdProvider() != owner) return@onSuccess
                    _state.value = _state.value.copy(confirming = false, confirmed = true, confirmation = result)
                }.onFailure {
                    if (accountIdProvider() == owner) _state.value = _state.value.copy(confirming = false, error = true)
                }
            } finally {
                confirmGate.exit()
            }
        }
    }

    fun reset() { verifyGate.exit(); confirmGate.exit(); _state.value = RecyclerScanState() }
}
