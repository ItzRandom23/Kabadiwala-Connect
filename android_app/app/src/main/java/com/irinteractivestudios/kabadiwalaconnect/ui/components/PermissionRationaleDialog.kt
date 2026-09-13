package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.util.FeaturePermission

/**
 * Explains WHY a permission is needed before the system dialog appears.
 * Shown only at the moment a feature needs it — never on app launch.
 * Shared rationale shown before camera/location features request access.
 */
@Composable
fun PermissionRationaleDialog(
    permission: FeaturePermission,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(permission.titleRes),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                stringResource(permission.textRes),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(
                    stringResource(R.string.perm_allow),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.perm_not_now),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    )
}
