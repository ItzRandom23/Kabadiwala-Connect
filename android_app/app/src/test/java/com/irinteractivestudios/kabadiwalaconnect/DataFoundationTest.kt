package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeEarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakePriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeRecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.util.InMemorySecureStorage
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SecureSessionRepository
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies local-data foundations start empty and preserve security invariants. */
class DataFoundationTest {

    @Test
    fun fakeRepositories_startEmpty_noFabricatedData() = runTest {
        assertEquals(emptyList<Any>(), FakeLotRepository().observeLots().first())
        assertEquals(emptyList<Any>(), FakePriceRepository().observePrices().first())
        assertEquals(emptyList<Any>(), FakeRecyclerRepository().observeRecyclers().first())
        assertEquals(EarningsSummary(), FakeEarningsRepository().observeSummary().first())
    }

    @Test
    fun syncQueueItem_hasSaneDefaults() {
        val item = SyncQueueItemEntity(
            operation = "CREATE_LOT",
            payloadJson = "{}",
            createdAtEpochMs = 1L
        )
        assertEquals(0L, item.uid)
        assertEquals(0, item.attempts)
        assertNull(item.lastErrorCode)
    }

    @Test
    fun inMemorySecureStorage_roundTrips() {
        val storage = InMemorySecureStorage()
        assertNull(storage.get("k"))
        storage.put("k", "v")
        assertEquals("v", storage.get("k"))
        storage.remove("k")
        assertNull(storage.get("k"))
    }

    @Test
    fun sessionSave_withoutRefreshToken_clearsPreviousRefreshCredential() {
        val storage = InMemorySecureStorage()
        val session = SecureSessionRepository(storage)
        session.save("token-a", 10_000L, "refresh-a")
        assertEquals("refresh-a", storage.get(SecureStorage.REFRESH_TOKEN))
        session.save("token-b", 20_000L)
        assertNull(storage.get(SecureStorage.REFRESH_TOKEN))
    }
}
