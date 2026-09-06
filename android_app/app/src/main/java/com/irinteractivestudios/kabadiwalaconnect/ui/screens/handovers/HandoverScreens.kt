package com.irinteractivestudios.kabadiwalaconnect.ui.screens.handovers

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DealSheetCard
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ProofRow
import java.text.DateFormat
import java.util.Date

@Composable
fun HandoverCreateScreen(lot: Lot, quote: Quote, collectorId: String, onSave: (HandoverLocationType, String, Long) -> Unit) {
    var type by remember { mutableStateOf(HandoverLocationType.RECYCLER_FACILITY) }
    var location by remember { mutableStateOf(quote.recyclerName) }
    val label = when (type) { HandoverLocationType.COLLECTOR_LOCATION -> stringResource(R.string.handover_collector); HandoverLocationType.RECYCLER_FACILITY -> stringResource(R.string.handover_recycler); HandoverLocationType.THIRD_PARTY -> stringResource(R.string.handover_third_party) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.handover_create_title), style = MaterialTheme.typography.headlineMedium)
        EvidenceSection(title = stringResource(R.string.quote_summary), status = stringResource(R.string.quote_status, quote.status.name)) {
                ProofRow(stringResource(R.string.handover_material), lot.materialLabel)
                ProofRow(stringResource(R.string.handover_weight), "%.1f kg".format(lot.weightKg))
                ProofRow(stringResource(R.string.handover_recycler), quote.recyclerName)
                Text("₹%.0f".format(quote.amountRupees), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        }
        Text(stringResource(R.string.handover_choose_location), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HandoverLocationType.entries.forEach { item -> FilterChip(selected = type == item, onClick = { type = item; location = if (item == HandoverLocationType.RECYCLER_FACILITY) quote.recyclerName else "" }, label = { Text(when (item) { HandoverLocationType.COLLECTOR_LOCATION -> stringResource(R.string.handover_collector); HandoverLocationType.RECYCLER_FACILITY -> stringResource(R.string.handover_recycler); HandoverLocationType.THIRD_PARTY -> stringResource(R.string.handover_third_party) }) }) } }
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.handover_location_label, label)) }, minLines = 2)
        Text(stringResource(R.string.handover_time_review, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.handover_offline_note))
        Button(onClick = { if (location.isNotBlank()) onSave(type, location.trim(), System.currentTimeMillis()) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = location.isNotBlank()) { Text(stringResource(R.string.handover_save)) }
    }
}

