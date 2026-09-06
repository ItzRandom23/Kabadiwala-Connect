package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies supported languages and safe fallback to English. */
class LocaleManagerTest {

    @Test
    fun supported_containsEnglishHindiMarathi() {
        assertTrue(LocaleManager.SUPPORTED.containsAll(listOf("en", "hi", "mr")))
    }

    @Test
    fun normalizeTag_keepsSupportedTags() {
        assertEquals("en", LocaleManager.normalizeTag("en"))
        assertEquals("hi", LocaleManager.normalizeTag("hi"))
        assertEquals("mr", LocaleManager.normalizeTag("mr"))
    }

    @Test
    fun normalizeTag_fallsBackToEnglish() {
        assertEquals("en", LocaleManager.normalizeTag("fr"))
        assertEquals("en", LocaleManager.normalizeTag(""))
        assertEquals("en", LocaleManager.normalizeTag(null))
    }
}
