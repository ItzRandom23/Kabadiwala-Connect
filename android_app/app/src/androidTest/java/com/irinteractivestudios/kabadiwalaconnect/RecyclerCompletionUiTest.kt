package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.*
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecyclerCompletionUiTest {
    @get:Rule val compose = createComposeRule()
    private val lot = BulkLotDto(id = "lot-1", kabadiwalaId = "collector-1", status = "SOLD", quantityKg = 0.5, askingRatePerKg = 500.0)
    private val offer = BulkOfferDto(id = "offer-1", bulkLotId = lot.id, recyclerId = "recycler-1", status = "ACCEPTED", offeredRatePerKg = 600.0, bulkLot = lot)
    private val handover = SupplyHandoverDto(id = "handover-1", bulkLotId = lot.id, collectorId = "collector-1", recyclerId = "recycler-1", status = "COMPLETED", referenceId = "RECEIPT-1", quotedWeightKg = 0.5, finalAcceptedKg = 0.5, quotedRatePerKg = 600.0, quotedValue = 300.0, finalValue = 300.0)

    @Test fun soldCollectorLotHasNoActiveOfferOrMessageButton() {
        compose.setContent { KabadiwalaConnectTheme {
            KabadiwalaSupplyScreen(SupplyChainState(loading = false, initialLoadComplete = true, bulkLots = listOf(lot), offers = listOf(offer)), KabadiwalaSection.LOTS, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("Message Recycler").assertDoesNotExist()
        compose.onNodeWithText("Recycler offers · 1").assertDoesNotExist()
        compose.onNodeWithText("Offers").performClick()
        compose.onNodeWithText("Message Recycler").assertDoesNotExist()
    }
    @Test fun soldRecyclerOfferHasNoMessageOrEditAction() {
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerSupplyScreen(SupplyChainState(loading = false, initialLoadComplete = true, offers = listOf(offer)), {}, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Message Kabadiwala").assertDoesNotExist()
        compose.onNodeWithText("Edit offer").assertDoesNotExist()
    }
    @Test fun collectorMustExplicitlyConfirmReceivedMoney() {
        var decision: String? = null
        val payment = SupplyPaymentDto(id = "payment-1", amount = 300.0, status = "RECORDED")
        compose.setContent { KabadiwalaConnectTheme { Column(Modifier.width(320.dp)) {
            SupplyPaymentSection(handover.copy(payments = listOf(payment)), recycler = false, onConfirm = { _, value, _ -> decision = value })
        } } }
        assertNull(decision)
        compose.onNodeWithText("I received ₹300.00").performClick()
        assertNull(decision)
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("ACCEPT", decision)
    }
    @Test fun materialReceiptDoesNotAutomaticallyRecordPayment() {
        var recorded: SupplyPaymentRequestDto? = null
        compose.setContent { KabadiwalaConnectTheme { Column(Modifier.width(320.dp)) {
            SupplyPaymentSection(handover, recycler = true, onRecord = { _, input -> recorded = input })
        } } }
        compose.onNodeWithText("Payment pending · ₹300.00").assertExists()
        assertNull(recorded)
        compose.onNodeWithText("Record payment").performClick()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(300.0, recorded!!.amount, 0.001)
        assertEquals("CASH", recorded!!.method)
    }
    @Test fun pickupChargeSaveDoesNotSubmitAvailability() {
        var availabilitySaved = 0
        var pricing: RecyclerProfileUpdateRequestDto? = null
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerPickupsScreen(availability = "FLEXIBLE", profile = RecyclerDto("recycler-1", "Facility", pickupAvailable = true, pickupIncluded = true), onSave = { availabilitySaved++ }, onSavePricing = { pricing = it })
        } }
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Pickup charges").performClick()
        compose.onNodeWithText("Save pickup charges").performScrollTo().performClick()
        assertEquals(0, availabilitySaved); assertNotNull(pricing); assertNull(pricing!!.pickupAvailability)
    }
    @Test fun availabilitySavingOnlyShowsItsOwnPendingState() {
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerPickupsScreen(availability = "FLEXIBLE", loading = false, saving = true, savingSection = "availability")
        } }
        compose.onNodeWithText("Saving…").assertExists()
        compose.onNodeWithText("Pickup charges").performClick()
        compose.onNodeWithText("Save pickup charges").assertExists().assertIsNotEnabled()
        compose.onNodeWithText("Saving…").assertDoesNotExist()
    }
    @Test fun scannerReturnsHomeOnlyAfterServerConfirmsReceipt() {
        var scanState by mutableStateOf(RecyclerScanState(supplyVerified = handover.copy(status = "COLLECTOR_CONFIRMED")))
        var returns = 0
        compose.setContent { KabadiwalaConnectTheme {
            RecyclerScanScreen(scanState, {}, { _, _, _ -> }, {}, onReceiptRecorded = { returns++ })
        } }
        compose.runOnIdle { assertEquals(0, returns); scanState = scanState.copy(supplyConfirmed = handover) }
        compose.waitForIdle(); assertEquals(1, returns)
    }
    @Test fun collectorReturnsHomeWhenVisibleHandoverCompletes() {
        var state by mutableStateOf(SupplyChainState(loading = false, initialLoadComplete = true, handovers = listOf(handover.copy(status = "COLLECTOR_CONFIRMED"))))
        var returns = 0
        compose.setContent { KabadiwalaConnectTheme {
            KabadiwalaSupplyScreen(state, KabadiwalaSection.LOTS, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, onHandoverCompleted = { returns++ })
        } }
        compose.runOnIdle { assertEquals(0, returns); state = state.copy(handovers = listOf(handover)) }
        compose.waitForIdle(); assertEquals(1, returns)
    }
    @Test fun recyclerMarketRemainsUsableAt320DpWithLargeText() {
        var openedDemand = false
        var openedOrders = false
        compose.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(originalDensity.density, 1.3f)) {
                KabadiwalaConnectTheme(darkTheme = true) {
                    Column(Modifier.width(320.dp)) {
                        RecyclerSupplyScreen(SupplyChainState(loading = false, initialLoadComplete = true,
                            bulkLots = listOf(lot.copy(status = "LISTED", materialCategory = "COPPER", quantityKg = 25.0, areaName = "Geeta Colony, Delhi")),
                            handovers = listOf(handover)), {}, { _, _ -> }, {}, { openedDemand = true }, onOpenOrders = { openedOrders = true })
                    }
                }
            }
        }
        compose.onNodeWithText("New demand").performClick()
        compose.onNodeWithText("Orders & payments").performClick()
        assertTrue(openedDemand); assertTrue(openedOrders)
        capture("recycler-market-320.png")
        compose.onNodeWithText("Offers").performClick()
        compose.onNodeWithText("No offers yet").assertExists()
        compose.onNodeWithText("Demand").performClick()
        capture("recycler-demand-320.png")
    }
    @Test fun completedOrderShowsPaymentPendingInLightThemeAndNoExpiredQrWarning() {
        compose.setContent { KabadiwalaConnectTheme(darkTheme = false) {
            Column(Modifier.width(320.dp)) { RecyclerOrdersScreen(liveHandovers = listOf(handover.copy(expiresAt = "2020-01-01T00:00:00Z"))) }
        } }
        compose.onNodeWithText("Payment pending · ₹300.00").performScrollTo().assertExists()
        compose.onNodeWithText("Record payment").assertExists()
        compose.onNodeWithText("QR expired").assertDoesNotExist()
        capture("recycler-orders-light-320.png")
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KabadiwalaUi")
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
