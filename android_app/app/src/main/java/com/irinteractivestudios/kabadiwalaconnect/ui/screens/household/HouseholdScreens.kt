package com.irinteractivestudios.kabadiwalaconnect.ui.screens.household

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import kotlinx.coroutines.delay

data class NearbyKabadiwala(
    val id: String,
    val name: String,
    val initials: String,
    val distanceKm: Double,
    val rating: Double,
    val jobs: Int,
    val etaMinutes: Int,
    val specialties: String,
    val verified: Boolean = true
)

private val dummyKabadiwalas = listOf(
    NearbyKabadiwala("kb-01", "Rafiq Scrap Services", "RS", 0.8, 4.9, 286, 25, "Paper · Metal · Appliances"),
    NearbyKabadiwala("kb-02", "Asha Eco Collect", "AE", 1.4, 4.8, 194, 35, "Plastic · Cardboard · Metal"),
    NearbyKabadiwala("kb-03", "Shree Ganesh Raddi", "SG", 2.2, 4.7, 341, 45, "Paper · Copper · E-waste"),
    NearbyKabadiwala("kb-04", "Green Wheel Kabadi", "GW", 3.6, 4.6, 129, 60, "Mixed household scrap")
).sortedBy { it.distanceKm }

@Composable
fun HouseholdHomeScreen(
    area: String,
    onCreateLot: () -> Unit,
    onFindKabadiwala: () -> Unit,
    onOpenDeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                DemoDataBanner()
                LocationPill(area)
                Text(stringResource(R.string.household_home_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.household_home_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 30.dp, bottomStart = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f), shape = CircleShape) {
                        Text(stringResource(R.string.household_new_pickup), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(stringResource(R.string.household_sell_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.household_sell_detail), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                    Button(
                        onClick = onCreateLot,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("household_create_lot")
                    ) { Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.household_create_lot)) }
                }
            }
        }
        item {
            SectionHeading(stringResource(R.string.household_how_sale_works), stringResource(R.string.household_clear_steps))
            Spacer(Modifier.height(12.dp))
            DealTimeline()
        }
        item {
            SectionHeading(stringResource(R.string.household_nearby_now), stringResource(R.string.household_trusted_count, dummyKabadiwalas.size))
            Spacer(Modifier.height(12.dp))
            NearbyPreview(dummyKabadiwalas.first(), onFindKabadiwala)
        }
        item {
            Surface(
                onClick = onOpenDeal,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("household_open_demo_deal")
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.household_preview_pickup), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.household_preview_detail), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
fun NearbyKabadiwalasScreen(area: String, onInvite: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LocationPill(area)
            Spacer(Modifier.height(10.dp))
            DemoDataBanner()
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.household_nearby_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.household_nearby_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.household_closest_filter), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(dummyKabadiwalas, key = { it.id }) { kabadiwala ->
            KabadiwalaCard(kabadiwala, dummyKabadiwalas.indexOf(kabadiwala) + 1) { onInvite(kabadiwala.id) }
        }
    }
}

private enum class DealState { WAITING, ACCEPTED }
private data class LocalMessage(val body: String, val mine: Boolean, val time: String)

@Composable
fun HouseholdDealScreen(kabadiwalaId: String, onFindAnother: () -> Unit, modifier: Modifier = Modifier) {
    val kabadiwala = dummyKabadiwalas.firstOrNull { it.id == kabadiwalaId } ?: dummyKabadiwalas.first()
    var dealState by remember { mutableStateOf(DealState.WAITING) }
    var draft by remember { mutableStateOf("") }
    var showRematch by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<LocalMessage>() }

    LaunchedEffect(kabadiwala.id) {
        delay(900)
        dealState = DealState.ACCEPTED
        if (messages.isEmpty()) messages += LocalMessage("Namaste! I can collect your mixed metal lot today at 5:30 PM.", false, "Now")
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoDataBanner()
            Text(stringResource(R.string.household_pickup_request), style = MaterialTheme.typography.headlineLarge)
            AnimatedContent(dealState, label = "deal-status") { status ->
                if (status == DealState.WAITING) WaitingBanner(kabadiwala.name)
                else AcceptedBanner(kabadiwala)
            }
            EstimateCard()
        }
        AnimatedVisibility(dealState == DealState.ACCEPTED, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.household_private_chat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showRematch = true }, modifier = Modifier.testTag("find_another_kabadiwala")) { Text(stringResource(R.string.household_find_another)) }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message -> ChatBubble(message) }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(500) },
                        placeholder = { Text(stringResource(R.string.household_chat_placeholder)) },
                        maxLines = 3,
                        modifier = Modifier.weight(1f).testTag("household_chat_input")
                    )
                    IconButton(
                        enabled = draft.isNotBlank(),
                        onClick = { messages += LocalMessage(draft.trim(), true, "Now"); draft = "" },
                        modifier = Modifier.size(56.dp).testTag("household_chat_send")
                    ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.household_send_message), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
    if (showRematch) AlertDialog(
        onDismissRequest = { showRematch = false },
        icon = { Icon(Icons.Filled.Search, null) },
        title = { Text(stringResource(R.string.household_find_new_title)) },
        text = { Text(stringResource(R.string.household_find_new_detail)) },
        confirmButton = { TextButton(onClick = onFindAnother) { Text(stringResource(R.string.household_find_another)) } },
        dismissButton = { TextButton(onClick = { showRematch = false }) { Text(stringResource(R.string.household_keep_chat)) } }
    )
}

