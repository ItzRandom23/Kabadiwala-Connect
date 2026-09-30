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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    val conversationsLastSyncedAt: Long? = null,
    val conversationsRefreshError: String? = null,
    val messages: Map<String, List<ChatMessageDto>> = emptyMap(),
    val messagesNextCursor: Map<String, String> = emptyMap(),
    val loadingOlderMessages: Set<String> = emptySet(),
    val analytics: DisputeAnalyticsDto? = null,
    val notifications: List<NotificationDto> = emptyList(),
    val unreadNotifications: Int = 0,
    val notificationsNextCursor: String? = null,
    val notificationsLoadingMore: Boolean = false,
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
    private var conversationsJob: Job? = null
    private var refreshJob: Job? = null
    private var notificationJob: Job? = null
    private var notificationOwner: String? = null
    private var notificationGeneration = 0L
    private var conversationsOwner: String? = null
    private var conversationsRefreshQueued = false
    private val exhaustedChatHistory = mutableSetOf<String>()
    private val messageRequestGenerations = mutableMapOf<String, Long>()
    private var chatOwner: String? = null

    private suspend fun withPendingNotificationReads(owner: String, items: List<NotificationDto>): List<NotificationDto> {
        val queued = syncQueue?.observeForAccount(owner)?.first().orEmpty().filter { it.lastErrorCode == null || it.attempts < 3 }
        if (queued.isEmpty()) return items
        val readAll = queued.any { it.operation == "MARK_ALL_NOTIFICATIONS_READ" }
        val readIds = queued.asSequence().filter { it.operation == "MARK_NOTIFICATION_READ" }
            .mapNotNull { runCatching { JsonParser.parseString(it.payloadJson).asJsonObject.get("id")?.asString }.getOrNull() }
            .toSet()
        if (!readAll && readIds.isEmpty()) return items
        val pendingAt = System.currentTimeMillis().toString()
        return items.map { if (it.readAt.isNullOrBlank() && (readAll || it.id in readIds)) it.copy(readAt = pendingAt) else it }
    }

    fun refreshNotifications() {
        notificationJob?.cancel()
        val owner = accountId()?.takeIf { it.isNotBlank() } ?: return
        val generation = ++notificationGeneration
        if (notificationOwner != owner) {
            notificationOwner = owner
            _state.value = _state.value.copy(notifications = emptyList(), unreadNotifications = 0, notificationsNextCursor = null)
        }
        notificationJob = viewModelScope.launch {
            val cached = cache?.notifications(owner).orEmpty()
            if (accountId() == owner && generation == notificationGeneration && _state.value.notifications.isEmpty() && cached.isNotEmpty()) {
                _state.value = _state.value.copy(notifications = cached, unreadNotifications = cached.count { it.readAt.isNullOrBlank() })
            }
            try {
                val response = api.getNotifications(limit = 50)
                val items = withPendingNotificationReads(owner, response.requireData())
                if (accountId() != owner || generation != notificationGeneration) return@launch
                cache?.saveNotifications(items, owner)
                _state.value = _state.value.copy(
                    notifications = items,
                    unreadNotifications = items.count { it.readAt.isNullOrBlank() },
                    notificationsNextCursor = response.body()?.page?.nextCursor,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accountId() == owner && generation == notificationGeneration) _state.value = _state.value.copy(error = userFacingError(error, "Could not refresh notifications. Cached notifications are shown."))
            }
        }
    }

    fun loadMoreNotifications() {
        val cursor = _state.value.notificationsNextCursor ?: return
        if (_state.value.notificationsLoadingMore) return
        val owner = accountId()?.takeIf { it.isNotBlank() } ?: return
        val generation = notificationGeneration
        viewModelScope.launch {
            _state.value = _state.value.copy(notificationsLoadingMore = true)
            try {
                val response = api.getNotifications(limit = 50, cursor = cursor)
                val page = withPendingNotificationReads(owner, response.requireData())
                if (accountId() != owner || generation != notificationGeneration || _state.value.notificationsNextCursor != cursor) return@launch
                val merged = (_state.value.notifications + page).distinctBy { it.id }
                cache?.appendNotifications(page, owner)
                _state.value = _state.value.copy(
                    notifications = merged,
                    unreadNotifications = merged.count { it.readAt.isNullOrBlank() },
                    notificationsNextCursor = response.body()?.page?.nextCursor,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accountId() == owner && generation == notificationGeneration) _state.value = _state.value.copy(error = userFacingError(error, "Could not load older notifications. Try again."))
            } finally {
                if (accountId() == owner && generation == notificationGeneration) _state.value = _state.value.copy(notificationsLoadingMore = false)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        val owner = accountId()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val previous = _state.value
            val cachedSchemes = cache?.schemes().orEmpty()
            val cachedActivities = cache?.activities().orEmpty()
            val cachedNotifications = cache?.notifications(owner).orEmpty()
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
                    request(previous.notifications.ifEmpty { cachedNotifications }) { api.getNotifications(limit = 100).requireData().let { items -> owner?.let { withPendingNotificationReads(it, items) } ?: items }.also { items -> if (accountId() == owner) owner?.let { cache?.saveNotifications(items, it) } } }
                }
                val schemesResult = schemes.await()
                val activitiesResult = activities.await()
                val rewardsResult = rewards.await()
                val conversationsResult = conversations.await()
                val analyticsResult = analytics.await()
                val notificationsResult = notifications.await()
                val failureCount = listOf(schemesResult, activitiesResult, rewardsResult, conversationsResult, analyticsResult, notificationsResult)
                    .count { it.failed }
                val resolvedNotifications = notificationsResult.value.orEmpty()
                // Keep the inbox summary consistent with the rows currently
                // rendered. The separate count endpoint can lag the list query.
                val resolvedUnread = resolvedNotifications.count { it.readAt.isNullOrBlank() }
                if (accountId() != owner) return@supervisorScope
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

    fun refreshRewards() = refreshFeature("rewards") { owner ->
        val items = api.getRewards().requireData()
        if (accountId() != owner) return@refreshFeature
        _state.value = _state.value.copy(rewards = items)
    }
    fun refreshSchemes() = refreshFeature("schemes") { owner ->
        val items = api.getGovernmentSchemes().requireData()
        if (accountId() != owner) return@refreshFeature
        _state.value = _state.value.copy(schemes = items)
        cache?.saveSchemes(items)
    }
    fun refreshActivities() = refreshFeature("activities") { owner ->
        val items = api.getDiyActivities().requireData()
        if (accountId() != owner) return@refreshFeature
        _state.value = _state.value.copy(activities = items)
        cache?.saveActivities(items)
    }
    fun refreshAnalytics() = refreshFeature("analytics") { owner ->
        val result = api.getDisputeAnalytics().requireData()
        if (accountId() != owner) return@refreshFeature
        _state.value = _state.value.copy(analytics = result)
    }

    private fun refreshFeature(feature: String, block: suspend (String) -> Unit) {
        val owner = accountId() ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            try {
                if (feature == "schemes" && _state.value.schemes.isEmpty()) {
                    val cached = cache?.schemes().orEmpty()
                    if (accountId() == owner) _state.value = _state.value.copy(schemes = cached)
                }
                if (feature == "activities" && _state.value.activities.isEmpty()) {
                    val cached = cache?.activities().orEmpty().ifEmpty { offlineActivities }
                    if (accountId() == owner) _state.value = _state.value.copy(activities = cached)
                }
                // The feature request is isolated from unrelated schemes,
                // conversations and notifications. Cancellation propagates through Retrofit.
                block(owner)
                if (accountId() == owner) _state.value = _state.value.copy(loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accountId() == owner) _state.value = _state.value.copy(loading = false,
                    error = userFacingError(error, "Could not refresh $feature. Cached data is shown."))
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
        val owner = accountId()?.takeIf { it.isNotBlank() } ?: return
        val original = _state.value.notifications.firstOrNull { it.id == id } ?: return
        if (!original.readAt.isNullOrBlank()) return
        val updated = _state.value.notifications.map { if (it.id == id) it.copy(readAt = System.currentTimeMillis().toString()) else it }
        _state.value = _state.value.copy(notifications = updated, unreadNotifications = (_state.value.unreadNotifications - 1).coerceAtLeast(0))
        viewModelScope.launch {
            cache?.appendNotifications(updated, owner)
            val failure = try {
                api.markNotificationRead(id).requireData()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error
            }
            if (failure != null) {
                val queued = failure.isRetryableTransportFailure() && syncQueue != null && try {
                    syncQueue.enqueue(SyncQueueItemEntity(operation = "MARK_NOTIFICATION_READ", payloadJson = JsonObject().apply { addProperty("id", id) }.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = owner))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!queued) {
                    if (accountId() == owner) {
                        val rolledBack = _state.value.notifications.map { if (it.id == id) original else it }
                        cache?.appendNotifications(listOf(original), owner)
                        _state.value = _state.value.copy(notifications = rolledBack, unreadNotifications = rolledBack.count { it.readAt.isNullOrBlank() }, error = userFacingError(failure, "Could not update this notification. Try again."))
                    }
                    return@launch
                }
                requestSync()
            }
        }
    }

    fun markAllNotificationsRead() {
        val owner = accountId()?.takeIf { it.isNotBlank() } ?: return
        val original = _state.value.notifications
        val readAt = System.currentTimeMillis().toString()
        val updated = original.map { it.copy(readAt = it.readAt ?: readAt) }
        _state.value = _state.value.copy(notifications = updated, unreadNotifications = 0)
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
                val queued = failure.isRetryableTransportFailure() && syncQueue != null && try {
                    syncQueue.enqueue(SyncQueueItemEntity(operation = "MARK_ALL_NOTIFICATIONS_READ", payloadJson = "{}", createdAtEpochMs = System.currentTimeMillis(), accountId = owner))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!queued) {
                    if (accountId() == owner) {
                        cache?.appendNotifications(original, owner)
                        _state.value = _state.value.copy(notifications = original, unreadNotifications = original.count { it.readAt.isNullOrBlank() }, error = userFacingError(failure, "Could not update your notifications. Try again."))
                    }
                    return@launch
                }
                requestSync()
            }
            if (accountId() == owner) cache?.markAllNotificationsRead(owner, readAt)
        }
    }

    fun loadConversations() {
        val owner = accountId()
        if (conversationsOwner != owner) {
            conversationsJob?.cancel()
            conversationsOwner = owner
            conversationsRefreshQueued = false
            _state.value = _state.value.copy(conversations = emptyList(), conversationsLastSyncedAt = null, conversationsRefreshError = null, sending = false)
        } else if (conversationsJob?.isActive == true) {
            conversationsRefreshQueued = true
            return
        }
        conversationsJob = viewModelScope.launch {
            val cached = cache?.conversations(owner).orEmpty()
            if (accountId() == owner && _state.value.conversations.isEmpty() && cached.isNotEmpty()) _state.value = _state.value.copy(conversations = cached)
            do {
                conversationsRefreshQueued = false
                try {
                    val conversations = api.getConversations().requireData()
                    if (accountId() != owner) return@launch
                    cache?.saveConversations(conversations)
                    if (accountId() != owner) return@launch
                    _state.value = _state.value.copy(conversations = conversations, conversationsLastSyncedAt = System.currentTimeMillis(), conversationsRefreshError = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (accountId() == owner) _state.value = _state.value.copy(conversationsRefreshError = userFacingError(error, "Could not refresh messages. Saved conversations are shown."))
                }
            } while (conversationsRefreshQueued && accountId() == owner)
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch { fetchMessages(conversationId) }
    }

    private suspend fun fetchMessages(conversationId: String) {
        val owner = accountId()
        if (chatOwner != owner) {
            chatOwner = owner
            exhaustedChatHistory.clear()
            messageRequestGenerations.clear()
            _state.value = _state.value.copy(messages = emptyMap(), messagesNextCursor = emptyMap(), sending = false)
        }
        val generation = (messageRequestGenerations[conversationId] ?: 0L) + 1L
        messageRequestGenerations[conversationId] = generation
        val current = { accountId() == owner && messageRequestGenerations[conversationId] == generation }
        val cached = cache?.messages(conversationId, accountId()).orEmpty()
        if (current() && _state.value.messages[conversationId].isNullOrEmpty() && cached.isNotEmpty()) {
            _state.value = _state.value.copy(messages = _state.value.messages + (conversationId to cached))
        }
        val result = try {
            val response = api.getMessages(conversationId)
            val items = response.requireData()
            if (!current()) return
            cache?.saveMessages(items)
            if (!current()) return
            val next = response.body()?.page?.nextCursor
            if (conversationId !in exhaustedChatHistory && conversationId !in _state.value.messagesNextCursor && next != null) {
                _state.value = _state.value.copy(messagesNextCursor = _state.value.messagesNextCursor + (conversationId to next))
            }
            items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cached
        }
            .mergePending(_state.value.messages[conversationId].orEmpty() + cached)
        if (current()) _state.value = _state.value.copy(messages = _state.value.messages + (conversationId to result))
    }

    fun loadOlderMessages(conversationId: String) {
        val cursor = _state.value.messagesNextCursor[conversationId] ?: return
        if (conversationId in _state.value.loadingOlderMessages) return
        val owner = accountId() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingOlderMessages = _state.value.loadingOlderMessages + conversationId)
            try {
                val response = api.getMessages(conversationId, cursor = cursor)
                val older = response.requireData()
                if (accountId() != owner || _state.value.messagesNextCursor[conversationId] != cursor) return@launch
                cache?.saveMessages(older)
                val merged = older.mergePending(_state.value.messages[conversationId].orEmpty())
                val next = response.body()?.page?.nextCursor
                if (next == null) exhaustedChatHistory += conversationId
                _state.value = _state.value.copy(
                    messages = _state.value.messages + (conversationId to merged),
                    messagesNextCursor = if (next == null) _state.value.messagesNextCursor - conversationId else _state.value.messagesNextCursor + (conversationId to next),
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accountId() == owner) _state.value = _state.value.copy(error = userFacingError(error, "Could not load older messages. Try again."))
            } finally {
                if (accountId() == owner) _state.value = _state.value.copy(loadingOlderMessages = _state.value.loadingOlderMessages - conversationId)
            }
        }
    }

    fun startPolling(conversationId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchMessages(conversationId)
                // Push wakes active chats; this is a fallback for missed push
                // and development builds without an FCM provider.
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
        clearDraft(conversationId)
        sendMessageWithClientId(conversationId, trimmed, UUID.randomUUID().toString())
    }

    fun clearDraft(conversationId: String) {
        _state.value = _state.value.copy(drafts = _state.value.drafts - conversationId)
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
        val owner = accountId()?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (accountId() != owner) return@launch
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
                if (accountId() != owner) return@launch
                cache?.deleteMessage(pending.id)
                if (accountId() != owner) return@launch
                upsertLocalMessage(conversationId, message)
                null
            } catch (cancelled: CancellationException) {
                if (accountId() == owner) upsertLocalMessage(conversationId, pending.copy(status = "FAILED"))
                throw cancelled
            } catch (error: Exception) {
                error
            }
            if (accountId() != owner) return@launch
            if (sendFailure != null) {
                val payload = JsonObject().apply {
                    addProperty("id", pending.id)
                    addProperty("conversationId", conversationId)
                    addProperty("clientMessageId", clientId)
                    addProperty("body", trimmed)
                }
                val queued = sendFailure.isRetryableTransportFailure() && syncQueue != null && try {
                    syncQueue.enqueueOnce(SyncQueueItemEntity(operation = "SEND_CHAT_MESSAGE", payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = owner, idempotencyKey = clientId))
                    accountId() == owner
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (accountId() != owner) return@launch
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
    return (this + cached)
        .distinctBy { it.clientMessageId.ifBlank { it.id } }
        .sortedBy { it.createdAt.orEmpty() }
}
