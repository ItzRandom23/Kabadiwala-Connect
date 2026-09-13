package com.irinteractivestudios.kabadiwalaconnect.ui.screens.payments
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcPrimaryButton
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ProofRow
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PaymentRecordScreen(lots: List<Lot>, repo: PaymentRepository, onSaved: (String, Double) -> Unit) {
    var lot by remember(lots) { mutableStateOf(lots.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val valid = lot != null && amount != null && amount > 0.0 && amount < 1_000_000.0 && !saving
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.payment_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.payment_date_now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        EvidenceSection(title = stringResource(R.string.payment_choose_lot)) {
            if (lots.isEmpty()) {
                Text(stringResource(R.string.payment_no_lots), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lots.take(4).size, key = { lots[it].id }) { index ->
                        val item = lots[index]
                        FilterChip(
                            selected = lot?.id == item.id,
                            onClick = { lot = item },
                            label = { Text(item.materialLabel) }
                        )
                    }
                }
                lot?.let { selected ->
                    ProofRow(stringResource(R.string.handover_material), selected.materialLabel)
                    ProofRow(stringResource(R.string.handover_weight), stringResource(R.string.lot_weight_value, selected.weightKg))
                    ProofRow(
                        stringResource(R.string.quote_estimated),
                        selected.estimatedValueRupees?.let { value -> rupees(value) } ?: stringResource(R.string.lot_value_placeholder)
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { character -> character.isDigit() || character == '.' } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.payment_amount)) },
            textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountText.isNotBlank() && !valid,
            singleLine = true
        )
        if (valid) {
            EvidenceSection(title = stringResource(R.string.payment_confirm_title), status = stringResource(R.string.payment_waiting_sync)) {
                Text(rupees(amount), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                ProofRow(stringResource(R.string.payment_method), paymentMethodLabel(method))
                ProofRow(stringResource(R.string.handover_date), stringResource(R.string.payment_date_now))
            }
        }

        Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PaymentMethod.entries.size, key = { PaymentMethod.entries[it].name }) { index ->
                val item = PaymentMethod.entries[index]
                FilterChip(selected = method == item, onClick = { method = item }, label = { Text(paymentMethodLabel(item)) })
            }
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it.take(1000) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.payment_notes)) }, minLines = 2)
        if (saveError) Text(stringResource(R.string.payment_save_error), color = MaterialTheme.colorScheme.error)
        if (amountText.isNotBlank() && !valid) Text(stringResource(R.string.payment_amount_error), color = MaterialTheme.colorScheme.error)
        KcPrimaryButton(text = stringResource(R.string.payment_save), onClick = { confirm = true }, enabled = valid)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.payment_confirm_title)) },
            text = { Text(stringResource(R.string.payment_confirm_text, amount ?: 0.0)) },
            confirmButton = {
                TextButton(onClick = {
                    val item = lot!!
                    confirm = false
                    saving = true
                    saveError = false
                    scope.launch {
                        runCatching {
                            repo.record(Payment(UUID.randomUUID().toString(), item.id, null, amount!!, method, System.currentTimeMillis(), notes, PaymentSyncState.WAITING_TO_SYNC))
                            onSaved(item.id, amount)
                        }.onFailure { saveError = true }
                        saving = false
                    }
                }) { Text(stringResource(R.string.payment_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_back)) } }
        )
    }
}

@Composable
fun PaymentList(payments: List<Payment>, onOpen: (Payment) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(payments.size, key = { payments[it].id }) { index ->
            val payment = payments[index]
            EvidenceSection(
                title = rupees(payment.amountRupees),
                status = stringResource(if (payment.syncState == PaymentSyncState.WAITING_TO_SYNC) R.string.payment_waiting_sync else R.string.payment_recorded),
                modifier = Modifier
            ) {
                ProofRow(stringResource(R.string.payment_method), paymentMethodLabel(payment.method))
                ProofRow(stringResource(R.string.payment_choose_lot), payment.lotId)
                TextButton(onClick = { onOpen(payment) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.payment_investigate)) }
            }
        }
    }
}

@Composable
private fun paymentMethodLabel(method: PaymentMethod): String = stringResource(
    when (method) {
        PaymentMethod.CASH -> R.string.payment_method_cash
        PaymentMethod.BANK_TRANSFER -> R.string.payment_method_bank_transfer
        PaymentMethod.DIGITAL_WALLET -> R.string.payment_method_digital_wallet
    }
)

@Composable
private fun rupees(amount: Double): String = stringResource(R.string.earnings_rupees, "%.0f".format(amount))
