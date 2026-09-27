package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.graphics.Color
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightPrimary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightSurface
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightError
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightBackground
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightContainerHigh
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightRaised
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightSuccess
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightTertiary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightWarning
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLime
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLimeOn
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcSurfaceSunken
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightOnPrimary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcGreenPrimaryContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcGreenOnPrimaryContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightText
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightMuted
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightWarningContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightOnWarningContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightSuccessContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightOnSuccessContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightErrorContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightOnErrorContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkPrimary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnPrimary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkPrimaryContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnPrimaryContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkBackground
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkSurface
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkText
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkMuted
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkWarning
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkWarningContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnWarningContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkSuccess
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkSuccessContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnSuccessContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkError
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnError
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkErrorContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcDarkOnErrorContainer
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Protects the light-theme accent from becoming invisible on light surfaces. */
class ThemeContrastTest {

    @Test
    fun lightPrimaryIsReadableOnLightSurface() {
        assertTrue(
            "Light primary must meet WCAG AA for normal text",
            contrastRatio(KcLightPrimary, KcLightSurface) >= 4.5
        )
    }

    @Test
    fun limeActionTextIsReadableOnLimeActionFill() {
        assertTrue(
            "Lime action text must remain readable on lime buttons",
            contrastRatio(KcLimeOn, KcLime) >= 4.5
        )
    }

    @Test
    fun lightSemanticTextIsReadableOnLightSurface() {
        assertTrue(
            "Light tertiary text/icon color must remain readable on light surfaces",
            contrastRatio(KcLightTertiary, KcLightSurface) >= 4.5
        )
        assertTrue(
            "Light warning text must remain readable on light surfaces",
            contrastRatio(KcLightWarning, KcLightSurface) >= 4.5
        )
        assertTrue(
            "Light success text must remain readable on light surfaces",
            contrastRatio(KcLightSuccess, KcLightSurface) >= 4.5
        )
        assertTrue(
            "Light error text must remain readable on light surfaces",
            contrastRatio(KcLightError, KcLightSurface) >= 4.5
        )
    }

    @Test
    fun lightSurfaceRolesHaveAnOrderedTonalLadder() {
        val surface = relativeLuminance(KcLightSurface)
        val background = relativeLuminance(KcLightBackground)
        val low = relativeLuminance(KcSurfaceSunken)
        val container = relativeLuminance(KcLightContainer)
        val high = relativeLuminance(KcLightContainerHigh)

        assertTrue("Light surface should be brighter than the canvas", surface > background)
        assertTrue("The light surface ladder should step down at low", background > low)
        assertTrue("The light surface ladder should step down at container", low > container)
        assertTrue("The light surface ladder should step down at high", container > high)
        assertEquals(Color(0xFFE2E8DC), KcLightRaised)
    }

    @Test
    fun brandTokensMatchTheRequestedLightPaletteAndKeepReadablePairs() {
        assertEquals(Color(0xFF386A20), KcLightPrimary)
        assertEquals(Color(0xFFB8F397), KcGreenPrimaryContainer)
        assertEquals(Color(0xFFF8FAF5), KcLightBackground)
        assertEquals(Color(0xFFFFFFFF), KcLightSurface)
        assertEquals(Color(0xFF191D17), KcLightText)
        assertEquals(Color(0xFF5F665B), KcLightMuted)
        assertTrue(contrastRatio(KcLightPrimary, KcLightOnPrimary) >= 4.5)
        assertTrue(contrastRatio(KcGreenPrimaryContainer, KcGreenOnPrimaryContainer) >= 4.5)
        assertTrue(contrastRatio(KcLightText, KcLightBackground) >= 4.5)
        assertTrue(contrastRatio(KcLightMuted, KcLightBackground) >= 4.5)
        assertTrue(contrastRatio(KcLightWarningContainer, KcLightOnWarningContainer) >= 4.5)
        assertTrue(contrastRatio(KcLightSuccessContainer, KcLightOnSuccessContainer) >= 4.5)
        assertTrue(contrastRatio(KcLightErrorContainer, KcLightOnErrorContainer) >= 4.5)
    }

    @Test
    fun darkBrandAndStatusPairsMeetNormalTextContrast() {
        assertEquals(Color(0xFF9DD67D), KcDarkPrimary)
        assertEquals(Color(0xFF24510E), KcDarkPrimaryContainer)
        assertEquals(Color(0xFF10140F), KcDarkBackground)
        assertEquals(Color(0xFF171C16), KcDarkSurface)
        assertEquals(Color(0xFFE1E4DC), KcDarkText)
        assertEquals(Color(0xFFC0C9B9), KcDarkMuted)
        assertTrue(contrastRatio(KcDarkPrimary, KcDarkOnPrimary) >= 4.5)
        assertTrue(contrastRatio(KcDarkPrimaryContainer, KcDarkOnPrimaryContainer) >= 4.5)
        assertTrue(contrastRatio(KcDarkText, KcDarkBackground) >= 4.5)
        assertTrue(contrastRatio(KcDarkMuted, KcDarkBackground) >= 4.5)
        assertTrue(contrastRatio(KcDarkWarning, KcDarkBackground) >= 4.5)
        assertTrue(contrastRatio(KcDarkSuccess, KcDarkSurface) >= 4.5)
        assertTrue(contrastRatio(KcDarkWarningContainer, KcDarkOnWarningContainer) >= 4.5)
        assertTrue(contrastRatio(KcDarkSuccessContainer, KcDarkOnSuccessContainer) >= 4.5)
        assertTrue(contrastRatio(KcDarkError, KcDarkOnError) >= 4.5)
        assertTrue(contrastRatio(KcDarkErrorContainer, KcDarkOnErrorContainer) >= 4.5)
    }

    private fun assertEquals(expected: Color, actual: Color) {
        assertTrue("Expected $expected but got $actual", expected == actual)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

}
