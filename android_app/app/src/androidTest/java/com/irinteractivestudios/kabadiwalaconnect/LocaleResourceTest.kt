package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that every advertised locale resolves resources and survives a fresh context. */
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
    fun everySupportedLanguageLocalizesNewlyExternalizedUiCopy() {
        val english = LocaleManager.applyTag(context, LocaleManager.ENGLISH)
        val longUiCopy = R.string.ui_copy_55b4cfc1987d
        val adminTitle = R.string.ui_copy_ffdf3f8e46d5
        val englishFormat = english.getString(R.string.ui_copy_55b4cfc1987d)

        LocaleManager.SUPPORTED.filterNot { it == LocaleManager.ENGLISH }.forEach { tag ->
            val localized = LocaleManager.applyTag(context, tag)
            assertNotEquals("$tag fell back to English for an extracted screen message", englishFormat, localized.getString(longUiCopy))
            assertNotEquals("$tag fell back to English for an extracted screen title", english.getString(adminTitle), localized.getString(adminTitle))
            assertTrue("$tag produced an empty screen message", localized.getString(longUiCopy).isNotBlank())
        }
    }

    @Test
    fun everySupportedLanguageSurvivesFreshContextWrap() {
        val original = LocaleManager.persistedTag(context)
        val hadSelection = LocaleManager.hasPersistedTag(context)
        try {
            LocaleManager.SUPPORTED.forEach { tag ->
                assertEquals("Could not persist $tag", true, LocaleManager.persistTag(context, tag))
                val fresh = LocaleManager.wrap(context)
                assertEquals(tag, fresh.resources.configuration.locales[0].language)
                assertEquals(tag, LocaleManager.persistedTag(fresh))
                if (tag != LocaleManager.ENGLISH) {
                    assertNotEquals("$tag did not resolve its app name", "Kabadiwala Connect", fresh.getString(R.string.app_name))
                }
            }
        } finally {
            if (hadSelection) LocaleManager.persistTag(context, original)
            else context.getSharedPreferences("kc_locale_prefs", Context.MODE_PRIVATE).edit().remove("language_tag").commit()
        }
    }
}
