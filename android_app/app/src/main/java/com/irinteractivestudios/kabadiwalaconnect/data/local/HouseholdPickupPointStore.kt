package com.irinteractivestudios.kabadiwalaconnect.data.local

import android.content.Context
import java.security.MessageDigest

/**
 * Device-local, account-scoped home pickup coordinates. These are saved only
 * after the user confirms an India-bounded pin in the picker, so offline
 * listings never fall back to the phone's current GPS position.
 */
class HouseholdPickupPointStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(accountId: String?, expectedAddress: String): SavedPickupPoint? {
        val key = accountId?.takeIf(String::isNotBlank)?.let(::keyFor) ?: return null
        val latitude = prefs.getString("$key.latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString("$key.longitude", null)?.toDoubleOrNull() ?: return null
        val address = prefs.getString("$key.address", null) ?: return null
        if (!sameAddress(address, expectedAddress) || !isWithinIndiaBounds(latitude, longitude)) return null
        return SavedPickupPoint(latitude, longitude)
    }

    fun save(accountId: String?, address: String, latitude: Double, longitude: Double) {
        val key = accountId?.takeIf(String::isNotBlank)?.let(::keyFor) ?: return
        if (address.isBlank() || !isWithinIndiaBounds(latitude, longitude)) return
        prefs.edit()
            .putString("$key.latitude", latitude.toString())
            .putString("$key.longitude", longitude.toString())
            .putString("$key.address", address.trim())
            .apply()
    }

    private fun keyFor(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        return "account." + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun sameAddress(left: String, right: String): Boolean =
        right.isNotBlank() && left.trim().replace(WHITESPACE, " ").equals(right.trim().replace(WHITESPACE, " "), ignoreCase = true)

    private fun isWithinIndiaBounds(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in INDIA_SOUTH..INDIA_NORTH && longitude in INDIA_WEST..INDIA_EAST

    private companion object {
        const val PREFS_NAME = "household_pickup_points"
        val WHITESPACE = Regex("\\s+")
        const val INDIA_SOUTH = 6.4
        const val INDIA_WEST = 68.0
        const val INDIA_NORTH = 37.2
        const val INDIA_EAST = 97.5
    }
}

data class SavedPickupPoint(val latitude: Double, val longitude: Double)
