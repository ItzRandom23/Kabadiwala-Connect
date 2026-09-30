package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.ui.util.AccountFeatureScopeViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.*
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.ui.util.accountFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerOrdersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanViewModel
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import retrofit2.Response
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real ViewModels/navigation/Compose with deterministic slow API responses. */
class LiveScreenStateTest {
    @get:Rule val compose = createComposeRule()
    private val scheduled = PickupRequestDto(id = "pickup-live", listingId = "listing-live", householdId = "household-live",
        kabadiwalaId = "collector-live", status = "SCHEDULED", finalCategory = "PLASTIC")

    private class Backend(val scheduled: PickupRequestDto) {
        @Volatile var stallReads = false
        @Volatile var stallAssigned = false
        @Volatile var failCompletion = false
        @Volatile var holdCompletion = false
        @Volatile var pendingCompletion: Continuation<Any?>? = null
        @Volatile var history: List<PickupRequestDto> = emptyList()
        @Volatile var completionCalls = 0
        val held = CopyOnWriteArrayList<Continuation<Any?>>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            if (method.name == "completePickup") {
                completionCalls++
                if (failCompletion) throw IOException("fixture unavailable")
                stallReads = true
                if (holdCompletion) {
                    @Suppress("UNCHECKED_CAST")
                    pendingCompletion = args!!.last() as Continuation<Any?>
                    COROUTINE_SUSPENDED
                } else Response.success(ApiEnvelope(true, scheduled.copy(status = "COMPLETED", actualWeight = 2.0, ratePerKg = 10.0, finalAmount = 20.0)))
            } else if (method.name == "getKabadiwalaPickups") {
                val scope = args?.get(2) as? String
                if (stallReads || (stallAssigned && scope == "assigned")) {
                    @Suppress("UNCHECKED_CAST")
                    held += args!!.last() as Continuation<Any?>
                    COROUTINE_SUSPENDED
                } else {
                    Response.success(ApiEnvelope(true, when (scope) { "assigned" -> listOf(scheduled); "history" -> history; else -> emptyList() }))
                }
            } else if (method.name == "getKabadiwalaListings") {
                Response.success(ApiEnvelope(true, emptyList<HouseholdListingDto>()))
            } else throw IOException("optional fixture endpoint unavailable")
        } as ApiService
    }

    @Test fun completedPickupRemainsCompletedAcrossHomeAndPickupsWhileRefreshIsStalled() {
        val backend = Backend(scheduled)
        val instances = CopyOnWriteArrayList<SupplyChainViewModel>()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SupplyChainViewModel(backend.api,
                roleProvider = { AccountRole.COLLECTOR }, accountIdProvider = { "collector-live" }) as T
        }
        compose.setContent { KabadiwalaConnectTheme {
            val owner: AccountFeatureScopeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            owner.useAccount("collector-live:COLLECTOR")
            val nav = rememberNavController()
            NavHost(nav, "home") {
                listOf("home", "pickups").forEach { route -> composable(route) {
                    val vm: SupplyChainViewModel = accountFeatureViewModel(owner, "collector-live", factory)
                    if (!instances.contains(vm)) instances += vm
                    val state by vm.state.collectAsStateWithLifecycle()
                    Column {
                        Button(onClick = { vm.completePickup(scheduled.id, PickupCompletionDto(2.0, "PLASTIC", ratePerKg = 10.0)) }) { Text("Finish fixture pickup") }
                        Button(onClick = { nav.navigate("pickups") }) { Text("Open fixture pickups") }
                        KabadiwalaSupplyScreen(state, if (route == "home") KabadiwalaSection.HOME else KabadiwalaSection.PICKUPS,
                            vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup,
                            vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, currentCollectorId = "collector-live")
                    }
                } }
            }
        } }
        compose.waitUntil(5_000) { instances.firstOrNull()?.state?.value?.pickups?.isNotEmpty() == true }
        compose.onNodeWithText("Finish fixture pickup").performClick()
        compose.waitUntil(5_000) { instances[0].state.value.pickups.firstOrNull()?.status == "COMPLETED" }
        compose.onNodeWithText("Open fixture pickups").performClick()
        compose.onNodeWithText("Completed").assertExists()
        assertEquals(1, instances.size)
        assertEquals(20.0, instances[0].state.value.pickups.first().finalAmount!!, 0.0)
        assertTrue(backend.held.isNotEmpty())
        assertEquals(1, backend.completionCalls)
    }

    @Test fun historyPublishesBeforeAnUnrelatedAssignedPickupResponse() {
        val backend = Backend(scheduled).apply { stallAssigned = true; history = listOf(scheduled.copy(status = "COMPLETED")) }
        lateinit var vm: SupplyChainViewModel
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupplyChainViewModel(backend.api, roleProvider = { AccountRole.COLLECTOR }, accountIdProvider = { "collector-live" }) as T
            })
        }
        compose.runOnIdle { vm.refreshKabadiwala() }
        compose.waitUntil(5_000) { vm.state.value.pickups.any { it.status == "COMPLETED" } }
        assertTrue(backend.held.isNotEmpty())
    }

    @Test fun failedCompletionKeepsTheConfirmedScheduledStatus() {
        val backend = Backend(scheduled).apply { failCompletion = true }
        lateinit var vm: SupplyChainViewModel
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupplyChainViewModel(backend.api, roleProvider = { AccountRole.COLLECTOR }, accountIdProvider = { "collector-live" }) as T
            })
        }
        compose.runOnIdle { vm.refreshKabadiwala() }
        compose.waitUntil(5_000) { vm.state.value.pickups.isNotEmpty() }
        compose.runOnIdle {
            vm.completePickup(scheduled.id, PickupCompletionDto(2.0, "PLASTIC", ratePerKg = 10.0))
        }
        compose.waitUntil(5_000) { "complete-${scheduled.id}" !in vm.state.value.busy }
        assertEquals("SCHEDULED", vm.state.value.pickups.first().status)
        assertNotNull(vm.state.value.error)
    }

    @Test fun slowCompletionAcknowledgesImmediatelyAndPreventsDuplicateSubmission() {
        val backend = Backend(scheduled).apply { holdCompletion = true }
        lateinit var vm: SupplyChainViewModel
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupplyChainViewModel(backend.api, roleProvider = { AccountRole.COLLECTOR }, accountIdProvider = { "collector-live" }) as T
            })
        }
        compose.runOnIdle { vm.refreshKabadiwala() }
        compose.waitUntil(5_000) { vm.state.value.pickups.isNotEmpty() }
        compose.runOnIdle {
            vm.completePickup(scheduled.id, PickupCompletionDto(2.0, "PLASTIC", ratePerKg = 10.0))
            assertTrue("complete-${scheduled.id}" in vm.state.value.busy)
            assertEquals("SCHEDULED", vm.state.value.pickups.first().status)
            vm.completePickup(scheduled.id, PickupCompletionDto(2.0, "PLASTIC", ratePerKg = 10.0))
            assertEquals(1, backend.completionCalls)
            backend.pendingCompletion!!.resumeWith(Result.success(Response.success(ApiEnvelope(true, scheduled.copy(status = "COMPLETED")))))
        }
        compose.waitUntil(5_000) { vm.state.value.pickups.first().status == "COMPLETED" }
    }

    @Test fun oldReadCannotRestoreScheduledAfterCompletion() {
        val backend = Backend(scheduled)
        lateinit var vm: SupplyChainViewModel
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupplyChainViewModel(backend.api, roleProvider = { AccountRole.COLLECTOR }, accountIdProvider = { "collector-live" }) as T
            })
        }
        compose.runOnIdle { vm.refreshKabadiwala() }
        compose.waitUntil(5_000) { vm.state.value.pickups.isNotEmpty() && !vm.state.value.loading }
        compose.runOnIdle { backend.stallAssigned = true; vm.refreshKabadiwala() }
        compose.waitUntil(5_000) { backend.held.isNotEmpty() }
        val olderReads = backend.held.toList()
        compose.runOnIdle { vm.completePickup(scheduled.id, PickupCompletionDto(2.0, "PLASTIC", ratePerKg = 10.0)) }
        compose.waitUntil(5_000) { vm.state.value.pickups.first().status == "COMPLETED" }
        compose.runOnIdle {
            olderReads.forEach { it.resumeWith(Result.success(Response.success(ApiEnvelope(true, listOf(scheduled))))) }
        }
        compose.runOnIdle { assertEquals("COMPLETED", vm.state.value.pickups.first().status) }
    }

    @Test fun scannerConfirmationUpdatesOrdersWithoutWaitingForARefresh() {
        val prepared = SupplyHandoverDto(id = "handover-live", recyclerId = "recycler-live", status = "COLLECTOR_CONFIRMED", qrCodeData = "kc-supply-handover-v1.fixture")
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, _ ->
            when (method.name) {
                "getSupplyHandovers" -> Response.success(ApiEnvelope(true, listOf(prepared)))
                "confirmSupplyHandover" -> Response.success(ApiEnvelope(true, prepared.copy(status = "COMPLETED", finalAcceptedKg = 2.0)))
                else -> throw IOException("unexpected fixture endpoint")
            }
        } as ApiService
        lateinit var orders: RecyclerOrdersViewModel
        lateinit var scan: RecyclerScanViewModel
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                (if (modelClass == RecyclerOrdersViewModel::class.java) RecyclerOrdersViewModel(api, accountIdProvider = { "recycler-live" })
                else RecyclerScanViewModel(api, accountIdProvider = { "recycler-live" })) as T
        }
        compose.setContent {
            orders = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
            scan = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
        }
        compose.runOnIdle { orders.refresh(); scan.verify(prepared.qrCodeData!!) }
        compose.waitUntil(5_000) { scan.state.value.supplyVerified != null && orders.state.value.handovers.isNotEmpty() }
        compose.runOnIdle { scan.confirm(2.0, true, null) }
        compose.waitUntil(5_000) { orders.state.value.handovers.first().status == "COMPLETED" }
        assertEquals(2.0, orders.state.value.handovers.first().finalAcceptedKg!!, 0.0)
    }
}
