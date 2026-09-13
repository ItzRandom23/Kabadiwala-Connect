package com.irinteractivestudios.kabadiwalaconnect.di

import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import android.content.Context
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomPriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomRecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomQuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RemoteQuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockAuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockOtpService
import com.irinteractivestudios.kabadiwalaconnect.data.auth.RemoteAuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.RoomCollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SecureSessionRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.CollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RetrofitProvider
import com.irinteractivestudios.kabadiwalaconnect.data.repository.EarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeEarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakePriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.FakeRecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.HandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomHandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RemoteHandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.OfflineFirstHandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomPaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomDisputeRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.PriceEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.RecyclerEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.LotEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.PaymentEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.local.toSyncEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.toDomain
import com.irinteractivestudios.kabadiwalaconnect.data.local.toEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentRecordState
import com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentSyncState
import kotlinx.coroutines.flow.first
import com.irinteractivestudios.kabadiwalaconnect.data.sync.SyncScheduler
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.KeystoreSecureStorage
import com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage
import com.irinteractivestudios.kabadiwalaconnect.util.SystemConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.AndroidPriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.util.PriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.data.auth.readAccount
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow

/**
 * Manual service locator for the app's local and remote repositories.
 *
 * No DI framework is added on purpose: keeps the APK small and the
 * startup path simple on entry-level devices. ViewModels receive what
 * they need from here via a ViewModelProvider.Factory in MainActivity.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    /** Uses the configured backend and injects the current encrypted bearer token. */
    val apiService: ApiService by lazy {
        RetrofitProvider.create(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { secureStorage.get(SecureStorage.AUTH_TOKEN) },
            tokenRefresher = { runBlocking { authenticationRepository.refreshAccessToken() } }
        )
    }

    val lotRepository: LotRepository by lazy { RoomLotRepository(database.lotDao(), database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId } }
    val lotWriter: LotWriter by lazy { lotRepository as LotWriter }
    val priceRepository: PriceRepository by lazy { RoomPriceRepository(database.priceDao()) }
    val priceSpeaker: PriceSpeaker by lazy { AndroidPriceSpeaker(appContext) }
    val recyclerRepository: RecyclerRepository by lazy { RoomRecyclerRepository(database.recyclerDao()) }
    val quoteRepository: QuoteRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) RoomQuoteRepository(database.quoteDao())
        else RemoteQuoteRepository(database.quoteDao(), apiService, database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId }
    }
    val handoverRepository: HandoverRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) RoomHandoverRepository(database.handoverDao())
        else OfflineFirstHandoverRepository(
            RoomHandoverRepository(database.handoverDao()),
            RemoteHandoverRepository(database.handoverDao(), apiService),
            database.syncQueueDao()
        ) { syncScheduler.requestSync() }
    }
    val paymentRepository: PaymentRepository by lazy { RoomPaymentRepository(database.paymentDao(), database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId } }
    val earningsRepository: EarningsRepository get() = paymentRepository
    val disputeRepository: com.irinteractivestudios.kabadiwalaconnect.data.repository.DisputeRepository by lazy { RoomDisputeRepository(database.disputeDao()) }

    val connectivityObserver: ConnectivityObserver by lazy {
        SystemConnectivityObserver(appContext)
    }

    val secureStorage: SecureStorage by lazy { KeystoreSecureStorage(appContext) }

    val sessionRepository by lazy { SecureSessionRepository(secureStorage) }
    val authenticationRepository: AuthenticationRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) {
            MockAuthenticationRepository(MockOtpService(), sessionRepository, secureStorage)
        } else {
            RemoteAuthenticationRepository(apiService, sessionRepository, secureStorage)
        }
    }
    val collectorProfileRepository: CollectorProfileRepository by lazy { RoomCollectorProfileRepository(database.collectorProfileDao()) }

    fun hasValidSession(): Boolean = sessionRepository.isSessionValid()
    fun hasRestorableSession(): Boolean = hasValidSession() || !secureStorage.get(SecureStorage.REFRESH_TOKEN).isNullOrBlank()

    fun currentAccount(): AccountProfile? = secureStorage.readAccount()

    suspend fun refreshAccount() { authenticationRepository.refreshAccount() }

    suspend fun refreshCatalogs(location: String? = null, latitude: Double? = null, longitude: Double? = null) {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return
        // Price, lot, quote and payment catalogues are collector-facing
        // resources. Recycler sessions have their own operational endpoints;
        // avoid predictable 403 traffic every time connectivity changes.
        if (currentAccount()?.role == AccountRole.RECYCLER) return
        val account = currentAccount()
        val resolvedLocation = location?.trim()?.takeIf { it.isNotBlank() }
            ?: account?.areaName?.trim()?.takeIf { it.isNotBlank() }
            ?: return
        val categories = listOf("CRT", "LCD_PANEL", "PCB", "CABLE", "COPPER", "BATTERY", "MOTOR", "MAGNET", "PLASTIC", "OTHER")
        val prices = categories.mapNotNull { category ->
            runCatching { apiService.getPriceBoard(category, resolvedLocation).requireData() }.getOrNull()?.let { board ->
                // Keep the trend chart backed by the same server snapshot as
                // the headline rate. History is optional so a partial outage
                // never removes an otherwise valid price board.
                val history = runCatching {
                    apiService.getPriceHistory(category, resolvedLocation, 30).requireData().history
                        .map { it.marketPrice }
                        .joinToString(",")
                }.getOrDefault("")
                PriceEntity(
                    id = "${resolvedLocation}_$category",
                    location = board.location ?: resolvedLocation,
                    materialLabel = category.toDisplayMaterial(),
                    ratePerKg = board.marketPrice,
                    minRatePerKg = board.priceMin,
                    maxRatePerKg = board.priceMax,
                    updatedAtEpochMs = board.lastUpdated?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                    trend = board.trend?.direction?.lowercase() ?: "stable",
                    historyCsv = history,
                    unit = board.unit,
                    source = listOfNotNull(board.source?.organization, board.source?.type).joinToString(" · ").ifBlank { "SYSTEM" },
                    qualityStatus = board.qualityStatus,
                    disclaimer = board.disclaimer,
                    trendPercentage = board.trend?.percentage ?: 0.0,
                    complianceRegime = board.complianceRegime
                )
            }
        }
        if (prices.isNotEmpty()) database.priceDao().replaceLocation(resolvedLocation, prices)
        val recyclers = runCatching { apiService.getRecyclers(resolvedLocation, 50, null, null, "proximity", 1, 100, latitude ?: account?.latitude, longitude ?: account?.longitude).requireData() }.getOrNull()?.items.orEmpty().map { recycler ->
            RecyclerEntity(
                id = recycler.id,
                name = recycler.name,
                authorized = recycler.authorizationStatus == "VERIFIED",
                distanceKm = recycler.distanceKm,
                area = recycler.facilityLocation?.areaName.orEmpty(),
                facility = recycler.facilityLocation?.areaName.orEmpty(),
                address = recycler.facilityLocation?.areaName.orEmpty(),
                acceptedMaterialsCsv = recycler.materialsAccepted.joinToString(",") { it.category.toDisplayMaterial() },
                offeredRatePerKg = recycler.rates.firstOrNull()?.pricePerKg ?: 0.0,
                pickupAvailable = recycler.pickupAvailability == "TODAY" || recycler.pickupAvailability == "THIS_WEEK",
                operatingHours = recycler.operatingHours?.toString().orEmpty(),
                typicalHandoverHours = recycler.averageHandoverTime?.filter { it.isDigit() }?.toIntOrNull() ?: 24,
                contactPhone = recycler.contact?.phone.orEmpty(),
                latitude = recycler.facilityLocation?.latitude,
                longitude = recycler.facilityLocation?.longitude,
                authorizationAuthority = recycler.authorizationDetails?.authority,
                authorizationValidUntilEpochMs = recycler.authorizationDetails?.validUntil?.let(::parseRemoteTimestamp),
                rating = recycler.rating,
                reviewCount = recycler.reviewCount,
                completedHandovers = recycler.completedHandovers,
                lastUpdatedEpochMs = recycler.lastUpdated?.let(::parseRemoteTimestamp)
            )
        }
        if (recyclers.isNotEmpty()) database.recyclerDao().replaceAll(recyclers)

        val remoteLots = runCatching { apiService.getLots(page = 1, limit = 100).requireData() }.getOrNull()?.items.orEmpty()
        if (remoteLots.isNotEmpty()) {
            val unsyncedIds = currentAccount()?.profileId?.let { database.lotDao().observeForCollector(it).first() }?.filterNot { it.synced }?.map { it.id }?.toSet().orEmpty()
            database.lotDao().saveAll(remoteLots.filterNot { it.id in unsyncedIds }.map { lot ->
                LotEntity(
                    id = lot.id,
                    collectorId = lot.collectorId.orEmpty(),
                    materialLabel = lot.materialCategory.toDisplayMaterial(),
                    condition = lot.condition,
                    weightKg = lot.weight,
                    localPhotoPath = null,
                    serverPhotoUrl = lot.photoUrl,
                    estimatedValueRupees = lot.estimatedValue,
                    quoteRupees = lot.quotedPrice,
                    finalValueRupees = lot.finalPrice,
                    location = lot.collectionAreaName ?: lot.collectionLocation?.areaName.orEmpty(),
                    createdAtEpochMs = lot.createdAt?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                    updatedAtEpochMs = lot.updatedAt?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                    status = lot.status.toLocalLotStatus().name,
                    notes = lot.notes.orEmpty(),
                    synced = true,
                    materialSubcategory = lot.materialSubcategory,
                    sourceType = lot.sourceType,
                    wasteRegime = lot.wasteRegime ?: "E_WASTE",
                    originalWeight = lot.originalWeight,
                    originalWeightUnit = lot.originalWeightUnit,
                    imageProvenance = lot.imageProvenance,
                    imageQualityStatus = lot.imageQualityStatus ?: "UNVERIFIED",
                    locationPrecision = lot.collectionLocation?.precision,
                    serverUpdatedAtEpochMs = lot.updatedAt?.let(::parseRemoteTimestamp),
                    version = lot.version
                )
            })
        }

        val payments = runCatching { apiService.getPayments().requireData() }.getOrNull().orEmpty()
        if (payments.isNotEmpty()) {
            database.paymentDao().insertAll(payments.map { payment ->
                PaymentEntity(
                    id = payment.id,
                    lotId = payment.lotId,
                    handoverId = payment.handoverId,
                    amountRupees = payment.amount,
                    method = runCatching { com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.valueOf(payment.method) }.getOrDefault(com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.CASH).name,
                    paidAtEpochMs = payment.date?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                    notes = "",
                    syncState = PaymentSyncState.SYNCED.name,
                    recordState = if (payment.status == "DISPUTED") PaymentRecordState.DISCREPANCY.name else PaymentRecordState.NORMAL.name,
                    accountId = currentAccount()?.profileId
                )
            })
        }
    }

    /** Refreshes the authoritative earnings ledger without requiring a full catalogue reload. */
    suspend fun refreshEarnings(): Boolean {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return false
        if (currentAccount()?.role == AccountRole.RECYCLER) return false
        val payments = apiService.getEarnings().requireData().payments
        database.paymentDao().insertAll(payments.map { payment ->
            PaymentEntity(
                id = payment.id,
                lotId = payment.lotId,
                handoverId = payment.handoverId,
                amountRupees = payment.amount,
                method = runCatching { com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.valueOf(payment.method) }
                    .getOrDefault(com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.CASH).name,
                paidAtEpochMs = payment.date?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                notes = "",
                syncState = PaymentSyncState.SYNCED.name,
                recordState = if (payment.status == "DISPUTED") PaymentRecordState.DISCREPANCY.name else PaymentRecordState.NORMAL.name,
                accountId = currentAccount()?.profileId
            )
        })
        return true
    }

    /** Pulls durable cross-role events without requiring push infrastructure. */
    suspend fun refreshActivity(): Boolean {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return false
        val response = apiService.getActivityChanges(secureStorage.get(SecureStorage.ACTIVITY_CURSOR)).requireData()
        val accountId = currentAccount()?.profileId
        if (response.notifications.isNotEmpty()) {
            FutureCacheStore(database.futureCacheDao()).appendNotifications(response.notifications)
        }
        response.serverTime?.takeIf { it.isNotBlank() }?.let { secureStorage.put(SecureStorage.ACTIVITY_CURSOR, it) }
        // Collector catalogue reconciliation already protects unsynced local
        // rows. Refreshing it here means a remote quote/handover notification
        // is reflected the next time the affected screen is opened.
        if (accountId != null && currentAccount()?.role != AccountRole.RECYCLER) {
            runCatching { reconcileChanges() }
        }
        return true
    }

    fun unreadNotificationCount(accountId: String): Flow<Int> = database.futureCacheDao().unreadNotificationCount(accountId)

    /**
     * Pulls server-authoritative changes after queued mutations have been
     * uploaded. The cursor is deliberately opaque: only the server decides
     * its format and it is advanced after the Room transaction succeeds.
     * Unsynced local rows are never overwritten, so a reconnect cannot erase
     * work that is still waiting in the outbox.
     */
    suspend fun reconcileChanges(): Boolean {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return false
        if (currentAccount()?.role == AccountRole.RECYCLER) return false
        val cursor = secureStorage.get(SecureStorage.SYNC_CURSOR)
        val payload = apiService.getChanges(cursor).requireData()
        database.withTransaction {
            payload.changes.lots.forEach { remote ->
                val local = database.lotDao().findById(remote.id)
                if (local == null || local.synced) {
                    val now = System.currentTimeMillis()
                    database.lotDao().save(
                        LotEntity(
                            id = remote.id,
                            collectorId = remote.collectorId ?: currentAccount()?.profileId.orEmpty(),
                            materialLabel = remote.materialCategory.toDisplayMaterial(),
                            condition = remote.condition,
                            weightKg = remote.weight,
                            localPhotoPath = local?.localPhotoPath,
                            serverPhotoUrl = remote.photoUrl,
                            estimatedValueRupees = remote.estimatedValue,
                            quoteRupees = remote.quotedPrice,
                            finalValueRupees = remote.finalPrice,
                            location = remote.collectionAreaName ?: remote.collectionLocation?.areaName.orEmpty(),
                            createdAtEpochMs = remote.createdAt?.let(::parseRemoteTimestamp) ?: local?.createdAtEpochMs ?: now,
                            updatedAtEpochMs = remote.updatedAt?.let(::parseRemoteTimestamp) ?: now,
                            status = remote.status.toLocalLotStatus().name,
                            notes = remote.notes.orEmpty(),
                            synced = true,
                            materialSubcategory = remote.materialSubcategory,
                            sourceType = remote.sourceType,
                            wasteRegime = remote.wasteRegime ?: "E_WASTE",
                            originalWeight = remote.originalWeight,
                            originalWeightUnit = remote.originalWeightUnit,
                            imageProvenance = remote.imageProvenance,
                            imageQualityStatus = remote.imageQualityStatus ?: "UNVERIFIED",
                            locationPrecision = remote.collectionLocation?.precision,
                            serverUpdatedAtEpochMs = remote.updatedAt?.let(::parseRemoteTimestamp),
                            version = remote.version
                        )
                    )
                }
            }
            payload.changes.payments.forEach { remote ->
                val local = database.paymentDao().findById(remote.id)
                if (local == null || local.syncState == PaymentSyncState.SYNCED.name) {
                    database.paymentDao().insert(
                        PaymentEntity(
                            id = remote.id,
                            lotId = remote.lotId,
                            handoverId = remote.handoverId,
                            amountRupees = remote.amount,
                            method = runCatching { com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.valueOf(remote.method) }
                                .getOrDefault(com.irinteractivestudios.kabadiwalaconnect.domain.model.PaymentMethod.CASH).name,
                            paidAtEpochMs = remote.date?.let(::parseRemoteTimestamp) ?: local?.paidAtEpochMs ?: System.currentTimeMillis(),
                            notes = local?.notes.orEmpty(),
                            syncState = PaymentSyncState.SYNCED.name,
                            recordState = if (remote.status == "DISPUTED") PaymentRecordState.DISCREPANCY.name else PaymentRecordState.NORMAL.name,
                            accountId = currentAccount()?.profileId
                        )
                    )
                }
            }
            payload.changes.handovers.forEach { remote ->
                val local = database.handoverDao().get(remote.id)
                if (local == null) {
                    database.handoverDao().insert(remote.toSyncEntity())
                } else if (local.synced) {
                    database.handoverDao().insert(remote.toDomain(local.toDomain()).toEntity())
                }
            }
        }
        payload.serverTime?.takeIf { it.isNotBlank() }?.let { secureStorage.put(SecureStorage.SYNC_CURSOR, it) }
        return true
    }

    /**
     * Logout is also a local privacy boundary. Account-owned Room rows,
     * queued mutations, and evidence files must not remain visible to the
     * next account on a shared phone or be uploaded under the next token.
     */
    suspend fun clearAccount() {
        database.withTransaction {
            database.syncQueueDao().clear()
            database.collectorProfileDao().clear()
            database.lotDao().clearAll()
            database.quoteDao().clearAll()
            database.handoverDao().clearAll()
            database.paymentDao().clearAll()
            database.disputeDao().clearAll()
            database.futureCacheDao().clearConversations()
            database.futureCacheDao().clearMessages()
            database.futureCacheDao().clearNotifications()
        }
        withContext(Dispatchers.IO) {
            File(appContext.filesDir, "lot_photos").deleteRecursively()
            File(appContext.filesDir, "handover_photos").deleteRecursively()
        }
        secureStorage.remove(SecureStorage.ACCOUNT_EMAIL)
        secureStorage.remove(SecureStorage.ACCOUNT_ROLE)
        secureStorage.remove(SecureStorage.ACCOUNT_VERIFICATION_STATUS)
        secureStorage.remove(SecureStorage.ACCOUNT_PHONE)
        secureStorage.remove(SecureStorage.ACCOUNT_DISPLAY_NAME)
        secureStorage.remove(SecureStorage.ACCOUNT_AREA_NAME)
        secureStorage.remove(SecureStorage.ACCOUNT_LANGUAGE)
        secureStorage.remove(SecureStorage.ACCOUNT_PROFILE_ID)
        secureStorage.remove(SecureStorage.ACCOUNT_LATITUDE)
        secureStorage.remove(SecureStorage.ACCOUNT_LONGITUDE)
        secureStorage.remove(SecureStorage.COLLECTOR_ID)
        secureStorage.remove(SecureStorage.SYNC_CURSOR)
        secureStorage.remove(SecureStorage.ACTIVITY_CURSOR)
    }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
}

private fun String.toDisplayMaterial() = when (this) {
    "LCD_PANEL" -> "LCD Panel"
    "PCB" -> "PCB / Circuit Board"
    "CABLE" -> "Cables"
    else -> replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun String.toLocalLotStatus() = when (this) {
    "PAID" -> LotStatus.PAID
    "CANCELLED" -> LotStatus.CANCELLED
    "DISPUTED" -> LotStatus.DISPUTED
    "COLLECTOR_CONFIRMED" -> LotStatus.COLLECTOR_CONFIRMED
    "RECYCLER_CONFIRMED", "HANDED_OVER" -> LotStatus.HANDED_OVER
    "QUOTE_RECEIVED" -> LotStatus.QUOTE_RECEIVED
    "QUOTE_REQUESTED" -> LotStatus.LOCKED
    else -> LotStatus.SAVED
}

private fun parseRemoteTimestamp(value: String): Long? {
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd")
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        }.getOrNull()
    }
}
