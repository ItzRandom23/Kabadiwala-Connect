package com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.components.SectionCard
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager

@Composable
fun ProfileScreen(profile: AccountProfile?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (profile == null) {
            Text(stringResource(R.string.profile_not_added), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        SectionCard(title = stringResource(R.string.profile_personal_details)) {
            ProfileRow(Icons.Filled.Person, stringResource(R.string.profile_name), profile.displayName ?: profile.businessName ?: stringResource(R.string.profile_not_added))
            ProfileRow(Icons.Filled.Phone, stringResource(R.string.profile_mobile), profile.phoneNumber.ifBlank { stringResource(R.string.profile_not_added) })
            ProfileRow(Icons.Filled.Email, stringResource(R.string.profile_email), profile.email.ifBlank { stringResource(R.string.profile_not_added) })
            ProfileRow(Icons.Filled.Person, stringResource(R.string.profile_role), if (profile.role == AccountRole.RECYCLER) stringResource(R.string.profile_recycler) else stringResource(R.string.profile_collector))
            ProfileRow(Icons.Filled.Language, stringResource(R.string.profile_language), LocaleManager.LABELS[profile.preferredLanguage] ?: profile.preferredLanguage)
            ProfileRow(Icons.Filled.LocationOn, stringResource(R.string.profile_area), profile.areaName ?: stringResource(R.string.profile_not_added))
            ProfileRow(Icons.Filled.Verified, stringResource(R.string.profile_verification), verificationLabel(profile.verificationStatus))
        }
    }
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
