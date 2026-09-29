package com.irinteractivestudios.kabadiwalaconnect.util

import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.FailureKind
import com.irinteractivestudios.kabadiwalaconnect.data.remote.toAppFailure

/**
 * Converts transport/backend failures into copy that is safe for customers.
 * Provider messages, database details, request IDs, and raw response bodies
 * stay in developer logs rather than appearing in normal role screens.
 */
fun userFacingError(error: Throwable, fallback: String): String {
    val remote = error as? RemoteApiException
    return when (remote?.code) {
        "GEMINI_UNAVAILABLE", "SERVICE_UNAVAILABLE", "HTTP_503" ->
            "The service is temporarily unavailable. Please try again."
        "INVALID_PHOTO", "PHOTO_REQUIRED", "PHOTO_UPLOAD_FAILED" ->
            "That photo could not be uploaded. Choose another clear image and retry."
        else -> error.toAppFailure().let { if (it.kind == FailureKind.UNKNOWN) fallback else it.message }
    }
}
