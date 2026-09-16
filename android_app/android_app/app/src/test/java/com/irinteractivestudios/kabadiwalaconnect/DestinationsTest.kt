package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.KABADIWALA_BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the 5-tab navigation structure and Home start destination. */
class DestinationsTest {

    @Test
    fun bottomTabs_areExactlyFive() {
        assertEquals(5, BOTTOM_TABS.size)
    }

    @Test
    fun appStartsOnHome() {
        assertEquals(Destinations.HOME, Destinations.START)
        assertTrue(Destinations.TOP_LEVEL.contains(Destinations.START))
    }

    @Test
    fun topLevel_containsAllFiveTabs() {
        assertEquals(5, Destinations.TOP_LEVEL.size)
        BOTTOM_TABS.forEach { tab ->
            assertTrue(Destinations.TOP_LEVEL.contains(tab.route))
        }
    }

    @Test
    fun routes_areUnique() {
        val routes = BOTTOM_TABS.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun testTags_areUnique() {
        val tags = BOTTOM_TABS.map { it.testTag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun secondaryScreens_areOutsideBottomTabs() {
        val tabRoutes = BOTTOM_TABS.map { it.route }.toSet()
        assertTrue(!tabRoutes.contains(Destinations.SAFETY))
        assertTrue(!tabRoutes.contains(Destinations.HELP))
    }

    @Test
    fun liveKabadiwalaNavigation_usesSupplyChainTabs() {
        assertEquals(5, KABADIWALA_BOTTOM_TABS.size)
        assertEquals(KABADIWALA_BOTTOM_TABS.map { it.route }, Destinations.topLevelFor(AccountRole.COLLECTOR, newNavigation = true))
        assertTrue(Destinations.KABADIWALA_TOP_LEVEL.contains(Destinations.KABADIWALA_INVENTORY))
    }

    @Test
    fun lotEdit_routeKeepsLotId() {
        assertEquals("lots/edit/LOT-123", Destinations.lotEdit("LOT-123"))
    }
}
