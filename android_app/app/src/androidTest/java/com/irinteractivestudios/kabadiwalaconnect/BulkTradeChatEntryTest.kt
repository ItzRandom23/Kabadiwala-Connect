package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkOfferDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSection
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.RecyclerSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BulkTradeChatEntryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun kabadiwalaReservedLotOpensRecyclerChat() {
        var selected: Pair<String, String>? = null
        composeRule.setContent {
            KabadiwalaConnectTheme {
                KabadiwalaSupplyScreen(
                    state = SupplyChainState(loading = false, initialLoadComplete = true,
                        bulkLots = listOf(BulkLotDto(id = "bulk-1", status = "RESERVED", reservedForId = "recycler-1"))),
                    section = KabadiwalaSection.LOTS,
                    onRefresh = {}, onAccept = {}, onSchedule = { _, _ -> }, onStatus = { _, _ -> },
                    onComplete = { _, _ -> }, onCreateBulk = {}, onCancelBulk = {}, onAcceptOffer = {},
                    onOpenBulkChat = { lotId, recyclerId -> selected = lotId to recyclerId }
                )
            }
        }
        scrollUntilVisible("Message Recycler")
        composeRule.onNodeWithText("Message Recycler").performScrollTo().performClick()
        assertEquals("bulk-1" to "recycler-1", selected)
    }

    @Test fun recyclerAcceptedOfferOpensKabadiwalaChat() {
        var selected: Pair<String, String>? = null
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerSupplyScreen(
                    state = SupplyChainState(loading = false, initialLoadComplete = true,
                        offers = listOf(BulkOfferDto(id = "offer-1", bulkLotId = "bulk-1", recyclerId = "recycler-1", status = "ACCEPTED"))),
                    onRefresh = {}, onOffer = { _, _ -> }, onReceive = {}, onOpenDemand = {},
                    onOpenBulkChat = { lotId, collectorId -> selected = lotId to collectorId }
                )
            }
        }
        scrollUntilVisible("Message Kabadiwala")
        composeRule.onNodeWithText("Message Kabadiwala").performScrollTo().performClick()
        assertEquals("bulk-1" to "", selected)
    }

    private fun scrollUntilVisible(text: String) {
        repeat(7) {
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.onRoot().performTouchInput { swipeUp() }
        }
    }
}
