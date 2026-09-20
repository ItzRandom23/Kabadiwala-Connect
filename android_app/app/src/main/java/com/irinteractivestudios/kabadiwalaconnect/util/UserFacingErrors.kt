package com.irinteractivestudios.kabadiwalaconnect.util

import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import java.io.IOException

/**
 * Converts transport/backend failures into copy that is safe for customers.
 * Provider messages, database details, request IDs, and raw response bodies
 * stay in developer logs rather than appearing in normal role screens.
 */
fun userFacingError(error: Throwable, fallback: String): String {
    val remote = error as? RemoteApiException
    return when (remote?.code) {
        "AUTHENTICATION_REQUIRED", "TOKEN_INVALID", "TOKEN_EXPIRED", "HTTP_401" ->
            "Your session expired. Please sign in again."
        "AUTHORIZATION_ERROR", "HTTP_403" ->
            "This action is not available for your role."
        "OTP_RATE_LIMITED", "OTP_COOLDOWN", "HTTP_429" ->
            "Too many requests. Please wait a moment and try again."
        "VALIDATION_ERROR", "HTTP_400", "HTTP_422" ->
            "Please check the details and try again."
        "CONFLICT", "HTTP_409" ->
            "This item changed. Refresh and try again."
        "NOT_FOUND", "HTTP_404" ->
            "This item is no longer available."
        "GEMINI_UNAVAILABLE", "SERVICE_UNAVAILABLE", "HTTP_503" ->
            "The service is temporarily unavailable. Please try again."
        "INVALID_PHOTO", "PHOTO_REQUIRED", "PHOTO_UPLOAD_FAILED" ->
            "That photo could not be uploaded. Choose another clear image and retry."
        else -> if (error is IOException) {
            "Could not reach the service. Check your connection and try again."
        } else {
            fallback
        }
    }
}
