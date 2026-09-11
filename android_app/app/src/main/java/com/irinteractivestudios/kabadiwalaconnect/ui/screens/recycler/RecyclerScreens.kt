package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.irinteractivestudios.kabadiwalaconnect.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcAmberSecondary
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme

data class MarketplaceLot(val id: String, val material: String, val weight: String, val range: String, val area: String, val distance: String, val requestId: String = id)

private val demoLots = listOf(
    MarketplaceLot("LOT-1042", "PCB / Circuit Board", "8.5 kg", "₹2,635 – ₹2,933", "Kothrud, Pune", "4.2 km"),
    MarketplaceLot("LOT-1038", "Copper Cable", "12.4 kg", "₹6,800 – ₹7,200", "Aundh, Pune", "7.8 km"),
    MarketplaceLot("LOT-1031", "Mixed Electronics", "18.0 kg", "₹1,900 – ₹2,400", "Shivajinagar, Pune", "10.6 km")
)

@Composable
fun RecyclerVerificationScreen(profile: AccountProfile?, onRefresh: () -> Unit = {}) {
    var checked by remember { mutableStateOf(false) }
    val status = profile?.verificationStatus ?: RecyclerVerificationStatus.PENDING
    val detailRes = when (status) {
        RecyclerVerificationStatus.PENDING -> R.string.recycler_verification_pending_detail
        RecyclerVerificationStatus.REJECTED -> R.string.recycler_verification_rejected_detail
        RecyclerVerificationStatus.SUSPENDED -> R.string.recycler_verification_suspended_detail
        RecyclerVerificationStatus.VERIFIED -> R.string.recycler_verification_verified_detail
    }
    val statusColor = when (status) {
        RecyclerVerificationStatus.VERIFIED -> MaterialTheme.colorScheme.primaryContainer
        RecyclerVerificationStatus.REJECTED, RecyclerVerificationStatus.SUSPENDED -> MaterialTheme.colorScheme.errorContainer
        RecyclerVerificationStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Filled.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
        Text(if (status == RecyclerVerificationStatus.VERIFIED) "Your facility is verified" else "Your facility is not yet approved", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(detailRes), style = MaterialTheme.typography.bodyLarge)
        StatusCard(status.name, "Backend verification status", statusColor)
        profile?.businessName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        Text("Approval status is controlled by the backend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (checked) Text("Status checked: ${status.name}.", color = if (status == RecyclerVerificationStatus.REJECTED || status == RecyclerVerificationStatus.SUSPENDED) MaterialTheme.colorScheme.error else KcAmberSecondary, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { checked = true; onRefresh() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Check verification status") }
    }
}

