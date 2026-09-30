package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyPaymentRequestDto

/** Material receipt does not establish that money was paid or received. */
@Composable
fun SupplyPaymentSection(
    handover: SupplyHandoverDto,
    recycler: Boolean,
    busy: Boolean = false,
    actionsEnabled: Boolean = true,
    onRecord: (String, SupplyPaymentRequestDto) -> Unit = { _, _ -> },
    onConfirm: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    if (handover.status != "COMPLETED") return
    val expected = handover.finalValue ?: handover.quotedValue
    val payment = handover.payments.firstOrNull()
    var dialog by rememberSaveable(handover.id) { mutableStateOf<String?>(null) }
    var method by rememberSaveable(handover.id) { mutableStateOf("CASH") }
    var reference by rememberSaveable(handover.id) { mutableStateOf("") }
    var reason by rememberSaveable(handover.id) { mutableStateOf("") }
    HorizontalDivider()
    Text("Material received · ₹${"%.2f".format(expected)}")
    Text(when (payment?.status) {
        "VERIFIED" -> "Payment received · ₹${"%.2f".format(payment.amount)} · confirmed by Kabadiwala"
        "RECORDED" -> "Payment recorded · ₹${"%.2f".format(payment.amount)} · awaiting Kabadiwala confirmation"
        "DISPUTED" -> "Payment issue reported"
        "REVERSED" -> "Payment reversed · requires review"
        else -> "Payment pending · ₹${"%.2f".format(expected)}"
    }, color = if (payment?.status == "DISPUTED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (handover.poolId != null) {
        Text("Pooled payments are recorded separately for each contributing collector.", style = MaterialTheme.typography.bodySmall)
    } else if (actionsEnabled && recycler && payment == null && expected > 0) {
        Text("Pay the Kabadiwala, then record the payment. This app does not transfer money.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { dialog = "RECORD" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "Saving…" else "Record payment") }
    } else if (actionsEnabled && !recycler && payment?.status == "RECORDED") {
        Text("Confirm only after you receive the money.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { dialog = "ACCEPT" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "Saving…" else "I received ₹${"%.2f".format(payment.amount)}") }
        TextButton(onClick = { dialog = "RAISE_ISSUE" }, enabled = !busy) { Text("I have not received the correct payment") }
    }
    if (dialog != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(when (dialog) { "RECORD" -> "Record ₹${"%.2f".format(expected)} payment"; "ACCEPT" -> "Confirm payment received"; else -> "Report payment issue" }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (dialog) {
                "RECORD" -> {
                    Text("Record only a payment you have actually made.")
                    listOf("CASH" to "Cash", "BANK_TRANSFER" to "Bank transfer", "DIGITAL_WALLET" to "Digital wallet").forEach { (key, label) ->
                        FilterChip(selected = method == key, onClick = { method = key }, label = { Text(label) })
                    }
                    if (method != "CASH") OutlinedTextField(reference, { reference = it.take(200) }, label = { Text("Transaction reference · required") }, modifier = Modifier.fillMaxWidth())
                }
                "ACCEPT" -> Text("Have you received ₹${"%.2f".format(payment?.amount ?: 0.0)} from the Recycler?")
                else -> OutlinedTextField(reason, { reason = it.take(120) }, label = { Text("Reason · required") }, modifier = Modifier.fillMaxWidth())
            }
        } },
        confirmButton = { TextButton(enabled = !busy && (dialog != "RECORD" || method == "CASH" || reference.trim().length >= 2) && (dialog != "RAISE_ISSUE" || reason.trim().length >= 2), onClick = {
            if (dialog == "RECORD") onRecord(handover.id, SupplyPaymentRequestDto(expected, method, reference.trim().takeIf { method != "CASH" }))
            else onConfirm(handover.id, dialog!!, reason.trim().takeIf { dialog == "RAISE_ISSUE" })
            dialog = null
        }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
    )
}
