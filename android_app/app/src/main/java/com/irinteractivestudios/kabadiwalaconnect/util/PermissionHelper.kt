package com.irinteractivestudios.kabadiwalaconnect.util

import android.Manifest
import androidx.annotation.StringRes
import com.irinteractivestudios.kabadiwalaconnect.R

/**
 * Permission foundation (Phase 1).
 *
 * Rule: permissions are declared in the manifest but NEVER requested on
 * app launch. Each permission is requested only when its feature is used:
 * - [CAMERA] -> lot photos (Phase 2+)
 * - [LOCATION] -> collection location (Phase 2+)
 *
 * [PermissionDecisions] keeps the rationale flow unit-testable without
 * Android dependencies.
 */
enum class FeaturePermission(
    val manifestValue: String,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int
) {
    CAMERA(
        Manifest.permission.CAMERA,
        R.string.perm_camera_title,
        R.string.perm_camera_text
    ),
    LOCATION(
        Manifest.permission.ACCESS_FINE_LOCATION,
        R.string.perm_location_title,
        R.string.perm_location_text
    )
}

object PermissionDecisions {

    /** Next step for a feature-gated permission request. */
    enum class NextStep {
        /** Already granted — proceed with the feature. */
        PROCEED,

        /** First request — ask the system directly. */
        REQUEST,

        /** Denied once — explain why, then ask again. */
        SHOW_RATIONALE,

        /** Denied twice / "don't ask again" — guide to system settings. */
        OPEN_SETTINGS_HINT
    }

    /**
     * Pure decision function.
     *
     * @param hasPermission permission already granted.
     * @param shouldShowRationale from ActivityResult / Activity APIs.
     * @param deniedBefore true if the user denied at least once before.
     */
    fun nextStep(
        hasPermission: Boolean,
        shouldShowRationale: Boolean,
        deniedBefore: Boolean
    ): NextStep = when {
        hasPermission -> NextStep.PROCEED
        shouldShowRationale -> NextStep.SHOW_RATIONALE
        deniedBefore -> NextStep.OPEN_SETTINGS_HINT
        else -> NextStep.REQUEST
    }
}
