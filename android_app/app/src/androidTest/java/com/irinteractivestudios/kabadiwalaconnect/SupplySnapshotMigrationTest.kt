package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupplySnapshotMigrationTest {
    @Test fun upgradeFromVersion27PreservesNotificationsAndQueuedKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "supply-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("room-schema-27.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val sqlite = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL("INSERT INTO future_notifications (id, accountId, type, title, body, route, readAt, createdAt) VALUES ('old-notification', 'account-1', 'CHAT_MESSAGE', 'Message', 'Existing message', NULL, NULL, '2026-01-01T00:00:00Z')")
        sqlite.execSQL("INSERT INTO sync_queue (operation, payloadJson, createdAtEpochMs, attempts, lastErrorCode, nextAttemptAtEpochMs, accountId) VALUES ('REQUEST_HOUSEHOLD_PICKUP', '{\"listingId\":\"listing-1\",\"idempotencyKey\":\"key-1\"}', 1, 0, NULL, 0, 'account-1')")
        sqlite.execSQL("INSERT INTO sync_queue (operation, payloadJson, createdAtEpochMs, attempts, lastErrorCode, nextAttemptAtEpochMs, accountId) VALUES ('SEND_CHAT_MESSAGE', '{\"clientMessageId\":\"message-key-1\",\"body\":\"saved\"}', 2, 0, NULL, 0, 'account-1')")
        sqlite.version = 27
        sqlite.close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_27_28, AppDatabase.MIGRATION_28_29)
            .build()
        try {
            room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM future_notifications WHERE id = 'old-notification'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM supply_snapshots").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            room.openHelper.writableDatabase.query("SELECT idempotencyKey FROM sync_queue WHERE operation = 'REQUEST_HOUSEHOLD_PICKUP'").use {
                assertTrue(it.moveToFirst())
                assertEquals("key-1", it.getString(0))
            }
            room.openHelper.writableDatabase.query("SELECT idempotencyKey FROM sync_queue WHERE operation = 'SEND_CHAT_MESSAGE'").use {
                assertTrue(it.moveToFirst())
                assertEquals("message-key-1", it.getString(0))
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
