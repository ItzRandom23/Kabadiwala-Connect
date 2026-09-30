package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkOfferDto
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanState
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.*
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Rule
import org.junit.Test

class RecyclerScanSafetyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmationDisablesCameraVerificationAndReset() {
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerScanScreen(RecyclerScanState(confirming = true), {}, { _, _, _ -> }, {})
        } }
        compose.onNodeWithText("Open camera scanner").assertIsNotEnabled()
        compose.onNodeWithText("Verify handover").assertIsNotEnabled()
        compose.onNodeWithText("Clear and scan another").assertIsNotEnabled()
    }

    @Test fun collectorCannotAcceptOwnCounterProposal() {
        val lot = BulkLotDto(id = "lot", status = "LISTED", quantityKg = 1.0, minimumRatePerKg = 200.0)
        val offer = BulkOfferDto(id = "offer", bulkLotId = "lot", recyclerId = "recycler", offeredRatePerKg = 250.0,
            status = "PENDING", bulkLot = lot, counterRatePerKg = 300.0)
        compose.setContent { KabadiwalaConnectTheme {
            KabadiwalaSupplyScreen(SupplyChainState(loading = false, initialLoadComplete = true,
                bulkLots = listOf(lot), offers = listOf(offer)), KabadiwalaSection.LOTS,
                {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("Offers").performClick()
        compose.onNodeWithText("Accept").performScrollTo().assertIsNotEnabled()
    }
}
