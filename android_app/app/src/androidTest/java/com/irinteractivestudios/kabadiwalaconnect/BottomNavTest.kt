package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device test: app launches into Home and every bottom tab opens.
 * Run on an emulator/device from Android Studio
 * (Right-click -> Run) or via connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesOnHome() {
        composeTestRule.onNodeWithTag("nav_home")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun allBottomTabsOpen() {
        val tabs = listOf("nav_prices", "nav_recyclers", "nav_earnings", "nav_settings")
        tabs.forEach { tag ->
            composeTestRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .performClick()
            composeTestRule.onNodeWithTag(tag).assertIsSelected()
        }
        // Back to Home.
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.onNodeWithTag("nav_home").assertIsSelected()
    }
}
