package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.formatIsoDateInput
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.isValidIsoDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecyclerVerificationDateTest {
    @Test
    fun numericTypingGetsIsoSeparatorsAutomatically() {
        assertEquals("2026-09-22", formatIsoDateInput("20260922"))
    }

    @Test
    fun pastedIsoDateAndInvalidCharactersAreNormalized() {
        assertEquals("2026-09-22", formatIsoDateInput("2026/09/22"))
        assertEquals("2026-09-22", formatIsoDateInput("2026-09-22"))
        assertEquals("2026-09", formatIsoDateInput("2026-09abc"))
    }

    @Test
    fun validationRejectsImpossibleCalendarDates() {
        assertTrue(isValidIsoDate("2026-09-22"))
        assertFalse(isValidIsoDate("2026-02-30"))
        assertFalse(isValidIsoDate("20260922"))
    }
}
