package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeData
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeCommandCenterTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun experimentalToolsAreVisibleAndReachableFromHome() {
        var openedRewards = false
        composeRule.setContent {
            KabadiwalaConnectTheme {
                HomeScreen(
                    state = UiState.Success(HomeData(2)),
                    onSeePrices = {},
                    onFindRecyclers = {},
                    onOpenRewards = { openedRewards = true }
                )
            }
        }

        listOf("home_rewards", "home_schemes", "home_activities", "home_messages", "home_disputes")
            .forEach { composeRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithTag("home_rewards").performScrollTo().performClick()
        assertTrue(openedRewards)
    }
}
