package com.irinteractivestudios.kabadiwalaconnect.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcStatusPill
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.OfflineContent
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoDataProvider
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme
import com.irinteractivestudios.kabadiwalaconnect.util.UiState

/** Collector command centre: one dominant field action, then a lean work queue. */
@Composable
fun HomeScreen(
    state: UiState<HomeData>,
    onSeePrices: () -> Unit,
    onFindRecyclers: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateLot: () -> Unit = {},
    onMyLots: () -> Unit = {},
    onOpenRewards: () -> Unit = {},
    onOpenSchemes: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    demoMode: Boolean = false,
    household: Boolean = false
) {
    val lotCount = when (state) {
        is UiState.Success -> state.data.lotCount
        is UiState.Offline -> state.cached?.lotCount ?: 0
        is UiState.Syncing -> state.cached?.lotCount ?: 0
        else -> 0
    }
    val scenario = DemoDataProvider.scenario
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background,
                    .72f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.surfaceContainer
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(if (household) R.string.home_household_title else R.string.home_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(if (household) R.string.home_household_subtitle else R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NextCollectionPanel(demoMode = demoMode, household = household, onCreateLot = onCreateLot)
        when (state) {
            is UiState.Loading -> LoadingContent()
            // An empty work queue is communicated by the metrics; a large
            // empty illustration would displace the next action and field tools.
            is UiState.Empty -> Unit
            is UiState.Error -> ErrorContent(onRetry = onRetry)
            is UiState.Offline -> if (state.cached == null && !demoMode) OfflineContent()
            else -> Unit
        }
        SnapshotPanel(
            lotCount = lotCount,
            currentPrice = if (demoMode) scenario.copper.ratePerKg.formatted() else "—"
        )
        SectionLabel(stringResource(R.string.home_work_queue))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CompactAction(stringResource(R.string.home_my_lots), Icons.Filled.Inventory2, onMyLots, Modifier.weight(1f), "home_my_lots")
            CompactAction(stringResource(R.string.home_quick_prices), Icons.Filled.CurrencyRupee, onSeePrices, Modifier.weight(1f), "home_see_prices")
            CompactAction(stringResource(R.string.home_quick_recyclers), Icons.Filled.Recycling, onFindRecyclers, Modifier.weight(1f), "home_find_recyclers")
        }
        if (demoMode) {
            SectionLabel(stringResource(R.string.home_todays_signal))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CurrencyRupee, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_price_copper), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.home_price_updated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${scenario.copper.ratePerKg.formatted()} / kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        SectionLabel(stringResource(R.string.home_field_tools))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ToolShortcut(stringResource(R.string.settings_rewards), Icons.Filled.AutoAwesome, "See value and recognition for verified work.", onOpenRewards, "home_rewards")
                ToolDivider()
                ToolShortcut(stringResource(R.string.settings_schemes), Icons.Filled.School, "Find support schemes that apply to your work.", onOpenSchemes, "home_schemes")
                ToolDivider()
                ToolShortcut(stringResource(R.string.settings_diy), Icons.Filled.Recycling, "Learn safe reuse and sorting practices.", onOpenActivities, "home_activities")
                ToolDivider()
                ToolShortcut(stringResource(R.string.settings_messages), Icons.AutoMirrored.Filled.Chat, "Keep recycler and collection conversations together.", onOpenChat, "home_messages")
                ToolDivider()
                ToolShortcut(stringResource(R.string.settings_disputes), Icons.Filled.Gavel, "Track and resolve handover concerns clearly.", onOpenDisputes, "home_disputes")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NextCollectionPanel(demoMode: Boolean, household: Boolean, onCreateLot: () -> Unit) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .heightIn(min = 190.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
            Spacer(
                Modifier
                    .fillMaxHeight()
                    .width(7.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KcStatusPill(if (demoMode) stringResource(R.string.home_demo_badge) else stringResource(R.string.home_field_ready))
                Text(stringResource(if (household) R.string.home_household_action_title else R.string.home_start_next_collection), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (household) R.string.home_household_action_detail else R.string.home_collection_brief),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Button(
                    onClick = onCreateLot,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("home_create_lot")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (household) R.string.home_household_create_lot else R.string.home_create_lot), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable private fun SectionLabel(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun CompactAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, tag: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)), modifier = modifier.height(100.dp).testTag(tag).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(22.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun ToolShortcut(title: String, icon: ImageVector, detail: String, onClick: () -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag(tag).clickable(onClick = onClick)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SnapshotPanel(lotCount: Int, currentPrice: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.15f)) {
                Text(
                    stringResource(R.string.home_cached_lots).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    lotCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(
                Modifier
                    .width(1.dp)
                    .height(60.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = .28f))
            )
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(
                    stringResource(R.string.home_current_prices).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    currentPrice,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = KcTheme.extended.value
                )
            }
        }
    }
}

@Composable
private fun ToolDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = .18f),
        modifier = Modifier.padding(start = 36.dp)
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    KabadiwalaConnectTheme(darkTheme = false) {
        HomeScreen(
            state = UiState.Success(HomeData(lotCount = 3)),
            onSeePrices = {},
            onFindRecyclers = {},
            demoMode = true
        )
    }
}
