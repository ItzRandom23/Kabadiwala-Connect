package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.ProtectedRequestBlockedException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.isRetryableTransportFailure
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RemoteFailurePolicyTest {
    @Test fun retriesTransportFailuresAndTransientHttpResponses() {
        assertTrue(IOException("offline").isRetryableTransportFailure())
        listOf(408, 429, 500, 503).forEach { code ->
            assertTrue(RemoteApiException("HTTP_$code", "temporary", code).isRetryableTransportFailure())
        }
    }

    @Test fun doesNotRetryPermanentOrSessionGateFailures() {
        listOf(400, 401, 403, 404, 409, 422).forEach { code ->
            assertFalse(RemoteApiException("HTTP_$code", "permanent", code).isRetryableTransportFailure())
        }
        assertFalse(ProtectedRequestBlockedException.isRetryableTransportFailure())
        assertFalse(CancellationException("cancelled").isRetryableTransportFailure())
        assertFalse(IllegalStateException("invalid response").isRetryableTransportFailure())
    }
}
