package com.irinteractivestudios.kabadiwalaconnect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kabadiwala Connect field-operations palette.
 *
 * High-contrast light theme for outdoor readability on entry-level devices.
 * Dark text on light surfaces everywhere; color is never the only signal
 * (icons + text labels always accompany status colors).
 */

// Shared Kabadiwala Connect semantic palette. The Collector surface stays
// warm and light; the operations portals map these roles to their dark theme.
val KcGreenPrimary = Color(0xFF0E4B3A)
val KcGreenOnPrimary = Color(0xFFFFFFFF)
val KcGreenPrimaryContainer = Color(0xFFDDEFE2)
val KcGreenOnPrimaryContainer = Color(0xFF092E24)

// Amber is reserved for value, attention, and time-sensitive states.
val KcAmberSecondary = Color(0xFF9A5F16)
val KcAmberOnSecondary = Color(0xFFFFFFFF)
val KcAmberSecondaryContainer = Color(0xFFFFE1BD)
val KcAmberOnSecondaryContainer = Color(0xFF3F2504)
val KcLimeAccent = Color(0xFFB7E36D)
val KcTealSecondary = Color(0xFF1E8A77)

// Warm canvas and quiet surfaces keep the mobile experience approachable.
val KcBackground = Color(0xFFF7F6F0)
val KcOnBackground = Color(0xFF17231E)
val KcSurface = Color(0xFFFFFFFF)
val KcOnSurface = Color(0xFF17231E)
val KcSurfaceVariant = Color(0xFFEAF0EB)
val KcSurfaceSunken = Color(0xFFF0F1EB)
val KcSurfaceRaised = Color(0xFFFBFCF8)
val KcSurfaceHigh = Color(0xFFE5EBE5)
val KcOnSurfaceVariant = Color(0xFF43534A)
val KcOutline = Color(0xFF718078)

// Status colors: large, high-contrast, always paired with icon + label.
val KcSuccess = Color(0xFF237A56)
val KcOnSuccess = Color(0xFFFFFFFF)
val KcSuccessContainer = Color(0xFFC9EAD3)
val KcOnSuccessContainer = Color(0xFF063A1F)

val KcWarning = Color(0xFF9A5F16)
val KcOnWarning = Color(0xFFFFFFFF)
val KcWarningContainer = Color(0xFFFFE3A3)
val KcOnWarningContainer = Color(0xFF3A2B00)

val KcError = Color(0xFFC94C4C)
val KcOnError = Color(0xFFFFFFFF)
val KcErrorContainer = Color(0xFFF9DAD6)
val KcOnErrorContainer = Color(0xFF410002)

val KcInfo = Color(0xFF3E73B8)
val KcOnInfo = Color(0xFFFFFFFF)
val KcInfoContainer = Color(0xFFD6E4FF)
val KcOnInfoContainer = Color(0xFF001D35)

// Dark-scheme counterparts (same hues, lifted for dark backgrounds).
val KcDarkBackground = Color(0xFF091612)
val KcDarkOnBackground = Color(0xFFF2F5F0)
val KcDarkSurface = Color(0xFF10251E)
val KcDarkOnSurface = Color(0xFFF2F5F0)
val KcDarkSurfaceRaised = Color(0xFF163127)
val KcDarkSurfaceHigh = Color(0xFF1C3B2F)
val KcDarkOutline = Color(0xFF52665C)
val KcDarkMuted = Color(0xFFB6C5BB)

// The transparent logo asset contains a deep-green recycling loop. A warm,
// high-contrast plate keeps that mark readable on dark teal surfaces.
val KcLogoPlateDark = Color(0xFFF4F1D8)
val KcLogoPlateDarkBorder = Color(0xFFD5E59A)
