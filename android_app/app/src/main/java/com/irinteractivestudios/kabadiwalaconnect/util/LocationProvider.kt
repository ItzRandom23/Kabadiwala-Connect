package com.irinteractivestudios.kabadiwalaconnect.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val areaName: String?
)

fun interface LocationProvider {
    suspend fun current(): CurrentLocation?
}

/** Gets a fresh location for onboarding without adding a Google Play Services dependency. */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext

    override suspend fun current(): CurrentLocation? {
        val location = withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(10_000L) { requestFreshLocation() }
        } ?: return null

        val areaName = withContext(Dispatchers.IO) { reverseGeocode(location) }
        return CurrentLocation(location.latitude, location.longitude, areaName)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fineGranted = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val providers = buildList {
            if (fineGranted && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }.distinct()
        if (providers.isEmpty()) return null

        val lastKnown = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time <= 60_000L }

        val fresh = suspendCancellableCoroutine<Location?> { continuation ->
            lateinit var listener: LocationListener
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!continuation.isActive) return
                    manager.removeUpdates(listener)
                    continuation.resume(location)
                }
            }

            var registered = false
            providers.forEach { provider ->
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    registered = true
                }
            }
            if (!registered) continuation.resume(null)
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }

        return fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.areaName()
        }.getOrNull()
    }
}

private fun Address.areaName(): String? = listOf(
    locality,
    subLocality,
    subAdminArea,
    adminArea,
    featureName
).firstOrNull { !it.isNullOrBlank() }
