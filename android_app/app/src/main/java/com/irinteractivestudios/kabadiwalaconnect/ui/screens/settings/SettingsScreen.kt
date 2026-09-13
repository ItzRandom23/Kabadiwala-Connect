package com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.components.SectionCard
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity

/**
 * Settings tab: language picker, Safety, Help, About.
 * Everything works offline. Safety and Help open the app's secondary screens.
 */
@Composable
fun SettingsScreen(
    language: String,
    appearance: String = "SYSTEM",
    appVersion: String,
    onLanguageChange: (String) -> Unit,
    onAppearanceChange: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenSafety: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenRewards: () -> Unit = {},
    onOpenSchemes: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    syncItems: List<SyncQueueItemEntity> = emptyList(),
    syncPendingCount: Int = 0,
    onRetrySync: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        SectionCard(title = stringResource(R.string.settings_profile)) {
            SettingsRow(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.settings_profile),
                onClick = onOpenProfile,
                testTag = "settings_profile"
            )
        }

        SectionCard(title = stringResource(R.string.settings_language)) {
            SettingsRow(
                icon = Icons.Filled.Language,
                label = LocaleManager.LABELS[language] ?: language,
                onClick = { showLanguagePicker = true },
                testTag = "settings_language_picker"
            )
            Text(
                text = stringResource(R.string.settings_change_language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
            )
        }

        SectionCard(title = stringResource(R.string.settings_appearance)) {
            AppearanceRow("SYSTEM", stringResource(R.string.appearance_system), appearance, onAppearanceChange, "appearance_system")
            AppearanceRow("LIGHT", stringResource(R.string.appearance_light), appearance, onAppearanceChange, "appearance_light")
            AppearanceRow("DARK", stringResource(R.string.appearance_dark), appearance, onAppearanceChange, "appearance_dark")
        }

        SectionCard(title = stringResource(R.string.settings_safety)) {
            SettingsRow(
                icon = Icons.Filled.HealthAndSafety,
                label = stringResource(R.string.settings_safety),
                onClick = onOpenSafety,
                testTag = "settings_safety"
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Help,
                label = stringResource(R.string.settings_help),
                onClick = onOpenHelp,
                testTag = "settings_help"
            )
            SettingsRow(Icons.Filled.Recycling, stringResource(R.string.settings_diy), onOpenActivities, "settings_diy")
        }

        SectionCard(title = stringResource(R.string.settings_trust_tools)) {
            SettingsRow(Icons.Filled.Notifications, stringResource(R.string.notifications_title), onOpenNotifications, "settings_notifications")
            SettingsRow(Icons.Filled.AutoAwesome, stringResource(R.string.settings_rewards), onOpenRewards, "settings_rewards")
            SettingsRow(Icons.Filled.School, stringResource(R.string.settings_schemes), onOpenSchemes, "settings_schemes")
            SettingsRow(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.settings_messages), onOpenChat, "settings_messages")
            SettingsRow(Icons.Filled.Gavel, stringResource(R.string.settings_disputes), onOpenDisputes, "settings_disputes")
        }

        SectionCard(title = stringResource(R.string.settings_about)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("settings_about")) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(R.string.settings_version, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        SectionCard(title = stringResource(R.string.settings_sync_center)) {
            SettingsRow(
                icon = Icons.Filled.CloudOff,
                label = if (syncPendingCount > 0) stringResource(R.string.settings_sync_pending, syncPendingCount) else stringResource(R.string.settings_sync_synced),
                onClick = onRetrySync,
                testTag = "settings_sync_status"
            )
            if (syncItems.isNotEmpty()) {
                Text(stringResource(R.string.settings_sync_details), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
                syncItems.take(6).forEach { item ->
                    val operation = item.operation.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                    val status = item.lastErrorCode?.let { stringResource(R.string.settings_sync_failed, it) }
                        ?: if (item.attempts > 0) stringResource(R.string.settings_sync_retrying, item.attempts) else stringResource(R.string.settings_sync_waiting)
                    Text("$operation · $status", style = MaterialTheme.typography.bodySmall, color = if (item.lastErrorCode != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 40.dp, top = 4.dp, end = 12.dp))
                }
            }
        }
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            label = stringResource(R.string.settings_logout),
            onClick = { showLogoutConfirm = true },
            testTag = "settings_logout"
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    LocaleManager.SUPPORTED.forEach { tag ->
                        LanguageRow(
                            tag = tag,
                            label = LocaleManager.LABELS[tag] ?: tag,
                            selected = language == tag,
                            onSelect = { selected ->
                                onLanguageChange(selected)
                                showLanguagePicker = false
                            },
                            testTag = "lang_$tag"
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_warning)) },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.common_back)) } },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text(stringResource(R.string.settings_logout)) }
            }
        )
    }
}

@Composable
private fun AppearanceRow(
    mode: String,
    label: String,
    selectedMode: String,
    onSelect: (String) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onSelect(mode) }
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selectedMode == mode, onClick = { onSelect(mode) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LanguageRow(
    tag: String,
    label: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onSelect(tag) }
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = { onSelect(tag) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
