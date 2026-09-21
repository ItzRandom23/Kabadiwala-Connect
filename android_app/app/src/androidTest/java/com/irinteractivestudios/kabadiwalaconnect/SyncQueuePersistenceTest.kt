package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the durable queue contract across a database close/reopen, which
 * is the storage boundary crossed when Android kills and later recreates the
 * application process. Account filtering is asserted at the DAO boundary so
 * a worker cannot replay another account's offline work.
 */
@RunWith(AndroidJUnit4::class)
class SyncQueuePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "sync-queue-persistence-test.db"
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(databaseName)
    }

    @Test
    fun queuedOperationsSurviveReopenAndRemainAccountScoped() = runBlocking {
        database = openDatabase()
        val queue = database!!.syncQueueDao()
        queue.enqueue(
            SyncQueueItemEntity(
                operation = "CREATE_LOT",
                payloadJson = "{\"id\":\"lot-a\"}",
                createdAtEpochMs = 1L,
                accountId = "account-a"
            )
        )
        queue.enqueue(
            SyncQueueItemEntity(
                operation = "REQUEST_HOUSEHOLD_PICKUP",
                payloadJson = "{\"id\":\"pickup-b\",\"idempotencyKey\":\"pickup-key-b\"}",
                createdAtEpochMs = 2L,
                accountId = "account-b"
            )
        )
        database!!.close()

        database = openDatabase()
        val accountAItems = database!!.syncQueueDao().observeForAccount("account-a").first()
        val accountAPending = database!!.syncQueueDao().observePendingForAccount("account-a").first()
        val accountBItems = database!!.syncQueueDao().observeForAccount("account-b").first()

        assertEquals(1, accountAItems.size)
        assertEquals("CREATE_LOT", accountAItems.single().operation)
        assertEquals(accountAItems, accountAPending)
        assertEquals(1, accountBItems.size)
        assertTrue(accountBItems.single().payloadJson.contains("pickup-b"))
        assertEquals(
            accountBItems.single().uid,
            database!!.syncQueueDao().findUidByOperationAndIdempotencyKey(
                "REQUEST_HOUSEHOLD_PICKUP",
                "account-b",
                "pickup-key-b"
            )
        )
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    ).build()
}
