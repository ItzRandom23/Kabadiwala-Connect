package com.irinteractivestudios.kabadiwalaconnect

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectorPhotoCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun viewPhotoDisplaysLoadedImageWithoutAnotherLoadPrompt() {
        val photo = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).let { bitmap ->
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                stream.toByteArray()
            }
        }
        var requests = 0
        var state by mutableStateOf(
            SupplyChainState(
                loading = false,
                initialLoadComplete = true,
                pickups = listOf(PickupRequestDto(id = "pickup-1", listingId = "listing-1", status = "REQUESTED")),
                listings = listOf(HouseholdListingDto(id = "listing-1", photoCount = 1, materialCategory = "OTHER"))
            )
        )

        composeRule.setContent {
            KabadiwalaConnectTheme {
                KabadiwalaSupplyScreen(
                    state = state,
                    section = KabadiwalaSection.PICKUPS,
                    onRefresh = {},
                    onAccept = {},
                    onSchedule = { _, _ -> },
                    onStatus = { _, _ -> },
                    onComplete = { _, _ -> },
                    onCreateBulk = {},
                    onCancelBulk = {},
                    onAcceptOffer = {},
                    listingPhotos = state.listingPhotos,
                    listingPhotoErrors = state.listingPhotoErrors,
                    onLoadListingPhotos = { _, _ ->
                        requests++
                        state = state.copy(listingPhotos = mapOf("listing-1" to listOf(photo)))
                    }
                )
            }
        }

        composeRule.onNodeWithText("View scrap photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Photos available: 1 of 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("View scrap photo").performClick()
        composeRule.waitForIdle()
        assertEquals(1, requests)
    }
}
