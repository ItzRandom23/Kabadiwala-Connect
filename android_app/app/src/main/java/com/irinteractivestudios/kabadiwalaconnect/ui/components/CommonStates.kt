package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcRadius
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState

/** Minimum touch-target height for primary actions (low-literacy friendly). */
val KcMinTouchHeight = 56.dp

/**
 * Offline strip shown above screen content whenever connectivity is
 * [ConnectionState.OFFLINE] or [ConnectionState.LIMITED].
 */
@Composable
fun OfflineBanner(state: ConnectionState, modifier: Modifier = Modifier) {
    if (state == ConnectionState.ONLINE) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DemoDataBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.demo_preview_banner),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Compact, persistent environment marker that does not compete with content. */
@Composable
fun TestingEnvironmentIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.extraSmall,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.testing_mode_banner), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Large primary action button: 56dp target, icon + short text. */
@Composable
fun KcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = KcRadius.pill,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = KcMinTouchHeight)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun StateColumn(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

/** Reusable Loading / Offline / Empty / Error / Success / Syncing states. */
@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .testTag("state_loading")
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun EmptyContent(modifier: Modifier = Modifier) {
    StateColumn(
        icon = Icons.Filled.Inbox,
        tint = MaterialTheme.colorScheme.outline,
        title = stringResource(R.string.common_no_data),
        detail = stringResource(R.string.common_no_data_detail),
        modifier = modifier.testTag("state_empty")
    )
}

@Composable
fun ErrorContent(onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    StateColumn(
        icon = Icons.Filled.ErrorOutline,
        tint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.common_error_title),
        detail = null,
        modifier = modifier.testTag("state_error")
    ) { if (onRetry != null) KcPrimaryButton(text = stringResource(R.string.common_retry), onClick = onRetry) }
}

@Composable
fun OfflineContent(modifier: Modifier = Modifier) {
    StateColumn(
        icon = Icons.Filled.CloudOff,
        tint = KcTheme.extended.warning,
        title = stringResource(R.string.common_offline_title),
        detail = stringResource(R.string.common_offline_detail),
        modifier = modifier.testTag("state_offline")
    )
}

@Composable
fun SuccessContent(text: String, modifier: Modifier = Modifier) {
    StateColumn(
        icon = Icons.Filled.CheckCircle,
        tint = KcTheme.extended.success,
        title = text,
        detail = null,
        modifier = modifier.testTag("state_success")
    )
}

@Composable
fun SyncingContent(modifier: Modifier = Modifier) {
    StateColumn(
        icon = Icons.Filled.Sync,
        tint = MaterialTheme.colorScheme.tertiary,
        title = stringResource(R.string.common_syncing),
        detail = null,
        modifier = modifier.testTag("state_syncing")
    )
}

/** Big-number summary card (lots count, rupee totals). */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Compact, reusable metric for operational summaries. */
@Composable
fun KcMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false
) {
    Surface(
        color = if (emphasis) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface,
        contentColor = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** A small status surface whose color remains paired with explicit text. */
@Composable
fun KcStatusPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/** Titled section card used by Settings rows and info screens. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/**
 * Lightweight loading placeholder for list/detail surfaces. It intentionally
 * stays static so low-end devices and reduced-motion users do not pay for a
 * shimmer animation while data is loading.
 */
@Composable
fun KcSkeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .testTag("state_skeleton")
    )
}
