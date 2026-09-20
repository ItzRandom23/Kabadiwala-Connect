package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.irinteractivestudios.kabadiwalaconnect.KabadiwalaApp
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/**
 * Device test: app launches into Home and every bottom tab opens.
 * Run on an emulator/device from Android Studio
 * (Right-click -> Run) or via connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavTest {

    @get:Rule
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(
            android.content.Intent(
                ApplicationProvider.getApplicationContext(),
                MainActivity::class.java
            ).apply { putExtra("demoRole", "COLLECTOR") }
        )
    ) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }

    @Before
    fun enterHomeWhenUnauthenticated() {
        val app = ApplicationProvider.getApplicationContext<KabadiwalaApp>()
        runBlocking {
            app.container.authenticationRepository.logout()
            app.container.clearAccount()
        }
        LocaleManager.persistTag(app, LocaleManager.ENGLISH)
        // The activity rule starts the app through its explicit debug-only
        // preview entry point, so navigation assertions do not depend on a
        // scroll position or onboarding timing.
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        // A fresh test process can render the first-run language gate before
        // the synchronous preference change is observed by Activity recreation.
        // Complete it explicitly so the rest of this class always starts from
        // the same welcome surface.
        if (composeTestRule.onAllNodesWithTag("first_language_en").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithTag("first_language_en").performClick()
            composeTestRule.onNodeWithTag("first_language_continue").performClick()
            composeTestRule.waitForIdle()
        }
        if (composeTestRule.onAllNodesWithTag("auth_start_over").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithTag("auth_start_over").performClick()
            composeTestRule.waitForIdle()
        }
        if (composeTestRule.onAllNodesWithTag("auth_demo").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithTag("auth_demo").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun appLaunchesOnHome() {
        composeTestRule.onNodeWithTag("nav_home")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun allBottomTabsOpen() {
        val tabs = listOf("nav_inventory", "nav_pickups", "nav_bulk_lots", "nav_settings")
        tabs.forEach { tag ->
            composeTestRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .performClick()
            try {
                composeTestRule.waitUntil(timeoutMillis = 10_000) {
                    runCatching { composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
                }
            } catch (error: Throwable) {
                throw AssertionError("Navigation did not settle for $tag", error)
            }
            composeTestRule.onNodeWithTag(tag).assertIsSelected()
        }
        // Back to Home.
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeTestRule.onAllNodesWithTag("nav_home").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun changingLanguageInDemoKeepsDemoSession() {
        composeTestRule.onNodeWithTag("nav_settings")
            .performClick()
        composeTestRule.onNodeWithTag("settings_language_picker")
            .performClick()
        composeTestRule.onNodeWithTag("lang_hi")
            .performClick()
        // Locale application recreates MainActivity asynchronously. Wait for
        // the restored navigation hierarchy instead of racing the recreation.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeTestRule.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("nav_settings")
            .assertIsDisplayed()
            .assertIsSelected()
        assertTrue(composeTestRule.onAllNodesWithTag("auth_get_started").fetchSemanticsNodes().isEmpty())
    }
}
