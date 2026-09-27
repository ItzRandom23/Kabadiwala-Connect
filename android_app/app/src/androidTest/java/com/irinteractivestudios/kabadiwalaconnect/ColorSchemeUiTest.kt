package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcStatusPill
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ColorSchemeUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun lightThemePublishesTheBrandRolesAndSemanticStatusLabels() {
        var observedColors: List<Color>? = null
        composeRule.setContent {
            KabadiwalaConnectTheme(darkTheme = false) {
                observedColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    KcStatusPill("Pending")
                    KcStatusPill("Verified")
                    KcStatusPill("Completed")
                    KcStatusPill("Cancelled")
                    KcStatusPill("Failed")
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(
            listOf(
                Color(0xFF386A20), Color(0xFFB8F397), Color(0xFFF8FAF5),
                Color(0xFFFFFFFF), Color(0xFFE2E8DC), Color(0xFF191D17), Color(0xFF5F665B)
            ),
            observedColors
        )
        listOf("Pending", "Verified", "Completed", "Cancelled", "Failed").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun darkThemePublishesDarkRolesAndRetainsAllStatusLabels() {
        var observedColors: List<Color>? = null
        composeRule.setContent {
            KabadiwalaConnectTheme(darkTheme = true) {
                observedColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh
                )
                Column {
                    KcStatusPill("Pending")
                    KcStatusPill("Verified")
                    KcStatusPill("Completed")
                    KcStatusPill("Cancelled")
                    KcStatusPill("Failed")
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(
            listOf(
                Color(0xFF9DD67D), Color(0xFF24510E), Color(0xFF10140F),
                Color(0xFF171C16), Color(0xFF41493D), Color(0xFFE1E4DC),
                Color(0xFFC0C9B9), Color(0xFF20271F)
            ),
            observedColors
        )
        listOf("Pending", "Verified", "Completed", "Cancelled", "Failed").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }
}
