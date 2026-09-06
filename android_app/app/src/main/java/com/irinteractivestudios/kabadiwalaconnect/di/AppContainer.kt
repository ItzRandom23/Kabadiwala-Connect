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
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomPaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomDisputeRepository
import com.irinteractivestudios.kabadiwalaconnect.data.local.PriceEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.RecyclerEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.LotEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.PaymentEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual service locator (Phase 1).
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
        RetrofitProvider.create(BuildConfig.API_BASE_URL) { secureStorage.get(SecureStorage.AUTH_TOKEN) }
    }

    val lotRepository: LotRepository by lazy { RoomLotRepository(database.lotDao(), database.syncQueueDao()) { syncScheduler.requestSync() } }
    val lotWriter: LotWriter by lazy { lotRepository as LotWriter }
    val priceRepository: PriceRepository by lazy { RoomPriceRepository(database.priceDao()) }
    val priceSpeaker: PriceSpeaker by lazy { AndroidPriceSpeaker(appContext) }
    val recyclerRepository: RecyclerRepository by lazy { RoomRecyclerRepository(database.recyclerDao()) }
    val quoteRepository: QuoteRepository by lazy {
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL.contains(".invalid")) RoomQuoteRepository(database.quoteDao())
        else RemoteQuoteRepository(database.quoteDao(), apiService)
    }
    val handoverRepository: HandoverRepository by lazy { RoomHandoverRepository(database.handoverDao()) }
    val paymentRepository: PaymentRepository by lazy { RoomPaymentRepository(database.paymentDao(), database.syncQueueDao()) { syncScheduler.requestSync() } }
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

    fun currentAccount(): AccountProfile? = secureStorage.readAccount()

    suspend fun refreshAccount() { authenticationRepository.refreshAccount() }

    suspend fun refreshCatalogs(location: String = "Pune") {
        if (!hasValidSession() || BuildConfig.API_BASE_URL.contains(".invalid")) return
        val categories = listOf("CRT", "LCD_PANEL", "PCB", "CABLE", "BATTERY", "MOTOR", "MAGNET", "PLASTIC", "OTHER")
        val prices = categories.mapNotNull { category ->
            runCatching { apiService.getPriceBoard(category, location).requireData() }.getOrNull()?.let { board ->
                PriceEntity(
                    id = "${location}_$category",
                    location = board.location ?: location,
                    materialLabel = category.toDisplayMaterial(),
                    ratePerKg = board.marketPrice,
                    minRatePerKg = board.priceMin,
                    maxRatePerKg = board.priceMax,
                    updatedAtEpochMs = board.lastUpdated?.let(::parseRemoteTimestamp) ?: System.currentTimeMillis(),
                    trend = board.trend?.direction?.lowercase() ?: "stable",
                    historyCsv = ""
                )
            }
        }
        if (prices.isNotEmpty()) database.priceDao().replaceLocation(location, prices)
        val recyclers = runCatching { apiService.getRecyclers(location, 50, null, null, "proximity", 1, 100).requireData() }.getOrNull()?.items.orEmpty().map { recycler ->
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
            val unsyncedIds = database.lotDao().observeAll().first().filterNot { it.synced }.map { it.id }.toSet()
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
                    synced = true
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
                    syncState = PaymentSyncState.SAVED_LOCALLY.name,
                    recordState = if (payment.status == "DISPUTED") PaymentRecordState.DISCREPANCY.name else PaymentRecordState.NORMAL.name
                )
            })
        }
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
        }
        withContext(Dispatchers.IO) {
            File(appContext.filesDir, "lot_photos").deleteRecursively()
            File(appContext.filesDir, "handover_photos").deleteRecursively()
        }
        secureStorage.remove(SecureStorage.ACCOUNT_EMAIL)
        secureStorage.remove(SecureStorage.ACCOUNT_ROLE)
        secureStorage.remove(SecureStorage.ACCOUNT_VERIFICATION_STATUS)
        secureStorage.remove(SecureStorage.ACCOUNT_LANGUAGE)
        secureStorage.remove(SecureStorage.ACCOUNT_PROFILE_ID)
        secureStorage.remove(SecureStorage.COLLECTOR_ID)
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
    "CANCELLED", "DISPUTED" -> LotStatus.CANCELLED
    "COLLECTOR_CONFIRMED", "RECYCLER_CONFIRMED", "HANDED_OVER" -> LotStatus.COLLECTOR_CONFIRMED
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
