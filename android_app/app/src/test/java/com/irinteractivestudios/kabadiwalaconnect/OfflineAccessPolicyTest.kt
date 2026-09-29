package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.OfflineAccessPolicy
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAccessPolicyTest {
    private val household = AccountProfile("user", "", AccountRole.HOUSEHOLD)

    @Test fun eligibleWithinSevenDaysOnly() {
        val now = 1_900_000_000_000L
        assertTrue(OfflineAccessPolicy.eligible(household, now - OfflineAccessPolicy.WINDOW_MS, now))
        assertFalse(OfflineAccessPolicy.eligible(household, now - OfflineAccessPolicy.WINDOW_MS - 1, now))
        assertFalse(OfflineAccessPolicy.eligible(household, now + 1, now))
        assertFalse(OfflineAccessPolicy.eligible(household, null, now))
    }

    @Test fun adminAndSuspendedAccountsCannotUseOfflineCache() {
        val now = 1_900_000_000_000L
        assertFalse(OfflineAccessPolicy.eligible(household.copy(role = AccountRole.ADMIN), now, now))
        assertFalse(OfflineAccessPolicy.eligible(household.copy(accountStatus = "SUSPENDED"), now, now))
    }
}
