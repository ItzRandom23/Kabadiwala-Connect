package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.notifications.mayDisplayPush
import com.irinteractivestudios.kabadiwalaconnect.notifications.pickupPushCopy
import com.irinteractivestudios.kabadiwalaconnect.notifications.pushTypeMatchesRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPolicyTest {
    @Test fun `role specific pushes cannot cross roles`() {
        assertFalse(pushTypeMatchesRole("PICKUP_REQUESTED", AccountRole.HOUSEHOLD))
        assertTrue(pushTypeMatchesRole("PICKUP_REQUESTED", AccountRole.COLLECTOR))
        assertFalse(pushTypeMatchesRole("BULK_OFFER_ACCEPTED", AccountRole.COLLECTOR))
        assertTrue(pushTypeMatchesRole("BULK_OFFER_ACCEPTED", AccountRole.RECYCLER))
        assertFalse(pushTypeMatchesRole("PICKUP_WEIGHED", AccountRole.RECYCLER))
        assertTrue(pushTypeMatchesRole("CHAT_MESSAGE", AccountRole.HOUSEHOLD))
        assertFalse(pushTypeMatchesRole("CHAT_MESSAGE", null))
    }
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
