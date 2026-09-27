package com.irinteractivestudios.kabadiwalaconnect

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomPaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Payment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxAtomicityTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun lotIsRolledBackWhenOutboxInsertFails() = runBlocking {
        rejectOutboxInserts()
        val repository = RoomLotRepository(database.lotDao(), database.syncQueueDao(), accountId = { "collector-1" })

        val result = runCatching {
            repository.save(Lot(id = "lot-1", collectorId = "collector-1", materialLabel = "CABLE", weightKg = 2.0, createdAtEpochMs = 1L))
        }

        assertFalse(result.isSuccess)
        assertEquals(null, database.lotDao().findById("lot-1"))
        assertEquals(0, database.syncQueueDao().count())
    }

    @Test
    fun paymentIsRolledBackWhenOutboxInsertFails() = runBlocking {
        rejectOutboxInserts()
        val repository = RoomPaymentRepository(database.paymentDao(), database.syncQueueDao(), accountId = { "collector-1" })

        val result = runCatching {
            repository.record(Payment(id = "payment-1", lotId = "lot-1", amountRupees = 10.0, paidAtEpochMs = 1L))
        }

        assertFalse(result.isSuccess)
        assertEquals(null, database.paymentDao().findById("payment-1"))
        assertEquals(0, database.syncQueueDao().count())
    }

    private fun rejectOutboxInserts() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_outbox BEFORE INSERT ON sync_queue BEGIN SELECT RAISE(ABORT, 'simulated outbox failure'); END"
        )
    }
}
