package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Normalizes camera/gallery photos before a multipart request. */
object ImagePipeline {
    private const val MAX_DIMENSION = 1600
    private const val INITIAL_QUALITY = 88

    /** Copies a picker URI into app-private storage and normalizes it once. */
    fun importUri(context: Context, uri: Uri, outputDirectory: File): File {
        outputDirectory.mkdirs()
        val source = File.createTempFile("kc-source-", ".image", outputDirectory)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("unsupported_image")
            prepareForUpload(source, outputDirectory)
        } finally {
            source.delete()
        }
    }

    fun prepareForUpload(source: File, outputDirectory: File, maxBytes: Long = PhotoValidator.MAX_BYTES): File {
        require(source.isFile && source.length() > 0L) { "unsupported_image" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "unsupported_image" }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("unsupported_image")
        var outputBitmap: Bitmap = decoded
        try {
            val exif = runCatching { ExifInterface(source.absolutePath) }.getOrNull()
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            }
            val largest = maxOf(decoded.width, decoded.height)
            if (largest > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / largest.toFloat()
                matrix.postScale(scale, scale)
            }
            if (!matrix.isIdentity) outputBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            outputDirectory.mkdirs()
            val output = File.createTempFile("kc-material-", ".jpg", outputDirectory)
            var quality = INITIAL_QUALITY
            do {
                output.outputStream().use { stream -> check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "unsupported_image" } }
                if (output.length() <= maxBytes) return output
                quality -= 8
            } while (quality >= 48)
            output.delete()
            throw IllegalArgumentException("unsupported_image")
        } finally {
            if (outputBitmap !== decoded) outputBitmap.recycle()
            decoded.recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_DIMENSION * 2) sample *= 2
        return sample
    }
}
