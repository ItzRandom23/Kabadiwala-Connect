package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndiaFormatTest {
    @Test
    fun utcPickupSlotDisplaysIndiaLocalTime() {
        assertEquals("28 Sep 2026, 10:30 AM", IndiaFormat.dateTimeIso("2026-09-28T05:00:00Z", Locale.US))
    }

    @Test
    fun invalidPickupTimestampIsNotMisrepresentedAsLocalTime() {
        assertNull(IndiaFormat.dateTimeIso("not-a-date", Locale.US))
    }
}
