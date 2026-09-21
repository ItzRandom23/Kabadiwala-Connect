package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.graphics.Color
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightPrimary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLightSurface
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLime
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcLimeOn
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
