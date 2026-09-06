package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeEarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakePriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeRecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.util.InMemorySecureStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies Phase 1 local-data foundations start empty and behave. */
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
}
