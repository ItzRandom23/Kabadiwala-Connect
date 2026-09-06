package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.util.AvailableAppUpdate

@Composable
fun AppUpdatePrompt(
    update: AvailableAppUpdate,
    isBusy: Boolean,
    errorMessage: String?,
    onLater: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onLater() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_available_detail, update.versionName))
                update.releaseNotes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_available_notes, it))
                }
                if (isBusy) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_downloading))
                }
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall, enabled = !isBusy) {
                Text(stringResource(R.string.update_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !isBusy) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
