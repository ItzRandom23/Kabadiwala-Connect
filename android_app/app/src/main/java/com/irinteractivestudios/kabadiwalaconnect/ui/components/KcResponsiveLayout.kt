package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Density and gutter choices for constrained viewports; type and touch targets stay intact. */
@Immutable
data class KcResponsiveLayout(
    val isCompact: Boolean,
    val isNarrow: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp
)

@Composable
fun rememberKcResponsiveLayout(): KcResponsiveLayout {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val fontScale = configuration.fontScale
    return remember(width, height, fontScale) {
        val narrow = width < 360
        val compact = narrow || height < 700 || fontScale >= 1.2f
        KcResponsiveLayout(
            isCompact = compact,
            isNarrow = narrow,
            horizontalPadding = when {
                width >= 600 -> 24.dp
                narrow -> 14.dp
                else -> 20.dp
            },
            verticalPadding = if (compact) 12.dp else 18.dp,
            sectionSpacing = if (compact) 12.dp else 16.dp,
            cardPadding = if (compact) 14.dp else 18.dp
        )
    }
}
