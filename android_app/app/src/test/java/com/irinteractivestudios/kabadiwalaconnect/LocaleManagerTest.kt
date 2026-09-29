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
        assertTrue("Bodo should not be advertised until its translations are complete", !LocaleManager.SUPPORTED.contains("brx"))
        assertTrue("Kashmiri should not be advertised until its translations are complete", !LocaleManager.SUPPORTED.contains("ks"))
    }

    @Test
    fun normalizeTag_keepsSupportedTags() {
        assertEquals("en", LocaleManager.normalizeTag("en"))
        assertEquals("hi", LocaleManager.normalizeTag("hi"))
        assertEquals("mr", LocaleManager.normalizeTag("mr"))
    }

    @Test
    fun normalizeTag_acceptsCaseAndRegionVariants() {
        assertEquals("hi", LocaleManager.normalizeTag("HI-in"))
        assertEquals("mr", LocaleManager.normalizeTag("mr_IN"))
    }

    @Test
    fun normalizeTag_fallsBackToEnglish() {
        assertEquals("en", LocaleManager.normalizeTag("fr"))
        assertEquals("en", LocaleManager.normalizeTag(""))
        assertEquals("en", LocaleManager.normalizeTag(null))
    }

    @Test
    fun toBackendName_preservesSupportedDetectionLanguages() {
        assertEquals("ENGLISH", LocaleManager.toBackendName("en"))
        assertEquals("HINDI", LocaleManager.toBackendName("hi"))
        assertEquals("MARATHI", LocaleManager.toBackendName("mr"))
    }

    @Test
    fun everyAdvertisedLanguageRoundTripsThroughBackendName() {
        assertEquals(21, LocaleManager.SUPPORTED.distinct().size)
        LocaleManager.SUPPORTED.forEach { tag ->
            assertEquals(tag, LocaleManager.fromBackendName(LocaleManager.toBackendName(tag)))
            assertEquals(tag, LocaleManager.fromBackendName(tag))
        }
        assertEquals("hi", LocaleManager.fromBackendName("hi-IN"))
        assertEquals("mr", LocaleManager.fromBackendName(" marathi "))
        assertEquals("en", LocaleManager.normalizeTag("brx"))
        assertEquals("en", LocaleManager.normalizeTag("ks"))
        assertEquals("ENGLISH", LocaleManager.toBackendName("brx"))
        assertEquals("ENGLISH", LocaleManager.toBackendName("ks"))
    }
}
