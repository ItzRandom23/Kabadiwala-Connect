package com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject

@Composable
fun AdminConsoleScreen(
    state: AdminConsoleState,
    onSection: (AdminSection) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (JsonObject) -> Unit,
    onClearSelection: () -> Unit,
    onAuthorizeRecycler: (String, String, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onResolveDispute: (String, String, String?) -> Unit,
    onVerifyPayment: (String) -> Unit,
    onDisputePickupPayment: (String, String) -> Unit,
    onReversePayment: (String, String, String?, String?, String?) -> Unit,
    onResolveAnomaly: (String, String, String, String?) -> Unit,
    onImportPrice: (String, String, String, Double, Double, Double, String, String) -> Unit,
    onUpdatePrice: (String, Double, Double, Double, String) -> Unit,
    onExportDataset: () -> Unit,
    onLogout: () -> Unit
) {
    var recyclerDialog by remember { mutableStateOf<JsonObject?>(null) }
    var disputeDialog by remember { mutableStateOf<String?>(null) }
    var paymentDialog by remember { mutableStateOf<String?>(null) }
    var anomalyDialog by remember { mutableStateOf<String?>(null) }
    var importDialog by remember { mutableStateOf(false) }
    var updatePriceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Operator console", style = MaterialTheme.typography.headlineSmall)
                Text("Restricted operations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onLogout) { Text("Sign out") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableSections.forEach { section ->
                FilterChip(selected = state.section == section, onClick = { onSection(section) }, label = { Text(section.label) })
            }
        }
        if (state.error != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }
        if (state.message != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(state.message, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
            }
        }
        if (state.availableSections.isEmpty()) {
            Text("No operator permissions are assigned to this account. Contact your system administrator.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (state.section == AdminSection.TOOLS) {
            AdminTools(
                busy = state.actionBusy,
                canManagePrices = state.canManagePrices,
                canExportDataset = state.canExportDataset,
                onImportPrice = { importDialog = true },
                onUpdatePrice = { updatePriceDialog = true },
                onExportDataset = onExportDataset
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.loading) "Loading…" else "${state.items.size} open item(s)", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onRefresh, enabled = !state.loading && !state.actionBusy) { Text("Refresh") }
            }
            if (!state.loading && state.items.isEmpty()) {
                Text("No operator actions waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.items.forEach { item ->
                when (state.section) {
                    AdminSection.RECYCLERS -> RecyclerReviewCard(item, state.actionBusy, state.canAuthorizeRecyclers, { recyclerDialog = item }, onSelect)
                    AdminSection.DISPUTES -> DisputeReviewCard(item, state.actionBusy) { disputeDialog = item.stringValue("id", "disputeId") }
                    AdminSection.PAYMENTS -> PaymentReviewCard(item, state.actionBusy) { paymentDialog = item.stringValue("id", "paymentId") }
                    AdminSection.ANOMALIES -> AnomalyReviewCard(item, state.actionBusy) { anomalyDialog = item.stringValue("id", "flagId") }
                    AdminSection.TOOLS -> Unit
                }
            }
        }
    }

    recyclerDialog?.let { item ->
        RecyclerAuthorizationDialog(
            item = item,
            busy = state.actionBusy,
            onDismiss = { recyclerDialog = null },
            onSubmit = { status, reason, authority, registration, type, evidence, source, validUntil ->
                recyclerDialog = null
                onAuthorizeRecycler(item.stringValue("id"), status, reason, authority, registration, type, evidence, source, validUntil)
            }
        )
    }
    disputeDialog?.let { id ->
        DisputeResolutionDialog(id, state.actionBusy, { disputeDialog = null }) { resolution, notes ->
            disputeDialog = null
            onResolveDispute(id, resolution, notes)
        }
    }
    paymentDialog?.let { id ->
        val pickupPayment = state.items.firstOrNull { it.get("id")?.asString == id }?.get("kind")?.asString == "HOUSEHOLD_PICKUP_SETTLEMENT"
        PaymentActionDialog(id, state.actionBusy, pickupPayment, { paymentDialog = null }, onVerify = {
            paymentDialog = null
            onVerifyPayment(id)
        }, onDisputePickup = { notes ->
            paymentDialog = null
            onDisputePickupPayment(id, notes)
        }, onReverse = { reason, provider, reference, evidence ->
            paymentDialog = null
            onReversePayment(id, reason, provider, reference, evidence)
        })
    }
    anomalyDialog?.let { id ->
        AnomalyResolutionDialog(id, state.actionBusy, { anomalyDialog = null }) { action, resolution, evidence ->
            anomalyDialog = null
            onResolveAnomaly(id, action, resolution, evidence)
        }
    }
    if (importDialog) PriceImportDialog(state.actionBusy, { importDialog = false }) { externalId, material, city, min, max, market, org, reference ->
        importDialog = false
        onImportPrice(externalId, material, city, min, max, market, org, reference)
    }
    if (updatePriceDialog) PriceUpdateDialog(state.actionBusy, { updatePriceDialog = false }) { id, min, max, market, reason ->
        updatePriceDialog = false
        onUpdatePrice(id, min, max, market, reason)
    }
    state.selected?.let { item ->
        if (state.section == AdminSection.RECYCLERS) RecyclerRecordDetailsDialog(item, onClearSelection) else AlertDialog(
            onDismissRequest = onClearSelection,
            title = { Text("Record details") },
            text = { Text(item.entrySet().joinToString("\n") { (key, value) -> "$key: ${if (value.isJsonPrimitive) value.asString else value.toString()}" }) },
            confirmButton = { TextButton(onClick = onClearSelection) { Text("Close") } }
        )
    }
}

