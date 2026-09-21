package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.localizedSupplyChainText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SupplyChainLocalizationTest {

    @Test
    fun hindiPhotoStatesAreLocalized() {
        withLanguage("hi") {
            assertEquals("जाँच फिर से करें", localizedSupplyChainText("Try detection again"))
            assertEquals("2 एंगल उपलब्ध", localizedSupplyChainText("2 angles available"))
        }
    }

    @Test
    fun marathiPhotoStatesAreLocalized() {
        withLanguage("mr") {
            assertEquals("पुन्हा फोटो तपासा", localizedSupplyChainText("Try detection again"))
            assertEquals("2 अँगल उपलब्ध", localizedSupplyChainText("2 angles available"))
        }
    }

    private fun withLanguage(language: String, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(language))
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
