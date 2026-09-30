package com.irinteractivestudios.kabadiwalaconnect.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Invalidation hints only. No credentials, request bodies or personal data. */
data class DataChangeEvent(val accountId: String, val path: String)

object DataChangeEvents {
    private val changes = MutableSharedFlow<DataChangeEvent>(extraBufferCapacity = 64)
    val events = changes.asSharedFlow()

    fun publish(accountId: String?, path: String) {
        if (!accountId.isNullOrBlank()) changes.tryEmit(DataChangeEvent(accountId, path))
    }

    internal fun invalidatesData(method: String, path: String): Boolean =
        method in setOf("POST", "PUT", "PATCH", "DELETE") &&
            !path.contains("/auth/") && !path.contains("/devices") &&
            !path.endsWith("/material-suggestion") && !path.endsWith("/verify")
}