@Composable
private fun AdminTools(busy: Boolean, canManagePrices: Boolean, canExportDataset: Boolean, onImportPrice: () -> Unit, onUpdatePrice: () -> Unit, onExportDataset: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Validated data tools", style = MaterialTheme.typography.titleLarge)
            Text("Server permission required. Every action is audited.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canManagePrices) {
                Button(onClick = onImportPrice, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import price row") }
                OutlinedButton(onClick = onUpdatePrice, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Update price observation") }
            }
            if (canExportDataset) OutlinedButton(onClick = onExportDataset, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export safe dataset") }
        }
    }
}

@Composable
private fun RecyclerReviewCard(item: JsonObject, busy: Boolean, canAuthorize: Boolean, onReview: () -> Unit, onSelect: (JsonObject) -> Unit) {
    val materials = item.getAsJsonArray("materials")?.mapNotNull { runCatching { it.asJsonObject.stringValue("category") }.getOrNull() }.orEmpty()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(item.stringValue("name").ifBlank { "Unnamed facility" }, style = MaterialTheme.typography.titleLarge)
            Text(item.stringValue("areaName").ifBlank { "Area not provided" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.stringValue("authorizationStatus").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.primary)
            Text("Registration  ${item.stringValue("licenseNumber").ifBlank { "Not provided" }}")
            Text("Materials  ${materials.take(3).joinToString(" · ").ifBlank { "Not provided" }}${if (materials.size > 3) " · +${materials.size - 3}" else ""}")
            Text("Pickup  ${if (item.booleanValue("pickupAvailable")) "Available" else "Not available"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSelect(item) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Review") }
                if (canAuthorize) Button(onClick = onReview, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Verify") }
            }
        }
    }
}

@Composable
private fun DisputeReviewCard(item: JsonObject, busy: Boolean, onResolve: () -> Unit) = ReviewCard(
    title = item.stringValue("id", "disputeId"),
    subtitle = "${item.stringValue("status")} · ${item.stringValue("reason", "type")}",
    item = item,
    actionLabel = "Resolve",
    busy = busy,
    onAction = onResolve
)

