package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import android.app.Activity
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
    primary = KcLime,
    onPrimary = KcLimeOn,
    primaryContainer = KcGreenPrimaryContainer,
    onPrimaryContainer = KcGreenOnPrimaryContainer,
    secondary = KcLightMuted,
    onSecondary = KcWhite,
    secondaryContainer = KcLightRaised,
    onSecondaryContainer = KcLightText,
    tertiary = KcCyan,
    onTertiary = KcLightText,
    background = KcLightBackground,
    onBackground = KcLightText,
    surface = KcLightSurface,
    onSurface = KcLightText,
    surfaceVariant = KcLightRaised,
    surfaceContainerLowest = KcLightSurface,
    surfaceContainerLow = KcLightRaised,
    surfaceContainer = KcSurfaceSunken,
    surfaceContainerHigh = KcLightRaised,
    onSurfaceVariant = KcLightMuted,
    outline = KcLightOutline,
    error = KcError,
    onError = KcOnError,
    errorContainer = KcErrorContainer,
    onErrorContainer = KcOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = KcLime,
    onPrimary = KcLimeOn,
    primaryContainer = Color(0xFF354A1A),
    onPrimaryContainer = Color(0xFFE7FFAA),
    secondary = KcMutedText,
    onSecondary = KcLimeOn,
    secondaryContainer = KcGraphiteRaised,
    onSecondaryContainer = KcText,
    tertiary = KcCyan,
    onTertiary = KcLimeOn,
    background = KcGraphiteBackground,
    onBackground = KcText,
    surface = KcGraphiteSurface,
    onSurface = KcText,
    surfaceVariant = KcGraphiteRaised,
    surfaceContainerLowest = KcGraphiteBackground,
    surfaceContainerLow = KcGraphiteSurface,
    surfaceContainer = KcGraphiteRaised,
    surfaceContainerHigh = KcSurfaceHigh,
    onSurfaceVariant = KcMuted,
    outline = KcGraphiteBorder,
    error = KcErrorRed,
    onError = KcLimeOn,
    errorContainer = Color(0xFF5B2424),
    onErrorContainer = Color(0xFFFFDADA)
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
        value = KcLime,
        success = KcMint,
        warning = KcAmber,
        logoPlate = KcSurfaceRaised,
        logoPlateBorder = KcLime.copy(alpha = .7f),
        isOperations = false
    )
}

object KcTheme {
    val extended: KcExtendedColors
        @Composable get() = LocalKcExtendedColors.current
}

private val KcShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Shared 4/8/12/16/20/24/32dp rhythm for all Compose surfaces. */
object KcSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Radius roles: controls stay crisp while hero surfaces get more air. */
object KcRadius {
    val control = 8.dp
    val surface = 12.dp
    val hero = 16.dp
    val sheet = 24.dp
    /** Fully rounded capsule shape for full-width actions and compact controls. */
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Kabadiwala Connect theme.
 *
 * Dynamic (wallpaper) colors are intentionally OFF so the high-contrast
 * brand palette stays consistent on every device, including entry-level
 * phones where dynamic theming can hurt readability.
 */
@Composable
fun KabadiwalaConnectTheme(
    darkTheme: Boolean = true,
    role: AccountRole = AccountRole.COLLECTOR,
    content: @Composable () -> Unit
) {
    // Role changes the operational accent, not the user's light/dark choice.
    // A forced dark palette made the Recycler screen inconsistent with the
    // accessibility setting and with the rest of the application.
    val effectiveDark = darkTheme
    val colorScheme = if (effectiveDark) DarkColorScheme else LightColorScheme
    val extended = KcExtendedColors(
        value = KcLime,
        success = if (effectiveDark) KcMint else KcSuccess,
        warning = if (effectiveDark) KcAmber else KcWarning,
        logoPlate = if (effectiveDark) KcLogoPlateDark else KcGreenPrimaryContainer,
        logoPlateBorder = if (effectiveDark) KcLogoPlateDarkBorder else KcLime.copy(alpha = .22f),
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