@Composable
fun HandoverDocumentScreen(
    handover: Handover,
    photoPath: String?,
    referencePrice: Price? = null,
    onUpdateEvidence: (actualWeightKg: Double, materialConfirmed: Boolean, scalePhotoPath: String?) -> Unit = { _, _, _ -> },
    onCaptureScalePhoto: () -> Unit = {},
    onOpenDispute: () -> Unit = {},
    onRate: () -> Unit = {},
    onMark: () -> Unit
) {
    val context = LocalContext.current
    val shareText = stringResource(R.string.handover_share_text, handover.id, handover.materialLabel, handover.quotedPriceRupees)
    val qr = remember(handover.id) { createQr(handover.id) }
    var actualWeightText by remember(handover.id, handover.actualWeightKg) { mutableStateOf(handover.actualWeightKg?.toString() ?: handover.weightKg.toString()) }
    var materialConfirmed by remember(handover.id, handover.materialConfirmed) { mutableStateOf(handover.materialConfirmed) }
    val actualWeight = actualWeightText.toDoubleOrNull()
    val evidenceReady = actualWeight != null && actualWeight > 0 && actualWeight <= 500 && materialConfirmed
    val diff = actualWeight?.let { kotlin.math.abs(it - handover.weightKg) / handover.weightKg }
    val dealLot = Lot(id = handover.lotId, materialLabel = handover.materialLabel, condition = "", weightKg = handover.weightKg, location = handover.collectionLocation, createdAtEpochMs = handover.createdAtEpochMs)
    val dealQuote = Quote(id = handover.quoteId, recyclerId = handover.recyclerId, lotId = handover.lotId, amountRupees = handover.quotedPriceRupees, recyclerName = handover.recyclerName, pricePerKg = if (handover.weightKg > 0) handover.quotedPriceRupees / handover.weightKg else 0.0, marketRatePerKg = referencePrice?.ratePerKg ?: 0.0)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.handover_document_title), style = MaterialTheme.typography.headlineMedium) }
        item { photoPath?.let { path -> android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()?.let { Image(it, stringResource(R.string.handover_photo), Modifier.fillMaxWidth().height(180.dp)) } } }
        item { DealSheetCard(dealLot, dealQuote, referencePrice, handover.actualWeightKg, handover.actualWeightKg?.let { dealQuote.pricePerKg * it }) }
        item { EvidenceSection(title = handover.id, status = stringResource(if (handover.status == HandoverStatus.HANDED_OVER) R.string.handover_status_done else R.string.handover_status_saved)) { Text("₹%.0f".format(handover.quotedPriceRupees), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold); ProofRow(stringResource(R.string.handover_material), handover.materialLabel); ProofRow(stringResource(R.string.handover_weight), "%.1f kg".format(handover.weightKg)); ProofRow(stringResource(R.string.handover_collection), handover.collectionLocation); ProofRow(stringResource(R.string.handover_handover_location), handover.handoverLocation); ProofRow(stringResource(R.string.handover_date), DateFormat.getDateTimeInstance().format(Date(handover.timestampEpochMs))); ProofRow(stringResource(R.string.handover_recycler), handover.recyclerName); ProofRow(stringResource(R.string.handover_collector_id), handover.collectorId) } }
        item {
            EvidenceSection(title = stringResource(R.string.handover_scale_proof_title), status = if (evidenceReady) stringResource(R.string.handover_proof_saved) else null) {
                    Text(stringResource(R.string.handover_scale_proof_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(actualWeightText, { actualWeightText = it.filter { char -> char.isDigit() || char == '.' }.take(7) }, label = { Text(stringResource(R.string.handover_final_weight_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(materialConfirmed, { materialConfirmed = it })
                        Text(stringResource(R.string.handover_material_confirmed), style = MaterialTheme.typography.bodyLarge)
                    }
                    handover.scalePhotoPath?.let { path -> android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()?.let { Image(it, stringResource(R.string.handover_scale_photo), Modifier.fillMaxWidth().height(150.dp)) } }
                    OutlinedButton(onClick = onCaptureScalePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(if (handover.scalePhotoPath == null) R.string.handover_add_scale_photo else R.string.handover_retake_scale_photo)) }
                    diff?.takeIf { it > .05 }?.let { Text(stringResource(R.string.handover_weight_difference), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(onClick = { onUpdateEvidence(actualWeight!!, materialConfirmed, handover.scalePhotoPath) }, enabled = evidenceReady, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.handover_save_proof)) }
                    if (handover.evidenceUpdatedAtEpochMs != null) Text(stringResource(R.string.handover_proof_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        item { qr?.let { Image(it.asImageBitmap(), stringResource(R.string.handover_qr), Modifier.size(180.dp)) }; Text(stringResource(R.string.handover_expiry), style = MaterialTheme.typography.bodyMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, null)) }) { Text(stringResource(R.string.handover_share)) }; OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:"); putExtra("sms_body", shareText) }) }) { Text(stringResource(R.string.handover_sms)) } } }
        item { OutlinedButton(onClick = onOpenDispute, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.handover_report_problem)) } }
        if (handover.status == HandoverStatus.HANDED_OVER) item { OutlinedButton(onClick = onRate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.handover_rate_recycler)) } }
        item { Button(onClick = onMark, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = handover.status != HandoverStatus.HANDED_OVER) { Text(stringResource(R.string.handover_mark_done)) } }
    }
}

