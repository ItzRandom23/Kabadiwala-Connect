package com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.components.SectionCard
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager

data class ProfileEditDraft(val displayName: String, val email: String, val areaName: String)

@Composable
fun ProfileScreen(
    profile: AccountProfile?,
    modifier: Modifier = Modifier,
    onSave: ((ProfileEditDraft) -> Unit)? = null,
    saving: Boolean = false,
    saveError: String? = null
) {
    var editing by remember(profile?.profileId) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (profile == null) {
            Text(stringResource(R.string.profile_not_added), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        SectionCard(title = stringResource(R.string.profile_personal_details)) {
            val activeLanguage = LocaleManager.persistedTag(LocalContext.current)
            ProfileRow(Icons.Filled.Person, stringResource(R.string.profile_name), profile.displayName ?: profile.businessName ?: stringResource(R.string.profile_not_added))
            ProfileRow(Icons.Filled.Phone, stringResource(R.string.profile_mobile), profile.phoneNumber.ifBlank { stringResource(R.string.profile_not_added) })
            ProfileRow(Icons.Filled.Email, stringResource(R.string.profile_email), profile.email.ifBlank { stringResource(R.string.profile_not_added) })
            ProfileRow(Icons.Filled.Person, stringResource(R.string.profile_role), roleLabel(profile.role))
            ProfileRow(Icons.Filled.Language, stringResource(R.string.profile_language), LocaleManager.LABELS[activeLanguage] ?: activeLanguage)
            ProfileRow(Icons.Filled.LocationOn, stringResource(R.string.profile_area), profile.areaName ?: stringResource(R.string.profile_not_added))
            ProfileRow(Icons.Filled.Verified, stringResource(R.string.profile_verification), verificationLabel(profile.verificationStatus))
            if (onSave != null && profile.role != AccountRole.ADMIN) {
                Button(onClick = { editing = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("  Edit account details")
                }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (editing && profile != null && onSave != null) {
        ProfileEditorDialog(profile, saving, onDismiss = { if (!saving) editing = false }) { draft ->
            editing = false
            onSave(draft)
        }
    }
}

@Composable
private fun ProfileEditorDialog(profile: AccountProfile, saving: Boolean, onDismiss: () -> Unit, onSave: (ProfileEditDraft) -> Unit) {
    var name by remember(profile.profileId) { mutableStateOf(profile.displayName ?: profile.businessName.orEmpty()) }
    var email by remember(profile.profileId) { mutableStateOf(profile.email) }
    var area by remember(profile.profileId) { mutableStateOf(profile.areaName.orEmpty()) }
    val valid = name.trim().isNotEmpty() && area.trim().isNotEmpty() && (email.isBlank() || email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit account details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your mobile number and account type stay verified and cannot be changed here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(160) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Security email") }, singleLine = true, supportingText = { Text("Optional recovery and sign-in email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(area, { area = it.take(160) }, label = { Text(stringResource(R.string.profile_area)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.common_back)) } },
        confirmButton = { TextButton(onClick = { onSave(ProfileEditDraft(name.trim(), email.trim(), area.trim())) }, enabled = valid && !saving) { Text(if (saving) "Saving…" else "Save") } }
    )
}

@Composable
private fun roleLabel(role: AccountRole): String = when (role) {
    AccountRole.HOUSEHOLD -> stringResource(R.string.profile_household)
    AccountRole.COLLECTOR -> stringResource(R.string.profile_collector)
    AccountRole.RECYCLER -> stringResource(R.string.profile_recycler)
    AccountRole.ADMIN -> "Operator"
}

@Composable
private fun verificationLabel(status: RecyclerVerificationStatus): String = when (status) {
    RecyclerVerificationStatus.VERIFIED -> stringResource(R.string.profile_verified)
    RecyclerVerificationStatus.PENDING -> stringResource(R.string.profile_pending)
    RecyclerVerificationStatus.REJECTED -> stringResource(R.string.profile_rejected)
    RecyclerVerificationStatus.SUSPENDED -> stringResource(R.string.profile_suspended)
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
