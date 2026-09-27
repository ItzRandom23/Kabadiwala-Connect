package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSection
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.KabadiwalaSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecyclerHandoverFlowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun reservedLotCanPrepareItsFirstHandoverQr() {
        var preparedLotId: String? = null
        showLots(SupplyChainState(loading = false, initialLoadComplete = true,
            bulkLots = listOf(BulkLotDto(id = "bulk-1", materialCategory = "COPPER", quantityKg = 5.0, status = "RESERVED"))),
            onPrepare = { preparedLotId = it })

        scrollUntilVisible("Prepare handover QR")
        composeRule.onNodeWithText("Prepare handover QR").performScrollTo().performClick()
        assertEquals("bulk-1", preparedLotId)
    }

    @Test fun preparedHandoverCanBeConfirmed() {
        var confirmedId: String? = null
        val future = java.time.Instant.ofEpochMilli(System.currentTimeMillis() + 60 * 60 * 1000).toString()
        showLots(SupplyChainState(loading = false, initialLoadComplete = true,
            handovers = listOf(SupplyHandoverDto(id = "live-1", bulkLotId = "bulk-1", status = "PREPARED", qrCodeData = "live-code", expiresAt = future))),
            onPrepare = {}, onConfirm = { confirmedId = it })

        scrollUntilVisible("Confirm collector side")
        composeRule.onNodeWithText("Confirm collector side").performScrollTo().performClick()
        assertEquals("live-1", confirmedId)
    }

    @Test fun expiredQrCanBeRenewed() {
        var renewedLotId: String? = null
        val past = java.time.Instant.ofEpochMilli(System.currentTimeMillis() - 60 * 1000).toString()
        showLots(SupplyChainState(loading = false, initialLoadComplete = true,
            handovers = listOf(SupplyHandoverDto(id = "expired-1", bulkLotId = "bulk-2", status = "COLLECTOR_CONFIRMED", qrCodeData = "expired-code", expiresAt = past))),
            onPrepare = { renewedLotId = it })

        scrollUntilVisible("Prepare fresh QR")
        composeRule.onNodeWithText("Prepare fresh QR").performScrollTo().performClick()
        assertEquals("bulk-2", renewedLotId)
    }

    private fun showLots(state: SupplyChainState, onPrepare: (String) -> Unit, onConfirm: (String) -> Unit = {}) {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                KabadiwalaSupplyScreen(
                    state = state,
                    section = KabadiwalaSection.LOTS,
                    onRefresh = {}, onAccept = {}, onSchedule = { _, _ -> }, onStatus = { _, _ -> },
                    onComplete = { _, _ -> }, onCreateBulk = {}, onCancelBulk = {}, onAcceptOffer = {},
                    onPrepareBulkHandover = onPrepare, onConfirmCollectorHandover = onConfirm
                )
            }
        }
    }

    private fun scrollUntilVisible(text: String) {
        repeat(5) {
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
                composeRule.onRoot().performTouchInput { swipeUp() }
            }
        }
    }
}
