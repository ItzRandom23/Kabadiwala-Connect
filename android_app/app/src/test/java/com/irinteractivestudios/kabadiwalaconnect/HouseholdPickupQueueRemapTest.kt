package com.irinteractivestudios.kabadiwalaconnect

import com.google.gson.JsonParser
import com.irinteractivestudios.kabadiwalaconnect.data.sync.remapHouseholdPickupListingReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HouseholdPickupQueueRemapTest {
    @Test
    fun replacesOnlyTheTemporaryListingReferenceAndPreservesRequestDetails() {
        val updated = remapHouseholdPickupListingReference(
            """{"listingId":"local-listing-1","kabadiwalaId":"collector-2","idempotencyKey":"pickup-key"}""",
            "local-listing-1",
            "server-listing-9"
        )

        val payload = JsonParser.parseString(requireNotNull(updated)).asJsonObject
        assertEquals("server-listing-9", payload.get("listingId").asString)
        assertEquals("collector-2", payload.get("kabadiwalaId").asString)
        assertEquals("pickup-key", payload.get("idempotencyKey").asString)
    }

    @Test
    fun leavesUnrelatedOrAlreadyReconciledRequestsAlone() {
        val unrelated = """{"listingId":"local-other"}"""
        val alreadyRemote = """{"listingId":"server-listing-9"}"""

        assertNull(remapHouseholdPickupListingReference(unrelated, "local-listing-1", "server-listing-9"))
        assertNull(remapHouseholdPickupListingReference(alreadyRemote, "local-listing-1", "server-listing-9"))
    }
}
