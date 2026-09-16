package com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Payment
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcMetric
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcPrimaryButton
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ProofRow
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat

/**
 * Earnings tab: local-first totals with large rupee figures and an explicit
 * server refresh affordance for reconciliation.
 */
@Composable
fun EarningsScreen(
    state: UiState<EarningsSummary>,
    onRetry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    refreshError: Boolean = false,
    onRecordPayment: () -> Unit = {},
    payments: List<Payment> = emptyList(),
    modifier: Modifier = Modifier
) {
    val hasActivity = when (state) {
        is UiState.Success -> state.data.hasActivity()
        is UiState.Offline -> state.cached?.hasActivity() == true
        is UiState.Syncing -> state.cached?.hasActivity() == true
        else -> false
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.earnings_title),
            style = MaterialTheme.typography.headlineLarge
        )
        onRefresh?.let { refresh ->
            OutlinedButton(onClick = refresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (refreshing) R.string.handover_saving else R.string.future_refresh))
            }
        }
        if (refreshError) Text(stringResource(R.string.earnings_refresh_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        when (state) {
            is UiState.Loading -> LoadingContent()
            is UiState.Error -> ErrorContent(onRetry = onRetry)
            is UiState.Empty -> EarningsEmptyState()
            is UiState.Success -> if (state.data.hasActivity()) EarningsCards(state.data) else EarningsEmptyState()
            is UiState.Offline -> if (state.cached?.hasActivity() == true) EarningsCards(state.cached) else EarningsEmptyState()
            is UiState.Syncing -> if (state.cached?.hasActivity() == true) EarningsCards(state.cached) else EarningsEmptyState()
        }
        if (payments.isNotEmpty()) PaymentLedger(payments)
        if (hasActivity) {
            KcPrimaryButton(text = stringResource(R.string.payment_record), onClick = onRecordPayment)
        }
    }
}

@Composable
private fun EarningsEmptyState() {
    EvidenceSection(title = stringResource(R.string.earnings_empty_title)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.earnings_empty_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun EarningsSummary.hasActivity(): Boolean =
    totalRupees > 0.0 || pendingRupees > 0.0 || thisMonthRupees > 0.0 || averageLotValueRupees > 0.0

@Composable
private fun PaymentLedger(payments: List<Payment>) {
    EvidenceSection(title = stringResource(R.string.earnings_ledger_title)) {
        payments.take(8).forEach { payment ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.earnings_lot_reference, payment.lotId.takeLast(8)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.earnings_payment_status, payment.method.name.replace('_', ' '), payment.syncState.name.replace('_', ' ')), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(rupees(payment.amountRupees), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EarningsCards(summary: EarningsSummary) {
    EvidenceSection(title = stringResource(R.string.earnings_total)) {
        Text(rupees(summary.totalRupees), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        KcMetric(label = stringResource(R.string.earnings_pending), value = rupees(summary.pendingRupees), modifier = Modifier.weight(1f), emphasis = summary.pendingRupees > 0)
        KcMetric(label = stringResource(R.string.earnings_this_month), value = rupees(summary.thisMonthRupees), modifier = Modifier.weight(1f))
    }
    EvidenceSection(title = stringResource(R.string.earnings_average)) {
        Text(rupees(summary.averageLotValueRupees), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun rupees(amount: Double): String =
    stringResource(R.string.earnings_rupees, IndiaFormat.number(amount))
