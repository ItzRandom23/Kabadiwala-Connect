package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.FutureFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.chatTimestamp
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.mergePending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReliabilityTest {
    @Test fun pendingLegacyTimestampSortsAfterOlderServerMessage() {
        val old = ChatMessageDto(id = "old", clientMessageId = "old", createdAt = "2026-01-01T00:00:00Z")
        val pending = ChatMessageDto(id = "pending", clientMessageId = "new", createdAt = "1767225601000", status = "QUEUED_OFFLINE")
        assertEquals(listOf("old", "pending"), listOf(old).mergePending(listOf(pending)).map { it.id })
        assertEquals(1767225601000L, chatTimestamp("2026-01-01T00:00:01Z"))
    }

    @Test fun acknowledgementReplacesPendingWithoutDuplicate() {
        val sent = ChatMessageDto(id = "server", clientMessageId = "same", createdAt = "2026-01-01T00:00:01Z", status = "SENT")
        val local = sent.copy(id = "local-same", status = "QUEUED_OFFLINE", createdAt = "1767225601000")
        assertEquals(listOf(sent), listOf(sent).mergePending(listOf(local)))
    }

    @Test fun refreshFailureExposesRecoveryAndClearsPendingState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, _, _ ->
                throw IllegalStateException("unavailable")
            } as ApiService
            val vm = FutureFeatureViewModel(api, accountId = { "household-1" })
            vm.loadMessages("pickup-chat")
            runCurrent()
            assertNotNull(vm.state.value.messagesRefreshErrors["pickup-chat"])
            assertTrue(vm.state.value.messagesLoading.isEmpty())
            vm.loadConversations()
            runCurrent()
            assertNotNull(vm.state.value.conversationsRefreshError)
            assertFalse(vm.state.value.conversationsLoading)
        } finally { Dispatchers.resetMain() }
    }
}
