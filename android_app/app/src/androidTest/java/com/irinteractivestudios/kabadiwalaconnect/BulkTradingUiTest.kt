package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkOfferDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.*
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BulkTradingUiTest {
    @get:Rule val compose = createComposeRule()
    private val lot = BulkLotDto(id = "bulk-1", kabadiwalaId = "collector-1", materialCategory = "PLASTIC", quantityKg = 10.0, askingRatePerKg = 120.0, minimumRatePerKg = 100.0, areaName = "Delhi")

    @Test fun incomingOfferIsActionableDirectlyUnderTheLot() {
        var accepted: String? = null
        compose.setContent { KabadiwalaConnectTheme {
            KabadiwalaSupplyScreen(
                SupplyChainState(loading = false, initialLoadComplete = true, bulkLots = listOf(lot), offers = listOf(BulkOfferDto(id = "offer-1", bulkLotId = lot.id, recyclerId = "recycler-1", offeredRatePerKg = 110.0, bulkLot = lot, recyclerName = "Demo Recycler"))),
                KabadiwalaSection.LOTS, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, { accepted = it }
            )
        } }
        reveal("Demo Recycler")
        compose.onNodeWithText("Demo Recycler").assertExists()
        reveal("Accept")
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        assertEquals("offer-1", accepted)
    }

    @Test fun belowMinimumIsBlockedAndValidOfferSubmitsTheCorrectLotAndRate() {
        var submitted: Pair<String, Double>? = null
        showRecycler(onOffer = { id, rate -> submitted = id to rate })
        reveal("Make offer")
        compose.onNodeWithText("Make offer").performScrollTo().performClick()
        val rate = compose.onNode(hasSetTextAction())
        rate.performTextReplacement("99.99")
        compose.onNodeWithText("Send offer").assertIsNotEnabled()
        rate.performTextReplacement("100")
        compose.onNodeWithText("Send offer").assertIsEnabled().performClick()
        assertEquals(lot.id to 100.0, submitted)
    }

    @Test fun withdrawnOfferCanBeSubmittedAgainAndPendingOfferCanBeEdited() {
        var submitted: Pair<String, Double>? = null
        showRecycler(BulkOfferDto(id = "offer-1", bulkLotId = lot.id, recyclerId = "recycler-1", status = "CANCELLED", offeredRatePerKg = 110.0, bulkLot = lot), onOffer = { id, rate -> submitted = id to rate })
        reveal("Make a new offer")
        compose.onAllNodesWithText("Make a new offer")[0].performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("115")
        compose.onNodeWithText("Send offer").performClick()
        assertEquals(lot.id to 115.0, submitted)
    }

    @Test fun pendingOfferCanBeEdited() {
        var submitted: Pair<String, Double>? = null
        showRecycler(BulkOfferDto(id = "offer-1", bulkLotId = lot.id, recyclerId = "recycler-1", offeredRatePerKg = 110.0, bulkLot = lot), onOffer = { id, rate -> submitted = id to rate })
        reveal("Edit offer")
        compose.onAllNodesWithText("Edit offer")[0].performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("120")
        compose.onNodeWithText("Send offer").performClick()
        assertEquals(lot.id to 120.0, submitted)
    }

    @Test fun confirmedOfferCannotBeEditedAndKeepsCollectorIdentityForChat() {
        var selected: Pair<String, String>? = null
        val reserved = lot.copy(status = "RESERVED", reservedForId = "recycler-1")
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerSupplyScreen(
                SupplyChainState(loading = false, initialLoadComplete = true, offers = listOf(BulkOfferDto(id = "offer-1", bulkLotId = lot.id, recyclerId = "recycler-1", status = "ACCEPTED", offeredRatePerKg = 110.0, bulkLot = reserved))),
                {}, { _, _ -> }, {}, {}, onOpenBulkChat = { id, collector -> selected = id to collector }
            )
        } }
        reveal("Message Kabadiwala")
        compose.onNodeWithText("Edit offer").assertDoesNotExist()
        compose.onNodeWithText("Make a new offer").assertDoesNotExist()
        compose.onNodeWithText("Message Kabadiwala").performScrollTo().performClick()
        assertEquals(lot.id to "collector-1", selected)
    }

    private fun showRecycler(offer: BulkOfferDto? = null, onOffer: (String, Double) -> Unit) {
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerSupplyScreen(
                SupplyChainState(loading = false, initialLoadComplete = true, bulkLots = listOf(lot), offers = listOfNotNull(offer)),
                {}, onOffer, {}, {}
            )
        } }
    }
    private fun reveal(text: String) {
        repeat(8) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onRoot().performTouchInput { swipeUp() }
        }
    }
}
