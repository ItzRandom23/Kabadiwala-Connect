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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

private val LightColorScheme = lightColorScheme(
    primary = KcVioletStrong,
    onPrimary = Color.White,
    primaryContainer = KcGreenPrimaryContainer,
    onPrimaryContainer = KcGreenOnPrimaryContainer,
    secondary = KcMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3D7FF),
    onSecondaryContainer = Color(0xFF3C064B),
    tertiary = KcCyan,
    onTertiary = Color(0xFF002F3A),
    background = KcLightBackground,
    onBackground = KcLightText,
    surface = KcLightSurface,
    onSurface = KcLightText,
    surfaceVariant = KcLightRaised,
    surfaceContainerLowest = KcLightSurface,
    surfaceContainerLow = KcLightRaised,
    surfaceContainer = KcSurfaceSunken,
    surfaceContainerHigh = KcSurfaceHigh.copy(alpha = .18f),
    onSurfaceVariant = KcLightMuted,
    outline = KcLightOutline,
    error = KcError,
    onError = KcOnError,
    errorContainer = KcErrorContainer,
    onErrorContainer = KcOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = KcViolet,
    onPrimary = Color(0xFF1E093E),
    primaryContainer = KcVioletStrong,
    onPrimaryContainer = Color(0xFFF3E9FF),
    secondary = KcMagenta,
    onSecondary = Color(0xFF30003D),
    secondaryContainer = Color(0xFF4B165D),
    onSecondaryContainer = Color(0xFFFFD7FF),
    tertiary = KcCyan,
    onTertiary = Color(0xFF00333D),
    background = KcNight,
    onBackground = KcText,
    surface = KcSurface,
    onSurface = KcText,
    surfaceVariant = KcSurfaceRaised,
    surfaceContainerLowest = KcNight,
    surfaceContainerLow = KcSurface,
    surfaceContainer = KcSurfaceRaised,
    surfaceContainerHigh = KcSurfaceHigh,
    onSurfaceVariant = KcMuted,
    outline = KcOutline,
    error = KcCoral,
    onError = Color(0xFF3C0010),
    errorContainer = Color(0xFF5F1730),
    onErrorContainer = Color(0xFFFFD9E1)
)

@Immutable
data class KcExtendedColors(
    val value: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val logoPlate: androidx.compose.ui.graphics.Color,
    val logoPlateBorder: androidx.compose.ui.graphics.Color,
    val isOperations: Boolean
)

private val LocalKcExtendedColors = staticCompositionLocalOf {
    KcExtendedColors(
        value = KcVioletStrong,
        success = KcMint,
        warning = KcAmber,
        logoPlate = KcSurfaceRaised,
        logoPlateBorder = KcViolet.copy(alpha = .7f),
        isOperations = false
    )
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
    // Role changes the operational accent, not the user's light/dark choice.
    // A forced dark palette made the Recycler screen inconsistent with the
    // accessibility setting and with the rest of the application.
    val effectiveDark = darkTheme
    val colorScheme = if (effectiveDark) DarkColorScheme else LightColorScheme
    val extended = KcExtendedColors(
        value = if (effectiveDark) KcViolet else KcVioletStrong,
        success = if (effectiveDark) KcMint else KcSuccess,
        warning = if (effectiveDark) KcAmber else KcWarning,
        logoPlate = if (effectiveDark) KcLogoPlateDark else KcGreenPrimaryContainer,
        logoPlateBorder = if (effectiveDark) KcLogoPlateDarkBorder else KcVioletStrong.copy(alpha = .22f),
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
