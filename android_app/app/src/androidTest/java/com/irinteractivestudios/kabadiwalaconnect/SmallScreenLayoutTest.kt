package com.irinteractivestudios.kabadiwalaconnect

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcBottomBar
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.HouseholdSupplyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SmallScreenLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun pendingRecyclerNavigationUsesAReadableShortLabelAtSmallWidth() {
        composeRule.setContent {
            val smallConfiguration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 640
                fontScale = 1.3f
            }
            CompositionLocalProvider(LocalConfiguration provides smallConfiguration) {
                KabadiwalaConnectTheme {
                    Box(Modifier.width(320.dp)) {
                        KcBottomBar(
                            currentRoute = Destinations.RECYCLER_VERIFY,
                            role = AccountRole.RECYCLER,
                            recyclerPending = true,
                            onNavigate = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("nav_verification").assertIsDisplayed()
        composeRule.onNodeWithText("Verify").assertIsDisplayed()
    }

    @Test
    fun householdActionAndSummaryStayInsideA320DpViewport() {
        composeRule.setContent {
            val smallConfiguration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 640
                fontScale = 1.3f
            }
            CompositionLocalProvider(LocalConfiguration provides smallConfiguration) {
                KabadiwalaConnectTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        HouseholdSupplyScreen(
                            state = SupplyChainState(loading = false, initialLoadComplete = true),
                            onRefresh = {},
                            onCreateListing = {},
                            onRequestPickup = { _, _ -> }
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithText("Sell scrap").fetchSemanticsNode().boundsInRoot
        assertTrue("Action must stay inside viewport", action.left >= viewport.left && action.right <= viewport.right)
        composeRule.onNodeWithText("Sell scrap").assertIsDisplayed()
    }

    @Test
    fun householdListingsFinishIndependentlyOfTheOtherDashboardRequests() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                HouseholdSupplyScreen(
                    state = SupplyChainState(
                        loading = true,
                        initialLoadComplete = false,
                        householdListingsLoading = false,
                        householdListingsLoaded = true
                    ),
                    onRefresh = {},
                    onCreateListing = {},
                    onRequestPickup = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("No listings yet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Loading your listings…").assertCountEquals(0)
    }
}
