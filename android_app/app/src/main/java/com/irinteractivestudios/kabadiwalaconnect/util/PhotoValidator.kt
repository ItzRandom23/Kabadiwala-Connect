package com.irinteractivestudios.kabadiwalaconnect.util

import android.graphics.BitmapFactory
import java.io.File

data class PhotoValidation(
    val valid: Boolean,
    val reason: PhotoValidator.Reason? = null,
    val warning: PhotoValidator.Warning? = null
)

typealias Reason = PhotoValidator.Reason
typealias Warning = PhotoValidator.Warning

object PhotoValidator {
    enum class Reason { MISSING, TOO_SMALL, TOO_LARGE, INVALID }
    enum class Warning { TOO_DARK, TOO_BRIGHT, LOW_DETAIL }
    const val MIN_WIDTH = 320
    const val MIN_HEIGHT = 240
    const val MAX_BYTES = 8L * 1024L * 1024L

    fun validate(path: String): PhotoValidation {
        val file = File(path)
        if (!file.exists()) return PhotoValidation(false, Reason.MISSING)
        if (file.length() > MAX_BYTES) return PhotoValidation(false, Reason.TOO_LARGE)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return PhotoValidation(false, Reason.INVALID)
        if (options.outWidth < MIN_WIDTH || options.outHeight < MIN_HEIGHT) return PhotoValidation(false, Reason.TOO_SMALL)
        val sample = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 320) }
        val bitmap = BitmapFactory.decodeFile(path, sample) ?: return PhotoValidation(false, Reason.INVALID)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        if (pixels.isEmpty()) return PhotoValidation(false, Reason.INVALID)
        val luminance = pixels.sumOf { pixel ->
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
        }.toDouble() / pixels.size
        val warning = when {
            luminance < 35 -> Warning.TOO_DARK
            luminance > 235 -> Warning.TOO_BRIGHT
            else -> null
        }
        return PhotoValidation(true, warning = warning)
    }
}
