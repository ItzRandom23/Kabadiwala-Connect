package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.FailureKind
import com.irinteractivestudios.kabadiwalaconnect.util.failureResource
import org.junit.Assert.*
import org.junit.Test

class UserFacingErrorResourcesTest {
    @Test fun transportKindsHaveResourcePresentation() {
        assertEquals(R.string.auth_network_error, failureResource(FailureKind.NO_INTERNET))
        assertEquals(R.string.auth_network_error, failureResource(FailureKind.TIMEOUT))
        assertEquals(R.string.auth_session_restore_failed, failureResource(FailureKind.AUTHENTICATION))
        assertEquals(R.string.deal_not_available, failureResource(FailureKind.NOT_FOUND))
    }

    @Test fun validationAndConflictDetailsAreNotReplacedByGenericCopy() {
        assertNull(failureResource(FailureKind.VALIDATION))
        assertNull(failureResource(FailureKind.CONFLICT))
        assertNull(failureResource(FailureKind.AUTHORIZATION))
    }
}
