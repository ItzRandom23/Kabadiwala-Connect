package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.SingleFlightGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightGateTest {
    @Test fun acceptsOneActionUntilItExits() {
        val gate = SingleFlightGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        gate.exit()
        assertTrue(gate.tryEnter())
    }
}
