package com.irinteractivestudios.kabadiwalaconnect.util

/** Central release boundary for the local presentation-only demo journey. */
object DemoModePolicy {
    fun enabled(isDebugBuild: Boolean, requested: Boolean): Boolean =
        isDebugBuild && requested
}
