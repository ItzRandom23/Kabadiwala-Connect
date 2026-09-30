package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SafetyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SafetyLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allMaterialFiltersRemainInsideSmallScreenWithLargeText() {
        val context: Context = ApplicationProvider.getApplicationContext()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                KabadiwalaConnectTheme { Box(Modifier.width(320.dp).height(600.dp)) { SafetyScreen() } }
            }
        }
        val chip = compose.onNode(hasText(context.getString(R.string.safety_other)) and hasClickAction())
        chip.performScrollTo().assertIsDisplayed()
        val child = chip.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Last filter overflows the screen", child.right <= root.right && child.left >= root.left)
        chip.performClick()
        compose.onNodeWithText(context.getString(R.string.safety_other_do)).performScrollTo().assertIsDisplayed()
    }
}
