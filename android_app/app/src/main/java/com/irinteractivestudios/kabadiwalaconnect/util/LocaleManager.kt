package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * In-app language handling for English plus the 22 official Indian languages.
 *
 * Uses a tiny synchronous SharedPreferences file so the saved language can
 * be applied in [attachBaseContext] before any UI is inflated (works fully
 * offline). This deliberately keeps the startup preference synchronous so the
 * selected locale is applied before Compose renders.
 */
object LocaleManager {

    const val ENGLISH = "en"
    const val HINDI = "hi"
    const val MARATHI = "mr"
    const val ASSAMESE = "as"
    const val BENGALI = "bn"
    const val BODO = "brx"
    const val DOGRI = "doi"
    const val GUJARATI = "gu"
    const val KANNADA = "kn"
    const val KASHMIRI = "ks"
    const val KONKANI = "kok"
    const val MAITHILI = "mai"
    const val MALAYALAM = "ml"
    const val MANIPURI = "mni"
    const val NEPALI = "ne"
    const val ODIA = "or"
    const val PUNJABI = "pa"
    const val SANSKRIT = "sa"
    const val SANTALI = "sat"
    const val SINDHI = "sd"
    const val TAMIL = "ta"
    const val TELUGU = "te"
    const val URDU = "ur"
    const val DEFAULT = ENGLISH

    val SUPPORTED = listOf(ENGLISH, ASSAMESE, BENGALI, BODO, DOGRI, GUJARATI, HINDI, KANNADA, KASHMIRI, KONKANI, MAITHILI, MALAYALAM, MANIPURI, MARATHI, NEPALI, ODIA, PUNJABI, SANSKRIT, SANTALI, SINDHI, TAMIL, TELUGU, URDU)

    val LABELS = mapOf(
        ENGLISH to "English", ASSAMESE to "অসমীয়া", BENGALI to "বাংলা", BODO to "बड़ो", DOGRI to "डोगरी",
        GUJARATI to "ગુજરાતી", HINDI to "हिन्दी", KANNADA to "ಕನ್ನಡ", KASHMIRI to "कॉशुर", KONKANI to "कोंकणी",
        MAITHILI to "मैथिली", MALAYALAM to "മലയാളം", MANIPURI to "মৈতৈলোন্", MARATHI to "मराठी", NEPALI to "नेपाली",
        ODIA to "ଓଡ଼ିଆ", PUNJABI to "ਪੰਜਾਬੀ", SANSKRIT to "संस्कृतम्", SANTALI to "ᱥᱟᱱᱛᱟᱲᱤ", SINDHI to "सिन्धी",
        TAMIL to "தமிழ்", TELUGU to "తెలుగు", URDU to "اُردُو"
    )

    /** Stable server enum names for the 22 scheduled languages plus English. */
    private val BACKEND_NAMES = mapOf(
        ENGLISH to "ENGLISH", ASSAMESE to "ASSAMESE", BENGALI to "BENGALI", BODO to "BODO",
        DOGRI to "DOGRI", GUJARATI to "GUJARATI", HINDI to "HINDI", KANNADA to "KANNADA",
        KASHMIRI to "KASHMIRI", KONKANI to "KONKANI", MAITHILI to "MAITHILI", MALAYALAM to "MALAYALAM",
        MANIPURI to "MANIPURI", MARATHI to "MARATHI", NEPALI to "NEPALI", ODIA to "ODIA",
        PUNJABI to "PUNJABI", SANSKRIT to "SANSKRIT", SANTALI to "SANTALI", SINDHI to "SINDHI",
        TAMIL to "TAMIL", TELUGU to "TELUGU", URDU to "URDU"
    )

    private val TAGS_BY_BACKEND_NAME = BACKEND_NAMES.entries.associate { (tag, name) -> name to tag }

    private const val PREFS = "kc_locale_prefs"
    private const val KEY_TAG = "language_tag"

    /** Returns [tag] if supported, otherwise [DEFAULT]. Pure — unit-tested. */
    fun normalizeTag(tag: String?): String =
        if (SUPPORTED.contains(tag)) tag!! else DEFAULT

    fun toBackendName(tag: String?): String = BACKEND_NAMES[normalizeTag(tag)] ?: "ENGLISH"

    fun fromBackendName(name: String?): String = TAGS_BY_BACKEND_NAME[name?.uppercase(Locale.ROOT)] ?: DEFAULT

    fun persistedTag(context: Context): String =
        normalizeTag(prefs(context).getString(KEY_TAG, DEFAULT))

    /** True only after the user has explicitly chosen a language at least once. */
    fun hasPersistedTag(context: Context): Boolean = prefs(context).contains(KEY_TAG)

    fun persistTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_TAG, normalizeTag(tag)).apply()
    }

    /**
     * Wraps [context] with the persisted locale. Call from
     * Application and Activity [android.content.ContextWrapper.attachBaseContext].
     */
    fun wrap(context: Context): Context =
        applyTag(context, persistedTag(context))

    fun applyTag(context: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(normalizeTag(tag))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
