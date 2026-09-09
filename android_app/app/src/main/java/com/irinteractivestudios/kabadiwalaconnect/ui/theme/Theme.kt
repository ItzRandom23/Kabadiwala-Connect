package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

private val LightColorScheme = lightColorScheme(
    primary = KcGreenPrimary,
    onPrimary = KcGreenOnPrimary,
    primaryContainer = KcGreenPrimaryContainer,
    onPrimaryContainer = KcGreenOnPrimaryContainer,
    secondary = KcAmberSecondary,
    onSecondary = KcAmberOnSecondary,
    secondaryContainer = KcAmberSecondaryContainer,
    onSecondaryContainer = KcAmberOnSecondaryContainer,
    tertiary = KcTealSecondary,
    onTertiary = KcGreenOnPrimary,
    background = KcBackground,
    onBackground = KcOnBackground,
    surface = KcSurface,
    onSurface = KcOnSurface,
    surfaceVariant = KcSurfaceVariant,
    surfaceContainerLowest = KcSurface,
    surfaceContainerLow = KcSurfaceRaised,
    surfaceContainer = KcSurfaceSunken,
    surfaceContainerHigh = KcSurfaceHigh,
    onSurfaceVariant = KcOnSurfaceVariant,
    outline = KcOutline,
    error = KcError,
    onError = KcOnError,
    errorContainer = KcErrorContainer,
    onErrorContainer = KcOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = KcGreenPrimaryContainer,
    onPrimary = KcGreenOnPrimaryContainer,
    primaryContainer = KcGreenPrimary,
    onPrimaryContainer = KcGreenPrimaryContainer,
    secondary = KcAmberSecondaryContainer,
    onSecondary = KcAmberOnSecondaryContainer,
    tertiary = KcTealSecondary,
    onTertiary = KcGreenOnPrimary,
    background = KcDarkBackground,
    onBackground = KcDarkOnBackground,
    surface = KcDarkSurface,
    onSurface = KcDarkOnSurface,
    surfaceVariant = KcDarkSurfaceRaised,
    surfaceContainerLowest = KcDarkBackground,
    surfaceContainerLow = KcDarkSurface,
    surfaceContainer = KcDarkSurfaceRaised,
    surfaceContainerHigh = KcDarkSurfaceHigh,
    onSurfaceVariant = KcDarkMuted,
    outline = KcDarkOutline,
    error = KcErrorContainer,
    onError = KcOnErrorContainer
)

@Immutable
data class KcExtendedColors(
    val value: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val isOperations: Boolean
)

private val LocalKcExtendedColors = staticCompositionLocalOf {
    KcExtendedColors(KcAmberSecondary, KcSuccess, KcWarning, false)
}

object KcTheme {
    val extended: KcExtendedColors
        @Composable get() = LocalKcExtendedColors.current
}

private val KcShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Kabadiwala Connect theme.
 *
 * Dynamic (wallpaper) colors are intentionally OFF so the high-contrast
 * brand palette stays consistent on every device, including entry-level
 * phones where dynamic theming can hurt readability.
 */
@Composable
fun KabadiwalaConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    role: AccountRole = AccountRole.COLLECTOR,
    content: @Composable () -> Unit
) {
    // Recycler work is intentionally rendered as a darker operations surface.
    // Collector light/dark remains user controlled for outdoor readability.
    val effectiveDark = darkTheme || role == AccountRole.RECYCLER
    val colorScheme = if (effectiveDark) DarkColorScheme else LightColorScheme
    val extended = KcExtendedColors(
        value = if (effectiveDark) KcAmberSecondaryContainer else KcAmberSecondary,
        success = if (effectiveDark) KcSuccessContainer else KcSuccess,
        warning = if (effectiveDark) KcWarningContainer else KcWarning,
        isOperations = role == AccountRole.RECYCLER
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !effectiveDark
        }
    }

    CompositionLocalProvider(LocalKcExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = KcShapes,
            content = content
        )
    }
}