@Composable
fun DisputeCenterScreen(
    handover: Handover,
    disputes: List<Dispute>,
    onSubmit: (DisputeType, String) -> Unit
) {
    var type by remember { mutableStateOf(DisputeType.WEIGHT_DISCREPANCY) }
    var description by remember { mutableStateOf("") }
    val hasOpenReport = disputes.any { it.status != DisputeStatus.RESOLVED && it.status != DisputeStatus.REJECTED }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(stringResource(R.string.dispute_title), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.dispute_detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            EvidenceSection(title = handover.materialLabel, status = stringResource(if (handover.status == HandoverStatus.HANDED_OVER) R.string.handover_status_done else R.string.handover_status_saved)) {
                ProofRow(stringResource(R.string.handover_weight), "%.1f kg".format(handover.weightKg))
                ProofRow(stringResource(R.string.handover_recycler), handover.recyclerName)
                handover.actualWeightKg?.let { ProofRow(stringResource(R.string.handover_final_weight_label), "%.1f kg".format(it)) }
            }
        }
        if (hasOpenReport) {
            item {
                EvidenceSection(title = stringResource(R.string.dispute_submitted), status = stringResource(R.string.dispute_pending_sync)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.dispute_pending_sync), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.dispute_reason_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DisputeReasonChip(DisputeType.WEIGHT_DISCREPANCY, type, R.string.dispute_reason_weight) { type = it }
                DisputeReasonChip(DisputeType.MATERIAL_MISMATCH, type, R.string.dispute_reason_material) { type = it }
                DisputeReasonChip(DisputeType.PRICE_DISAGREEMENT, type, R.string.dispute_reason_price) { type = it }
                DisputeReasonChip(DisputeType.OTHER, type, R.string.dispute_reason_other) { type = it }
            }
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text(stringResource(R.string.dispute_description_label)) },
                placeholder = { Text(stringResource(R.string.dispute_description_hint)) }
            )
        }
        item {
            Button(
                onClick = { onSubmit(type, description.trim()) },
                enabled = description.trim().length >= 10 && !hasOpenReport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(stringResource(R.string.dispute_submit)) }
        }
        item { Text(stringResource(R.string.dispute_timeline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (disputes.isEmpty()) item { Text(stringResource(R.string.dispute_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(disputes, key = { it.id }) { dispute ->
            EvidenceSection(title = disputeTypeLabel(dispute.type), status = disputeStatusLabel(dispute.status)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dispute.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DisputeReasonChip(type: DisputeType, selected: DisputeType, label: Int, onSelect: (DisputeType) -> Unit) {
    FilterChip(selected = type == selected, onClick = { onSelect(type) }, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun disputeTypeLabel(type: DisputeType) = stringResource(
    when (type) {
        DisputeType.WEIGHT_DISCREPANCY -> R.string.dispute_reason_weight
        DisputeType.MATERIAL_MISMATCH -> R.string.dispute_reason_material
        DisputeType.PRICE_DISAGREEMENT -> R.string.dispute_reason_price
        DisputeType.OTHER -> R.string.dispute_reason_other
    }
)

@Composable
private fun disputeStatusLabel(status: DisputeStatus) = stringResource(
    when (status) {
        DisputeStatus.SAVED_LOCALLY -> R.string.dispute_status_saved
        DisputeStatus.OPEN -> R.string.dispute_status_open
        DisputeStatus.UNDER_REVIEW -> R.string.dispute_status_under_review
        DisputeStatus.RESOLVED -> R.string.dispute_status_resolved
        DisputeStatus.REJECTED -> R.string.dispute_status_rejected
    }
)

@Composable private fun Detail(label: Int, value: String) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.bodyLarge) }
private fun createQr(value: String): Bitmap? = runCatching { val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 480, 480); Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565).also { bitmap -> for (x in 0 until 480) for (y in 0 until 480) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }.getOrNull()
