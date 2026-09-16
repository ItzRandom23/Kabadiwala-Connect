package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.MockRecyclerData
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerFilterEngine
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerFilters
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerMatcher
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecyclerDiscoveryTest {
    @Test fun search_matchesNameOrArea() {
        val result = RecyclerFilterEngine.apply(MockRecyclerData.all, RecyclerFilters(query = "wakad"))
        assertEquals(listOf("EcoDrop Materials"), result.map { it.name })
    }

    @Test fun filters_applyRadiusMaterialAndPickup() {
        val result = RecyclerFilterEngine.apply(MockRecyclerData.all, RecyclerFilters(radiusKm = 10, material = "Cables", pickupOnly = true))
        assertEquals(listOf("GreenLoop Recycling"), result.map { it.name })
    }

    @Test fun sorting_ratePlacesHighestFirst() {
        val result = RecyclerFilterEngine.apply(MockRecyclerData.all, RecyclerFilters(sort = RecyclerSortMode.RATE))
        assertEquals("Mumbai E-Cycle", result.first().name)
    }

    @Test fun matching_rewardsMaterialDistancePickupRateAndVerification() {
        val match = RecyclerMatcher.score(MockRecyclerData.all.first(), "Battery")
        assertTrue(match.total >= 80)
        assertTrue(match.material > 0)
        assertTrue(match.verified > 0)
    }

    @Test fun emptyResults_areReturnedWithoutFallbackData() {
        val result = RecyclerFilterEngine.apply(MockRecyclerData.all, RecyclerFilters(query = "nowhere"))
        assertTrue(result.isEmpty())
    }
}
