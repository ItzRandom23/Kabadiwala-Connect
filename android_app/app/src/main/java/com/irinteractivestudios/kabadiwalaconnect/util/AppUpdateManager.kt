package com.irinteractivestudios.kabadiwalaconnect.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI

data class AvailableAppUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String?
)

enum class InstallUpdateResult {
    STARTED,
    PERMISSION_REQUIRED
}

/** Checks and installs APK updates described by the server's update manifest. */
object AppUpdateManager {
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    suspend fun check(context: Context): AvailableAppUpdate? = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.APP_UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank() || manifestUrl.contains(".invalid")) return@withContext null

        runCatching {
            val request = Request.Builder().url(manifestUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val manifest = gson.fromJson(body, UpdateManifest::class.java) ?: return@use null
                val currentVersion = currentVersionCode(context)
                if (manifest.versionCode <= currentVersion || manifest.apkUrl.isBlank()) return@use null
                AvailableAppUpdate(
                    versionCode = manifest.versionCode,
                    versionName = manifest.versionName.ifBlank { manifest.versionCode.toString() },
                    apkUrl = URI(manifestUrl).resolve(manifest.apkUrl).toString(),
                    releaseNotes = manifest.releaseNotes?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }.getOrNull()
    }

    suspend fun download(context: Context, update: AvailableAppUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(directory, "kabadiwala-connect-${update.versionCode}.apk")
        if (target.isFile && target.length() > 0L) return@withContext target

        val temporary = File(directory, ".${target.name}.download")
        try {
            val request = Request.Builder().url(update.apkUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Update download failed: ${response.code}")
                val body = response.body ?: throw IOException("Update download was empty")
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            if (!temporary.isFile || temporary.length() == 0L || !temporary.renameTo(target)) {
                throw IOException("Downloaded update is invalid")
            }
            target
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun install(activity: Activity, apk: File): InstallUpdateResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return InstallUpdateResult.PERMISSION_REQUIRED
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        return InstallUpdateResult.STARTED
    }

    private fun currentVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private data class UpdateManifest(
        val versionCode: Long = 0L,
        val versionName: String = "",
        val apkUrl: String = "",
        val releaseNotes: String? = null
    )
}
