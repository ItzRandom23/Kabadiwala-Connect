package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerProfileUpdateRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerVerificationRequestDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.recyclerVerificationStatusFromAuthorization
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.rememberKcResponsiveLayout
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileEditDraft
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

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
    val layout = rememberKcResponsiveLayout()
    // Once the server profile is present it owns this screen's state. An
    // unrecognized server state fails closed instead of showing stale account
    // status (for example VERIFIED) alongside a pending verification shell.
    val status = if (recyclerProfile != null) {
        recyclerVerificationStatusFromAuthorization(recyclerProfile.authorizationStatus)
            ?: RecyclerVerificationStatus.PENDING
    } else {
        profile?.verificationStatus ?: RecyclerVerificationStatus.PENDING
    }
    val authorizationDetails = recyclerProfile?.authorizationDetails
    val evidenceAlreadySubmitted = authorizationDetails?.let {
        listOf(it.authority, it.type, it.registrationNumber, it.evidenceReference)
            .all { value -> !value.isNullOrBlank() }
    } == true
    val authorizationExpired = recyclerProfile?.authorizationStatus.equals("EXPIRED", ignoreCase = true)
    var authority by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.authority.orEmpty()) }
    var registrationNumber by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.registrationNumber.orEmpty()) }
    var authorizationType by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.type.orEmpty()) }
    var validUntil by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.validUntil?.take(10).orEmpty()) }
    var evidenceReference by remember(recyclerProfile?.id) { mutableStateOf(recyclerProfile?.authorizationDetails?.evidenceReference.orEmpty()) }
    val context = LocalContext.current
    var declarationAccepted by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<Int?>(null) }
    val detailRes = when (status) {
        RecyclerVerificationStatus.PENDING -> when {
            authorizationExpired -> R.string.recycler_verification_expired_detail
            recyclerProfile == null && loading -> R.string.recycler_verification_checking_detail
            recyclerProfile == null -> R.string.recycler_verification_unavailable_detail
            evidenceAlreadySubmitted -> R.string.recycler_verification_pending_detail
            else -> R.string.recycler_verification_evidence_needed_detail
        }
        RecyclerVerificationStatus.REJECTED -> R.string.recycler_verification_rejected_detail
        RecyclerVerificationStatus.UNDER_REVIEW -> R.string.recycler_verification_pending_detail
        RecyclerVerificationStatus.SUSPENDED -> R.string.recycler_verification_suspended_detail
        RecyclerVerificationStatus.VERIFIED -> R.string.recycler_verification_verified_detail
    }
    val statusColors = when (status) {
        RecyclerVerificationStatus.VERIFIED -> KcTheme.extended.successContainer to KcTheme.extended.onSuccessContainer
        RecyclerVerificationStatus.REJECTED, RecyclerVerificationStatus.SUSPENDED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        RecyclerVerificationStatus.PENDING, RecyclerVerificationStatus.UNDER_REVIEW -> KcTheme.extended.warningContainer to KcTheme.extended.onWarningContainer
    }
    // A new Recycler profile starts in PENDING before it has sent any evidence.
    // Only that empty pending profile (or a rejected profile) can submit.
    // Fail closed while profile data is unavailable so a refresh/error cannot
    // accidentally reopen a request already awaiting review.
    val canSubmit = status == RecyclerVerificationStatus.REJECTED || authorizationExpired ||
        (status == RecyclerVerificationStatus.PENDING && recyclerProfile != null && !evidenceAlreadySubmitted)
    val statusLabelRes = when (status) {
        RecyclerVerificationStatus.PENDING -> when {
            authorizationExpired -> R.string.recycler_verification_status_expired
            recyclerProfile == null && loading -> R.string.recycler_verification_status_checking
            recyclerProfile == null -> R.string.recycler_verification_status_unavailable
            evidenceAlreadySubmitted -> R.string.recycler_verification_status_pending
            else -> R.string.recycler_verification_status_evidence_needed
        }
        RecyclerVerificationStatus.VERIFIED -> R.string.recycler_verification_status_verified
        RecyclerVerificationStatus.REJECTED -> R.string.recycler_verification_status_rejected
        RecyclerVerificationStatus.UNDER_REVIEW -> R.string.recycler_verification_status_pending
        RecyclerVerificationStatus.SUSPENDED -> R.string.recycler_verification_status_suspended
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.recycler_verification_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(recyclerProfile?.name ?: profile?.displayName ?: stringResource(R.string.nav_profile), style = MaterialTheme.typography.titleMedium, maxLines = if (layout.isCompact) 2 else 1)
                }
            }
        }
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
        StatusCard(stringResource(statusLabelRes), stringResource(R.string.recycler_verification_status_detail), statusColors.first, statusColors.second)
        if (authorizationDetails != null && listOf(authorizationDetails.authority, authorizationDetails.type, authorizationDetails.registrationNumber, authorizationDetails.validUntil).any { !it.isNullOrBlank() }) {
            VerificationEvidenceSummary(recyclerProfile)
        }
        if (canSubmit) {
            Text(stringResource(R.string.recycler_verification_controlled_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        if (status == RecyclerVerificationStatus.VERIFIED) {
            Button(onClick = onOpenMarketplace, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(stringResource(R.string.recycler_verification_open_marketplace))
            }
        } else if (status == RecyclerVerificationStatus.SUSPENDED) {
            Text(stringResource(R.string.recycler_verification_suspended_action), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else if (status == RecyclerVerificationStatus.UNDER_REVIEW || status == RecyclerVerificationStatus.PENDING && !authorizationExpired && evidenceAlreadySubmitted) {
            Text(if (status == RecyclerVerificationStatus.UNDER_REVIEW) "Your facility verification is currently being reviewed." else stringResource(R.string.recycler_verification_already_submitted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    OutlinedButton(onClick = {
                        val today = java.util.Calendar.getInstance()
                        android.app.DatePickerDialog(context, { _, year, month, day -> validUntil = "%04d-%02d-%02d".format(year, month + 1, day) }, today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (validUntil.isBlank()) "Choose authorization validity date" else "Valid until $validUntil") }
                    OutlinedTextField(evidenceReference, { evidenceReference = it }, label = { Text(stringResource(R.string.recycler_verification_evidence_reference)) }, placeholder = { Text(stringResource(R.string.recycler_verification_evidence_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = declarationAccepted, onCheckedChange = { declarationAccepted = it })
                        Text(stringResource(R.string.recycler_verification_declaration), style = MaterialTheme.typography.bodyMedium)
                    }
                    localError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        onClick = {
                            val validationError = validateVerificationForm(authority, registrationNumber, authorizationType, validUntil, evidenceReference, declarationAccepted)
                            localError = validationError
                            if (validationError == null) onSubmit(RecyclerVerificationRequestDto(authority.trim(), registrationNumber.trim(), authorizationType.trim(), evidenceReference.trim(), validUntil.trim().ifBlank { null }))
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
    declarationAccepted: Boolean
): Int? = when {
    authority.trim().length < 2 -> R.string.recycler_verification_error_authority
    authorizationType.trim().length < 2 -> R.string.recycler_verification_error_type
    registrationNumber.trim().length < 2 -> R.string.recycler_verification_error_registration
    validUntil.isNotBlank() && !isFutureIsoDate(validUntil.trim()) -> R.string.recycler_verification_error_date
    evidenceReference.trim().length < 2 -> R.string.recycler_verification_error_evidence
    !declarationAccepted -> R.string.recycler_verification_error_declaration
    else -> null
}

/**
 * Keeps the date field usable with numeric keyboards, which usually do not
 * expose a hyphen key. Users can type or paste eight digits and the ISO
 * separators are inserted automatically.
 */
internal fun formatIsoDateInput(value: String): String {
    val digits = value.filter(Char::isDigit).take(8)
    return buildString {
        append(digits.take(4))
        if (digits.length > 4) append('-')
        append(digits.drop(4).take(2))
        if (digits.length > 6) append('-')
        append(digits.drop(6).take(2))
    }
}

internal fun isValidIsoDate(value: String): Boolean {
    if (!value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return false
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching { formatter.parse(value)?.let { formatter.format(it) == value } == true }.getOrDefault(false)
}

internal fun isFutureIsoDate(value: String): Boolean {
    if (!isValidIsoDate(value)) return false
    return runCatching {
        LocalDate.parse(value)
            .atTime(LocalTime.MAX)
            .atOffset(ZoneOffset.UTC)
            .toInstant()
            .isAfter(Instant.now())
    }.getOrDefault(false)
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
                style = MaterialTheme.typography.headlineMedium,
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
                        label = { Text(stringResource(R.string.safety_all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = materialFilter == "PCB",
                        onClick = { materialFilter = "PCB" },
                        label = { Text(stringResource(R.string.ui_copy_70606d6bfa77)) }
                    )
                }
                item {
                    FilterChip(
                        selected = materialFilter == "Copper",
                        onClick = { materialFilter = "Copper" },
                        label = { Text(stringResource(R.string.home_price_copper)) }
                    )
                }
                item {
                    FilterChip(
                        selected = needsResponseOnly,
                        onClick = { needsResponseOnly = !needsResponseOnly },
                        label = { Text(stringResource(R.string.ui_copy_1c000c699af7)) }
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
                                    Text(stringResource(R.string.ui_copy_63f67aed8b97))
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
                OutlinedTextField(rateText, { rateText = it.filter { char -> char.isDigit() || char == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_b76ac98c768f)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
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
                Text(stringResource(R.string.ui_copy_33906a7dfdec), style = MaterialTheme.typography.labelSmall)
                Text(openLots.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text(stringResource(R.string.ui_copy_c4c92ee8b1c7), style = MaterialTheme.typography.labelSmall)
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
    onScan: () -> Unit = {},
    paymentBusy: Set<String> = emptySet(),
    paymentError: String? = null,
    onRecordPayment: (String, com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyPaymentRequestDto) -> Unit = { _, _ -> }
) {
    val layout = rememberKcResponsiveLayout()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recycler_orders_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.recycler_orders_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!demoMode) TextButton(onClick = onRefresh) { Text(stringResource(R.string.future_refresh)) }
            }
        }
        paymentError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (demoMode) {
            item { DemoDataBanner() }
            item { OperationalSurface { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = KcTheme.extended.success); Text(stringResource(R.string.ui_copy_1aac17eab604), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium) }; Text(stringResource(R.string.ui_copy_6bf3d6a002ae), style = MaterialTheme.typography.bodyMedium); Text(stringResource(R.string.ui_copy_444420a8f1a2), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.ui_copy_a3f0118b1910)) } } } }
            item { Text(stringResource(R.string.recycler_no_orders), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (liveLoading) {
            item { LoadingContent(Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else if (liveError) {
            item { ErrorContent(onRetry = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else if (liveHandovers.isEmpty()) {
            item { EmptyContent(Modifier.fillMaxWidth().heightIn(min = 300.dp)) }
        } else {
            items(liveHandovers, key = { it.id }) { handover -> LiveOrderCard(handover, onScan, handover.id in paymentBusy, onRecordPayment) }
        }
    }
}

@Composable
private fun LiveOrderCard(handover: SupplyHandoverDto, onScan: () -> Unit, paymentBusy: Boolean, onRecordPayment: (String, com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyPaymentRequestDto) -> Unit) {
    val status = (if (handover.status == "COMPLETED") "Material received" else handover.status).replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    val expiresAtMs = handover.expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    var nowEpochMs by remember(handover.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(handover.id, expiresAtMs) {
        if (expiresAtMs != null && expiresAtMs > nowEpochMs) {
            delay(expiresAtMs - nowEpochMs)
            nowEpochMs = System.currentTimeMillis()
        }
    }
    val qrExpired = expiresAtMs != null && expiresAtMs <= nowEpochMs
    OperationalSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (handover.status == "COMPLETED") Icons.Filled.Verified else Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.recycler_order_reference, handover.referenceId.ifBlank { handover.id.takeLast(8) }), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.recycler_order_weight, handover.finalAcceptedKg ?: handover.quotedWeightKg), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ui_copy_942b539440f3, handover.materialCategory.replace('_', ' '), "%.0f".format(handover.finalRatePerKg ?: handover.quotedRatePerKg)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.recycler_order_status, status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SupplyPaymentSection(handover, recycler = true, busy = paymentBusy, onRecord = onRecordPayment)
            if (handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) handover.expiresAt?.let { Text("QR expires: ${com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat.dateTimeIso(it) ?: "Time unavailable"} India time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (qrExpired && handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) Text(stringResource(R.string.ui_copy_b7ba58863e37), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (handover.status == "COLLECTOR_CONFIRMED" && !qrExpired) Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.recycler_order_scan)) }
        }
    }
}

@Composable
fun RecyclerScanScreen(
    state: RecyclerScanState,
    onVerify: (String) -> Unit,
    onConfirm: (Double, Boolean, String?) -> Unit,
    onReset: () -> Unit,
    onReceiptRecorded: () -> Unit = {}
) {
    LaunchedEffect(state.supplyConfirmed?.id, state.confirmed) {
        if (state.supplyConfirmed != null || state.confirmed) onReceiptRecorded()
    }
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
    var scannerLaunchError by rememberSaveable { mutableStateOf(false) }
    val scannerPrompt = stringResource(R.string.recycler_scan_title)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf(String::isNotBlank)?.let { value ->
            scannerLaunchError = false
            reference = value
            onVerify(value)
        }
    }
    val layout = rememberKcResponsiveLayout()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        Icon(Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.recycler_scan_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.recycler_scan_explanation), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                scannerLaunchError = false
                runCatching {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setBeepEnabled(false)
                            .setPrompt(scannerPrompt)
                    )
                }.onFailure { scannerLaunchError = true }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Icon(Icons.Filled.QrCodeScanner, null); Text(stringResource(R.string.recycler_scan_camera)) }
        OutlinedTextField(reference, { reference = it }, label = { Text(stringResource(R.string.recycler_scan_reference)) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onVerify(reference) }, enabled = reference.isNotBlank() && !state.checking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (state.checking) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.recycler_scan_validate))
        }
        if (state.error || scannerLaunchError) Text(stringResource(R.string.recycler_scan_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        state.supplyVerified?.let { handover ->
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = KcTheme.extended.success); Text(stringResource(R.string.recycler_passport_matched), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium) }
                    Text("${handover.referenceId} · ${handover.materialCategory.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ui_copy_a48304fae928, "%.1f".format(handover.quotedWeightKg), "%.0f".format(handover.quotedRatePerKg)))
                    Text(if (state.supplyFromCache) "Matched from saved passport · server confirmation is pending." else "Signed QR matched to the current server record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(actualWeight, { actualWeight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.handover_final_weight_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(materialMatch, { materialMatch = it }); Text(stringResource(R.string.handover_material_confirmed)) }
                    OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.recycler_scan_notes)) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { actualWeight.toDoubleOrNull()?.let { onConfirm(it, materialMatch, notes) } }, enabled = actualWeight.toDoubleOrNull()?.let { it > 0 && it <= 100000 } == true && !state.confirming && !state.supplyQueued && state.supplyConfirmed == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        if (state.confirming) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text(if (state.supplyQueued) "Saved on device — waiting to sync" else if (state.supplyConfirmed != null) "Receipt recorded" else "Confirm received material")
                    }
                    state.supplyQueued.takeIf { it }?.let { Text(stringResource(R.string.recycler_receipt_saved), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge) }
                    state.supplyConfirmed?.let { confirmed -> Text(stringResource(R.string.ui_copy_95f79e5abc82, statusNameForSupply(confirmed.status), "%.1f".format(confirmed.finalAcceptedKg ?: 0.0)), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge) }
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
@OptIn(ExperimentalLayoutApi::class)
fun RecyclerPickupsScreen(
    demoMode: Boolean = false,
    availability: String? = null,
    profile: com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto? = null,
    loading: Boolean = false,
    saving: Boolean = false,
    savingSection: String? = null,
    savedSection: String? = null,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onSave: (String) -> Unit = {},
    onSavePricing: (RecyclerProfileUpdateRequestDto) -> Unit = {}
) {
    val layout = rememberKcResponsiveLayout()
    var logisticsSection by rememberSaveable { mutableStateOf("AVAILABILITY") }
    var pickupReady by remember { mutableStateOf(false) }
    var selectedAvailability by remember(availability) { mutableStateOf(availability ?: "FLEXIBLE") }
    var pickupEnabled by remember(profile?.pickupAvailable) { mutableStateOf(profile?.pickupAvailable ?: false) }
    var freeKm by remember(profile?.serviceArea?.pickupFreeRadiusKm) { mutableStateOf((profile?.serviceArea?.pickupFreeRadiusKm ?: 0.0).toString()) }
    var maxKm by remember(profile?.serviceArea?.maxPickupDistanceKm) { mutableStateOf((profile?.serviceArea?.maxPickupDistanceKm ?: 25.0).toString()) }
    var pricingMode by remember(profile?.pickupIncluded, profile?.pickupFee) { mutableStateOf(if (profile?.pickupIncluded == true) "FREE" else if (profile?.pickupFee != null) "FIXED" else "PER_KM") }
    var amount by remember(profile?.pickupFee, profile?.serviceArea?.logisticsCostPerKm) { mutableStateOf((profile?.pickupFee ?: profile?.serviceArea?.logisticsCostPerKm ?: 0.0).toString()) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        Text(stringResource(R.string.recycler_pickups_title), style = MaterialTheme.typography.headlineMedium)
        if (demoMode) {
            DemoDataBanner()
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.ui_copy_8c640ec935e8), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium) }
                    Text(stringResource(R.string.ui_copy_893c1a36eccd), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ui_copy_c9d5dc070597), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pickupReady) Text(stringResource(R.string.recycler_pickup_ready), color = KcTheme.extended.success, style = MaterialTheme.typography.labelLarge)
                    else Button(onClick = { pickupReady = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.ui_copy_f024533938d2)) }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = logisticsSection == "AVAILABILITY", onClick = { logisticsSection = "AVAILABILITY" }, label = { Text("Availability") })
                FilterChip(selected = logisticsSection == "PRICING", onClick = { logisticsSection = "PRICING" }, label = { Text("Pickup charges") })
            }
            Text(if (logisticsSection == "AVAILABILITY") "Choose when your facility can receive material." else "Set the distance and cost for facility pickup.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (logisticsSection == "AVAILABILITY") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("TODAY" to "Today", "THIS_WEEK" to "This week", "FLEXIBLE" to "Flexible").forEach { (value, label) ->
                    FilterChip(selected = selectedAvailability == value, onClick = { selectedAvailability = value }, label = { Text(label) })
                }
            }
            OperationalSurface {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.ui_copy_94a285f814bc, selectedAvailability.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.recycler_availability_visible), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.common_retry)) } }
            Button(onClick = { onSave(selectedAvailability) }, enabled = !saving && !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (savingSection == "availability") CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(if (savingSection == "availability") "Saving…" else "Save availability")
            }
            if (savedSection == "availability") Text("Availability saved", color = KcTheme.extended.success)
            } else {
            Text(stringResource(R.string.ui_copy_321def52e839), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_85f335fd0fc5), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_copy_6a16e2fe1b83), modifier = Modifier.weight(1f))
                Switch(checked = pickupEnabled, onCheckedChange = { pickupEnabled = it })
            }
            if (pickupEnabled) {
                OutlinedTextField(maxKm, { maxKm = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_8598b6125c92)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FREE" to "Free", "FIXED" to "Fixed fee", "PER_KM" to "Per km").forEach { (key, label) -> FilterChip(selected = pricingMode == key, onClick = { pricingMode = key }, label = { Text(label) }) }
                }
                if (pricingMode == "PER_KM") OutlinedTextField(freeKm, { freeKm = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_88026327403e)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (pricingMode != "FREE") OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(if (pricingMode == "FIXED") "Fixed pickup fee · ₹" else "Beyond free distance · ₹/km") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            val max = maxKm.toDoubleOrNull()
            val free = freeKm.toDoubleOrNull()
            val charge = amount.toDoubleOrNull()
            val validPricing = !pickupEnabled || max != null && max > 0 && max <= 200 && (pricingMode == "FREE" || charge != null && charge >= 0) && (pricingMode != "PER_KM" || free != null && free >= 0 && free <= max)
            Button(onClick = {
                onSavePricing(if (!pickupEnabled) RecyclerProfileUpdateRequestDto(pickupAvailable = false) else RecyclerProfileUpdateRequestDto(pickupAvailable = true, maxPickupDistanceKm = max, pickupFreeRadiusKm = if (pricingMode == "PER_KM") free else 0.0, pickupIncluded = pricingMode == "FREE", pickupFee = if (pricingMode == "FIXED") charge else null, logisticsCostPerKm = if (pricingMode == "PER_KM") charge else null))
            }, enabled = validPricing && !saving && !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (savingSection == "pricing") "Saving…" else "Save pickup charges") }
            if (savedSection == "pricing") Text("Pickup charges saved", color = KcTheme.extended.success)
            }
        }
    }
}

