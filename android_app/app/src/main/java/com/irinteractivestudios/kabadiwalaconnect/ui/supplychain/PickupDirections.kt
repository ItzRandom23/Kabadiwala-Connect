package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Use the saved pickup address first: a manually entered address may differ from phone GPS. */
internal fun pickupDirectionsDestination(listing: HouseholdListingDto?): String? {
    listing?.pickupAddress?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val latitude = listing?.latitude
    val longitude = listing?.longitude
    return if (latitude != null && longitude != null && latitude in 6.0..38.0 && longitude in 68.0..98.0) {
        "$latitude,$longitude"
    } else null
}

internal fun pickupDirectionsUrl(destination: String): String =
    "https://www.google.com/maps/dir/?api=1&destination=${URLEncoder.encode(destination, StandardCharsets.UTF_8.name())}"

internal fun openPickupDirections(context: Context, destination: String): Boolean {
    val uri = Uri.parse(pickupDirectionsUrl(destination))
    val googleMaps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(googleMaps)
        return true
    } catch (_: ActivityNotFoundException) {
        // The Maps app is optional; its web directions page works in a browser.
    } catch (_: SecurityException) {
        // A device policy may block the Maps app while allowing a browser.
    }
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