@Composable
fun RecyclerMarketplaceScreen(
    demoMode: Boolean = false,
    liveLots: List<MarketplaceLot> = emptyList(),
    liveLoading: Boolean = false,
    liveError: String? = null,
    liveSubmittedIds: Set<String> = emptySet(),
    onRefresh: () -> Unit = {},
    onOfferSent: (String) -> Unit = {},
    onLiveOfferSent: (String, Double) -> Unit = { _, _ -> }
) {
    var sentLots by remember { mutableStateOf(emptySet<String>()) }
    var materialFilter by remember { mutableStateOf("All") }
    var needsResponseOnly by remember { mutableStateOf(false) }
    val visibleLots = demoLots.filter { lot ->
        val materialMatches = materialFilter == "All" || lot.material.contains(materialFilter, ignoreCase = true)
        val responseMatches = !needsResponseOnly || lot.id !in sentLots
        materialMatches && responseMatches
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nearby lots", style = MaterialTheme.typography.headlineLarge)
        Text("Verified collector lots that match your materials and service area.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OperationsPulse(
            openLots = if (demoMode) visibleLots.size else liveLots.size,
            needsResponse = if (demoMode) visibleLots.count { it.id !in sentLots } else liveLots.count { it.requestId !in liveSubmittedIds }
        )
        if (!demoMode) {
            if (liveLoading) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            liveError?.let { error ->
                Text("Could not refresh the marketplace. Your saved work is safe. Try again when the connection is better.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Try again") }
            }
            if (!liveLoading && liveLots.isEmpty() && liveError == null) {
                EmptyContent(modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (liveLots.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(liveLots, key = { it.id }) { lot ->
                        LiveMarketplaceCard(lot, submitted = lot.requestId in liveSubmittedIds, onOfferSent = onLiveOfferSent)
                    }
                }
            }
        } else {
            DemoDataBanner()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = materialFilter == "All", onClick = { materialFilter = "All" }, label = { Text("All") }) }
                item { FilterChip(selected = materialFilter == "PCB", onClick = { materialFilter = "PCB" }, label = { Text("PCB") }) }
                item { FilterChip(selected = materialFilter == "Copper", onClick = { materialFilter = "Copper" }, label = { Text("Copper") }) }
                item { FilterChip(selected = needsResponseOnly, onClick = { needsResponseOnly = !needsResponseOnly }, label = { Text("Needs response") }) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(visibleLots, key = { it.id }) { lot ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(lot.material, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(lot.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(lot.weight, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary); Text("  ·  ${lot.range}", style = MaterialTheme.typography.bodyLarge, color = KcAmberSecondary) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, modifier = Modifier.padding(end = 5.dp)); Text("${lot.area} · ${lot.distance}", style = MaterialTheme.typography.bodyMedium) }
                        if (lot.id in sentLots) Text("Offer saved. Collector will see it after sync.", color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
                        else Button(onClick = { sentLots = sentLots + lot.id; onOfferSent(lot.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Make an offer") }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun LiveMarketplaceCard(lot: MarketplaceLot, submitted: Boolean, onOfferSent: (String, Double) -> Unit) {
    var rateText by remember(lot.id) { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(lot.material, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(lot.weight, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Text("${lot.area}${lot.distance.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Text(lot.range, style = MaterialTheme.typography.bodyMedium, color = KcAmberSecondary)
            if (submitted) Text("Offer sent. Collector will see it after sync.", color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
            else {
                OutlinedTextField(rateText, { rateText = it.filter { char -> char.isDigit() || char == '.' }.take(8) }, label = { Text("Your offer · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val rate = rateText.toDoubleOrNull() ?: return@Button; onOfferSent(lot.requestId, rate) }, enabled = rateText.toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Send offer") }
            }
        }
    }
}

@Composable
private fun OperationsPulse(openLots: Int, needsResponse: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("OPEN MATCHES", style = MaterialTheme.typography.labelSmall)
                Text(openLots.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text("NEEDS RESPONSE", style = MaterialTheme.typography.labelSmall)
                Text(needsResponse.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun RecyclerOrdersScreen(
    demoMode: Boolean = false,
    liveHandovers: List<HandoverDto> = emptyList(),
    liveLoading: Boolean = false,
    liveError: Boolean = false,
    onRefresh: () -> Unit = {},
    onScan: () -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recycler_orders_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.recycler_orders_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!demoMode) TextButton(onClick = onRefresh) { Text(stringResource(R.string.future_refresh)) }
            }
        }
        if (demoMode) {
            item { DemoDataBanner() }
            item { OperationalSurface { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = KcTheme.extended.success); Text("Copper Cable · 12.4 kg", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium) }; Text("Pulkit · Kothrud, Pune", style = MaterialTheme.typography.bodyMedium); Text("₹535/kg · Pickup arranged", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.QrCodeScanner, null); Text("  Scan handover QR") } } } }
            item { Text("No other active orders", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (liveLoading) {
            item { LoadingContent(Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else if (liveError) {
            item { ErrorContent(onRetry = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else if (liveHandovers.isEmpty()) {
            item { EmptyContent(Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else {
            items(liveHandovers, key = { it.id }) { handover -> LiveOrderCard(handover, onScan) }
        }
    }
}

@Composable
private fun LiveOrderCard(handover: HandoverDto, onScan: () -> Unit) {
    val status = handover.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    OperationalSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (handover.status == "CONFIRMED_BY_RECYCLER") Icons.Filled.Verified else Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.recycler_order_reference, handover.referenceId ?: handover.id.takeLast(8)), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.recycler_order_weight, handover.actualWeight ?: handover.weight ?: 0.0), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.recycler_order_status, status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (handover.status == "GENERATED") Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.recycler_order_scan)) }
        }
    }
}

@Composable
fun RecyclerScanScreen(
    state: RecyclerScanState,
    onVerify: (String) -> Unit,
    onConfirm: (Double, Boolean, String?) -> Unit,
    onReset: () -> Unit
) {
    var reference by remember { mutableStateOf("") }
    var actualWeight by remember(state.verified?.handoverId) { mutableStateOf(state.verified?.actualWeight?.toString() ?: state.verified?.declaredWeight?.toString().orEmpty()) }
    var materialMatch by remember(state.verified?.handoverId) { mutableStateOf(true) }
    var notes by remember(state.verified?.handoverId) { mutableStateOf("") }
    val scannerPrompt = stringResource(R.string.recycler_scan_title)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf(String::isNotBlank)?.let { value ->
            reference = value
            onVerify(value)
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.recycler_scan_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.recycler_scan_explanation), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setPrompt(scannerPrompt)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.recycler_scan_camera)) }
        OutlinedTextField(reference, { reference = it }, label = { Text(stringResource(R.string.recycler_scan_reference)) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onVerify(reference) }, enabled = reference.isNotBlank() && !state.checking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (state.checking) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.recycler_scan_validate))
        }
        if (state.error) Text(stringResource(R.string.recycler_scan_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        state.verified?.let { verified ->
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = KcTheme.extended.success); Text(stringResource(R.string.recycler_scan_verified), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium) }
                    Text(stringResource(R.string.recycler_order_reference, verified.referenceId), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.recycler_scan_material, verified.materialCategory.replace('_', ' ')))
                    Text(stringResource(R.string.recycler_order_weight, verified.declaredWeight))
                    OutlinedTextField(actualWeight, { actualWeight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.handover_final_weight_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(materialMatch, { materialMatch = it }); Text(stringResource(R.string.handover_material_confirmed)) }
                    OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.recycler_scan_notes)) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { actualWeight.toDoubleOrNull()?.let { onConfirm(it, materialMatch, notes) } }, enabled = actualWeight.toDoubleOrNull()?.let { it > 0 } == true && !state.confirming && !state.confirmed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(if (state.confirmed) R.string.recycler_scan_confirmed else R.string.recycler_scan_confirm)) }
                }
            }
        }
        TextButton(onClick = { reference = ""; onReset() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.recycler_scan_clear)) }
    }
}

