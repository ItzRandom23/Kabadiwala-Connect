package com.irinteractivestudios.kabadiwalaconnect.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local Room database for the offline-first collector and recycler flows.
 * Every feature table is versioned through an explicit migration so cached
 * work remains recoverable across app upgrades.
 *
 * Encryption capability: SQLCipher integration can be supplied through the
 * builder without changing DAO/repository callers; secure session material is
 * already kept outside this database in Keystore-backed storage.
 */
@Database(
    entities = [SyncQueueItemEntity::class, CollectorProfileEntity::class, LotEntity::class, PriceEntity::class, RecyclerEntity::class, QuoteEntity::class, HandoverEntity::class, PaymentEntity::class, DisputeEntity::class, SchemeCacheEntity::class, ActivityCacheEntity::class, ConversationCacheEntity::class, MessageCacheEntity::class, NotificationCacheEntity::class, PendingPhotoUploadEntity::class, HouseholdListingCacheEntity::class],
    version = 27,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun collectorProfileDao(): CollectorProfileDao
    abstract fun lotDao(): LotDao
    abstract fun priceDao(): PriceDao
    abstract fun recyclerDao(): RecyclerDao
    abstract fun quoteDao(): QuoteDao
    abstract fun handoverDao(): HandoverDao
    abstract fun paymentDao(): PaymentDao
    abstract fun disputeDao(): DisputeDao
    abstract fun futureCacheDao(): FutureCacheDao
    abstract fun pendingPhotoUploadDao(): PendingPhotoUploadDao
    abstract fun householdListingCacheDao(): HouseholdListingCacheDao

    companion object {
        const val DB_NAME = "kabadiwala.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                    .build().also { instance = it }
            }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS collector_profile (collectorId TEXT NOT NULL PRIMARY KEY, phoneNumber TEXT NOT NULL, preferredLanguage TEXT NOT NULL, primaryLocation TEXT NOT NULL, locationSource TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, lastLoginEpochMs INTEGER NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lots (id TEXT NOT NULL PRIMARY KEY, collectorId TEXT NOT NULL, materialLabel TEXT NOT NULL, `condition` TEXT NOT NULL, weightKg REAL NOT NULL, localPhotoPath TEXT, serverPhotoUrl TEXT, estimatedValueRupees REAL, quoteRupees REAL, finalValueRupees REAL, location TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, synced INTEGER NOT NULL)")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS prices (id TEXT NOT NULL, location TEXT NOT NULL, materialLabel TEXT NOT NULL, ratePerKg REAL NOT NULL, minRatePerKg REAL NOT NULL, maxRatePerKg REAL NOT NULL, updatedAtEpochMs INTEGER NOT NULL, trend TEXT NOT NULL, historyCsv TEXT NOT NULL, PRIMARY KEY(id, location))")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS recyclers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, authorized INTEGER NOT NULL, distanceKm REAL, area TEXT NOT NULL, facility TEXT NOT NULL, address TEXT NOT NULL, acceptedMaterialsCsv TEXT NOT NULL, offeredRatePerKg REAL NOT NULL, pickupAvailable INTEGER NOT NULL, operatingHours TEXT NOT NULL, typicalHandoverHours INTEGER NOT NULL, contactPhone TEXT NOT NULL, latitude REAL, longitude REAL)")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS quotes (id TEXT NOT NULL PRIMARY KEY, recyclerId TEXT NOT NULL, lotId TEXT NOT NULL, amountRupees REAL NOT NULL, recyclerName TEXT NOT NULL, pricePerKg REAL NOT NULL, marketRatePerKg REAL NOT NULL, distanceKm REAL NOT NULL, pickupAvailable INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, expiresAtEpochMs INTEGER NOT NULL, status TEXT NOT NULL, deliveryState TEXT NOT NULL)") }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS handovers (id TEXT NOT NULL PRIMARY KEY, lotId TEXT NOT NULL, recyclerId TEXT NOT NULL, collectorId TEXT NOT NULL, recyclerName TEXT NOT NULL, materialLabel TEXT NOT NULL, weightKg REAL NOT NULL, quotedPriceRupees REAL NOT NULL, collectionLocation TEXT NOT NULL, handoverLocation TEXT NOT NULL, handoverLocationType TEXT NOT NULL, timestampEpochMs INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, quoteId TEXT NOT NULL, status TEXT NOT NULL, synced INTEGER NOT NULL)") }
        }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS payments (id TEXT NOT NULL PRIMARY KEY, lotId TEXT NOT NULL, handoverId TEXT, amountRupees REAL NOT NULL, method TEXT NOT NULL, paidAtEpochMs INTEGER NOT NULL, notes TEXT NOT NULL, syncState TEXT NOT NULL, recordState TEXT NOT NULL)") }
        }
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE handovers ADD COLUMN actualWeightKg REAL")
                db.execSQL("ALTER TABLE handovers ADD COLUMN materialConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE handovers ADD COLUMN collectorConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE handovers ADD COLUMN scalePhotoPath TEXT")
                db.execSQL("ALTER TABLE handovers ADD COLUMN evidenceUpdatedAtEpochMs INTEGER")
            }
        }
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recyclers ADD COLUMN authorizationAuthority TEXT")
                db.execSQL("ALTER TABLE recyclers ADD COLUMN authorizationValidUntilEpochMs INTEGER")
                db.execSQL("ALTER TABLE recyclers ADD COLUMN rating REAL")
                db.execSQL("ALTER TABLE recyclers ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recyclers ADD COLUMN completedHandovers INTEGER")
                db.execSQL("ALTER TABLE recyclers ADD COLUMN lastUpdatedEpochMs INTEGER")
            }
        }
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS disputes (id TEXT NOT NULL PRIMARY KEY, handoverId TEXT NOT NULL, lotId TEXT NOT NULL, collectorId TEXT NOT NULL, recyclerId TEXT NOT NULL, type TEXT NOT NULL, description TEXT NOT NULL, claimedWeightKg REAL, actualWeightKg REAL, status TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, synced INTEGER NOT NULL, remoteId TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_disputes_handoverId ON disputes(handoverId)")
            }
        }
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS future_schemes (id TEXT NOT NULL PRIMARY KEY, slug TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, documentsCsv TEXT NOT NULL, sourceUrl TEXT NOT NULL, lastVerifiedAt TEXT NOT NULL, cachedAtEpochMs INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS future_activities (id TEXT NOT NULL PRIMARY KEY, slug TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, materialsCsv TEXT NOT NULL, stepsCsv TEXT NOT NULL, warningsCsv TEXT NOT NULL, difficulty TEXT NOT NULL, minutes INTEGER NOT NULL, cachedAtEpochMs INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS future_conversations (id TEXT NOT NULL PRIMARY KEY, lotId TEXT NOT NULL, quoteId TEXT, collectorId TEXT NOT NULL, recyclerId TEXT NOT NULL, status TEXT NOT NULL, lastMessageAt TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS future_messages (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, senderId TEXT NOT NULL, senderRole TEXT NOT NULL, clientMessageId TEXT NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL, createdAt TEXT, readAt TEXT)")
            }
        }
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collector_profile ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE collector_profile ADD COLUMN longitude REAL")
            }
        }
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE handovers ADD COLUMN qrCodeData TEXT")
                db.execSQL("ALTER TABLE handovers ADD COLUMN referenceId TEXT")
                db.execSQL("ALTER TABLE handovers ADD COLUMN expiresAtEpochMs INTEGER")
            }
        }
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastErrorCode TEXT")
            }
        }
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS future_notifications (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, route TEXT, readAt TEXT, createdAt TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_future_notifications_accountId_createdAt ON future_notifications(accountId, createdAt)")
            }
        }
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lots ADD COLUMN materialSubcategory TEXT")
                db.execSQL("ALTER TABLE lots ADD COLUMN sourceType TEXT")
                db.execSQL("ALTER TABLE lots ADD COLUMN wasteRegime TEXT NOT NULL DEFAULT 'E_WASTE'")
                db.execSQL("ALTER TABLE lots ADD COLUMN originalWeight REAL")
                db.execSQL("ALTER TABLE lots ADD COLUMN originalWeightUnit TEXT")
                db.execSQL("ALTER TABLE lots ADD COLUMN imageProvenance TEXT")
                db.execSQL("ALTER TABLE lots ADD COLUMN imageQualityStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE lots ADD COLUMN locationPrecision TEXT")
                db.execSQL("ALTER TABLE lots ADD COLUMN serverUpdatedAtEpochMs INTEGER")
            }
        }
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN accountId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_nextAttemptAtEpochMs ON sync_queue(nextAttemptAtEpochMs)")
            }
        }
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prices ADD COLUMN unit TEXT NOT NULL DEFAULT 'KILOGRAM'")
                db.execSQL("ALTER TABLE prices ADD COLUMN source TEXT NOT NULL DEFAULT 'SYSTEM'")
                db.execSQL("ALTER TABLE prices ADD COLUMN qualityStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE prices ADD COLUMN disclaimer TEXT")
                db.execSQL("ALTER TABLE prices ADD COLUMN trendPercentage REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE prices ADD COLUMN complianceRegime TEXT")
            }
        }
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN accountId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_accountId_paidAtEpochMs ON payments(accountId, paidAtEpochMs)")
            }
        }
        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Older prototypes reached the same database version with
                // different subsets of these indexes. Normalize every index
                // Room expects without deleting offline user data.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_disputes_handoverId ON disputes(handoverId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_future_notifications_accountId_createdAt ON future_notifications(accountId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_nextAttemptAtEpochMs ON sync_queue(nextAttemptAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_accountId_paidAtEpochMs ON payments(accountId, paidAtEpochMs)")
            }
        }

        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Preserve existing offline lots while adding the backend
                // optimistic-concurrency version required for editing.
                db.execSQL("ALTER TABLE lots ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_photo_uploads (listingId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, localPath TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_photo_uploads_accountId ON pending_photo_uploads(accountId)")
            }
        }

        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lots ADD COLUMN locationLatitude REAL")
                db.execSQL("ALTER TABLE lots ADD COLUMN locationLongitude REAL")
            }
        }

        val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS household_listings (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, payloadJson TEXT NOT NULL, synced INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_household_listings_accountId ON household_listings(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_household_listings_accountId_synced ON household_listings(accountId, synced)")
            }
        }

        val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_photo_uploads ADD COLUMN localPathsJson TEXT")
            }
        }

        val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lots ADD COLUMN localPhotoPathsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Test seam: lets tests inject an in-memory database. */
        fun setForTesting(db: AppDatabase?) {
            instance = db
        }
    }
}
