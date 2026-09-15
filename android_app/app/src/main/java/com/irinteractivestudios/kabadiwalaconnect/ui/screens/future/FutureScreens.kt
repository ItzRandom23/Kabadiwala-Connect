package com.irinteractivestudios.kabadiwalaconnect.ui.screens.future

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ChatMessageDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ConversationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DiyActivityDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DisputeAnalyticsDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.GovernmentSchemeDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.NotificationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RewardLedgerDto
import java.text.NumberFormat
import java.util.Locale
import com.irinteractivestudios.kabadiwalaconnect.R

private fun rupees(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(value)

@Composable
fun RewardsScreen(state: FutureFeatureState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.future_bonuses_title), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.future_bonuses_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.future_refresh_bonuses)) }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.rewards.isEmpty() && !state.loading) item { EmptyFeatureCard(stringResource(R.string.future_no_bonus_title), stringResource(R.string.future_no_bonus_detail)) }
        items(state.rewards, key = { it.id }) { reward -> RewardCard(reward) }
        state.error?.let { error -> item { ErrorFeatureCard(error, onRefresh) } }
    }
}

@Composable
private fun RewardCard(reward: RewardLedgerDto) {
    val program = reward.program
    val threshold = program?.thresholdKg ?: 0.0
    val progress = if (threshold > 0) (reward.qualifyingKg / threshold).coerceIn(0.0, 1.0).toFloat() else 0f
    val progressDescription = stringResource(R.string.future_bonus_progress, (progress * 100).toInt())
    FeatureSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(10.dp))
                Text(program?.title ?: stringResource(R.string.future_collection_bonus), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(program?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().semantics { contentDescription = progressDescription })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${reward.qualifyingKg.formatOneDecimal()} / ${threshold.formatOneDecimal()} kg")
                Text(if (reward.status == "EARNED") stringResource(R.string.future_bonus_earned, rupees(reward.rewardAmount)) else stringResource(R.string.future_bonus_in_progress), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(program?.terms.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SchemesScreen(state: FutureFeatureState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { FeatureHeader(stringResource(R.string.future_schemes_title), stringResource(R.string.future_schemes_subtitle), Icons.Filled.School, onRefresh) }
        if (state.schemes.isEmpty() && !state.loading) item { EmptyFeatureCard(stringResource(R.string.future_schemes_offline_title), stringResource(R.string.future_schemes_offline_detail)) }
        items(state.schemes, key = { it.id }) { SchemeCard(it) }
        state.error?.let { item { ErrorFeatureCard(it, onRefresh) } }
    }
}

@Composable
private fun SchemeCard(scheme: GovernmentSchemeDto) {
    val context = LocalContext.current
    FeatureSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(scheme.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(scheme.description, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.future_documents, scheme.requiredDocuments.joinToString()), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(
                onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(scheme.sourceUrl))) } },
                enabled = scheme.sourceUrl.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.future_open_official_source), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.future_last_checked, scheme.lastVerifiedAt?.take(10) ?: stringResource(R.string.future_date_unavailable)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DiyActivitiesScreen(activities: List<DiyActivityDto>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { FeatureHeader(stringResource(R.string.future_diy_title), stringResource(R.string.future_diy_subtitle), Icons.Filled.Recycling, null) }
        items(activities, key = { it.id }) { activity ->
            FeatureSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(activity.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.future_minutes, activity.minutes), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(activity.description)
                    Text(stringResource(R.string.future_materials), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(activity.materials.joinToString(" • "))
                    activity.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(activity.safetyWarnings.joinToString(" "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListScreen(conversations: List<ConversationDto>, onOpen: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FeatureHeader(stringResource(R.string.future_messages_title), stringResource(R.string.future_messages_subtitle), Icons.AutoMirrored.Filled.Chat, onRefresh) }
        if (conversations.isEmpty()) item { EmptyFeatureCard(stringResource(R.string.future_no_messages_title), stringResource(R.string.future_no_messages_detail)) }
        items(conversations, key = { it.id }) { conversation ->
            FeatureSurface(Modifier.clickable { onOpen(conversation.id) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.future_transaction, conversation.lotId.takeLast(8)), fontWeight = FontWeight.Bold); Text(conversation.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(conversation: ConversationDto, messages: List<ChatMessageDto>, sending: Boolean, onSend: (String) -> Unit, onRetryMessage: (String) -> Unit = {}, draftSuggestion: String? = null, drafting: Boolean = false, onDraftReply: () -> Unit = {}, onProceedToHandover: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(draftSuggestion) { if (!draftSuggestion.isNullOrBlank()) draft = draftSuggestion }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.future_transaction_chat), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.future_chat_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val isMine = message.senderId.isBlank() || message.senderId == conversation.collectorId
                Surface(color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(message.body)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message.status.chatStatusLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (message.status != "SENT" && message.status != "READ") {
                                TextButton(onClick = { onRetryMessage(message.clientMessageId) }, enabled = !sending) { Text(stringResource(R.string.future_retry)) }
                            }
                        }
                    }
                }
            }
        }
        if (drafting || draftSuggestion != null) {
            Text(stringResource(if (drafting) R.string.future_chat_drafting else R.string.future_chat_draft_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onProceedToHandover?.let { proceed ->
            Button(onClick = proceed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.future_confirm_handover)) }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(1000) }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.future_message)) }, maxLines = 4)
            IconButton(onClick = onDraftReply, enabled = !sending && !drafting) { Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.future_chat_draft)) }
            Button(enabled = draft.isNotBlank() && !sending, onClick = { onSend(draft); draft = "" }) { Text(stringResource(R.string.future_send)) }
        }
    }
}

