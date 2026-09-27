package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecyclerBottomNavTest {
    @get:Rule
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(
            android.content.Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .apply { putExtra("demoRole", "RECYCLER") }
        )
    ) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }

    @Before
    fun enterRecyclerDemo() {
        val app = ApplicationProvider.getApplicationContext<KabadiwalaApp>()
        runBlocking {
            app.container.authenticationRepository.logout()
            app.container.clearAccount()
        }
        LocaleManager.persistTag(app, LocaleManager.ENGLISH)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
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
    fun allRecyclerTabsOpenFromRecyclerStartDestination() {
        val tabs = listOf("nav_orders", "nav_pickups", "nav_rates", "nav_profile")
        composeTestRule.onNodeWithTag("nav_marketplace").assertIsDisplayed().assertIsSelected()
        tabs.forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                runCatching { composeTestRule.onNodeWithTag(tag).assertIsSelected(); true }.getOrDefault(false)
            }
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertIsSelected()
        }
        composeTestRule.onNodeWithTag("nav_marketplace").performClick()
        composeTestRule.onNodeWithTag("nav_marketplace").assertIsSelected()
    }
}
