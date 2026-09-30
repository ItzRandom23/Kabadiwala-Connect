package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.resolveUserFacingError
import org.junit.Assert.*
import org.junit.Test

class LocalizedFailureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val network = "No internet connection. Check your network and retry."

    @Test fun selectedLanguageKeepsLocalizedNetworkAndAuthenticationErrorsAfterReopen() {
        val original = LocaleManager.persistedTag(context)
        val hadSelection = LocaleManager.hasPersistedTag(context)
        try {
            listOf("hi", "mr").forEach { language ->
                assertTrue(LocaleManager.persistTag(context, language))
                val reopened = LocaleManager.wrap(context)
                assertEquals(language, reopened.resources.configuration.locales[0].language)
                assertEquals(reopened.getString(R.string.auth_network_error), resolveUserFacingError(reopened, network))
                assertNotEquals(network, resolveUserFacingError(reopened, network))
                assertEquals(reopened.getString(R.string.auth_session_restore_failed),
                    resolveUserFacingError(reopened, "Your session expired. Please sign in again."))
            }
        } finally {
            if (hadSelection) LocaleManager.persistTag(context, original)
            else context.getSharedPreferences("kc_locale_prefs", Context.MODE_PRIVATE).edit().remove("language_tag").commit()
        }
    }

    @Test fun arbitraryBackendValidationIsNotTranslatedOrDiscarded() {
        val hindi = LocaleManager.applyTag(context, "hi")
        val detail = "Minimum valid offer is ₹200/kg"
        assertEquals(detail, resolveUserFacingError(hindi, detail))
        val english = LocaleManager.applyTag(context, "en")
        assertEquals(network, resolveUserFacingError(english, network))
    }
}
