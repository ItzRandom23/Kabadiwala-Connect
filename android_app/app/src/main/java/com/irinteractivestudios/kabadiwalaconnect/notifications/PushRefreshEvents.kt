package com.irinteractivestudios.kabadiwalaconnect.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A hint to refresh authorized data; message contents never travel through it. */
data class PushRefreshEvent(val accountId: String, val type: String?, val route: String?)

object PushRefreshEvents {
    private val mutableEvents = MutableSharedFlow<PushRefreshEvent>(extraBufferCapacity = 32)
    val events = mutableEvents.asSharedFlow()

    fun publish(event: PushRefreshEvent) {
        mutableEvents.tryEmit(event)
    }
}
