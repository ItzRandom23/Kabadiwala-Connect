package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkOfferRulesTest {
    @Test fun minimumIsEnforcedIncludingTheBoundary() {
        assertFalse(isValidBulkOffer(99.99, 100.0))
        assertTrue(isValidBulkOffer(100.0, 100.0))
        assertTrue(isValidBulkOffer(125.0, 100.0))
        assertTrue(isValidBulkOffer(1.0, null))
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1_000_001.0).forEach {
            assertFalse(isValidBulkOffer(it, null))
        }
    }
    @Test fun onlyEditableOffersOnListedLotsCanBeSubmittedAgain() {
        listOf(null, "PENDING", "CANCELLED", "REJECTED").forEach { assertTrue(canReviseBulkOffer("LISTED", it)) }
        listOf("ACCEPTED", "COMPLETED").forEach { assertFalse(canReviseBulkOffer("LISTED", it)) }
        listOf("RESERVED", "SOLD", "CANCELLED").forEach { assertFalse(canReviseBulkOffer(it, "CANCELLED")) }
    }
}
