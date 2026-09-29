package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.notifications.mayDisplayPush
import com.irinteractivestudios.kabadiwalaconnect.notifications.pickupPushCopy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPolicyTest {
    @Test fun `push requires the active intended account`() {
        assertTrue(mayDisplayPush("collector-1", "collector-1", true))
        assertFalse(mayDisplayPush("collector-1", "household-1", true))
        assertFalse(mayDisplayPush("collector-1", "collector-1", false))
        assertFalse(mayDisplayPush(null, "collector-1", true))
    }

    @Test fun `household request copy differs from collector request copy`() {
        assertEquals("Pickup request sent", pickupPushCopy("PICKUP_REQUEST_SENT")?.first)
        assertEquals("New pickup request", pickupPushCopy("PICKUP_REQUESTED")?.first)
        assertFalse(pickupPushCopy("PICKUP_REQUEST_SENT")!!.second.contains("selected you"))
    }
}
