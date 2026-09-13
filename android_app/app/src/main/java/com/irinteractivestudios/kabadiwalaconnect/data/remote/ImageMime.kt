package com.irinteractivestudios.kabadiwalaconnect.data.remote

import java.io.File

/**
 * Multipart image uploads must use a concrete MIME type. Sending a wildcard
 * image MIME type
 * makes multer reject the material-suggestion request even when the bytes are
 * a valid photo.
 */
fun File.imageMimeType(): String = when (extension.lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    else -> "image/jpeg"
}
