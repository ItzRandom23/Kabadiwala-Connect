package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.FutureFeatureViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.lang.reflect.Proxy
import retrofit2.Response
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureRefreshTest {
    @Test fun rewardsDoNotWaitForSchemesChatOrNotifications() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, _ ->
                calls += method.name
                check(method.name == "getRewards") { "Unrelated endpoint must not be requested" }
                Response.success(ApiEnvelope(true, listOf(RewardLedgerDto(id = "reward-1"))))
            } as ApiService
            val vm = FutureFeatureViewModel(api, accountId = { "collector-1" })
            vm.refreshRewards()
            runCurrent()
            assertEquals(listOf("getRewards"), calls)
            assertEquals("reward-1", vm.state.value.rewards.single().id)
            assertFalse(vm.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun activitiesRemainAvailableWithoutAConnection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, _, _ ->
                throw IllegalStateException("offline fixture")
            } as ApiService
            val vm = FutureFeatureViewModel(api, accountId = { "collector-1" })
            vm.refreshActivities()
            runCurrent()
            assertTrue(vm.state.value.activities.isNotEmpty())
            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }
}
