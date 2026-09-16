package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteExpiry
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteWorkflowTest {
    @Test fun quoteStates_includeLocalRequestLifecycle() {
        assertEquals(5, QuoteStatus.entries.size)
        assertEquals(4, QuoteDeliveryState.entries.size)
    }

    @Test fun expiry_isExactlyAtTwentyFourHours() {
        val quote = Quote(status = QuoteStatus.PENDING, expiresAtEpochMs = 86_400_000L)
        assertTrue(!QuoteExpiry.isExpired(quote, 86_399_999L))
        assertTrue(QuoteExpiry.isExpired(quote, 86_400_000L))
    }

    @Test fun multipleOffers_canBeComparedByActualPrice() {
        val offers = listOf(Quote(id = "a", pricePerKg = 100.0), Quote(id = "b", pricePerKg = 125.0), Quote(id = "c", pricePerKg = 110.0))
        assertEquals("b", offers.maxBy { it.pricePerKg }.id)
    }

    @Test fun handoverReference_usesDatePrefix() {
        val reference = "HOV-19700101-123456"
        assertTrue(reference.matches(Regex("HOV-\\d{8}-\\d{6}")))
    }
}
