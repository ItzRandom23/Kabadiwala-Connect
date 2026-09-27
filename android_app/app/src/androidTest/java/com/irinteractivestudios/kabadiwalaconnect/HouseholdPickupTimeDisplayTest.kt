package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HouseholdListingDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PickupRequestDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.HouseholdSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HouseholdPickupTimeDisplayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun householdSeesKabadiwalasIndiaLocalScheduledTime() {
        val state = SupplyChainState(
            loading = false,
            householdListingsLoading = false,
            householdListingsLoaded = true,
            initialLoadComplete = true,
            listings = listOf(HouseholdListingDto(id = "listing-1", materialCategory = "OTHER", status = "POSTED")),
            pickups = listOf(
                PickupRequestDto(
                    id = "pickup-1",
                    listingId = "listing-1",
                    kabadiwalaId = "collector-1",
                    status = "SCHEDULED",
                    scheduledSlot = "2026-09-28T05:00:00Z"
                )
            )
        )

        composeRule.setContent {
            KabadiwalaConnectTheme {
                HouseholdSupplyScreen(
                    state = state,
                    onRefresh = {},
                    onCreateListing = {},
                    onRequestPickup = { _, _ -> }
                )
            }
        }

        // LazyColumn does not compose an off-screen listing until it is scrolled
        // into view. The pickup-hours note adds another item above the card.
        repeat(4) {
            if (composeRule.onAllNodesWithText("Scheduled: 28 Sep 2026, 10:30 AM").fetchSemanticsNodes().isEmpty()) {
                composeRule.onRoot().performTouchInput { swipeUp() }
            }
        }
        composeRule.onNodeWithText("Scheduled: 28 Sep 2026, 10:30 AM")
            .performScrollTo()
            .assertIsDisplayed()
        val messageButton = composeRule.onNodeWithText("Message Kabadiwala")
        messageButton.performScrollTo().assertIsDisplayed()
        val scheduledTop = composeRule.onNodeWithText("Scheduled: 28 Sep 2026, 10:30 AM").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Scheduled time should appear above the chat button", scheduledTop < messageButton.fetchSemanticsNode().boundsInRoot.top)
    }
}
