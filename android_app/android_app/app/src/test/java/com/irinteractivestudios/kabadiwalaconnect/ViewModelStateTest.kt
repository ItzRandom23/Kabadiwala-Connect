package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.repository.EarningsRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceRepository
import com.irinteractivestudios.kabadiwalaconnect.data.repository.RecyclerRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeData
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeNextAction
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices.PricesViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers.RecyclersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.LanguageStore
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsViewModel
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.FakeConnectivityObserver
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Shared fakes with settable data for ViewModel tests. */
private class TestLotRepository(initial: List<Lot> = emptyList()) : LotRepository {
    private val flow = MutableStateFlow(initial)
    override fun observeLots(): Flow<List<Lot>> = flow.asStateFlow()
    fun emit(value: List<Lot>) {
        flow.value = value
    }
}

private class TestPriceRepository(initial: List<Price> = emptyList()) : PriceRepository {
    private val flow = MutableStateFlow(initial)
    override fun observePrices(): Flow<List<Price>> = flow.asStateFlow()
}

private class TestRecyclerRepository(initial: List<Recycler> = emptyList()) :
    RecyclerRepository {
    private val flow = MutableStateFlow(initial)
    override fun observeRecyclers(): Flow<List<Recycler>> = flow.asStateFlow()
}

private class TestEarningsRepository(initial: EarningsSummary = EarningsSummary()) :
    EarningsRepository {
    private val flow = MutableStateFlow(initial)
    override fun observeSummary(): Flow<EarningsSummary> = flow.asStateFlow()
}

private class TestLanguageStore(var tag: String = "en") : LanguageStore {
    override fun load(): String = tag
    override fun save(tag: String) {
        this.tag = tag
    }
}

private fun sampleLot() = Lot(
    id = "lot-1",
    materialLabel = "Copper wire",
    weightKg = 2.5,
    createdAtEpochMs = 1L
)

/**
 * Verifies each tab's offline-first state mapping and that state is
 * retained on the ViewModel (rotation-safe: re-collecting yields the
 * same state without recomputation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun home_onlineEmptyCache_showsEmpty() = runTest(dispatcher) {
        val vm = HomeViewModel(TestLotRepository(), FakeConnectivityObserver(ConnectionState.ONLINE))
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun home_offlineNoCache_showsOffline() = runTest(dispatcher) {
        val vm = HomeViewModel(TestLotRepository(), FakeConnectivityObserver(ConnectionState.OFFLINE))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Offline)
    }

    @Test
    fun home_offlineWithCache_keepsCachedLots() = runTest(dispatcher) {
        val vm = HomeViewModel(
            TestLotRepository(listOf(sampleLot())),
            FakeConnectivityObserver(ConnectionState.OFFLINE)
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Offline)
        assertEquals(1, (state as UiState.Offline<HomeData>).cached?.lotCount)
    }

    @Test
    fun home_onlineWithLots_showsSuccess() = runTest(dispatcher) {
        val vm = HomeViewModel(
            TestLotRepository(listOf(sampleLot())),
            FakeConnectivityObserver(ConnectionState.ONLINE)
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(1, (state as UiState.Success<HomeData>).data.lotCount)
        assertEquals(HomeNextAction.FIND_RECYCLERS, (state as UiState.Success<HomeData>).data.nextAction)
    }

    @Test
    fun home_state_survivesRecollection() = runTest(dispatcher) {
        val vm = HomeViewModel(
            TestLotRepository(listOf(sampleLot())),
            FakeConnectivityObserver(ConnectionState.ONLINE)
        )
        advanceUntilIdle()
        val first = vm.uiState.value
        val second = vm.uiState.first()
        assertEquals(first, second)
    }

    @Test
    fun prices_offlineNoCache_showsOffline() = runTest(dispatcher) {
        val vm = PricesViewModel(TestPriceRepository(), FakeConnectivityObserver(ConnectionState.OFFLINE))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Offline)
    }

    @Test
    fun prices_onlineWithCache_showsSuccess() = runTest(dispatcher) {
        val price = Price(id = "p1", materialLabel = "Copper", ratePerKg = 550.0)
        val vm = PricesViewModel(
            TestPriceRepository(listOf(price)),
            FakeConnectivityObserver(ConnectionState.ONLINE)
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(price), (state as UiState.Success).data)
    }

    @Test
    fun recyclers_limitedConnection_showsOffline() = runTest(dispatcher) {
        val vm = RecyclersViewModel(
            TestRecyclerRepository(),
            FakeConnectivityObserver(ConnectionState.LIMITED)
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Offline)
    }

    @Test
    fun earnings_localSummary_worksWithoutNetwork() = runTest(dispatcher) {
        val vm = EarningsViewModel(TestEarningsRepository(EarningsSummary(1250.0, 300.0)))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(EarningsSummary(1250.0, 300.0), (state as UiState.Success).data)
    }

    @Test
    fun settings_language_persistsAndNormalizes() {
        val store = TestLanguageStore("en")
        val vm = SettingsViewModel(store, "1.0")
        assertEquals("en", vm.language.value)

        vm.setLanguage("hi")
        assertEquals("hi", vm.language.value)
        assertEquals("hi", store.tag)

        vm.setLanguage("xx")
        assertEquals("en", vm.language.value)
        assertEquals("en", store.tag)
    }

    @Test
    fun settings_loadsPersistedLanguage() {
        val vm = SettingsViewModel(TestLanguageStore("mr"), "1.0")
        assertEquals("mr", vm.language.value)
        assertEquals("1.0", vm.appVersion)
    }
}
