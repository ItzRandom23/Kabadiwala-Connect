package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.HouseholdListingCacheEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run with processPhase=seed, force-stop the app, then processPhase=verify.
 * This tests persisted state across separate app processes, not network replay.
 */
@RunWith(AndroidJUnit4::class)
class OfflineListingProcessPersistenceTest {
    @Test
    fun listingPhotoAndReplayIdentityPersistAcrossProcesses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val phase = InstrumentationRegistry.getArguments().getString("processPhase", "roundtrip")
        val name = "offline-listing-process-test.db"
        val photo = File(context.filesDir, "offline-process-photo.jpg")
        if (phase != "verify") context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            if (phase != "verify") {
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                try { photo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
                finally { bitmap.recycle() }
                db.withTransaction {
                    db.syncQueueDao().enqueueOnce(SyncQueueItemEntity(
                        operation = "CREATE_HOUSEHOLD_LISTING", accountId = "process-owner",
                        idempotencyKey = "process-stable-key", createdAtEpochMs = 1,
                        payloadJson = """{"localListingId":"local-process-listing","photoPaths":["${photo.absolutePath}"]}"""
                    ))
                    db.householdListingCacheDao().upsert(HouseholdListingCacheEntity(
                        id = "local-process-listing", accountId = "process-owner", synced = false,
                        payloadJson = """{"id":"local-process-listing","status":"PENDING_SYNC"}""",
                        createdAtEpochMs = 1, updatedAtEpochMs = 1
                    ))
                }
            }
            val queue = db.syncQueueDao().observeForAccount("process-owner").first()
            assertEquals("process-stable-key", queue.single().idempotencyKey)
            assertTrue(queue.single().payloadJson.contains(photo.absolutePath))
            assertEquals("local-process-listing", db.householdListingCacheDao().findForAccount("process-owner").single().id)
            assertTrue(db.syncQueueDao().observeForAccount("different-owner").first().isEmpty())
            assertTrue(db.householdListingCacheDao().findForAccount("different-owner").isEmpty())
            assertTrue(photo.length() > 100)
        } finally {
            db.close()
            if (phase != "seed") { context.deleteDatabase(name); photo.delete() }
        }
    }
}