@Composable
fun RecyclerPickupsScreen(demoMode: Boolean = false) {
    var pickupReady by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Pickup schedule", style = MaterialTheme.typography.headlineLarge)
        if (demoMode) {
            DemoDataBanner()
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text("PCB · Kothrud", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium) }
                    Text("Today · 2:00–4:00 PM · Approx. 8.5 kg", style = MaterialTheme.typography.bodyMedium)
                    Text("Exact address is shared only after acceptance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pickupReady) Text("Pickup marked ready. Collector will see it after sync.", color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
                    else Button(onClick = { pickupReady = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Mark pickup ready") }
                }
            }
        } else EmptyContent(modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun RecyclerRatesScreen() {
    var pcb by remember { mutableStateOf("340") }
    var cable by remember { mutableStateOf("535") }
    var saved by remember { mutableStateOf(false) }
    val valid = pcb.toDoubleOrNull()?.takeIf { it > 0 } != null && cable.toDoubleOrNull()?.takeIf { it > 0 } != null
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("My buying rates", style = MaterialTheme.typography.headlineLarge)
        Text("These rates are shared with matched collectors and added to your rate history when saved.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RateEditor("PCB / Circuit Board", pcb, { pcb = it })
        RateEditor("Copper Cable", cable, { cable = it })
        if (saved) Text(stringResource(R.string.recycler_rate_draft_saved), color = KcAmberSecondary, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { saved = true }, enabled = valid, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save rates") }
    }
}

@Composable private fun RateEditor(label: String, value: String, onValueChange: (String) -> Unit) { OutlinedTextField(value, { onValueChange(it.filter { char -> char.isDigit() || char == '.' }.take(7)) }, label = { Text("$label · ₹/kg") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = value.isNotBlank() && value.toDoubleOrNull()?.let { it <= 0 } == true, modifier = Modifier.fillMaxWidth()) }

@Composable
fun RecyclerProfileScreen(profile: AccountProfile?, onLogout: () -> Unit) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ProfileScreen(profile, Modifier.weight(1f))
        OutlinedButton(onClick = { showLogoutConfirm = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 52.dp)) { Text(stringResource(R.string.settings_logout)) }
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

@Composable private fun StatusCard(label: String, detail: String, color: androidx.compose.ui.graphics.Color) { Surface(color = color, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null); Column(Modifier.padding(start = 12.dp)) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium) } } } }

@Composable private fun OperationalSurface(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}
