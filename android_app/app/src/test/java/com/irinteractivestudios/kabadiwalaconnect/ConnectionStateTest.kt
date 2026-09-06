package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.resolveConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the connectivity mapping shown in the offline banner. */
class ConnectionStateTest {

    @Test
    fun noNetwork_isOffline() {
        assertEquals(
            ConnectionState.OFFLINE,
            resolveConnectionState(hasNetwork = false, apiReachable = null)
        )
    }

    @Test
    fun noNetwork_overridesApiFlag() {
        assertEquals(
            ConnectionState.OFFLINE,
            resolveConnectionState(hasNetwork = false, apiReachable = true)
        )
    }

    @Test
    fun networkWithoutApiContact_isLimited() {
        assertEquals(
            ConnectionState.LIMITED,
            resolveConnectionState(hasNetwork = true, apiReachable = false)
        )
    }

    @Test
    fun networkWithUnknownApi_isOnline() {
        assertEquals(
            ConnectionState.ONLINE,
            resolveConnectionState(hasNetwork = true, apiReachable = null)
        )
    }

    @Test
    fun networkWithReachableApi_isOnline() {
        assertEquals(
            ConnectionState.ONLINE,
            resolveConnectionState(hasNetwork = true, apiReachable = true)
        )
    }
}