@Composable
private fun PaymentReviewCard(item: JsonObject, busy: Boolean, onReview: () -> Unit) = ReviewCard(
    title = item.stringValue("id", "paymentId"),
    subtitle = if (item.stringValue("kind") == "HOUSEHOLD_PICKUP_SETTLEMENT") "Pickup payout · ${item.stringValue("paymentMethod")} · ₹${item.stringValue("amount")} · ${item.stringValue("status")}" else "${item.stringValue("status")} · ${item.stringValue("amount")} ${item.stringValue("currency")}",
    item = item,
    actionLabel = "Review",
    busy = busy,
    onAction = onReview
)

@Composable
private fun AnomalyReviewCard(item: JsonObject, busy: Boolean, onResolve: () -> Unit) = ReviewCard(
    title = item.stringValue("id", "flagId"),
    subtitle = "${item.stringValue("riskLevel", "severity")} · ${item.stringValue("entityType")} ${item.stringValue("entityId")}",
    item = item,
    actionLabel = "Resolve",
    busy = busy,
    onAction = onResolve
)

@Composable
private fun ReviewCard(title: String, subtitle: String, item: JsonObject, actionLabel: String, busy: Boolean, onAction: () -> Unit, onSelect: ((JsonObject) -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.ifBlank { "Unlabelled record" }, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.compactSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, enabled = !busy) { Text(actionLabel) }
                if (onSelect != null) OutlinedButton(onClick = { onSelect(item) }, enabled = !busy) { Text("Review") }
            }
        }
    }
}

