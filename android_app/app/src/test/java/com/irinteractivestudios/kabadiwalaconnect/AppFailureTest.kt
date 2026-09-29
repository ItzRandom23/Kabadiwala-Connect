package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.FailureKind
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.toAppFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppFailureTest {
    @Test fun `network and timeout failures are retryable`() {
        assertEquals(FailureKind.NO_INTERNET, UnknownHostException().toAppFailure().kind)
        assertEquals(FailureKind.TIMEOUT, SocketTimeoutException().toAppFailure().kind)
        assertTrue(SocketTimeoutException().toAppFailure().retryable)
    }

    @Test fun `auth and validation failures need user action`() {
        assertEquals(FailureKind.AUTHENTICATION, RemoteApiException("TOKEN_EXPIRED", "Expired", 401).toAppFailure().kind)
        assertEquals(FailureKind.VALIDATION, RemoteApiException("VALIDATION_ERROR", "Invalid address", 422).toAppFailure().kind)
        assertFalse(RemoteApiException("VALIDATION_ERROR", "Invalid address", 422).toAppFailure().retryable)
    }
}
