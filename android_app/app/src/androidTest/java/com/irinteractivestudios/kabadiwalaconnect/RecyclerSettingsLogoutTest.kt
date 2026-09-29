package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecyclerSettingsLogoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun verifiedRecyclerCanReachLogoutFromProfileThroughSettings() {
        var settingsOpen by mutableStateOf(false)
        var loggedOut = false
        val profile = AccountProfile(
            id = "recycler-1",
            email = "recycler@example.test",
            role = AccountRole.RECYCLER,
            verificationStatus = RecyclerVerificationStatus.VERIFIED
        )
        composeRule.setContent {
            KabadiwalaConnectTheme {
                if (settingsOpen) SettingsScreen(
                    language = "en",
                    appVersion = "test",
                    onLanguageChange = {},
                    onOpenSafety = {},
                    onOpenHelp = {},
                    onLogout = { loggedOut = true }
                ) else RecyclerProfileScreen(profile = profile, onOpenSettings = { settingsOpen = true })
            }
        }
        composeRule.onNodeWithTag("recycler_open_settings").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_logout").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_logout_confirm").performClick()
        composeRule.runOnIdle { assertTrue(loggedOut) }
    }
}
