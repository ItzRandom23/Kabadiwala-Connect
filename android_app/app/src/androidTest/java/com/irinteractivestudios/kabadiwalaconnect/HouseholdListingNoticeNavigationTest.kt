package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.HouseholdListingCreateScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HouseholdListingNoticeNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ignoresAnOldListingNoticeButReturnsAfterANewSubmissionNotice() {
        var backCount = 0
        var state by mutableStateOf(SupplyChainState(notice = "Listing posted — choose a nearby Kabadiwala."))

        composeRule.setContent {
            KabadiwalaConnectTheme {
                HouseholdListingCreateScreen(
                    state = state,
                    initialArea = "Audit Area",
                    onBack = { backCount += 1 },
                    onSuggestMaterial = {},
                    onClearMaterialSuggestion = {},
                    onCreateListing = { _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals("Opening a new draft must not react to the previous listing result", 0, backCount)

        state = state.copy(notice = null)
        composeRule.waitForIdle()
        assertEquals("Clearing the old result must not close the form", 0, backCount)

        state = state.copy(notice = "Listing saved offline and will post when connected.")
        composeRule.waitForIdle()
        assertEquals("A new submission result must return to Household home", 1, backCount)
    }
}
