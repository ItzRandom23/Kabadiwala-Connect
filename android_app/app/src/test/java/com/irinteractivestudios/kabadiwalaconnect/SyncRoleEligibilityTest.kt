package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.sync.syncOperationsForRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRoleEligibilityTest {
    @Test fun notificationReadCanSyncForEveryMarketplaceRole() {
        listOf(AccountRole.HOUSEHOLD, AccountRole.COLLECTOR, AccountRole.RECYCLER).forEach { role ->
            val operations = syncOperationsForRole(role)
            assertTrue("$role", "MARK_NOTIFICATION_READ" in operations)
            assertTrue("$role", "MARK_ALL_NOTIFICATIONS_READ" in operations)
        }
    }

    @Test fun chatAndProtectedMutationsStayWithSupportedRoles() {
        val household = syncOperationsForRole(AccountRole.HOUSEHOLD)
        val collector = syncOperationsForRole(AccountRole.COLLECTOR)
        val recycler = syncOperationsForRole(AccountRole.RECYCLER)

        assertFalse("SEND_CHAT_MESSAGE" in household)
        assertTrue("SEND_CHAT_MESSAGE" in collector)
        assertTrue("SEND_CHAT_MESSAGE" in recycler)
        assertTrue("REQUEST_HOUSEHOLD_PICKUP" in household)
        assertFalse("REQUEST_HOUSEHOLD_PICKUP" in collector)
        assertFalse("CONFIRM_SUPPLY_HANDOVER" in recycler)
        assertFalse("CONFIRM_SUPPLY_HANDOVER" in collector)
        assertTrue(syncOperationsForRole(AccountRole.ADMIN).isEmpty())
    }
}
