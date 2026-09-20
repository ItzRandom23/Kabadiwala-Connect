package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingErrorsTest {
    @Test fun backendDetailsNeverReachCustomerCopy() {
        val result = userFacingError(
            RemoteApiException("INTERNAL_SERVER_ERROR", "Prisma error: private database detail", 500),
            "Could not load operator data"
        )

        assertEquals("Could not load operator data", result)
        assertFalse(result.contains("Prisma", ignoreCase = true))
    }

    @Test fun authRateLimitAndRoleErrorsUseRecoveryCopy() {
        assertEquals(
            "Your session expired. Please sign in again.",
            userFacingError(RemoteApiException("TOKEN_INVALID", "Bearer token required", 401), "fallback")
        )
        assertEquals(
            "Too many requests. Please wait a moment and try again.",
            userFacingError(RemoteApiException("OTP_RATE_LIMITED", "Too many requests", 429), "fallback")
        )
        assertEquals(
            "This action is not available for your role.",
            userFacingError(RemoteApiException("AUTHORIZATION_ERROR", "Recycler access required", 403), "fallback")
        )
    }
}