@Composable
private fun String.chatStatusLabel(): String = when (this) {
    "SENDING" -> stringResource(R.string.future_message_sending)
    "QUEUED_OFFLINE" -> stringResource(R.string.future_message_queued)
    "FAILED" -> stringResource(R.string.future_message_failed)
    "READ" -> stringResource(R.string.future_message_read)
    else -> stringResource(R.string.future_message_sent)
}

@Composable
fun VerifiedRatingScreen(
    recyclerName: String,
    submitting: Boolean,
    error: String?,
    onSubmit: (rating: Int, pickupReliability: Int, paymentClarity: Int, comment: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rating by remember { mutableStateOf(5) }
    var pickup by remember { mutableStateOf(5) }
    var payment by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.future_rate_recycler, recyclerName), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.future_review_verified_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { RatingPicker(stringResource(R.string.future_overall_experience), rating) { rating = it } }
        item { RatingPicker(stringResource(R.string.future_pickup_reliability), pickup) { pickup = it } }
        item { RatingPicker(stringResource(R.string.future_payment_clarity), payment) { payment = it } }
        item { OutlinedTextField(comment, { comment = it.take(500) }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.future_optional_comment)) }) }
        error?.let { item { ErrorFeatureCard(it) {} } }
        item { Button(onClick = { onSubmit(rating, pickup, payment, comment.trim()) }, enabled = !submitting, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (submitting) stringResource(R.string.future_submitting) else stringResource(R.string.future_submit_review)) } }
    }
}

@Composable
private fun RatingPicker(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..5).forEach { value -> FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value.toString()) }) } }
    }
}

@Composable
fun DisputeAnalyticsScreen(analytics: DisputeAnalyticsDto?, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { FeatureHeader(stringResource(R.string.future_dispute_title), stringResource(R.string.future_dispute_subtitle), Icons.Filled.Gavel, null) }
        if (analytics == null || analytics.total == 0) item { EmptyFeatureCard(stringResource(R.string.future_no_dispute_title), stringResource(R.string.future_no_dispute_detail)) }
        analytics?.let { data ->
            item { StatCard(stringResource(R.string.future_total_reports), data.total.toString(), stringResource(R.string.future_last_months, data.months)) }
            item { BreakdownCard(stringResource(R.string.future_by_reason), data.byType) }
            item { BreakdownCard(stringResource(R.string.future_by_status), data.byStatus) }
            if (data.averageResolutionHours != null) item { StatCard(stringResource(R.string.future_average_resolution), stringResource(R.string.future_hours, data.averageResolutionHours), stringResource(R.string.future_based_on_resolved)) }
            if (data.insufficientData) item { Text(stringResource(R.string.future_more_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun NotificationsScreen(
    notifications: List<NotificationDto>,
    unreadCount: Int,
    onRefresh: () -> Unit,
    onOpen: (NotificationDto) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(if (unreadCount == 0) R.string.notifications_all_caught_up else R.string.notifications_unread, unreadCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.future_refresh)) }
            }
        }
        if (unreadCount > 0) {
            item { TextButton(onClick = onMarkAllRead, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.notifications_mark_all_read)) } }
        }
        if (notifications.isEmpty()) item { EmptyFeatureCard(stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_detail)) }
        items(notifications, key = { it.id }) { notification ->
            FeatureSurface(
                modifier = Modifier.clickable { onOpen(notification) },
                containerColor = if (notification.readAt.isNullOrBlank()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(notification.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (notification.readAt.isNullOrBlank()) AssistChip(onClick = { onOpen(notification) }, label = { Text(stringResource(R.string.notifications_new)) })
                    }
                    Text(notification.body, style = MaterialTheme.typography.bodyMedium)
                    notification.createdAt?.take(16)?.let { Text(it.replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun FeatureHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onRefresh: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        onRefresh?.let { IconButton(onClick = it) { Icon(Icons.Filled.Refresh, stringResource(R.string.future_refresh)) } }
    }
}

@Composable
private fun StatCard(label: String, value: String, detail: String) {
    FeatureSurface { Column(Modifier.padding(16.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2) } }
}

@Composable
private fun BreakdownCard(title: String, values: Map<String, Int>) {
    FeatureSurface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (values.isEmpty()) Text(stringResource(R.string.future_no_data)) else values.toList().sortedByDescending { it.second }.forEach { (label, count) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }); Text(count.toString(), fontWeight = FontWeight.Bold) } } } }
}

@Composable
private fun EmptyFeatureCard(title: String, detail: String) { FeatureSurface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) } } }

@Composable
private fun ErrorFeatureCard(message: String, onRetry: () -> Unit) { FeatureSurface(containerColor = MaterialTheme.colorScheme.errorContainer, border = null) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f)); Button(onClick = onRetry) { Text(stringResource(R.string.future_retry)) } } } }

@Composable
private fun FeatureSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = border,
        content = content
    )
}

private fun Double.formatOneDecimal() = String.format(Locale.US, "%.1f", this)
