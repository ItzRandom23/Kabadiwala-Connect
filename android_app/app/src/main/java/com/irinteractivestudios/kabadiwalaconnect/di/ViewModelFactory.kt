package com.irinteractivestudios.kabadiwalaconnect.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices.PricesViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers.RecyclersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.PrefsLanguageStore
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.PrefsAppearanceStore
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotManagementViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerMarketplaceViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerOrdersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerProfileViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.FutureFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions.TransactionTimelineViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin.AdminConsoleViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainViewModel
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.util.PriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.AndroidLocationProvider
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.HandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation

/**
 * Builds screen ViewModels from the [AppContainer].
 * Keeps Android framework types out of the ViewModels that don't need them.
 */
class KcViewModelFactory(
    private val app: Application,
    private val container: AppContainer,
    private val initialCollectorId: String? = null
) : ViewModelProvider.Factory {
    val lots: LotRepository get() = container.lotRepository
    val lotWriter: LotWriter get() = container.lotWriter
    val priceSpeaker: PriceSpeaker get() = container.priceSpeaker
    val priceCatalog: PriceCatalogRepository get() = container.priceRepository as PriceCatalogRepository
    val recyclers: RecyclerRepository get() = container.recyclerRepository
    val recyclerCatalog: RecyclerCatalogRepository? get() = container.recyclerRepository as? RecyclerCatalogRepository
    val quoteRepository: QuoteRepository get() = container.quoteRepository
    val handoverRepository: HandoverRepository get() = container.handoverRepository
    val paymentRepository: PaymentRepository get() = container.paymentRepository
    val disputeRepository: com.irinteractivestudios.kabadiwalaconnect.data.repository.DisputeRepository get() = container.disputeRepository
    val apiService get() = container.apiService
    val syncQueue get() = container.database.syncQueueDao()
    fun requestSync() = container.syncScheduler.requestSync()
    suspend fun resetSyncItem(uid: Long) = container.database.syncQueueDao().resetForRetry(uid)
    val currentAccount: AccountProfile? get() = container.currentAccount()
    fun updateStoredAccountLanguage(tag: String) = container.updateStoredAccountLanguage(tag)
    suspend fun refreshCatalogs(location: String? = null, current: CurrentLocation? = null, force: Boolean = false) =
        container.refreshCatalogs(location, current?.latitude, current?.longitude, force)
    suspend fun refreshEarnings() = container.refreshEarnings()
    suspend fun refreshAccount() = container.refreshAccount()
    suspend fun refreshActivity() = container.refreshActivity()
    suspend fun exportAccount() = container.authenticationRepository.exportAccount()
    suspend fun deleteAccount() = container.authenticationRepository.deleteAccount()
    suspend fun clearAccount() = container.clearAccount()

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.lotRepository, container.connectivityObserver, container.database.syncQueueDao()) { container.currentAccount()?.profileId.orEmpty() }
        modelClass.isAssignableFrom(PricesViewModel::class.java) ->
            PricesViewModel(container.priceRepository, container.connectivityObserver)
        modelClass.isAssignableFrom(RecyclersViewModel::class.java) ->
            RecyclersViewModel(container.recyclerRepository, container.connectivityObserver, container.apiService)
        modelClass.isAssignableFrom(EarningsViewModel::class.java) ->
            EarningsViewModel(container.earningsRepository)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(PrefsLanguageStore(app), appVersionOf(app), PrefsAppearanceStore(app))
        modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
            OnboardingViewModel(
                auth = container.authenticationRepository,
                profiles = container.collectorProfileRepository,
                secureStorage = container.secureStorage,
                initialLanguage = LocaleManager.persistedTag(app),
                locationProvider = AndroidLocationProvider(app)
            )
        modelClass.isAssignableFrom(LotManagementViewModel::class.java) ->
            LotManagementViewModel(container.lotWriter, currentCollectorId(), container.apiService, priceCatalog)
        modelClass.isAssignableFrom(RecyclerMarketplaceViewModel::class.java) ->
            RecyclerMarketplaceViewModel(container.apiService)
        modelClass.isAssignableFrom(RecyclerOrdersViewModel::class.java) ->
            RecyclerOrdersViewModel(container.apiService, FormalisationCacheStore(app), { container.currentAccount()?.profileId })
        modelClass.isAssignableFrom(RecyclerScanViewModel::class.java) ->
            RecyclerScanViewModel(container.apiService, container.database.syncQueueDao(), FormalisationCacheStore(app), { container.currentAccount()?.profileId }, { container.syncScheduler.requestSync() }, IdempotencyKeyStore(app) { container.currentAccount()?.profileId })
        modelClass.isAssignableFrom(RecyclerProfileViewModel::class.java) ->
            RecyclerProfileViewModel(container.apiService)
        modelClass.isAssignableFrom(FutureFeatureViewModel::class.java) ->
            FutureFeatureViewModel(container.apiService, FutureCacheStore(container.database.futureCacheDao()), container.database.syncQueueDao(), { container.syncScheduler.requestSync() }) { container.currentAccount()?.profileId }
        modelClass.isAssignableFrom(TransactionTimelineViewModel::class.java) ->
            TransactionTimelineViewModel(container.apiService)
        modelClass.isAssignableFrom(SupplyChainViewModel::class.java) ->
            SupplyChainViewModel(container.apiService, { container.currentAccount()?.role }, FormalisationCacheStore(app), { container.currentAccount()?.profileId }, IdempotencyKeyStore(app) { container.currentAccount()?.profileId }, container.database.syncQueueDao(), { container.syncScheduler.requestSync() }, container.database.pendingPhotoUploadDao(), container.database.householdListingCacheDao(), { container.isAuthenticatedBackgroundWorkReady() })
        modelClass.isAssignableFrom(AdminConsoleViewModel::class.java) ->
            AdminConsoleViewModel(container.apiService)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.simpleName}")
    } as T

    private fun currentCollectorId(): String =
        container.secureStorage.get(com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage.COLLECTOR_ID)
            ?: initialCollectorId
            ?: "local-collector"

    private fun appVersionOf(app: Application): String = try {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        @Suppress("DEPRECATION")
        info.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
