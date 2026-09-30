package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.*
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import okhttp3.ResponseBody.Companion.toResponseBody

class QuoteSessionSafetyTest {
    @Test fun offlineHandoverConfirmationDoesNotMutateOrEnqueue() = runTest {
        var writes = 0
        val entity = com.irinteractivestudios.kabadiwalaconnect.domain.model.Handover(id = "handover-1", collectorId = "collector-1").toEntity()
        val dao = proxy<HandoverDao> { method, _ ->
            if (method == "observeForAccount") kotlinx.coroutines.flow.flowOf(entity) else { writes++; 1 }
        }
        val queue = proxy<SyncQueueDao> { _, _ -> writes++; 1L }
        val api = proxy<ApiService> { _, _ -> Response.error<Any>(503, "{}".toResponseBody()) }
        val repository = OfflineFirstHandoverRepository(RoomHandoverRepository(dao) { "collector-1" },
            RemoteHandoverRepository(dao, api, accountId = { "collector-1" }), queue, {})
        assertNotNull(runCatching { repository.markHandedOver("handover-1") }.exceptionOrNull())
        assertNotNull(runCatching { repository.updateEvidence("handover-1", 1.0, true, true, null) }.exceptionOrNull())
        assertEquals(0, writes)
    }
    @Test fun unsuccessfulAcceptanceDoesNotMutateCacheOrEnqueue() = runTest {
        var writes = 0
        val dao = proxy<QuoteDao> { _, _ -> writes++; 1 }
        val queue = proxy<SyncQueueDao> { _, _ -> writes++; 1L }
        val api = proxy<ApiService> { _, _ -> Response.error<Any>(503, "{}".toResponseBody()) }
        val repository = RemoteQuoteRepository(dao, api, queue, accountId = { "collector-1" })
        val failure = runCatching { repository.accept("quote-1") }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(0, writes)
    }

    @Test fun resultFromEarlierSessionCannotMutateQuoteCache() = runTest {
        var generation = 1L
        var writes = 0
        val dao = proxy<QuoteDao> { _, _ -> writes++; 1 }
        val api = proxy<ApiService> { _, _ ->
            generation++
            Response.success(ApiEnvelope(true, QuoteDto(id = "quote-1", lotId = "lot-1", recyclerId = "recycler-1", pricePerKg = 100.0, status = "ACCEPTED")))
        }
        val repository = RemoteQuoteRepository(dao, api, accountId = { "collector-1" }, sessionGenerationProvider = { generation })
        val failure = runCatching { repository.accept("quote-1") }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0, writes)
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args) } as T
}
