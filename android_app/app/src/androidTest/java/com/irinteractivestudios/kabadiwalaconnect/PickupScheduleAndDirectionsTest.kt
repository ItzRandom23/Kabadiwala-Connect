package com.irinteractivestudios.kabadiwalaconnect

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PickupRequestDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSection
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PickupScheduleAndDirectionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun acceptedPickupShowsDirectionsAndFifteenMinuteSchedulingRule() {
        showAcceptedPickup()
        composeRule.onNodeWithText("Show directions").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Schedule pickup").performScrollTo().performClick()
        composeRule.onNodeWithText("Pickups run 7:30 AM–6:30 PM. Choose a time at least 15 minutes from now.").assertIsDisplayed()
    }

    @Test
    fun directionsButtonSendsGoogleMapsViewIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("www.google.com", null)
        }
        val monitor = Instrumentation.ActivityMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        instrumentation.addMonitor(monitor)
        showAcceptedPickup()
        try {
            composeRule.onNodeWithText("Show directions").performScrollTo().performClick()
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun showAcceptedPickup() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                KabadiwalaSupplyScreen(
                    state = SupplyChainState(
                        loading = false,
                        initialLoadComplete = true,
                        pickups = listOf(PickupRequestDto(id = "pickup-1", listingId = "listing-1", kabadiwalaId = "collector-1", status = "ACCEPTED")),
                        listings = listOf(HouseholdListingDto(id = "listing-1", pickupAddress = "5, Geeta Colony Road, Delhi"))
                    ),
                    section = KabadiwalaSection.PICKUPS,
                    onRefresh = {},
                    onAccept = {},
                    onSchedule = { _, _ -> },
                    onStatus = { _, _ -> },
                    onComplete = { _, _ -> },
                    onCreateBulk = {},
                    onCancelBulk = {},
                    onAcceptOffer = {}
                )
            }
        }
    }
}
