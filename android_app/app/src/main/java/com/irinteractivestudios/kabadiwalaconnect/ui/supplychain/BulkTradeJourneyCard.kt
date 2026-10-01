package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.SupplyPaymentSection

/** One direct trade, from agreed offer through material receipt and confirmed money. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkTradeJourneyCard(
    lotId: String,
    lot: BulkLotDto?,
    offer: BulkOfferDto?,
    handover: SupplyHandoverDto?,
    details: BulkTradeDetailsDto?,
    detailsError: String?,
    recycler: Boolean,
    busy: Set<String>,
    onLoadDetails: (String) -> Unit,
    onPrepare: (String, String) -> Unit = { _, _ -> },
    onConfirmCollector: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onChat: (String, String) -> Unit = { _, _ -> },
    onRecordPayment: (String, SupplyPaymentRequestDto) -> Unit = { _, _ -> },
    onConfirmPayment: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val fulfillmentMode = lot?.fulfillmentMode ?: details?.fulfillmentMode
    val fixedLocation = when (fulfillmentMode) {
        "COLLECTOR_DELIVERY" -> "RECYCLER_FACILITY"
        "RECYCLER_PICKUP" -> "COLLECTOR_LOCATION"
        else -> null
    }
    var locationType by rememberSaveable(lotId) { mutableStateOf("COLLECTOR_LOCATION") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember(handover?.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(handover?.id, handover?.expiresAt, handover?.status, lifecycle) {
        if (handover?.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { now = System.currentTimeMillis(); delay(1_000) }
            }
        }
    }
    LaunchedEffect(lotId, handover?.id, handover?.status) { onLoadDetails(lotId) }
    val counterparty = if (recycler) details?.collectorLocation else details?.recyclerLocation
    val active = handover?.status !in setOf("COMPLETED", "CANCELLED", "EXPIRED", "REVIEW_REQUIRED") && lot?.status != "SOLD"
    val payment = handover?.payments?.firstOrNull()
    val step = when {
        payment?.status == "VERIFIED" -> 4
        payment != null && handover.status == "COMPLETED" -> 3
        handover?.status == "COMPLETED" -> 2
        handover != null -> 1
        else -> 0
    }
    val amount = handover?.finalValue ?: handover?.quotedValue
        ?: lot?.let { it.quantityKg * (offer?.offeredRatePerKg ?: it.askingRatePerKg) }
    val quoted = handover?.quotedWeightKg ?: lot?.quantityKg ?: 0.0
    val actual = handover?.finalAcceptedKg ?: quoted
    val agreedLocation = details?.handoverLocation
    val destinationType = agreedLocation?.type ?: fixedLocation
    val meetingProfile = when (destinationType) {
        "COLLECTOR_LOCATION" -> details?.collectorLocation
        "RECYCLER_FACILITY" -> details?.recyclerLocation
        else -> counterparty
    }
    val travels = fulfillmentMode == null || (recycler && fulfillmentMode == "RECYCLER_PICKUP") || (!recycler && fulfillmentMode == "COLLECTOR_DELIVERY")
    // When the server has recorded a third-party destination, use it rather
    // than silently directing the user to either participant's profile.
    val meetingDestination = when (destinationType) {
        "THIRD_PARTY" -> agreedLocation?.latitude?.let { lat ->
            agreedLocation.longitude?.let { lng -> if (lat.isFinite() && lng.isFinite()) "$lat,$lng" else null }
        } ?: agreedLocation?.areaName?.takeIf { it.isNotBlank() }
        "RECYCLER_FACILITY", "COLLECTOR_LOCATION" -> tradeDirectionsDestination(meetingProfile)
        else -> tradeDirectionsDestination(counterparty)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(materialName(handover?.materialCategory ?: lot?.materialCategory ?: "OTHER"),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            counterparty?.name?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            bulkTransportLabel(fulfillmentMode)?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.recycler_order_weight, actual), style = MaterialTheme.typography.bodyMedium)
            val rate = handover?.finalRatePerKg ?: handover?.quotedRatePerKg ?: offer?.offeredRatePerKg
            rate?.let {
                Text(stringResource(R.string.deal_offered_rate) + " · ₹${"%.2f".format(it)}/kg",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(if (handover?.finalValue != null) R.string.deal_final_amount else R.string.deal_expected_total), style = MaterialTheme.typography.labelLarge)
            Text(amount?.let { "₹${"%.2f".format(it)}" } ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            val steps = listOf(
                statusName("ACCEPTED"), stringResource(R.string.recycler_scan_title),
                stringResource(R.string.recycler_scan_confirmed), stringResource(R.string.payment_title),
                statusName("COMPLETED")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, label ->
                    Text("${index + 1}  $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal)
                }
            }
            if (handover?.status == "REVIEW_REQUIRED") {
                Text(statusName("REVIEW_REQUIRED"), color = MaterialTheme.colorScheme.error)
                handover.reviewReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (active) {
                val destinationLabel = when (destinationType) {
                    "COLLECTOR_LOCATION" -> stringResource(R.string.handover_collector)
                    "RECYCLER_FACILITY" -> stringResource(R.string.handover_recycler)
                    "THIRD_PARTY" -> stringResource(R.string.handover_third_party)
                    else -> counterparty?.address ?: counterparty?.areaName
                }
                destinationLabel?.let { Text(stringResource(R.string.handover_location_label, it), style = MaterialTheme.typography.bodyMedium) }
                val routeAddress = if (destinationType == "THIRD_PARTY") agreedLocation?.areaName
                    else meetingProfile?.address?.takeIf { it.isNotBlank() } ?: meetingProfile?.areaName
                routeAddress?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (travels) OutlinedButton(
                    enabled = meetingDestination != null,
                    onClick = {
                        if (meetingDestination != null && !openPickupDirections(context, meetingDestination)) {
                            Toast.makeText(context, "Google Maps directions could not be opened.", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Directions, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_copy_e36720dff49c) + (counterparty?.name?.takeIf { agreedLocation?.type != "THIRD_PARTY" }?.let { " · $it" } ?: ""))
                }
                val partnerId = if (recycler) details?.collectorId ?: lot?.kabadiwalaId else details?.recyclerId ?: offer?.recyclerId
                OutlinedButton(enabled = !partnerId.isNullOrBlank(), onClick = { partnerId?.let { onChat(lotId, it) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (recycler) R.string.ui_copy_9b1318f8a1b8 else R.string.ui_copy_e68ddcadeb21))
                }
            }
            detailsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onLoadDetails(lotId) }, enabled = "trade-details-$lotId" !in busy) { Text(stringResource(R.string.common_retry)) }
            }
            if (!recycler && (handover == null || handover.status in setOf("EXPIRED", "CANCELLED"))) {
                if (fixedLocation == null) {
                  Text(stringResource(R.string.handover_choose_location), fontWeight = FontWeight.SemiBold)
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("COLLECTOR_LOCATION" to R.string.handover_collector, "RECYCLER_FACILITY" to R.string.handover_recycler).forEach { (type, label) ->
                        FilterChip(selected = locationType == type, onClick = { locationType = type }, label = { Text(stringResource(label)) })
                    }
                  }
                }
                Button(onClick = { onPrepare(lotId, fixedLocation ?: locationType) }, enabled = "handover-bulk-$lotId" !in busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if ("handover-bulk-$lotId" in busy) stringResource(R.string.common_syncing)
                        else stringResource(R.string.ui_copy_6f8511b380c6) + " · " + stringResource(R.string.ui_copy_7be330b874d6))
                }
            }
            if (handover != null && handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) {
                val expired = handover.expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() <= now }.getOrDefault(true) } ?: false
                if (!recycler) {
                    val bitmap = remember(handover.qrCodeData, handover.recyclerQrScannedAt, expired) { if (!expired && handover.recyclerQrScannedAt == null) handover.qrCodeData?.let(::createSupplyQr) else null }
                    bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.handover_qr), Modifier.size(220.dp).align(Alignment.CenterHorizontally)) }
                    if (handover.recyclerQrScannedAt != null && !expired) {
                        Text(stringResource(R.string.recycler_scan_verified), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.handover_collector_confirmed), style = MaterialTheme.typography.bodySmall)
                    }
                    if (handover.status == "PREPARED" && !expired) Button(
                        onClick = { onConfirmCollector(handover.id) }, enabled = "collector-confirm-${handover.id}" !in busy && "handover-bulk-$lotId" !in busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(stringResource(R.string.ui_copy_7be330b874d6)) }
                    if (handover.status == "COLLECTOR_CONFIRMED" && !expired && handover.recyclerQrScannedAt == null) Text(stringResource(R.string.ui_copy_fecc7ddf5e07), style = MaterialTheme.typography.bodySmall)
                    if (expired) Button(onClick = { onPrepare(lotId, fixedLocation ?: agreedLocation?.type ?: locationType) },
                        enabled = "handover-bulk-$lotId" !in busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy_154331c55f35)) }
                } else {
                    if (expired) {
                        Text(stringResource(R.string.ui_copy_b7ba58863e37), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else if (handover.status == "PREPARED") {
                        Text(stringResource(R.string.household_waiting_for, counterparty?.name ?: stringResource(R.string.auth_demo_kabadiwala)), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.auth_demo_kabadiwala) + " · " + stringResource(R.string.ui_copy_7be330b874d6), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onScan, enabled = handover.status == "COLLECTOR_CONFIRMED" && !expired,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.recycler_order_scan)) }
                }
            }
            if (recycler && handover?.status == "EXPIRED") {
                Text(stringResource(R.string.ui_copy_b7ba58863e37), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (recycler && handover?.status == "CANCELLED") {
                Text(statusName("CANCELLED"), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.auth_demo_kabadiwala) + " · " + stringResource(R.string.ui_copy_6f8511b380c6), style = MaterialTheme.typography.bodySmall)
            }
            if (recycler && handover == null) {
                Text(stringResource(R.string.demo_recycler_status_accepted), style = MaterialTheme.typography.bodySmall)
            }
            handover?.let {
                SupplyPaymentSection(it, recycler, busy = "supply-payment-${it.id}" in busy,
                    onRecord = onRecordPayment, onConfirm = onConfirmPayment)
            }
        }
    }
}

@Composable
internal fun bulkTransportLabel(mode: String?): String? = when (mode) {
    "COLLECTOR_DELIVERY" -> stringResource(R.string.bulk_collector_delivery)
    "RECYCLER_PICKUP" -> stringResource(R.string.bulk_recycler_pickup)
    else -> null
}

private fun tradeDirectionsDestination(location: TradeLocationDto?): String? =
    location?.address?.trim()?.takeIf(String::isNotBlank)
        ?: if (location?.latitude != null && location.longitude != null &&
            location.latitude.isFinite() && location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0) {
            "${location.latitude},${location.longitude}"
        } else location?.areaName?.trim()?.takeIf(String::isNotBlank)

@Preview(name = "Recycler receipt / 320dp / large text", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Composable
private fun RecyclerReceiptPreview() {
    KabadiwalaConnectTheme {
        BulkTradeJourneyCard(
            lotId = "preview", lot = null, offer = null,
            handover = SupplyHandoverDto(id = "receipt", bulkLotId = "preview", status = "COMPLETED",
                materialCategory = "COPPER", quotedWeightKg = 2.0, finalAcceptedKg = 2.0,
                quotedValue = 1200.0, finalValue = 1200.0),
            details = BulkTradeDetailsDto(collectorLocation = TradeLocationDto(name = "Local collector")),
            detailsError = null, recycler = true, busy = emptySet(), onLoadDetails = {}
        )
    }
}

@Preview(name = "Collector accepted trade / light", widthDp = 430, showBackground = true)
@Composable
private fun CollectorTradePreview() {
    KabadiwalaConnectTheme(darkTheme = false) {
        BulkTradeJourneyCard(
            lotId = "preview", lot = BulkLotDto(id = "preview", materialCategory = "COPPER",
                quantityKg = 2.0, askingRatePerKg = 600.0, status = "RESERVED"),
            offer = null, handover = null,
            details = BulkTradeDetailsDto(recyclerId = "recycler",
                recyclerLocation = TradeLocationDto(name = "Recycling facility", address = "Delhi")),
            detailsError = null, recycler = false, busy = emptySet(), onLoadDetails = {}
        )
    }
}
