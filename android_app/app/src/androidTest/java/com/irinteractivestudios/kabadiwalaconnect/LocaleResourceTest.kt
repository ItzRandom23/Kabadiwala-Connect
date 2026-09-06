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
}
