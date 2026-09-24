package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kabadiwala Connect's forest and sage palette. Shared names are retained for
 * existing screens; the values now stay within one calm green, warm neutral,
 * and amber family in both system themes.
 */
val KcLime = Color(0xFF879C77) // Muted sage action in dark mode (legacy name).
val KcLimeOn = Color(0xFF17251B)
val KcGraphiteBackground = Color(0xFF101610)
val KcGraphiteSurface = Color(0xFF171F19)
val KcGraphiteRaised = Color(0xFF222E25)
val KcGraphiteBorder = Color(0xFF39483D)
val KcWhite = Color(0xFFF7F8F4)
val KcMutedText = Color(0xFFB2BDB3)
val KcErrorRed = Color(0xFFE9947A)

// Compatibility names used by older screens, mapped to the current palette.
val KcViolet = KcLime
val KcVioletStrong = Color(0xFF80A86F)
val KcVioletSoft = Color(0xFFDCE9D6)
val KcMagenta = KcMutedText
val KcCyan = Color(0xFFA5C7B2)
val KcMint = Color(0xFF8BAF91)
val KcAmber = Color(0xFFE5BC72)
val KcCoral = KcErrorRed

val KcNight = KcGraphiteBackground
val KcNightSecondary = Color(0xFF131B15)
val KcSurface = KcGraphiteSurface
val KcSurfaceRaised = KcGraphiteRaised
val KcSurfaceHigh = Color(0xFF2B3A2F)
val KcText = KcWhite
val KcMuted = KcMutedText
val KcOutline = KcGraphiteBorder

// Light mode uses a soft warm canvas with forest green actions and dark text.
val KcLightBackground = Color(0xFFF5F6F0)
val KcLightSurface = Color(0xFFFFFEFA)
val KcLightRaised = Color(0xFFE9EFE7)
val KcLightContainerHigh = Color(0xFFDCE6DC)
val KcLightText = Color(0xFF1B2920)
val KcLightMuted = Color(0xFF526257)
val KcLightOutline = Color(0xFF748276)
val KcLightOutlineVariant = Color(0xFFC7D1C7)
val KcLightPrimary = Color(0xFF2E6645)
val KcLightSecondary = Color(0xFF8A5B1D)
val KcLightSecondaryContainer = Color(0xFFF2E7D2)
val KcLightOnSecondaryContainer = Color(0xFF493419)
val KcLightTertiary = Color(0xFF496F52)
val KcLightTertiaryContainer = Color(0xFFE1EAE0)
val KcLightOnTertiaryContainer = Color(0xFF203427)
val KcLightWarning = Color(0xFF805514)
val KcLightSuccess = Color(0xFF356B4A)
val KcLightError = Color(0xFFA8422C)

// Semantic aliases retained for existing screens.
val KcGreenPrimary = KcLime
val KcGreenOnPrimary = KcLimeOn
val KcGreenPrimaryContainer = Color(0xFFDCE9D6)
val KcGreenOnPrimaryContainer = Color(0xFF1A3926)
val KcAmberSecondary = KcLightSecondary
val KcAmberOnSecondary = Color(0xFFFFFFFF)
val KcAmberSecondaryContainer = KcLightSecondaryContainer
val KcAmberOnSecondaryContainer = KcLightOnSecondaryContainer
val KcLimeAccent = KcLime
val KcTealSecondary = KcCyan
val KcBackground = KcLightBackground
val KcOnBackground = KcLightText
val KcOnSurface = KcLightText
val KcSurfaceVariant = KcLightRaised
val KcSurfaceSunken = Color(0xFFF0F2EC)
val KcOnSurfaceVariant = KcLightMuted

// Semantic state colors use forest/sage, amber, and warm terracotta rather
// than the default Material purple and pink containers.
val KcSuccess = Color(0xFF3D7952)
val KcOnSuccess = Color(0xFFFFFFFF)
val KcSuccessContainer = Color(0xFFD9EBDD)
val KcOnSuccessContainer = Color(0xFF193A26)
val KcWarning = Color(0xFF94651D)
val KcOnWarning = Color(0xFFFFFFFF)
val KcWarningContainer = Color(0xFFF1E2C4)
val KcOnWarningContainer = Color(0xFF483516)
val KcError = KcLightError
val KcOnError = Color(0xFFFFFFFF)
val KcErrorContainer = Color(0xFFF5DED6)
val KcOnErrorContainer = Color(0xFF4A241A)
val KcInfo = Color(0xFF3B7165)
val KcOnInfo = Color(0xFFFFFFFF)
val KcInfoContainer = Color(0xFFD9EAE1)
val KcOnInfoContainer = Color(0xFF18372E)

val KcDarkBackground = KcGraphiteBackground
val KcDarkOnBackground = KcText
val KcDarkSurface = KcSurface
val KcDarkOnSurface = KcText
val KcDarkSurfaceRaised = KcSurfaceRaised
val KcDarkSurfaceHigh = KcSurfaceHigh
val KcDarkOutline = KcOutline
val KcDarkMuted = KcMuted
val KcLogoPlateDark = KcSurfaceRaised
val KcLogoPlateDarkBorder = Color(0xFF667D5E)
