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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateUpdateDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerVerificationRequestDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme

data class MarketplaceLot(val id: String, val material: String, val weight: String, val range: String, val area: String, val distance: String, val requestId: String = id)

private val demoLots = listOf(
    MarketplaceLot("LOT-1042", "PCB / Circuit Board", "8.5 kg", "₹2,635 – ₹2,933", "Kothrud, Pune", "4.2 km"),
    MarketplaceLot("LOT-1038", "Copper Cable", "12.4 kg", "₹6,800 – ₹7,200", "Aundh, Pune", "7.8 km"),
    MarketplaceLot("LOT-1031", "Mixed Electronics", "18.0 kg", "₹1,900 – ₹2,400", "Shivajinagar, Pune", "10.6 km")
)

@Composable
fun RecyclerVerificationScreen(
    profile: AccountProfile?,
    recyclerProfile: RecyclerDto? = null,
    loading: Boolean = false,
    saving: Boolean = false,
    error: String? = null,
    saved: Boolean = false,
    onRefresh: () -> Unit = {},
    onSubmit: (RecyclerVerificationRequestDto) -> Unit = {},
    onOpenMarketplace: () -> Unit = {}
) {
    val status = recyclerProfile?.authorizationStatus?.let { value ->
        runCatching { RecyclerVerificationStatus.valueOf(value) }.getOrNull()
    } ?: profile?.verificationStatus ?: RecyclerVerificationStatus.PENDING
    var authority by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.authority.orEmpty()) }
    var registrationNumber by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.registrationNumber.orEmpty()) }
    var authorizationType by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.type.orEmpty()) }
    var validUntil by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.validUntil?.take(10).orEmpty()) }
    var evidenceReference by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.evidenceReference.orEmpty()) }
    var verificationSource by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.verificationSource.orEmpty()) }
    var declarationAccepted by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<Int?>(null) }
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
    val canSubmit = status == RecyclerVerificationStatus.PENDING || status == RecyclerVerificationStatus.REJECTED
    val statusLabelRes = when (status) {
        RecyclerVerificationStatus.PENDING -> R.string.recycler_verification_status_pending
        RecyclerVerificationStatus.VERIFIED -> R.string.recycler_verification_status_verified
        RecyclerVerificationStatus.REJECTED -> R.string.recycler_verification_status_rejected
        RecyclerVerificationStatus.SUSPENDED -> R.string.recycler_verification_status_suspended
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Filled.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(
                when {
                    status == RecyclerVerificationStatus.VERIFIED -> R.string.recycler_verification_verified_title
                    status == RecyclerVerificationStatus.REJECTED -> R.string.recycler_verification_rejected_title
                    else -> R.string.recycler_verification_not_approved_title
                }
            ),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(stringResource(detailRes), style = MaterialTheme.typography.bodyLarge)
        StatusCard(stringResource(statusLabelRes), stringResource(R.string.recycler_verification_status_detail), statusColor)
        recyclerProfile?.name?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        Text(stringResource(R.string.recycler_verification_controlled_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (status == RecyclerVerificationStatus.VERIFIED) {
            VerificationEvidenceSummary(recyclerProfile)
            Button(onClick = onOpenMarketplace, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(stringResource(R.string.recycler_verification_open_marketplace))
            }
        } else if (status == RecyclerVerificationStatus.SUSPENDED) {
            Text(stringResource(R.string.recycler_verification_suspended_action), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else if (canSubmit) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.recycler_verification_submit_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.recycler_verification_submit_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (status == RecyclerVerificationStatus.REJECTED) {
                        recyclerProfile?.authorizationDetails?.reviewReason?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.recycler_verification_review_feedback, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(authority, { authority = it }, label = { Text(stringResource(R.string.recycler_verification_authority)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(authorizationType, { authorizationType = it }, label = { Text(stringResource(R.string.recycler_verification_type)) }, placeholder = { Text(stringResource(R.string.recycler_verification_type_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(registrationNumber, { registrationNumber = it }, label = { Text(stringResource(R.string.recycler_verification_registration_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(validUntil, { validUntil = it.filter { char -> char.isDigit() || char == '-' }.take(10) }, label = { Text(stringResource(R.string.recycler_verification_valid_until)) }, placeholder = { Text(stringResource(R.string.recycler_verification_valid_until_hint)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(evidenceReference, { evidenceReference = it }, label = { Text(stringResource(R.string.recycler_verification_evidence_reference)) }, placeholder = { Text(stringResource(R.string.recycler_verification_evidence_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(verificationSource, { verificationSource = it }, label = { Text(stringResource(R.string.recycler_verification_source)) }, placeholder = { Text(stringResource(R.string.recycler_verification_source_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = declarationAccepted, onCheckedChange = { declarationAccepted = it })
                        Text(stringResource(R.string.recycler_verification_declaration), style = MaterialTheme.typography.bodyMedium)
                    }
                    localError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    error?.let { Text(stringResource(R.string.recycler_verification_submit_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        onClick = {
                            val validationError = validateVerificationForm(authority, registrationNumber, authorizationType, validUntil, evidenceReference, verificationSource, declarationAccepted)
                            localError = validationError
                            if (validationError == null) onSubmit(RecyclerVerificationRequestDto(authority.trim(), registrationNumber.trim(), authorizationType.trim(), evidenceReference.trim(), verificationSource.trim(), validUntil.trim()))
                        },
                        enabled = !saving && !loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text(stringResource(if (saving) R.string.recycler_verification_submitting else R.string.recycler_verification_submit))
                    }
                    if (saved) Text(stringResource(R.string.recycler_verification_submitted), color = KcTheme.extended.warning, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRefresh, enabled = !loading && !saving, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.recycler_verification_refresh)) }
            if (saved) Text(stringResource(R.string.recycler_verification_submitted), modifier = Modifier.weight(1f).padding(top = 15.dp), color = KcTheme.extended.warning, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VerificationEvidenceSummary(profile: RecyclerDto?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.recycler_verification_evidence_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            profile?.authorizationDetails?.authority?.let { Text(stringResource(R.string.recycler_verified_by, it)) }
            profile?.authorizationDetails?.type?.let { Text(stringResource(R.string.recycler_verification_type_value, it)) }
            profile?.authorizationDetails?.registrationNumber?.let { Text(stringResource(R.string.recycler_verification_registration_value, it)) }
            profile?.authorizationDetails?.validUntil?.take(10)?.let { Text(stringResource(R.string.recycler_verification_valid_until_value, it)) }
            Text(stringResource(R.string.recycler_verification_evidence_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun validateVerificationForm(
    authority: String,
    registrationNumber: String,
    authorizationType: String,
    validUntil: String,
    evidenceReference: String,
    verificationSource: String,
    declarationAccepted: Boolean
): Int? = when {
    authority.trim().length < 2 -> R.string.recycler_verification_error_authority
    authorizationType.trim().length < 2 -> R.string.recycler_verification_error_type
    registrationNumber.trim().length < 2 -> R.string.recycler_verification_error_registration
    !validUntil.trim().matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> R.string.recycler_verification_error_date
    evidenceReference.trim().length < 2 -> R.string.recycler_verification_error_evidence
    verificationSource.trim().length < 2 -> R.string.recycler_verification_error_source
    !declarationAccepted -> R.string.recycler_verification_error_declaration
    else -> null
}

@Composable
fun RecyclerMarketplaceScreen(
    demoMode: Boolean = false,
    liveLots: List<MarketplaceLot> = emptyList(),
    liveLoading: Boolean = false,
    liveError: String? = null,
    liveSubmittedIds: Set<String> = emptySet(),
    liveSubmittingIds: Set<String> = emptySet(),
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.recycler_marketplace_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            if (!demoMode) {
                TextButton(onClick = onRefresh, enabled = !liveLoading) {
                    Text(stringResource(R.string.future_refresh))
                }
            }
        }
        Text(
            stringResource(R.string.recycler_marketplace_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OperationsPulse(
            openLots = if (demoMode) visibleLots.size else liveLots.size,
            needsResponse = if (demoMode) {
                visibleLots.count { it.id !in sentLots }
            } else {
                liveLots.count { it.requestId !in liveSubmittedIds }
            }
        )

        if (!demoMode) {
            if (liveLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            if (liveError != null) {
                Text(
                    stringResource(R.string.recycler_marketplace_refresh_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            if (!liveLoading && liveLots.isEmpty() && liveError == null) {
                EmptyContent(modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (liveLots.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(liveLots, key = { it.id }) { lot ->
                        LiveMarketplaceCard(
                            lot = lot,
                            submitted = lot.requestId in liveSubmittedIds,
                            submitting = lot.requestId in liveSubmittingIds,
                            onOfferSent = onLiveOfferSent
                        )
                    }
                }
            }
        } else {
            DemoDataBanner()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = materialFilter == "All",
                        onClick = { materialFilter = "All" },
                        label = { Text("All") }
                    )
                }
                item {
                    FilterChip(
                        selected = materialFilter == "PCB",
                        onClick = { materialFilter = "PCB" },
                        label = { Text("PCB") }
                    )
                }
                item {
                    FilterChip(
                        selected = materialFilter == "Copper",
                        onClick = { materialFilter = "Copper" },
                        label = { Text("Copper") }
                    )
                }
                item {
                    FilterChip(
                        selected = needsResponseOnly,
                        onClick = { needsResponseOnly = !needsResponseOnly },
                        label = { Text("Needs response") }
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(visibleLots, key = { it.id }) { lot ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    lot.material,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    lot.id,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    lot.weight,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("  ·  ${lot.range}", style = MaterialTheme.typography.bodyLarge, color = KcTheme.extended.warning)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, null, modifier = Modifier.padding(end = 5.dp))
                                Text("${lot.area} · ${lot.distance}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (lot.id in sentLots) {
                                Text(
                                    stringResource(R.string.recycler_offer_saved),
                                    color = KcTheme.extended.success,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Button(
                                    onClick = {
                                        sentLots = sentLots + lot.id
                                        onOfferSent(lot.id)
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) {
                                    Text("Make an offer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMarketplaceCard(lot: MarketplaceLot, submitted: Boolean, submitting: Boolean, onOfferSent: (String, Double) -> Unit) {
    var rateText by remember(lot.id) { mutableStateOf("") }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(lot.material, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(lot.weight, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Text("${lot.area}${lot.distance.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Text(lot.range, style = MaterialTheme.typography.bodyMedium, color = KcTheme.extended.warning)
            if (submitted) Text(stringResource(R.string.recycler_offer_sent), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
            else {
                OutlinedTextField(rateText, { rateText = it.filter { char -> char.isDigit() || char == '.' }.take(8) }, label = { Text("Your offer · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val rate = rateText.toDoubleOrNull() ?: return@Button; onOfferSent(lot.requestId, rate) }, enabled = rateText.toDoubleOrNull()?.let { it > 0 } == true && !submitting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { if (submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text(if (submitting) "Sending…" else "Send offer") }
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
                Text("OPEN", style = MaterialTheme.typography.labelSmall)
                Text(openLots.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text("RESPONSE", style = MaterialTheme.typography.labelSmall)
                Text(needsResponse.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun RecyclerOrdersScreen(
    demoMode: Boolean = false,
    liveHandovers: List<SupplyHandoverDto> = emptyList(),
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
            item { Text(stringResource(R.string.recycler_no_orders), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun LiveOrderCard(handover: SupplyHandoverDto, onScan: () -> Unit) {
    val status = handover.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    OperationalSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (handover.status == "COMPLETED") Icons.Filled.Verified else Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.recycler_order_reference, handover.referenceId.ifBlank { handover.id.takeLast(8) }), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.recycler_order_weight, handover.finalAcceptedKg ?: handover.quotedWeightKg), style = MaterialTheme.typography.bodyLarge)
            Text("${handover.materialCategory.replace('_', ' ')} · ₹${"%.0f".format(handover.finalRatePerKg ?: handover.quotedRatePerKg)}/kg", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.recycler_order_status, status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (handover.status == "COLLECTOR_CONFIRMED") Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.recycler_order_scan)) }
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
    var actualWeight by remember(state.supplyVerified?.id ?: state.verified?.handoverId) {
        mutableStateOf(
            state.supplyVerified?.quotedWeightKg?.toString()
                ?: state.verified?.actualWeight?.toString()
                ?: state.verified?.declaredWeight?.toString()
                ?: ""
        )
    }
    var materialMatch by remember(state.verified?.handoverId) { mutableStateOf(true) }
    var notes by remember(state.verified?.handoverId) { mutableStateOf("") }
    val scannerPrompt = stringResource(R.string.recycler_scan_title)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf(String::isNotBlank)?.let { value ->
            reference = value
            onVerify(value)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        state.supplyVerified?.let { handover ->
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = KcTheme.extended.success); Text(stringResource(R.string.recycler_passport_matched), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium) }
                    Text("${handover.referenceId} · ${handover.materialCategory.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                    Text("Declared ${"%.1f".format(handover.quotedWeightKg)} kg · quoted ₹${"%.0f".format(handover.quotedRatePerKg)}/kg")
                    Text(if (state.supplyFromCache) "Matched from saved passport · server confirmation is pending." else "Signed QR matched to the current server record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(actualWeight, { actualWeight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.handover_final_weight_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(materialMatch, { materialMatch = it }); Text(stringResource(R.string.handover_material_confirmed)) }
                    OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.recycler_scan_notes)) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { actualWeight.toDoubleOrNull()?.let { onConfirm(it, materialMatch, notes) } }, enabled = actualWeight.toDoubleOrNull()?.let { it > 0 && it <= 100000 } == true && !state.confirming && !state.supplyQueued && state.supplyConfirmed == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        if (state.confirming) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text(if (state.supplyQueued) "Saved on device — waiting to sync" else if (state.supplyConfirmed != null) "Receipt recorded" else "Confirm received material")
                    }
                    state.supplyQueued.takeIf { it }?.let { Text(stringResource(R.string.recycler_receipt_saved), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge) }
                    state.supplyConfirmed?.let { confirmed -> Text("Server receipt: ${statusNameForSupply(confirmed.status)} · final ${"%.1f".format(confirmed.finalAcceptedKg ?: 0.0)} kg", color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
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
                    Button(onClick = { actualWeight.toDoubleOrNull()?.let { onConfirm(it, materialMatch, notes) } }, enabled = actualWeight.toDoubleOrNull()?.let { it > 0 && it <= 500 } == true && !state.confirming && !state.confirmed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(if (state.confirmed) R.string.recycler_scan_confirmed else R.string.recycler_scan_confirm)) }
                    if (state.confirmed) {
                        val settlement = state.confirmation?.getAsJsonObject("settlement")
                        settlement?.let {
                            Text(stringResource(R.string.recycler_settlement_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            it.get("finalAmount")?.asDouble?.let { amount -> Text(stringResource(R.string.recycler_settlement_amount, amount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
                            it.get("variancePercent")?.asDouble?.let { variance -> Text(stringResource(R.string.recycler_settlement_variance, variance), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { reference = ""; onReset() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.recycler_scan_clear)) }
    }
}

private fun statusNameForSupply(value: String) = value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun RecyclerPickupsScreen(
    demoMode: Boolean = false,
    availability: String? = null,
    loading: Boolean = false,
    saving: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onSave: (String) -> Unit = {}
) {
    var pickupReady by remember { mutableStateOf(false) }
    var selectedAvailability by remember(availability) { mutableStateOf(availability ?: "FLEXIBLE") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.recycler_pickups_title), style = MaterialTheme.typography.headlineLarge)
        if (demoMode) {
            DemoDataBanner()
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text("PCB · Kothrud", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium) }
                    Text("Today · 2:00–4:00 PM · Approx. 8.5 kg", style = MaterialTheme.typography.bodyMedium)
                    Text("Exact address is shared only after acceptance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pickupReady) Text(stringResource(R.string.recycler_pickup_ready), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
                    else Button(onClick = { pickupReady = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Mark pickup ready") }
                }
            }
        } else {
            Text(stringResource(R.string.recycler_pickups_detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error?.let { Text("Couldn’t load availability. Your saved setting is unchanged.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("TODAY" to "Today", "THIS_WEEK" to "This week", "FLEXIBLE" to "Flexible").forEach { (value, label) ->
                    FilterChip(selected = selectedAvailability == value, onClick = { selectedAvailability = value }, label = { Text(label) })
                }
            }
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Availability · ${selectedAvailability.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.recycler_availability_visible), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Try again") } }
            Button(onClick = { onSave(selectedAvailability) }, enabled = !saving && !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(if (saving) "Saving…" else "Save availability")
            }
        }
    }
}

@Composable
fun RecyclerRatesScreen(
    rates: List<com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto> = emptyList(),
    acceptedMaterials: List<String> = listOf("PCB", "COPPER"),
    loading: Boolean = false,
    saving: Boolean = false,
    saved: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onSave: (List<RecyclerRateUpdateDto>) -> Unit = {},
    demoMode: Boolean = false
) {
    val categories = remember(acceptedMaterials, rates) { (acceptedMaterials + rates.map { it.materialCategory }).filter { it.isNotBlank() }.distinct() }
    var values by remember(categories, rates) { mutableStateOf(categories.associateWith { category -> rates.firstOrNull { it.materialCategory == category }?.pricePerKg?.toString().orEmpty() }) }
    val valid = values.values.any { it.toDoubleOrNull()?.let { value -> value > 0 } == true }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.recycler_rates_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.recycler_rates_detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (demoMode) DemoDataBanner()
        if (loading && rates.isEmpty()) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        error?.let { Text(stringResource(R.string.recycler_rates_load_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        categories.forEach { category -> RateEditor(category.displayMaterial(), values[category].orEmpty()) { value -> values = values + (category to value) } }
        if (saved) Text(stringResource(R.string.recycler_rate_draft_saved), color = KcTheme.extended.warning, style = MaterialTheme.typography.bodyMedium)
        if (error != null) OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Try again") }
        Button(onClick = {
            onSave(values.mapNotNull { (category, value) -> value.toDoubleOrNull()?.takeIf { it > 0 }?.let { RecyclerRateUpdateDto(category, it) } })
        }, enabled = valid && !saving && !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(if (saving) "Saving…" else "Save rates")
        }
    }
}

private fun String.displayMaterial(): String = when (this) {
    "LCD_PANEL" -> "LCD panel"
    "PCB" -> "PCB / circuit board"
    "CABLE" -> "Cables"
    else -> replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
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
