package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.PermissionDecisions
import com.irinteractivestudios.kabadiwalaconnect.util.PermissionDecisions.NextStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies permissions are never forced: granted -> proceed,
 * first use -> direct request, repeat denial -> settings hint.
 */
class PermissionDecisionsTest {

    @Test
    fun granted_proceeds() {
        assertEquals(
            NextStep.PROCEED,
            PermissionDecisions.nextStep(
                hasPermission = true,
                shouldShowRationale = false,
                deniedBefore = true
            )
        )
    }

    @Test
    fun firstUse_requestsDirectly() {
        assertEquals(
            NextStep.REQUEST,
            PermissionDecisions.nextStep(
                hasPermission = false,
                shouldShowRationale = false,
                deniedBefore = false
            )
        )
    }

    @Test
    fun deniedOnce_showsRationale() {
        assertEquals(
            NextStep.SHOW_RATIONALE,
            PermissionDecisions.nextStep(
                hasPermission = false,
                shouldShowRationale = true,
                deniedBefore = true
            )
        )
    }

    @Test
    fun deniedPermanently_hintsAtSettings() {
        assertEquals(
            NextStep.OPEN_SETTINGS_HINT,
            PermissionDecisions.nextStep(
                hasPermission = false,
                shouldShowRationale = false,
                deniedBefore = true
            )
        )
    }
}
