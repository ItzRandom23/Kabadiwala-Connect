package com.irinteractivestudios.kabadiwalaconnect.ui.screens.future

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ChatMessageDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ChatDraftRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ConversationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DiyActivityDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DisputeAnalyticsDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.GovernmentSchemeDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RewardLedgerDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SendMessageRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.NotificationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity

data class FutureFeatureState(
    val loading: Boolean = false,
    val error: String? = null,
    val rewards: List<RewardLedgerDto> = emptyList(),
    val schemes: List<GovernmentSchemeDto> = emptyList(),
    val activities: List<DiyActivityDto> = emptyList(),
    val conversations: List<ConversationDto> = emptyList(),
    val messages: Map<String, List<ChatMessageDto>> = emptyMap(),
    val analytics: DisputeAnalyticsDto? = null,
    val notifications: List<NotificationDto> = emptyList(),
    val unreadNotifications: Int = 0,
    val sending: Boolean = false,
    val drafts: Map<String, String> = emptyMap(),
    val draftingConversationId: String? = null
)

class FutureFeatureViewModel(
    private val api: ApiService,
    private val cache: FutureCacheStore? = null,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: () -> Unit = {},
    private val accountId: () -> String? = { null }
) : ViewModel() {
    private val _state = MutableStateFlow(FutureFeatureState())
    val state: StateFlow<FutureFeatureState> = _state.asStateFlow()
    private var pollingJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            var failures = 0
            val cachedSchemes = cache?.schemes().orEmpty()
            val cachedActivities = cache?.activities().orEmpty()
            val schemes = runCatching { api.getGovernmentSchemes().requireData().also { cache?.saveSchemes(it) } }.onFailure { failures++ }.getOrDefault(_state.value.schemes.ifEmpty { cachedSchemes })
            val activities = runCatching { api.getDiyActivities().requireData().also { cache?.saveActivities(it) } }.onFailure { failures++ }.getOrDefault(_state.value.activities.ifEmpty { cachedActivities.ifEmpty { offlineActivities } })
            val rewards = runCatching { api.getRewards().requireData() }.onFailure { failures++ }.getOrDefault(_state.value.rewards)
            val conversations = runCatching { api.getConversations().requireData().also { cache?.saveConversations(it) } }.onFailure { failures++ }.getOrDefault(_state.value.conversations.ifEmpty { cachedConversations() })
            val analytics = runCatching { api.getDisputeAnalytics().requireData() }.onFailure { failures++ }.getOrNull() ?: _state.value.analytics
            val cachedNotifications = cache?.notifications(accountId()).orEmpty()
            val notifications = runCatching { api.getNotifications(limit = 100).requireData().also { cache?.saveNotifications(it, accountId()) } }.onFailure { failures++ }
                .getOrDefault(_state.value.notifications.ifEmpty { cachedNotifications })
            val unread = runCatching { api.getNotificationUnreadCount().requireData().count }.onFailure { failures++ }.getOrDefault(notifications.count { it.readAt.isNullOrBlank() })
            _state.value = _state.value.copy(loading = false, error = if (failures > 0) "Some information could not be refreshed. Cached data is shown." else null, schemes = schemes, activities = activities, rewards = rewards, conversations = conversations, analytics = analytics, notifications = notifications, unreadNotifications = unread)
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            val wasUnread = _state.value.notifications.firstOrNull { it.id == id }?.readAt.isNullOrBlank()
            val result = runCatching { api.markNotificationRead(id).requireData() }.getOrNull()
            if (result == null) {
                syncQueue?.enqueue(SyncQueueItemEntity(operation = "MARK_NOTIFICATION_READ", payloadJson = JsonObject().apply { addProperty("id", id) }.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = accountId()))
                requestSync()
            }
            if (_state.value.notifications.any { it.id == id }) {
                val updated = _state.value.notifications.map { if (it.id == id) it.copy(readAt = it.readAt ?: System.currentTimeMillis().toString()) else it }
                cache?.saveNotifications(updated, accountId())
                _state.value = _state.value.copy(notifications = updated, unreadNotifications = if (wasUnread) (_state.value.unreadNotifications - 1).coerceAtLeast(0) else _state.value.unreadNotifications)
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val result = runCatching { api.markAllNotificationsRead().requireData() }.getOrNull()
            if (result == null) {
                syncQueue?.enqueue(SyncQueueItemEntity(operation = "MARK_ALL_NOTIFICATIONS_READ", payloadJson = "{}", createdAtEpochMs = System.currentTimeMillis(), accountId = accountId()))
                requestSync()
            }
            if (result != null || _state.value.notifications.isNotEmpty()) {
                val updated = _state.value.notifications.map { it.copy(readAt = it.readAt ?: System.currentTimeMillis().toString()) }
                cache?.saveNotifications(updated, accountId())
                _state.value = _state.value.copy(notifications = updated, unreadNotifications = 0)
            }
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            val cached = cache?.messages(conversationId, accountId()).orEmpty()
            val result = runCatching { api.getMessages(conversationId).requireData().also { cache?.saveMessages(it) } }
                .getOrElse { cached }
                .mergePending(cached)
            _state.value = _state.value.copy(messages = _state.value.messages + (conversationId to result))
        }
    }

    fun startPolling(conversationId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                loadMessages(conversationId)
                delay(15_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun sendMessage(conversationId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || _state.value.sending) return
        sendMessageWithClientId(conversationId, trimmed, UUID.randomUUID().toString())
    }

    fun draftReply(conversationId: String, instruction: String? = null) {
        if (_state.value.draftingConversationId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(draftingConversationId = conversationId, error = null)
            val draft = runCatching {
                api.draftChatReply(conversationId, ChatDraftRequestDto(language = java.util.Locale.getDefault().displayLanguage, instruction = instruction)).requireData()
            }.getOrNull()
            if (draft != null && draft.text.isNotBlank()) _state.value = _state.value.copy(drafts = _state.value.drafts + (conversationId to draft.text))
            _state.value = _state.value.copy(draftingConversationId = null)
        }
    }

    fun retryMessage(conversationId: String, clientMessageId: String) {
        if (_state.value.sending) return
        val message = _state.value.messages[conversationId].orEmpty().firstOrNull { it.clientMessageId == clientMessageId } ?: return
        sendMessageWithClientId(conversationId, message.body, clientMessageId)
    }

    private fun sendMessageWithClientId(conversationId: String, trimmed: String, clientId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            val pending = ChatMessageDto(
                id = "local-$clientId",
                conversationId = conversationId,
                senderId = "",
                senderRole = "",
                clientMessageId = clientId,
                body = trimmed,
                status = "SENDING",
                createdAt = System.currentTimeMillis().toString()
            )
            upsertLocalMessage(conversationId, pending)
            val sent = runCatching { api.sendMessage(conversationId, SendMessageRequestDto(clientId, trimmed)).requireData() }
            sent.onSuccess { message ->
                cache?.deleteMessage(pending.id)
                upsertLocalMessage(conversationId, message)
            }.onFailure {
                upsertLocalMessage(conversationId, pending.copy(status = "QUEUED_OFFLINE"))
                val payload = JsonObject().apply {
                    addProperty("id", pending.id)
                    addProperty("conversationId", conversationId)
                    addProperty("clientMessageId", clientId)
                    addProperty("body", trimmed)
                }
                syncQueue?.enqueue(SyncQueueItemEntity(operation = "SEND_CHAT_MESSAGE", payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = accountId()))
                requestSync()
            }
            _state.value = _state.value.copy(sending = false)
        }
    }

    private fun upsertLocalMessage(conversationId: String, message: ChatMessageDto) {
        val current = _state.value.messages[conversationId].orEmpty()
        val updated = current.filterNot { it.clientMessageId == message.clientMessageId } + message
        val ordered = updated.sortedBy { it.createdAt.orEmpty() }
        _state.value = _state.value.copy(messages = _state.value.messages + (conversationId to ordered))
        viewModelScope.launch { cache?.saveMessages(listOf(message)) }
    }

    private suspend fun cachedConversations(): List<ConversationDto> = cache?.conversations(accountId()).orEmpty()

    override fun onCleared() {
        stopPolling()
    }

    companion object {
        val offlineActivities = listOf(
            DiyActivityDto(
                id = "offline-cable-organiser",
                slug = "cable-organizer",
                title = "Cable organiser",
                description = "Turn safe, unplugged cable lengths into a simple organiser.",
                materials = listOf("Clean insulated cables", "Cardboard strip", "Tape"),
                steps = listOf("Check that cables are unplugged and have no exposed wire.", "Bundle short lengths around cardboard.", "Secure and label the bundle."),
                safetyWarnings = listOf("Do not use exposed wires, batteries, CRTs, or capacitors."),
                difficulty = "Easy",
                minutes = 15
            )
        )
    }
}

private fun List<ChatMessageDto>.mergePending(cached: List<ChatMessageDto>): List<ChatMessageDto> {
    val serverClientIds = map { it.clientMessageId }.toSet()
    return (this + cached.filter { it.status != "SENT" && it.status != "READ" && it.clientMessageId !in serverClientIds })
        .distinctBy { it.clientMessageId.ifBlank { it.id } }
        .sortedBy { it.createdAt.orEmpty() }
}
