package com.irinteractivestudios.kabadiwalaconnect.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ConfirmedHandoverEvent(val accountId: String, val handover: SupplyHandoverDto)

/** Bridge the scanner's confirmed record to Orders and the marketplace state. */
object ConfirmedHandoverEvents {
    private val updates = MutableSharedFlow<ConfirmedHandoverEvent>(replay = 1, extraBufferCapacity = 8)
    val events = updates.asSharedFlow()
    fun publish(accountId: String?, handover: SupplyHandoverDto) {
        if (!accountId.isNullOrBlank()) updates.tryEmit(ConfirmedHandoverEvent(accountId, handover))
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clear() { updates.resetReplayCache() }
}
