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
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.FutureFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
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

/**
 * Builds the Phase 1 ViewModels from the [AppContainer].
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
    val currentAccount: AccountProfile? get() = container.currentAccount()

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.lotRepository, container.connectivityObserver)
        modelClass.isAssignableFrom(PricesViewModel::class.java) ->
            PricesViewModel(container.priceRepository, container.connectivityObserver)
        modelClass.isAssignableFrom(RecyclersViewModel::class.java) ->
            RecyclersViewModel(container.recyclerRepository, container.connectivityObserver)
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
            LotManagementViewModel(container.lotWriter, currentCollectorId(), container.apiService)
        modelClass.isAssignableFrom(RecyclerMarketplaceViewModel::class.java) ->
            RecyclerMarketplaceViewModel(container.apiService)
        modelClass.isAssignableFrom(RecyclerOrdersViewModel::class.java) ->
            RecyclerOrdersViewModel(container.apiService)
        modelClass.isAssignableFrom(FutureFeatureViewModel::class.java) ->
            FutureFeatureViewModel(container.apiService, FutureCacheStore(container.database.futureCacheDao()))
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
