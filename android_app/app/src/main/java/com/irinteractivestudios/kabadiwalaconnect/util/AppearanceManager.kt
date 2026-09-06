package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/** Persisted appearance preference. Kept synchronous so the first frame is correct. */
object AppearanceManager {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
    val SUPPORTED = listOf(SYSTEM, LIGHT, DARK)

    private const val PREFS = "kc_appearance_prefs"
    private const val KEY_MODE = "appearance_mode"

    fun normalize(mode: String?): String = mode?.uppercase()?.takeIf { it in SUPPORTED } ?: SYSTEM

    fun load(context: Context): String = normalize(prefs(context).getString(KEY_MODE, SYSTEM))

    fun save(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, normalize(mode)).apply()
    }

    @Composable
    fun isDark(mode: String): Boolean = when (normalize(mode)) {
        DARK -> true
        LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
