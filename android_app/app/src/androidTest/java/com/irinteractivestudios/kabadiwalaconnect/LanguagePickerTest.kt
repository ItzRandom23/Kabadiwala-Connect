package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.InitialLanguageScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguagePickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun firstRunCanChooseHindi() {
        var chosen: String? = null
        composeRule.setContent { KabadiwalaConnectTheme { InitialLanguageScreen { chosen = it } } }
        composeRule.onNodeWithTag("first_language_list").performScrollToIndex(8)
        composeRule.onNodeWithTag("first_language_hi").performScrollTo().performClick()
        composeRule.onNodeWithTag("first_language_continue").assertIsEnabled().performClick()
        assertEquals("hi", chosen)
    }

    @Test fun settingsCanChangeToMarathi() {
        var chosen: String? = null
        composeRule.setContent {
            KabadiwalaConnectTheme {
                SettingsScreen(language = "en", appVersion = "test", onLanguageChange = { chosen = it },
                    onOpenSafety = {}, onOpenHelp = {})
            }
        }
        composeRule.onNodeWithTag("settings_language_picker").performClick()
        composeRule.onNodeWithTag("lang_mr").performScrollTo().performClick()
        assertEquals("mr", chosen)
    }
}