@Composable
fun RecyclerRatesScreen(
    rates: List<com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto> = emptyList(),
    acceptedMaterials: List<String> = listOf("PCB", "COPPER"),
    supportedMaterials: List<String> = emptyList(),
    materialsError: Boolean = false,
    loading: Boolean = false,
    saving: Boolean = false,
    saved: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onSave: (List<RecyclerRateUpdateDto>) -> Unit = {},
    demoMode: Boolean = false
) {
    val layout = rememberKcResponsiveLayout()
    val categories = remember(acceptedMaterials, supportedMaterials, rates) { (acceptedMaterials + rates.map { it.materialCategory } + supportedMaterials).filter { it.isNotBlank() }.distinct() }
    var values by remember(categories, rates) { mutableStateOf(categories.associateWith { category -> rates.firstOrNull { it.materialCategory == category }?.pricePerKg?.toString().orEmpty() }) }
    var showAllMaterials by remember { mutableStateOf(false) }
    var materialQuery by remember { mutableStateOf("") }
    val visibleCategories = categories.filter { category ->
        (showAllMaterials || category in acceptedMaterials || rates.any { it.materialCategory == category }) &&
            category.displayMaterial().contains(materialQuery.trim(), ignoreCase = true)
    }
    val valid = values.values.any { it.toDoubleOrNull()?.let { value -> value > 0 } == true }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        Text(stringResource(R.string.recycler_rates_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.recycler_rates_detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (demoMode) DemoDataBanner()
        if (loading && rates.isEmpty()) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        if (materialsError) Text(stringResource(R.string.ui_copy_e828ee900b54), color = MaterialTheme.colorScheme.error)
        error?.let { Text(stringResource(R.string.recycler_rates_load_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(R.string.ui_copy_25e5313f975c, rates.size, acceptedMaterials.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(materialQuery, { materialQuery = it }, label = { Text(stringResource(R.string.ui_copy_d48f6233962b)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        visibleCategories.forEach { category -> RateEditor(category.displayMaterial(), values[category].orEmpty()) { value -> values = values + (category to value) } }
        if (categories.size > visibleCategories.size && materialQuery.isBlank()) TextButton(onClick = { showAllMaterials = true }) { Text(stringResource(R.string.ui_copy_83c5840ff9e4, categories.size)) }
        if (showAllMaterials && materialQuery.isBlank()) TextButton(onClick = { showAllMaterials = false }) { Text(stringResource(R.string.ui_copy_eb127c0c7f2c)) }
        if (saved) Text(stringResource(R.string.recycler_rate_draft_saved), color = KcTheme.extended.success, style = MaterialTheme.typography.bodyMedium)
        if (error != null || materialsError) OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.common_retry)) }
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

@Composable private fun RateEditor(label: String, value: String, onValueChange: (String) -> Unit) { OutlinedTextField(value, { onValueChange(it.filter { char -> char.isDigit() || char == '.' }.take(7)) }, label = { Text(stringResource(R.string.ui_copy_1157ba826e05, label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = value.isNotBlank() && value.toDoubleOrNull()?.let { it <= 0 } == true, modifier = Modifier.fillMaxWidth()) }

@Composable
fun RecyclerProfileScreen(profile: AccountProfile?, onSave: ((ProfileEditDraft) -> Unit)? = null, saving: Boolean = false, saveError: String? = null, onOpenSettings: () -> Unit = {}) {
    ProfileScreen(profile, onSave = onSave, saving = saving, saveError = saveError, onOpenSettings = onOpenSettings)
}

@Composable private fun StatusCard(label: String, detail: String, color: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) { Surface(color = color, contentColor = contentColor, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, tint = contentColor); Column(Modifier.padding(start = 12.dp)) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium) } } } }

@Composable private fun OperationalSurface(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}