@Composable
private fun RecyclerAuthorizationDialog(item: JsonObject, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String?, String?, String?, String?, String?, String?, String?) -> Unit) {
    var status by remember(item.stringValue("id")) { mutableStateOf("UNDER_REVIEW") }
    var reason by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var validUntil by remember { mutableStateOf("") }
    val context = LocalContext.current
    val submittedDate = item.stringValue("authorizationValidUntil").take(10)
    val canVerify = listOf("authorizationAuthority", "licenseNumber", "authorizationType", "authorizationEvidenceReference").all { item.stringValue(it).isNotBlank() }
    val valid = !busy && when (status) { "VERIFIED" -> canVerify && source.trim().length >= 3; "REJECTED" -> reason.trim().length >= 10; else -> true }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.imePadding(), title = { Text("Review ${item.stringValue("name").ifBlank { "facility" }}") }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Decision", style = MaterialTheme.typography.titleMedium)
            listOf("UNDER_REVIEW" to "Under review", "VERIFIED" to "Verified", "REJECTED" to "Rejected").forEach { (key, label) -> FilterChip(selected = status == key, onClick = { status = key }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }
            HorizontalDivider()
            Text("Submitted information", style = MaterialTheme.typography.titleMedium)
            DetailRow("Registration", item.stringValue("licenseNumber"))
            DetailRow("Authority", item.stringValue("authorizationAuthority"))
            DetailRow("Authorization type", item.stringValue("authorizationType"))
            DetailRow("Evidence", item.stringValue("authorizationEvidenceReference"))
            DetailRow("Valid until", submittedDate)
            if (status == "VERIFIED") {
                if (!canVerify) Text("The Recycler must complete registration, authority, type, and evidence before verification.", color = MaterialTheme.colorScheme.error)
                AdminField("Verification method · Required", source) { source = it.take(500) }
                OutlinedButton(onClick = {
                    val today = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(context, { _, year, month, day -> validUntil = "%04d-%02d-%02dT23:59:59.999Z".format(year, month + 1, day) }, today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text(if (validUntil.isBlank()) "Override validity date · Optional" else "Verified until ${validUntil.take(10)}") }
                AdminField("Internal note · Optional", reason) { reason = it.take(500) }
            } else if (status == "REJECTED") AdminField("Rejection reason · Required", reason) { reason = it.take(500) }
            else AdminField("Review note · Optional", reason) { reason = it.take(500) }
        }
    }, confirmButton = { Button(onClick = { onSubmit(status, reason, null, null, null, null, source, validUntil) }, enabled = valid) { Text("Submit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RecyclerRecordDetailsDialog(item: JsonObject, onDismiss: () -> Unit) {
    val location = item.objectValue("facilityLocation")
    val contact = item.objectValue("contact")
    val authorization = item.objectValue("authorizationDetails")
    val area = item.objectValue("serviceArea")
    val materials = item.getAsJsonArray("materialsAccepted")?.mapNotNull { runCatching { it.asJsonObject.stringValue("category") }.getOrNull() }.orEmpty()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.stringValue("name").ifBlank { "Facility review" }) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FACILITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DetailRow("Business name", item.stringValue("name"))
            DetailRow("Phone", contact?.stringValue("phone").orEmpty())
            DetailRow("Alternate phone", contact?.stringValue("alternatePhone").orEmpty())
            DetailRow("Email", contact?.stringValue("email").orEmpty())
            DetailRow("Address", location?.stringValue("address").orEmpty())
            DetailRow("Area", location?.stringValue("areaName").orEmpty())
            HorizontalDivider()
            Text("AUTHORIZATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DetailRow("Status", item.stringValue("authorizationStatus").replace('_', ' '))
            DetailRow("Registration number", authorization?.stringValue("registrationNumber").orEmpty())
            DetailRow("Authority", authorization?.stringValue("authority").orEmpty())
            DetailRow("Authorization type", authorization?.stringValue("type").orEmpty())
            DetailRow("Evidence", authorization?.stringValue("evidenceReference").orEmpty())
            DetailRow("Valid until", authorization?.stringValue("validUntil")?.take(10).orEmpty())
            authorization?.stringValue("reviewReason")?.takeIf(String::isNotBlank)?.let { DetailRow("Review feedback", it) }
            HorizontalDivider()
            Text("LOGISTICS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DetailRow("Materials accepted", materials.joinToString(" · "))
            DetailRow("Pickup", if (item.booleanValue("pickupAvailable")) "Available" else "Not available")
            if (item.booleanValue("pickupAvailable")) {
                DetailRow("Availability", item.stringValue("pickupAvailability").replace('_', ' '))
                DetailRow("Maximum distance", area?.stringValue("maxPickupDistanceKm")?.let { "$it km" }.orEmpty())
                DetailRow("Pricing", if (item.booleanValue("pickupIncluded")) "Included" else item.stringValue("pickupFee").takeIf(String::isNotBlank)?.let { "₹$it fixed" } ?: area?.stringValue("logisticsCostPerKm")?.takeIf(String::isNotBlank)?.let { "₹$it per km" }.orEmpty())
            }
            DetailRow("Operating hours", item.objectValue("operatingHours")?.stringValue("description").orEmpty())
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Not provided" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DisputeResolutionDialog(id: String, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) {
    var resolution by remember { mutableStateOf("ACCEPT_COLLECTOR") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Resolve dispute") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(id)
            listOf("ACCEPT_COLLECTOR", "ACCEPT_RECYCLER", "SPLIT_DIFFERENCE", "OTHER").forEach { FilterChip(selected = resolution == it, onClick = { resolution = it }, label = { Text(it) }) }
            AdminField("Notes", notes) { notes = it }
        }
    }, confirmButton = { Button(onClick = { onSubmit(resolution, notes) }, enabled = !busy && notes.isNotBlank()) { Text("Resolve") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PaymentActionDialog(id: String, busy: Boolean, pickupPayment: Boolean, onDismiss: () -> Unit, onVerify: () -> Unit, onDisputePickup: (String) -> Unit, onReverse: (String, String?, String?, String?) -> Unit) {
    var reverse by remember { mutableStateOf(false) }
    var dispute by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Payment review") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(id)
            if (reverse) {
                AdminField("Reversal reason", reason) { reason = it }
                AdminField("Provider", provider) { provider = it }
                AdminField("External reference", reference) { reference = it }
                AdminField("Evidence reference", evidence) { evidence = it }
            } else if (dispute) {
                Text("Explain why the external cash/UPI record does not reconcile.")
                AdminField("Reconciliation note", reason) { reason = it }
            } else Text(if (pickupPayment) "Confirm that this recorded cash/UPI payment matches the pickup evidence." else "Choose the audited action for this payment.")
        }
    }, confirmButton = {
        if (reverse) Button(onClick = { onReverse(reason, provider, reference, evidence) }, enabled = !busy && reason.isNotBlank()) { Text("Reverse") }
        else if (dispute) Button(onClick = { onDisputePickup(reason) }, enabled = !busy && reason.isNotBlank()) { Text("Flag for follow-up") }
        else Button(onClick = onVerify, enabled = !busy) { Text(if (pickupPayment) "Reconcile" else "Verify") }
    }, dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!reverse && pickupPayment && !dispute) TextButton(onClick = { dispute = true }) { Text("Flag mismatch") }
            else if (!reverse && !pickupPayment && !dispute) TextButton(onClick = { reverse = true }) { Text("Reverse instead") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    })
}

@Composable
private fun AnomalyResolutionDialog(id: String, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String, String?) -> Unit) {
    var action by remember { mutableStateOf("ACKNOWLEDGE") }
    var resolution by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Resolve anomaly") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(id)
            listOf("ACCEPT_AS_RECORDED", "REVERT_TO_QUOTE", "RELEASE_RESERVATION", "ACKNOWLEDGE").forEach { FilterChip(selected = action == it, onClick = { action = it }, label = { Text(it) }) }
            AdminField("Resolution", resolution) { resolution = it }
            AdminField("Evidence reference", evidence) { evidence = it }
        }
    }, confirmButton = { Button(onClick = { onSubmit(action, resolution, evidence) }, enabled = !busy && resolution.length >= 2) { Text("Resolve") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PriceImportDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String, String, Double, Double, Double, String, String) -> Unit) {
    var externalId by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var market by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    AdminFormDialog("Import price row", busy, onDismiss, "Import", listOf(
        "External id" to externalId, "Material category" to material, "City" to city, "Min price" to min, "Max price" to max, "Market price" to market, "Source organization" to organization, "Source reference" to reference
    ), { index, value -> when (index) { 0 -> externalId = value; 1 -> material = value; 2 -> city = value; 3 -> min = value; 4 -> max = value; 5 -> market = value; 6 -> organization = value; else -> reference = value } }) {
        val values = listOf(min.toDoubleOrNull(), max.toDoubleOrNull(), market.toDoubleOrNull())
        if (externalId.isNotBlank() && material.isNotBlank() && city.isNotBlank() && values.all { it != null }) onSubmit(externalId, material, city, values[0]!!, values[1]!!, values[2]!!, organization, reference)
    }
}

@Composable
private fun PriceUpdateDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, Double, Double, Double, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var market by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    AdminFormDialog("Update price", busy, onDismiss, "Update", listOf("Price id" to id, "Min price" to min, "Max price" to max, "Market price" to market, "Reason" to reason), { index, value -> when (index) { 0 -> id = value; 1 -> min = value; 2 -> max = value; 3 -> market = value; else -> reason = value } }) {
        val a = min.toDoubleOrNull(); val b = max.toDoubleOrNull(); val c = market.toDoubleOrNull()
        if (id.isNotBlank() && a != null && b != null && c != null && reason.isNotBlank()) onSubmit(id, a, b, c, reason)
    }
}

@Composable
private fun AdminFormDialog(title: String, busy: Boolean, onDismiss: () -> Unit, actionLabel: String, fields: List<Pair<String, String>>, onValue: (Int, String) -> Unit, onSubmit: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { fields.forEachIndexed { index, (label, value) -> AdminField(label, value) { onValue(index, it) } } } }, confirmButton = { Button(onClick = onSubmit, enabled = !busy) { Text(actionLabel) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AdminField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun JsonObject.stringValue(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    get(key)?.takeUnless { it.isJsonNull }?.let { element -> if (element.isJsonPrimitive) element.asString else element.toString().take(100) }
}.orEmpty()

private fun JsonObject.objectValue(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.booleanValue(key: String): Boolean =
    get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

private fun JsonObject.compactSummary(): String = entrySet().take(5).joinToString(" · ") { (key, value) -> "$key=${if (value.isJsonPrimitive) value.asString else value.toString().take(40)}" }
