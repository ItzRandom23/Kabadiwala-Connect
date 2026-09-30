package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanViewModel
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RecyclerScanViewModelTest {
    private fun api(block: suspend (String, Array<out Any?>) -> Any?): ApiService =
        Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            val arguments = args ?: emptyArray()
            @Suppress("UNCHECKED_CAST")
            val continuation = arguments.last() as Continuation<Any?>
            val call: suspend () -> Any? = { block(method.name, arguments) }
            call.startCoroutine(continuation)
            COROUTINE_SUSPENDED
        } as ApiService

    private fun response(id: String) = Response.success(ApiEnvelope(success = true,
        data = SupplyHandoverDto(id = id, qrCodeData = "kc-supply-handover-v1.$id", status = "COLLECTOR_CONFIRMED")))

    @Test fun resetCancelsOldVerificationAndNewQrWins() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val old = CompletableDeferred<Response<ApiEnvelope<SupplyHandoverDto>>>()
            val service = api { method, args ->
                assertEquals("verifySupplyHandover", method)
                if ((args[0] as VerifyHandoverRequestDto).qrCodeData.endsWith("old")) old.await() else response("new")
            }
            val vm = RecyclerScanViewModel(service, accountIdProvider = { "recycler" }, ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.verify("kc-supply-handover-v1.old")
            runCurrent()
            vm.reset()
            vm.verify("kc-supply-handover-v1.new")
            runCurrent()
            old.complete(response("old"))
            runCurrent()
            assertEquals("new", vm.state.value.supplyVerified?.id)
            assertFalse(vm.state.value.checking)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun resetAndOtherQrCannotInterruptConfirmation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val confirmation = CompletableDeferred<Response<ApiEnvelope<SupplyHandoverDto>>>()
            var confirms = 0
            var verifies = 0
            val service = api { method, _ ->
                if (method == "verifySupplyHandover") { verifies++; response("one") }
                else { assertEquals("confirmSupplyHandover", method); confirms++; confirmation.await() }
            }
            val vm = RecyclerScanViewModel(service, accountIdProvider = { "recycler" }, ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.verify("kc-supply-handover-v1.one")
            runCurrent()
            vm.confirm(1.0, true, null)
            runCurrent()
            vm.reset()
            vm.verify("kc-supply-handover-v1.two")
            vm.confirm(1.0, true, null)
            runCurrent()
            assertTrue(vm.state.value.confirming)
            assertEquals(1, verifies)
            assertEquals(1, confirms)
            confirmation.complete(response("one"))
            advanceUntilIdle()
            assertEquals("one", vm.state.value.supplyConfirmed?.id)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun changedAccountCannotReceiveOldQrResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var owner = "one"
            val pendingResponse = CompletableDeferred<Response<ApiEnvelope<SupplyHandoverDto>>>()
            val vm = RecyclerScanViewModel(api { _, _ -> pendingResponse.await() }, accountIdProvider = { owner }, ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.verify("kc-supply-handover-v1.one")
            runCurrent()
            owner = "two"
            pendingResponse.complete(response("one"))
            runCurrent()
            assertNull(vm.state.value.supplyVerified)
        } finally { Dispatchers.resetMain() }
    }
}
