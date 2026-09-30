package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irinteractivestudios.kabadiwalaconnect.data.remote.KabadiwalaProfileDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.HouseholdKabadiwalasScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NearbyRadiusUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingRadiusKeepsTheSearchCentreAndHidesOldResults() {
        var requested: CurrentLocation? = null
        var radius = 0
        compose.setContent { KabadiwalaConnectTheme {
            HouseholdKabadiwalasScreen(
                SupplyChainState(loading = false, initialLoadComplete = true, kabadiwalaAreaQuery = "Delhi",
                    kabadiwalaLatitude = 28.65, kabadiwalaLongitude = 77.22, kabadiwalaRadiusKm = 25,
                    kabadiwalas = listOf(KabadiwalaProfileDto(id = "old", displayName = "Twenty km partner", distanceKm = 20.0))),
                {}, { _, _ -> fail("A radius search must keep coordinates") },
                { centre, selected -> requested = centre; radius = selected }, {}, {}
            )
        } }
        compose.onNodeWithText("5 km").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(5, radius)
        assertEquals(28.65, requested!!.latitude, 0.00001)
        assertEquals(77.22, requested!!.longitude, 0.00001)
        compose.onNodeWithText("Twenty km partner").assertDoesNotExist()
    }

    @Test fun areaOnlyResultsAreNotPresentedAsWithinFiveKilometres() {
        compose.setContent { KabadiwalaConnectTheme {
            HouseholdKabadiwalasScreen(
                SupplyChainState(loading = false, initialLoadComplete = true, kabadiwalaAreaQuery = "Delhi",
                    kabadiwalaRadiusKm = 5, kabadiwalas = listOf(KabadiwalaProfileDto(id = "unknown", displayName = "Unmeasured partner"))),
                {}, { _, _ -> }, { _, _ -> }, {}, {}
            )
        } }
        compose.onNodeWithText("Unmeasured partner").assertDoesNotExist()
        compose.onNodeWithText("Search this area or use current location to find partners within the selected radius.").performScrollTo().assertIsDisplayed()
    }
}
