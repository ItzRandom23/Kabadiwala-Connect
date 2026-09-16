package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Ensures every language advertised by the picker resolves to its own pack. */
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
}
