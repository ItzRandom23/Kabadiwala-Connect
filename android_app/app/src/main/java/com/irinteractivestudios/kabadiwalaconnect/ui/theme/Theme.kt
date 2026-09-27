package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import android.app.Activity
import android.os.Build
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
    primary = KcLightPrimary,
    onPrimary = KcLightOnPrimary,
    primaryContainer = KcGreenPrimaryContainer,
    onPrimaryContainer = KcGreenOnPrimaryContainer,
    secondary = KcLightSecondary,
    onSecondary = KcLightOnSecondary,
    secondaryContainer = KcLightSecondaryContainer,
    onSecondaryContainer = KcLightOnSecondaryContainer,
    tertiary = KcLightTertiary,
    onTertiary = KcLightOnTertiary,
    tertiaryContainer = KcLightTertiaryContainer,
    onTertiaryContainer = KcLightOnTertiaryContainer,
    background = KcLightBackground,
    onBackground = KcLightText,
    surface = KcLightSurface,
    onSurface = KcLightText,
    surfaceVariant = KcLightRaised,
    surfaceContainerLowest = KcLightSurface,
    surfaceContainerLow = KcLightContainerLow,
    surfaceContainer = KcLightContainer,
    surfaceContainerHigh = KcLightContainerHigh,
    onSurfaceVariant = KcLightMuted,
    outline = KcLightOutline,
    outlineVariant = KcLightOutlineVariant,
    error = KcLightError,
    onError = KcOnError,
    errorContainer = KcLightErrorContainer,
    onErrorContainer = KcLightOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = KcDarkPrimary,
    onPrimary = KcDarkOnPrimary,
    primaryContainer = KcDarkPrimaryContainer,
    onPrimaryContainer = KcDarkOnPrimaryContainer,
    secondary = KcDarkSecondary,
    onSecondary = KcDarkOnSecondary,
    secondaryContainer = KcDarkSecondaryContainer,
    onSecondaryContainer = KcDarkOnSecondaryContainer,
    tertiary = KcDarkTertiary,
    onTertiary = KcDarkOnTertiary,
    tertiaryContainer = KcDarkTertiaryContainer,
    onTertiaryContainer = KcDarkOnTertiaryContainer,
    background = KcDarkBackground,
    onBackground = KcDarkText,
    surface = KcDarkSurface,
    onSurface = KcDarkText,
    surfaceVariant = KcDarkSurfaceVariant,
    surfaceContainerLowest = KcDarkBackground,
    surfaceContainerLow = KcDarkSurface,
    surfaceContainer = KcDarkSurfaceContainer,
    surfaceContainerHigh = KcDarkElevatedSurface,
    surfaceContainerHighest = KcDarkSurfaceHigh,
    onSurfaceVariant = KcDarkMuted,
    outline = KcDarkOutline,
    outlineVariant = KcDarkOutlineVariant,
    error = KcDarkError,
    onError = KcDarkOnError,
    errorContainer = KcDarkErrorContainer,
    onErrorContainer = KcDarkOnErrorContainer
)

@Immutable
data class KcExtendedColors(
    val value: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val onSuccessContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val warningContainer: androidx.compose.ui.graphics.Color,
    val onWarningContainer: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val infoContainer: androidx.compose.ui.graphics.Color,
    val onInfoContainer: androidx.compose.ui.graphics.Color,
    val logoPlate: androidx.compose.ui.graphics.Color,
    val logoPlateBorder: androidx.compose.ui.graphics.Color,
    val isOperations: Boolean
)

private val LocalKcExtendedColors = staticCompositionLocalOf {
    KcExtendedColors(
        value = KcDarkPrimary,
        success = KcDarkSuccess,
        successContainer = KcDarkSuccessContainer,
        onSuccessContainer = KcDarkOnSuccessContainer,
        warning = KcDarkWarning,
        warningContainer = KcDarkWarningContainer,
        onWarningContainer = KcDarkOnWarningContainer,
        info = KcDarkInfo,
        infoContainer = KcDarkInfoContainer,
        onInfoContainer = KcDarkOnInfoContainer,
        logoPlate = KcDarkElevatedSurface,
        logoPlateBorder = KcLogoPlateDarkBorder,
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
        value = if (effectiveDark) KcDarkPrimary else KcLightPrimary,
        success = if (effectiveDark) KcDarkSuccess else KcLightSuccess,
        successContainer = if (effectiveDark) KcDarkSuccessContainer else KcLightSuccessContainer,
        onSuccessContainer = if (effectiveDark) KcDarkOnSuccessContainer else KcLightOnSuccessContainer,
        warning = if (effectiveDark) KcDarkWarning else KcLightWarning,
        warningContainer = if (effectiveDark) KcDarkWarningContainer else KcLightWarningContainer,
        onWarningContainer = if (effectiveDark) KcDarkOnWarningContainer else KcLightOnWarningContainer,
        info = if (effectiveDark) KcDarkInfo else KcLightInfo,
        infoContainer = if (effectiveDark) KcDarkInfoContainer else KcLightInfoContainer,
        onInfoContainer = if (effectiveDark) KcDarkOnInfoContainer else KcLightOnInfoContainer,
        logoPlate = if (effectiveDark) KcDarkElevatedSurface else KcGreenPrimaryContainer,
        logoPlateBorder = if (effectiveDark) KcLogoPlateDarkBorder else KcLightPrimary.copy(alpha = .55f),
        isOperations = role == AccountRole.RECYCLER
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            val supportsLightNavigationIcons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            window.navigationBarColor = if (!effectiveDark && !supportsLightNavigationIcons) {
                KcDarkBackground.toArgb()
            } else {
                colorScheme.background.toArgb()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !effectiveDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !effectiveDark && supportsLightNavigationIcons
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
