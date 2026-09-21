package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiEnvelope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class ApiServiceErrorTest {
    @Test
    fun malformedBackendBodyDoesNotBecomeCustomerFacingExceptionText() {
        val response = Response.error<ApiEnvelope<String>>(
            500,
            "Prisma error: mongodb://user:password@example.invalid/private".toResponseBody()
        )

        val error = runCatching { response.requireData() }.exceptionOrNull()

        assertEquals("HTTP_500", (error as com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException).code)
        assertEquals("The request could not be completed", error.message)
        assertFalse(error.message.orEmpty().contains("Prisma", ignoreCase = true))
        assertFalse(error.message.orEmpty().contains("mongodb", ignoreCase = true))
    }
}
