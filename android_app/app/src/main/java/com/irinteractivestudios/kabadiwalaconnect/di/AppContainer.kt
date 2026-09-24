package com.irinteractivestudios.kabadiwalaconnect.di

import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import android.content.Context
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomLotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomPriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomRecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomQuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RemoteQuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AccountProfileUpdate
import com.irinteractivestudios.kabadiwalaconnect.data.auth.AuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockAuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.MockOtpService
import com.irinteractivestudios.kabadiwalaconnect.data.auth.RemoteAuthenticationRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.RoomCollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SecureSessionRepository
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionCoordinator
import com.irinteractivestudios.kabadiwalaconnect.data.auth.CollectorProfileRepository
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RetrofitProvider
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PreferencesUpdateDto
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
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
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
import com.irinteractivestudios.kabadiwalaconnect.notifications.FcmTokenRegistrar
import com.irinteractivestudios.kabadiwalaconnect.data.remote.NotificationDeviceRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.auth.readAccount
import com.irinteractivestudios.kabadiwalaconnect.data.auth.saveAccount
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual service locator for the app's local and remote repositories.
 *
 * No DI framework is added on purpose: keeps the APK small and the
 * startup path simple on entry-level devices. ViewModels receive what
 * they need from here via a ViewModelProvider.Factory in MainActivity.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogRefreshMutex = Mutex()
    private val authenticatedBackgroundWorkReady = AtomicBoolean(false)
    private var lastCatalogRefreshKey: String? = null
    private var lastCatalogRefreshElapsedMs: Long = 0L

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val sessionCoordinator = SessionCoordinator()

    /** Uses the configured backend and injects the current encrypted bearer token. */
    val apiService: ApiService by lazy {
        RetrofitProvider.create(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { secureStorage.get(SecureStorage.AUTH_TOKEN) },
            tokenRefresher = { runBlocking { authenticationRepository.refreshAccessToken(force = true) } },
            onAuthenticationFailure = { failedToken -> expireAccountSessionIfCurrentToken(failedToken) }
        )
    }

    val lotRepository: LotRepository by lazy { RoomLotRepository(database.lotDao(), database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId } }
    val lotWriter: LotWriter by lazy { lotRepository as LotWriter }
    val priceRepository: PriceRepository by lazy { RoomPriceRepository(database.priceDao()) }
    val priceSpeaker: PriceSpeaker by lazy { AndroidPriceSpeaker(appContext) }
    val recyclerRepository: RecyclerRepository by lazy { RoomRecyclerRepository(database.recyclerDao()) }
    val quoteRepository: QuoteRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) RoomQuoteRepository(database.quoteDao()) { currentAccount()?.profileId }
        else RemoteQuoteRepository(database.quoteDao(), apiService, database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId }
    }
    val handoverRepository: HandoverRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) RoomHandoverRepository(database.handoverDao()) { currentAccount()?.profileId }
        else OfflineFirstHandoverRepository(
            RoomHandoverRepository(database.handoverDao()) { currentAccount()?.profileId },
            RemoteHandoverRepository(database.handoverDao(), apiService) { currentAccount()?.profileId },
            database.syncQueueDao()
        ) { syncScheduler.requestSync() }
    }
    val paymentRepository: PaymentRepository by lazy { RoomPaymentRepository(database.paymentDao(), database.syncQueueDao(), { syncScheduler.requestSync() }) { currentAccount()?.profileId } }
    val earningsRepository: EarningsRepository get() = paymentRepository
    val disputeRepository: com.irinteractivestudios.kabadiwalaconnect.data.repository.DisputeRepository by lazy { RoomDisputeRepository(database.disputeDao()) { currentAccount()?.profileId } }

    val connectivityObserver: ConnectivityObserver by lazy {
        SystemConnectivityObserver(appContext)
    }

    val secureStorage: SecureStorage by lazy { KeystoreSecureStorage(appContext) }

    val sessionRepository by lazy {
        SecureSessionRepository(secureStorage) { token ->
            // The local placeholder backend is deliberately offline and uses
            // opaque mock credentials. Every configured real backend issues a
            // JWT, so malformed cached credentials must never unlock protected
            // navigation or background work.
            (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) || token.isJwtShape()
        }
    }
    val authenticationRepository: AuthenticationRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) {
            MockAuthenticationRepository(MockOtpService(), sessionRepository, secureStorage)
        } else {
            RemoteAuthenticationRepository(apiService, sessionRepository, secureStorage)
        }
    }
    val collectorProfileRepository: CollectorProfileRepository by lazy { RoomCollectorProfileRepository(database.collectorProfileDao()) }

    fun hasValidSession(): Boolean = sessionRepository.isSessionValid()
    /** A refresh credential alone is not an authenticated session. */
    fun hasRestorableSession(): Boolean = hasValidSession() && currentAccount() != null

    /**
     * Process-local gate for work that can send an authenticated request.
     * Cached credentials are not enough: MainActivity must finish session
     * restoration (or a fresh sign-in) before WorkManager and FCM registration
     * are allowed to run.
     */
    fun isAuthenticatedBackgroundWorkReady(): Boolean = authenticatedBackgroundWorkReady.get()

    fun markAuthenticatedBackgroundWorkReady(account: AccountProfile?) {
        authenticatedBackgroundWorkReady.set(
            account != null && hasValidSession() && currentAccount()?.profileId == account.profileId
        )
        if (account != null && isAuthenticatedBackgroundWorkReady()) sessionCoordinator.authenticated(account)
        else sessionCoordinator.unauthenticated()
    }

    fun revokeAuthenticatedBackgroundWork() {
        authenticatedBackgroundWorkReady.set(false)
    }

    /**
     * Resolves the account boundary before protected destinations are composed.
     * A refresh token is never treated as permission to render a protected
     * screen; it must first produce a valid access token and account profile.
     */
    suspend fun restoreAuthenticatedSession(): AccountProfile? {
        sessionCoordinator.beginRestoration()
        revokeAuthenticatedBackgroundWork()
        if (!hasValidSession()) {
            if (secureStorage.get(SecureStorage.REFRESH_TOKEN).isNullOrBlank()) {
                sessionCoordinator.unauthenticated()
                return null
            }
            return authenticationRepository.refreshAccount().also {
                if (it == null) {
                    // A rotating refresh credential that the backend has
                    // rejected is no longer a recoverable session. Remove
                    // the cached identity as well as the token so a stale
                    // profile cannot keep WorkManager looking for an account
                    // that is no longer authenticated. Room/outbox data is
                    // intentionally retained and remains keyed to its owner
                    // for recovery after a fresh sign-in.
                    if (!hasValidSession() && secureStorage.get(SecureStorage.REFRESH_TOKEN).isNullOrBlank()) {
                        expireAccountSession()
                    } else {
                        sessionCoordinator.unauthenticated()
                    }
                }
                markAuthenticatedBackgroundWorkReady(it)
            }
        }
        return (currentAccount() ?: authenticationRepository.refreshAccount()).also {
            if (it == null) sessionCoordinator.unauthenticated()
            markAuthenticatedBackgroundWorkReady(it)
        }
    }

    fun currentAccount(): AccountProfile? = secureStorage.readAccount()

    /**
     * Stores a provider token before authentication if necessary, then retries
     * registration once a server session exists. The token is not an auth
     * credential and is never sent anywhere except the authenticated backend.
     */
    fun queuePushToken(token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) return
        secureStorage.put(SecureStorage.PUSH_TOKEN, normalized)
        secureStorage.put(SecureStorage.PENDING_PUSH_TOKEN, normalized)
        registerPendingPushToken()
    }

    fun startPushTokenRegistration() {
        if (!isAuthenticatedBackgroundWorkReady() || !hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return
        FcmTokenRegistrar.fetchToken(appContext) { token -> queuePushToken(token) }
        registerPendingPushToken()
    }

    fun registerPendingPushToken() {
        if (!isAuthenticatedBackgroundWorkReady() || !hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return
        val accountId = currentAccount()?.profileId ?: return
        val token = secureStorage.get(SecureStorage.PENDING_PUSH_TOKEN) ?: return
        preferenceScope.launch {
            runCatching {
                apiService.registerNotificationDevice(
                    NotificationDeviceRequestDto(
                        token = token,
                        platform = "ANDROID",
                        appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                    )
                ).requireData()
            }.onSuccess {
                if (currentAccount()?.profileId == accountId && secureStorage.get(SecureStorage.PENDING_PUSH_TOKEN) == token) {
                    secureStorage.remove(SecureStorage.PENDING_PUSH_TOKEN)
                }
            }
        }
    }

    suspend fun unregisterCurrentPushToken(): Boolean {
        val token = secureStorage.get(SecureStorage.PUSH_TOKEN) ?: secureStorage.get(SecureStorage.PENDING_PUSH_TOKEN) ?: return true
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return false
        return runCatching {
            apiService.unregisterNotificationDevice(NotificationDeviceRequestDto(token = token)).requireData()
            secureStorage.remove(SecureStorage.PUSH_TOKEN)
            secureStorage.remove(SecureStorage.PENDING_PUSH_TOKEN)
        }.isSuccess
    }

    /** Keeps the cached account snapshot aligned with the app language setting. */
    fun updateStoredAccountLanguage(tag: String) {
        val normalized = LocaleManager.normalizeTag(tag)
        val accountId = currentAccount()?.profileId ?: return
        currentAccount()?.let { secureStorage.saveAccount(it.copy(preferredLanguage = normalized)) }

        // Keep the account preference in sync when the backend is available,
        // while preserving the offline-first behaviour of the picker.
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return
        preferenceScope.launch {
            runCatching {
                apiService.updatePreferences(
                    PreferencesUpdateDto(preferredLanguage = LocaleManager.toBackendName(normalized))
                ).requireData()
            }.onSuccess {
                // Do not let a late response update a different account after
                // logout/login on a shared device.
                currentAccount()
                    ?.takeIf { it.profileId == accountId }
                    ?.let { secureStorage.saveAccount(it.copy(preferredLanguage = normalized)) }
            }
        }
    }

    suspend fun refreshAccount(): AccountProfile? = authenticationRepository.refreshAccount()

    suspend fun updateAccountProfile(update: AccountProfileUpdate): AccountProfile? =
        authenticationRepository.updateAccountProfile(update)

    suspend fun refreshCatalogs(location: String? = null, latitude: Double? = null, longitude: Double? = null, force: Boolean = false) = catalogRefreshMutex.withLock {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return@withLock
        val account = currentAccount()
        // Recycler sessions have their own operational endpoints. Household
        // sessions may read the public price and recycler catalogues, but must
        // never enter collector-only lot/payment sync below.
        if (account?.role == AccountRole.RECYCLER) return@withLock
        if (account == null) return@withLock
        val resolvedLocation = location?.trim()?.takeIf { it.isNotBlank() }
            ?: account.areaName?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withLock
        val refreshKey = listOf(account.profileId, resolvedLocation, latitude, longitude).joinToString("|")
        val refreshStartedAt = android.os.SystemClock.elapsedRealtime()
        if (!force && refreshKey == lastCatalogRefreshKey && refreshStartedAt - lastCatalogRefreshElapsedMs < 30_000L) {
            return@withLock
        }
        val categories = listOf("CRT", "LCD_PANEL", "PCB", "CABLE", "COPPER", "BATTERY", "MOTOR", "MAGNET", "PLASTIC", "OTHER")
        val boardResults = coroutineScope {
            categories.map { category -> async {
                category to runCatching { apiService.getPriceBoard(category, resolvedLocation).requireData() }
            } }.awaitAll()
        }
        val prices = boardResults.mapNotNull { (category, result) ->
            result.getOrNull()?.takeIf { it.available && it.marketPrice != null }?.let { board ->
                runCatching {
                val marketPrice = board.marketPrice ?: return@let null
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
                    location = resolvedLocation,
                    materialLabel = category.toDisplayMaterial(),
                    ratePerKg = marketPrice,
                    minRatePerKg = board.priceMin ?: marketPrice,
                    maxRatePerKg = board.priceMax ?: marketPrice,
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
                }.getOrNull()
            }
        }
        // An authoritative empty response removes outdated local rates; a
        // network failure leaves the existing offline cache untouched.
        if (boardResults.all { it.second.isSuccess }) database.priceDao().replaceLocation(resolvedLocation, prices)
        val recyclers = runCatching { apiService.getRecyclers(resolvedLocation, 50, null, null, "proximity", 1, 100, latitude ?: account.latitude, longitude ?: account.longitude).requireData() }.getOrNull()?.items.orEmpty().map { recycler ->
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
        // A successful empty response is authoritative too. Keeping the old
        // rows here made a real “no verified recyclers in this area” result
        // look like cached availability.
        database.recyclerDao().replaceAll(recyclers)

        lastCatalogRefreshKey = refreshKey
        lastCatalogRefreshElapsedMs = android.os.SystemClock.elapsedRealtime()

        if (account.role == AccountRole.HOUSEHOLD) return@withLock

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
                    locationLatitude = lot.collectionLocation?.latitude,
                    locationLongitude = lot.collectionLocation?.longitude,
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
        if (currentAccount()?.role != AccountRole.COLLECTOR) return false
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
        if (response.notifications.isNotEmpty() && accountId != null && currentAccount()?.role == AccountRole.COLLECTOR) {
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
        if (currentAccount()?.role != AccountRole.COLLECTOR) return false
        val accountId = currentAccount()?.profileId ?: return false
        val cursor = secureStorage.get(SecureStorage.SYNC_CURSOR)
        val payload = apiService.getChanges(cursor).requireData()
        database.withTransaction {
            payload.changes.lots.forEach { remote ->
                // Deltas are expected to be server-scoped, but keep the local
                // account boundary defensive if a cursor is stale or a backend
                // regression returns another collector's record.
                if (remote.collectorId != accountId) return@forEach
                val local = database.lotDao().findByIdForCollector(remote.id, accountId)
                if (local == null || local.synced) {
                    val now = System.currentTimeMillis()
                    database.lotDao().save(
                        LotEntity(
                            id = remote.id,
                            collectorId = remote.collectorId,
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
                            locationLatitude = remote.collectionLocation?.latitude,
                            locationLongitude = remote.collectionLocation?.longitude,
                            serverUpdatedAtEpochMs = remote.updatedAt?.let(::parseRemoteTimestamp),
                            version = remote.version
                        )
                    )
                }
            }
            payload.changes.payments.forEach { remote ->
                val local = database.paymentDao().findByIdForAccount(remote.id, accountId)
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
                // This delta endpoint is collector-scoped. Require an explicit
                // owner instead of accepting an incomplete DTO and attaching
                // it to the current account as a fallback.
                if (remote.collectorId != accountId) return@forEach
                val local = database.handoverDao().getForAccount(remote.id, accountId)
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
        revokeAuthenticatedBackgroundWork()
        sessionCoordinator.unauthenticated()
        val accountId = currentAccount()?.profileId
        IdempotencyKeyStore(appContext).clearAccount(accountId)
        FormalisationCacheStore(appContext).clear(accountId)
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
            database.pendingPhotoUploadDao().clearAll()
            database.householdListingCacheDao().clearAll()
        }
        withContext(Dispatchers.IO) {
            File(appContext.filesDir, "lot_photos").deleteRecursively()
            File(appContext.filesDir, "handover_photos").deleteRecursively()
            File(appContext.filesDir, "household_photos").deleteRecursively()
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
        secureStorage.remove(SecureStorage.ACCOUNT_PERMISSIONS)
        secureStorage.remove(SecureStorage.COLLECTOR_ID)
        secureStorage.remove(SecureStorage.SYNC_CURSOR)
        secureStorage.remove(SecureStorage.ACTIVITY_CURSOR)
        secureStorage.remove(SecureStorage.PENDING_PUSH_TOKEN)
    }

    /**
     * Ends an invalid session without deleting durable local work.
     *
     * Token expiry/revocation is recoverable by signing in again. Clearing
     * Room here would destroy an offline lot or formal receipt captured just
     * before the network recovered. Explicit logout still uses clearAccount()
     * as the shared-device privacy boundary.
     */
    fun expireAccountSession() {
        revokeAuthenticatedBackgroundWork()
        sessionCoordinator.expired()
        secureStorage.remove(SecureStorage.AUTH_TOKEN)
        secureStorage.remove(SecureStorage.REFRESH_TOKEN)
        secureStorage.remove(SecureStorage.SESSION_EXPIRY)
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
        secureStorage.remove(SecureStorage.ACCOUNT_PERMISSIONS)
        secureStorage.remove(SecureStorage.COLLECTOR_ID)
        secureStorage.remove(SecureStorage.SYNC_CURSOR)
        secureStorage.remove(SecureStorage.ACTIVITY_CURSOR)
    }

    /**
     * An OkHttp response can arrive after the user has signed into another
     * account. Only expire the session if the rejected request belongs to the
     * currently stored credential (or there is no replacement credential).
     */
    fun expireAccountSessionIfCurrentToken(failedToken: String?) {
        val currentToken = secureStorage.get(SecureStorage.AUTH_TOKEN)
        if (!failedToken.isNullOrBlank() && !currentToken.isNullOrBlank() && currentToken != failedToken) return
        expireAccountSession()
    }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
}

private fun String.toDisplayMaterial() = when (this) {
    "LCD_PANEL" -> "LCD Panel"
    "PCB" -> "PCB / Circuit Board"
    "CABLE" -> "Cables"
    else -> replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun String.isJwtShape(): Boolean =
    split('.').let { parts -> parts.size == 3 && parts.all { it.isNotBlank() } }

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
