package com.irinteractivestudios.kabadiwalaconnect.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class FailureKind { NO_INTERNET, TIMEOUT, SERVER, AUTHENTICATION, AUTHORIZATION, VALIDATION, CONFLICT, NOT_FOUND, RATE_LIMITED, SYNC, UNKNOWN }

data class AppFailure(val kind: FailureKind, val message: String, val retryable: Boolean)

/** One transport-independent error contract for feature ViewModels. */
fun Throwable.toAppFailure(): AppFailure {
    val remote = this as? RemoteApiException
    return when {
        this is UnknownHostException -> AppFailure(FailureKind.NO_INTERNET, "No internet connection. Check your network and retry.", true)
        this is SocketTimeoutException || remote?.httpCode == 408 -> AppFailure(FailureKind.TIMEOUT, "The request timed out. Please retry.", true)
        this is ProtectedRequestBlockedException || remote?.httpCode == 401 || remote?.code in setOf("AUTHENTICATION_REQUIRED", "TOKEN_INVALID", "TOKEN_EXPIRED") -> AppFailure(FailureKind.AUTHENTICATION, "Your session expired. Please sign in again.", false)
        remote?.httpCode == 403 || remote?.code == "AUTHORIZATION_ERROR" -> AppFailure(FailureKind.AUTHORIZATION, "This action is not available for your account.", false)
        remote?.httpCode == 404 || remote?.code == "NOT_FOUND" -> AppFailure(FailureKind.NOT_FOUND, "This item is no longer available.", false)
        remote?.httpCode == 429 || remote?.code in setOf("OTP_RATE_LIMITED", "OTP_COOLDOWN") -> AppFailure(FailureKind.RATE_LIMITED, "Too many requests. Please wait a moment and retry.", true)
        remote?.httpCode == 409 -> AppFailure(FailureKind.CONFLICT, remote.message.ifBlank { "This record changed. Refresh and try again." }, true)
        remote?.httpCode in setOf(400, 422) -> AppFailure(FailureKind.VALIDATION, remote?.message?.takeUnless { it == "Invalid request" }.orEmpty().ifBlank { "Review the entered details and try again." }, false)
        (remote?.httpCode ?: 0) >= 500 -> AppFailure(FailureKind.SERVER, "The server could not finish this action. Please retry shortly.", true)
        remote?.code == "SYNC_FAILED" -> AppFailure(FailureKind.SYNC, "This change has not synced. Open Sync Center to retry.", true)
        this is IOException -> AppFailure(FailureKind.NO_INTERNET, "Connection issue. Check your internet and retry.", true)
        else -> AppFailure(FailureKind.UNKNOWN, "Could not complete this action. Please try again.", true)
    }
}
