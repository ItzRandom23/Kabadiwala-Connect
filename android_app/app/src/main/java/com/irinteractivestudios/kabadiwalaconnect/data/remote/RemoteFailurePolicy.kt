package com.irinteractivestudios.kabadiwalaconnect.data.remote

import kotlinx.coroutines.CancellationException
import java.io.IOException

/** True only when retrying the same request may succeed without changing user input or credentials. */
internal fun Throwable.isRetryableTransportFailure(): Boolean {
    if (this is CancellationException || this === ProtectedRequestBlockedException) return false
    return when (this) {
        is IOException -> true
        is RemoteApiException -> httpCode == 408 || httpCode == 429 || (httpCode ?: 0) >= 500
        else -> false
    }
}
