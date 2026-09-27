package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Checks locale selection and a small resource sample; it is not a translation coverage test. */
@RunWith(AndroidJUnit4::class)
class LocaleResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everySupportedLanguageLoadsLocalizedAppName() {
        val english = LocaleManager.applyTag(context, LocaleManager.ENGLISH)
            .getString(R.string.app_name)

        LocaleManager.SUPPORTED
            .filterNot { it == LocaleManager.ENGLISH }
            .forEach { tag ->
                val localized = LocaleManager.applyTag(context, tag)
                assertNotEquals("$tag unexpectedly falls back to English", english, localized.getString(R.string.app_name))
                assertEquals("$tag did not become the active locale", tag, localized.resources.configuration.locales[0].language)
            }
    }

    @Test
    fun hindiAndMarathiResolveCurrentCoreCopy() {
        val english = LocaleManager.applyTag(context, LocaleManager.ENGLISH)
        val hindi = LocaleManager.applyTag(context, LocaleManager.HINDI)
        val marathi = LocaleManager.applyTag(context, LocaleManager.MARATHI)
        val required = listOf(
            R.string.home_household_title,
            R.string.recycler_verification_status_pending,
            R.string.prices_subtitle,
            R.string.lot_edit_title,
            R.string.settings_language_section,
            R.string.transaction_passport_title,
            R.string.auth_benefit_sell_title,
            R.string.home_next_label,
            R.string.payment_calculated_amount,
            R.string.nav_kabadiwala_inventory
        )

        required.forEach { id ->
            assertNotEquals("Hindi fell back to English for $id", english.getString(id), hindi.getString(id))
            assertNotEquals("Marathi fell back to English for $id", english.getString(id), marathi.getString(id))
        }
    }

    @Test
    fun savedLanguageSurvivesFreshContextWrap() {
        val original = LocaleManager.persistedTag(context)
        val hadSelection = LocaleManager.hasPersistedTag(context)
        try {
            listOf(LocaleManager.HINDI, LocaleManager.MARATHI, LocaleManager.TAMIL, LocaleManager.ENGLISH).forEach { tag ->
                LocaleManager.persistTag(context, tag)
                val fresh = LocaleManager.wrap(context)
                assertEquals(tag, fresh.resources.configuration.locales[0].language)
                assertEquals(tag, LocaleManager.persistedTag(fresh))
            }
        } finally {
            if (hadSelection) LocaleManager.persistTag(context, original)
            else context.getSharedPreferences("kc_locale_prefs", Context.MODE_PRIVATE).edit().remove("language_tag").commit()
        }
    }
}
