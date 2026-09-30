package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.data.remote.AppFailure
import com.irinteractivestudios.kabadiwalaconnect.data.remote.FailureKind

/** Resource presentation for transport errors. Server validation and conflict
 * details remain intact: their contents cannot safely be inferred or translated.
 */
internal fun failureResource(kind: FailureKind): Int? = when (kind) {
    FailureKind.NO_INTERNET, FailureKind.TIMEOUT -> R.string.auth_network_error
    FailureKind.AUTHENTICATION -> R.string.auth_session_restore_failed
    FailureKind.NOT_FOUND -> R.string.deal_not_available
    FailureKind.SERVER, FailureKind.UNKNOWN -> R.string.common_error_title
    else -> null
}

private fun canonicalFailureKind(message: String): FailureKind? = when (message) {
    "No internet connection. Check your network and retry.",
    "Connection issue. Check your internet and retry." -> FailureKind.NO_INTERNET
    "The request timed out. Please retry." -> FailureKind.TIMEOUT
    "Your session expired. Please sign in again." -> FailureKind.AUTHENTICATION
    "This item is no longer available." -> FailureKind.NOT_FOUND
    "The server could not finish this action. Please retry shortly.",
    "The service is temporarily unavailable. Please try again." -> FailureKind.SERVER
    "Could not complete this action. Please try again." -> FailureKind.UNKNOWN
    else -> null
}

/** Bridge for features that still store a safe error String in UiState. Only
 * exact canonical messages are mapped, never substrings or arbitrary API text.
 */
fun resolveUserFacingError(context: Context, message: String): String {
    if (context.resources.configuration.locales[0].language == "en") return message
    val resource = canonicalFailureKind(message)?.let(::failureResource) ?: return message
    return context.getString(resource)
}

fun resolveUserFacingError(context: Context, failure: AppFailure): String {
    if (context.resources.configuration.locales[0].language == "en") return failure.message
    return failureResource(failure.kind)?.let(context::getString) ?: failure.message
}

@Composable
fun localizedUserFacingError(message: String): String = resolveUserFacingError(LocalContext.current, message)
