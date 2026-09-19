package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.DemoModePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo journey must never be available to a non-debug build. */
class DemoModeBoundaryTest {

    @Test
    fun demoRequest_isRejectedForReleaseBuilds() {
        assertFalse(DemoModePolicy.enabled(isDebugBuild = false, requested = true))
    }

    @Test
    fun demoRequest_isAllowedOnlyWhenExplicitlyRequestedInDebug() {
        assertTrue(DemoModePolicy.enabled(isDebugBuild = true, requested = true))
        assertFalse(DemoModePolicy.enabled(isDebugBuild = true, requested = false))
    }
}
