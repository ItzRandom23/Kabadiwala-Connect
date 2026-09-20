package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionCoordinator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionState
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun lifecycleTransitionsNeverExposeAnAccountOutsideAuthenticatedState() {
        val coordinator = SessionCoordinator()
        val account = AccountProfile("household-1", "household@example.com", AccountRole.HOUSEHOLD)

        assertEquals(SessionState.RESTORING, coordinator.snapshot.value.state)
        assertFalse(coordinator.snapshot.value.restorable)

        coordinator.authenticated(account)
        assertEquals(SessionState.AUTHENTICATED, coordinator.snapshot.value.state)
        assertEquals(account.profileId, coordinator.snapshot.value.account?.profileId)
        assertTrue(coordinator.snapshot.value.restorable)

        coordinator.expired()
        assertEquals(SessionState.EXPIRED, coordinator.snapshot.value.state)
        assertFalse(coordinator.snapshot.value.restorable)
        assertEquals(null, coordinator.snapshot.value.account)

        coordinator.unauthenticated()
        assertEquals(SessionState.UNAUTHENTICATED, coordinator.snapshot.value.state)
        assertFalse(coordinator.snapshot.value.restorable)
    }
}
