package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Graphite Neon Room palette.
 *
 * Keep the action colour scarce: it marks the primary action, selected
 * navigation, progress, and important feedback. Everything else stays in the
 * graphite surface family so hierarchy remains legible in the field.
 */
val KcLime = Color(0xFFC9FF3A)
val KcLimeOn = Color(0xFF11150E)
val KcGraphiteBackground = Color(0xFF0B0E0C)
val KcGraphiteSurface = Color(0xFF141915)
val KcGraphiteRaised = Color(0xFF1D251F)
val KcGraphiteBorder = Color(0xFF2A352D)
val KcWhite = Color(0xFFFFFFFF)
val KcMutedText = Color(0xFFAEB9AF)
val KcErrorRed = Color(0xFFFF6B6B)

// Compatibility names used by older screens. They now resolve to the same
// product tokens instead of carrying a second, visually different palette.
val KcViolet = KcLime
val KcVioletStrong = KcLime
val KcVioletSoft = Color(0xFFE7FFAA)
val KcMagenta = KcMutedText
val KcCyan = Color(0xFF9BC7A7)
val KcMint = KcLime
val KcAmber = Color(0xFFFFD166)
val KcCoral = KcErrorRed

val KcNight = KcGraphiteBackground
val KcNightSecondary = Color(0xFF101410)
val KcSurface = KcGraphiteSurface
val KcSurfaceRaised = KcGraphiteRaised
val KcSurfaceHigh = Color(0xFF263229)
val KcText = KcWhite
val KcMuted = KcMutedText
val KcOutline = KcGraphiteBorder

// Light mode keeps the brand identity but swaps the canvas and text roles for
// comfortable contrast. The same lime remains reserved for primary actions.
val KcLightBackground = Color(0xFFF5F8F3)
val KcLightSurface = Color(0xFFFFFFFF)
val KcLightRaised = Color(0xFFE9F0E7)
val KcLightText = Color(0xFF11150E)
val KcLightMuted = Color(0xFF4F5D52)
val KcLightOutline = Color(0xFF748276)
// Lime is the brand action fill, but it is too bright to use as text on a
// light canvas. This readable green keeps the same hue family at 7:1+ against
// white while leaving KcLime available for high-emphasis action surfaces.
val KcLightPrimary = Color(0xFF3A6200)

// Semantic aliases retained for existing screens and tests.
val KcGreenPrimary = KcLime
val KcGreenOnPrimary = KcLimeOn
val KcGreenPrimaryContainer = Color(0xFFE7FFAA)
val KcGreenOnPrimaryContainer = KcLimeOn
val KcAmberSecondary = Color(0xFFB87500)
val KcAmberOnSecondary = Color(0xFF241400)
val KcAmberSecondaryContainer = Color(0xFFFFE3A8)
val KcAmberOnSecondaryContainer = Color(0xFF382100)
val KcLimeAccent = KcLime
val KcTealSecondary = KcCyan
val KcBackground = KcLightBackground
val KcOnBackground = KcLightText
val KcOnSurface = KcLightText
val KcSurfaceVariant = KcLightRaised
val KcSurfaceSunken = Color(0xFFEDF3EF)
val KcOnSurfaceVariant = KcLightMuted
val KcSuccess = Color(0xFF078A59)
val KcOnSuccess = Color.White
val KcSuccessContainer = Color(0xFFB9F8D7)
val KcOnSuccessContainer = Color(0xFF003824)
val KcWarning = Color(0xFF9A6200)
val KcOnWarning = Color.White
val KcWarningContainer = Color(0xFFFFE2A8)
val KcOnWarningContainer = Color(0xFF392100)
val KcError = KcErrorRed
val KcOnError = Color.White
val KcErrorContainer = Color(0xFFFFD9DF)
val KcOnErrorContainer = Color(0xFF410014)
val KcInfo = Color(0xFF006C86)
val KcOnInfo = Color.White
val KcInfoContainer = Color(0xFFB9EEFF)
val KcOnInfoContainer = Color(0xFF003642)

val KcDarkBackground = KcGraphiteBackground
val KcDarkOnBackground = KcText
val KcDarkSurface = KcSurface
val KcDarkOnSurface = KcText
val KcDarkSurfaceRaised = KcSurfaceRaised
val KcDarkSurfaceHigh = KcSurfaceHigh
val KcDarkOutline = KcOutline
val KcDarkMuted = KcMuted
val KcLogoPlateDark = KcSurfaceRaised
val KcLogoPlateDarkBorder = KcLime.copy(alpha = .7f)
