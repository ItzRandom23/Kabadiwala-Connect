package com.irinteractivestudios.kabadiwalaconnect.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
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
    val areaName: String?,
    /** Street-level label when Android's geocoder has one; do not use for service-area matching. */
    val formattedAddress: String? = null,
    val accuracyMeters: Float? = null
)

fun interface LocationProvider {
    suspend fun current(): CurrentLocation?
}

/** Gets a fresh location for onboarding without adding a Google Play Services dependency. */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext

    override suspend fun current(): CurrentLocation? {
        val location = withContext(Dispatchers.Main.immediate) { requestFreshLocation() } ?: return null

        val address = reverseGeocode(location)
        return CurrentLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            areaName = address?.collectionAreaName(),
            formattedAddress = address?.formattedStreetAddress(),
            accuracyMeters = location.accuracy.takeIf { it.isFinite() && it > 0f }
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fineGranted = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val providers = buildList {
            if (fineGranted && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) add(LocationManager.GPS_PROVIDER)
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) add(LocationManager.NETWORK_PROVIDER)
        }.distinct()
        if (providers.isEmpty()) return null

        val now = System.currentTimeMillis()
        val lastKnown = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location ->
                val ageMs = now - location.time
                ageMs in 0..60_000L && location.accuracy.isFinite() && location.accuracy > 0f
            }
            .minByOrNull { location -> location.accuracy + ((now - location.time).coerceAtLeast(0L) / 2_000L).toFloat() }

        var bestFresh: Location? = null
        val targetAccuracyMeters = if (fineGranted) 30f else 100f
        val fresh = withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                lateinit var listener: LocationListener
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!continuation.isActive) return
                        if (!location.accuracy.isFinite() || location.accuracy <= 0f) return
                        if (bestFresh == null || location.accuracy < bestFresh!!.accuracy) {
                            bestFresh = Location(location)
                        }
                        if (location.accuracy <= targetAccuracyMeters) {
                            manager.removeUpdates(listener)
                            continuation.resume(Location(location))
                        }
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
        }

        if (fresh != null) return fresh
        val bestLiveFix = bestFresh
        if (bestLiveFix == null) return lastKnown
        if (lastKnown == null) return bestLiveFix

        // A recent, materially more accurate cached fix is better than a poor
        // live network fix. Otherwise prefer the fix measured during this call.
        val cachedAgeMs = System.currentTimeMillis() - lastKnown.time
        val usefulCachedFix = cachedAgeMs in 0..30_000L &&
            lastKnown.accuracy <= targetAccuracyMeters * 2f &&
            lastKnown.accuracy < bestLiveFix.accuracy * 0.7f
        return if (usefulCachedFix) lastKnown else bestLiveFix
    }

    private suspend fun reverseGeocode(location: Location): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(8_000L) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 5) { addresses ->
                            if (continuation.isActive) continuation.resume(addresses.orEmpty().bestAddress())
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 5).orEmpty().bestAddress()
                }
            }
        }.getOrNull()
    }
}

private fun List<Address>.bestAddress(): Address? = maxByOrNull { address ->
    (if (!address.premises.isNullOrBlank()) 3 else 0) +
        (if (!address.subThoroughfare.isNullOrBlank()) 2 else 0) +
        (if (!address.thoroughfare.isNullOrBlank()) 3 else 0) +
        (if (!address.postalCode.isNullOrBlank()) 1 else 0) +
        (if (!address.getAddressLine(0).isNullOrBlank()) 1 else 0)
}

/**
 * Builds a collection-friendly label such as "Hadapsar, Pune, Maharashtra".
 * A state by itself is deliberately rejected: coarse GPS is still saved, but
 * the collector must confirm a real locality instead of silently storing an
 * unusable state-wide collection area.
 */
private fun Address.collectionAreaName(): String? {
    val localParts = listOfNotNull(
        subLocality?.trim()?.takeIf(String::isNotBlank),
        locality?.trim()?.takeIf(String::isNotBlank)
            ?: subAdminArea?.trim()?.takeIf(String::isNotBlank)
    ).distinctBy(String::lowercase)
    if (localParts.isEmpty()) return null
    return (localParts + listOfNotNull(adminArea?.trim()?.takeIf(String::isNotBlank)))
        .distinctBy(String::lowercase)
        .take(3)
        .joinToString(", ")
}

/** Returns a human-readable pickup address only when street/building detail exists. */
private fun Address.formattedStreetAddress(): String? {
    val lines = (0..maxAddressLineIndex).mapNotNull { index ->
        getAddressLine(index)?.trim()?.takeIf(String::isNotBlank)
    }.distinctBy(String::lowercase)
    val hasStreetDetail = !premises.isNullOrBlank() || !subThoroughfare.isNullOrBlank() || !thoroughfare.isNullOrBlank()
    val detailedLine = lines.joinToString(", ").takeIf { hasStreetDetail && it.isNotBlank() }
    val structured = listOfNotNull(
        premises?.trim()?.takeIf(String::isNotBlank),
        listOfNotNull(subThoroughfare?.trim()?.takeIf(String::isNotBlank), thoroughfare?.trim()?.takeIf(String::isNotBlank))
            .joinToString(" ").takeIf(String::isNotBlank),
        subLocality?.trim()?.takeIf(String::isNotBlank),
        locality?.trim()?.takeIf(String::isNotBlank),
        subAdminArea?.trim()?.takeIf(String::isNotBlank),
        adminArea?.trim()?.takeIf(String::isNotBlank),
        postalCode?.trim()?.takeIf(String::isNotBlank)
    ).distinctBy(String::lowercase).joinToString(", ")
    return (detailedLine ?: structured.takeIf { hasStreetDetail && it.isNotBlank() })
        ?.take(160)
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
