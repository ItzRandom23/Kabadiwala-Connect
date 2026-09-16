package com.irinteractivestudios.kabadiwalaconnect.util

/**
 * Network status exposed to the UI (offline banner, queued-action labels).
 */
enum class ConnectionState {
    /** Network connected; API assumed reachable unless proven otherwise. */
    ONLINE,

    /** No usable network (covers intermittent 2G/3G dropouts). */
    OFFLINE,

    /** Network is up but the API cannot be reached. */
    LIMITED
}

/**
 * Pure mapping from low-level signals to [ConnectionState].
 * Kept free of Android APIs so it is unit-testable on the JVM.
 *
 * @param hasNetwork true when the device has a validated/usable network.
 * @param apiReachable null = unknown (assume reachable), false = a recent
 * API call failed despite having network.
 */
fun resolveConnectionState(hasNetwork: Boolean, apiReachable: Boolean?): ConnectionState =
    when {
        !hasNetwork -> ConnectionState.OFFLINE
        apiReachable == false -> ConnectionState.LIMITED
        else -> ConnectionState.ONLINE
    }