@Composable
private fun DemoDataBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("demo_data_banner")
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.household_demo_banner_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.household_demo_banner_detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun LocationPill(area: String) = Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        Text(area, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun SectionHeading(title: String, eyebrow: String) { Column { Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(title, style = MaterialTheme.typography.titleLarge) } }

@Composable private fun DealTimeline() {
    val steps = listOf(stringResource(R.string.household_step_create) to stringResource(R.string.household_step_range), stringResource(R.string.household_step_choose) to stringResource(R.string.household_step_invite), stringResource(R.string.household_step_chat) to stringResource(R.string.household_step_agree))
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { steps.forEachIndexed { index, (title, detail) ->
        Row(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
                if (index < steps.lastIndex) Box(Modifier.width(2.dp).height(34.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .35f)))
            }
            Column(Modifier.padding(start = 12.dp, bottom = 18.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    } }
}

@Composable private fun NearbyPreview(item: NearbyKabadiwala, onSeeAll: () -> Unit) {
    Surface(onClick = onSeeAll, shape = RoundedCornerShape(8.dp, 24.dp, 24.dp, 24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(item)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.household_distance_pickup, item.distanceKm, item.etaMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@Composable private fun KabadiwalaCard(item: NearbyKabadiwala, rank: Int, onInvite: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth().testTag("kabadiwala_${item.id}")) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(item)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("#$rank  ${item.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1); if (item.verified) { Spacer(Modifier.width(5.dp)); Icon(Icons.Filled.CheckCircle, "Verified", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                    Text(item.specialties, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Signal(Icons.Filled.LocationOn, stringResource(R.string.household_distance, item.distanceKm), if (rank == 1) stringResource(R.string.household_closest) else stringResource(R.string.household_away))
                Signal(Icons.Filled.Star, item.rating.toString(), stringResource(R.string.household_pickups, item.jobs))
                Signal(Icons.Filled.Schedule, stringResource(R.string.household_minutes, item.etaMinutes), stringResource(R.string.household_typical))
            }
            Button(onClick = onInvite, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("invite_${item.id}")) { Text(stringResource(R.string.household_share_lot)); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        }
    }
}

@Composable private fun InitialsAvatar(item: NearbyKabadiwala) = Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text(item.initials, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }

@Composable private fun Signal(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, detail: String) { Column(Modifier) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary); Text(value, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge) }; Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun WaitingBanner(name: String) = Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.secondary); Column(Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.household_waiting_for, name), fontWeight = FontWeight.Bold); Text(stringResource(R.string.household_chat_after_accept), style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun AcceptedBanner(item: NearbyKabadiwala) = Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 7.dp), modifier = Modifier.fillMaxWidth().testTag("deal_accepted")) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.household_accepted, item.name), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.household_chat_open), style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun EstimateCard() = Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(7.dp, 20.dp, 20.dp, 20.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.household_estimated_range), style = MaterialTheme.typography.labelSmall); Text("₹860–₹1,080", style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.household_final_after_inspection), style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Filled.Shield, null, Modifier.size(30.dp)) } }

@Composable private fun ChatBubble(message: LocalMessage) { Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) { Surface(color = if (message.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = if (message.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = if (message.mine) RoundedCornerShape(20.dp, 7.dp, 20.dp, 20.dp) else RoundedCornerShape(7.dp, 20.dp, 20.dp, 20.dp), modifier = Modifier.fillMaxWidth(.84f)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) { Text(message.body); Text(message.time, style = MaterialTheme.typography.labelSmall, color = if (message.mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable private fun HouseholdHomePreview() { KabadiwalaConnectTheme { HouseholdHomeScreen("Kothrud, Pune", {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable private fun NearbyPreviewScreen() { KabadiwalaConnectTheme { NearbyKabadiwalasScreen("Kothrud, Pune", {}) } }
