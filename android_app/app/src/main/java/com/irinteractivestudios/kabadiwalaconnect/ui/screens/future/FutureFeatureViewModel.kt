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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.isRetryableTransportFailure
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity

data class FutureFeatureState(
    // A newly-created ViewModel has not asked the server for anything yet.
    // Keep that distinction visible so an empty first frame is never mistaken
    // for a successful empty response.
    val loading: Boolean = true,
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
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val previous = _state.value
            val cachedSchemes = cache?.schemes().orEmpty()
            val cachedActivities = cache?.activities().orEmpty()
            val cachedNotifications = cache?.notifications(accountId()).orEmpty()
            supervisorScope {
                val schemes = async {
                    request(previous.schemes.ifEmpty { cachedSchemes }) { api.getGovernmentSchemes().requireData().also { cache?.saveSchemes(it) } }
                }
                val activities = async {
                    request(previous.activities.ifEmpty { cachedActivities.ifEmpty { offlineActivities } }) { api.getDiyActivities().requireData().also { cache?.saveActivities(it) } }
                }
                val rewards = async { request(previous.rewards) { api.getRewards().requireData() } }
                val conversations = async {
                    request(previous.conversations.ifEmpty { cachedConversations() }) { api.getConversations().requireData().also { cache?.saveConversations(it) } }
                }
                val analytics = async { request(previous.analytics) { api.getDisputeAnalytics().requireData() } }
                val notifications = async {
                    request(previous.notifications.ifEmpty { cachedNotifications }) { api.getNotifications(limit = 100).requireData().also { cache?.saveNotifications(it, accountId()) } }
                }
                val unread = async {
                    request(null) { api.getNotificationUnreadCount().requireData().count }
                }
                val schemesResult = schemes.await()
                val activitiesResult = activities.await()
                val rewardsResult = rewards.await()
                val conversationsResult = conversations.await()
                val analyticsResult = analytics.await()
                val notificationsResult = notifications.await()
                val unreadResult = unread.await()
                val failureCount = listOf(schemesResult, activitiesResult, rewardsResult, conversationsResult, analyticsResult, notificationsResult, unreadResult)
                    .count { it.failed }
                val resolvedNotifications = notificationsResult.value.orEmpty()
                val resolvedUnread = unreadResult.value ?: resolvedNotifications.count { it.readAt.isNullOrBlank() }
                _state.value = _state.value.copy(
                    loading = false,
                    error = if (failureCount > 0) "Some information could not be refreshed. Cached data is shown." else null,
                    schemes = schemesResult.value.orEmpty(),
                    activities = activitiesResult.value.orEmpty(),
                    rewards = rewardsResult.value.orEmpty(),
                    conversations = conversationsResult.value.orEmpty(),
                    analytics = analyticsResult.value,
                    notifications = resolvedNotifications,
                    unreadNotifications = resolvedUnread
                )
            }
        }
    }

    private data class RequestResult<T>(val value: T?, val failed: Boolean)

    private suspend fun <T> request(fallback: T, block: suspend () -> T): RequestResult<T> = try {
        RequestResult(block(), false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        RequestResult(fallback, true)
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            val wasUnread = _state.value.notifications.firstOrNull { it.id == id }?.readAt.isNullOrBlank()
            val failure = try {
                api.markNotificationRead(id).requireData()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error
            }
            if (failure != null) {
                val owner = accountId()?.takeIf { it.isNotBlank() }
                val queued = failure.isRetryableTransportFailure() && syncQueue != null && owner != null && try {
                    syncQueue.enqueue(SyncQueueItemEntity(operation = "MARK_NOTIFICATION_READ", payloadJson = JsonObject().apply { addProperty("id", id) }.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = owner))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!queued) {
                    _state.value = _state.value.copy(error = userFacingError(failure, "Could not update this notification. Try again."))
                    return@launch
                }
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
            val failure = try {
                api.markAllNotificationsRead().requireData()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error
            }
            if (failure != null) {
                val owner = accountId()?.takeIf { it.isNotBlank() }
                val queued = failure.isRetryableTransportFailure() && syncQueue != null && owner != null && try {
                    syncQueue.enqueue(SyncQueueItemEntity(operation = "MARK_ALL_NOTIFICATIONS_READ", payloadJson = "{}", createdAtEpochMs = System.currentTimeMillis(), accountId = owner))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!queued) {
                    _state.value = _state.value.copy(error = userFacingError(failure, "Could not update your notifications. Try again."))
                    return@launch
                }
                requestSync()
            }
            if (failure == null || _state.value.notifications.isNotEmpty()) {
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
            val sendFailure = try {
                val message = api.sendMessage(conversationId, SendMessageRequestDto(clientId, trimmed)).requireData()
                cache?.deleteMessage(pending.id)
                upsertLocalMessage(conversationId, message)
                null
            } catch (cancelled: CancellationException) {
                upsertLocalMessage(conversationId, pending.copy(status = "FAILED"))
                throw cancelled
            } catch (error: Exception) {
                error
            }
            if (sendFailure != null) {
                val payload = JsonObject().apply {
                    addProperty("id", pending.id)
                    addProperty("conversationId", conversationId)
                    addProperty("clientMessageId", clientId)
                    addProperty("body", trimmed)
                }
                val owner = accountId()?.takeIf { it.isNotBlank() }
                val queued = sendFailure.isRetryableTransportFailure() && syncQueue != null && owner != null && try {
                    syncQueue.enqueue(SyncQueueItemEntity(operation = "SEND_CHAT_MESSAGE", payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = owner))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                upsertLocalMessage(conversationId, pending.copy(status = if (queued) "QUEUED_OFFLINE" else "FAILED"))
                if (queued) requestSync()
                else _state.value = _state.value.copy(error = userFacingError(sendFailure, "Message could not be sent. Retry it when ready."))
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
        refreshJob?.cancel()
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
