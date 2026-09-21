package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeData
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import org.junit.Rule
import org.junit.Test

class HomeCommandCenterTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dashboardKeepsPrimaryWorkActionsFocused() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                HomeScreen(
                    state = UiState.Success(HomeData(2)),
                    onSeePrices = {},
                    onFindRecyclers = {},
                )
            }
        }

        listOf("home_my_lots", "home_see_prices", "home_find_recyclers")
            .forEach { composeRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        composeRule.onAllNodesWithTag("home_rewards").assertCountEquals(0)
    }
}
