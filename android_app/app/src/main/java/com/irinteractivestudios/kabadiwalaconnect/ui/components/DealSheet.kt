package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Quote
import java.util.Locale

@Composable
fun DealSheetCard(
    lot: Lot,
    quote: Quote,
    referencePrice: Price? = null,
    finalWeightKg: Double? = null,
    finalAmountRupees: Double? = null,
    modifier: Modifier = Modifier
) {
    val weight = finalWeightKg ?: lot.weightKg
    val amount = finalAmountRupees ?: quote.amountRupees
    EvidenceSection(title = stringResource(R.string.deal_sheet_title), modifier = modifier) {
            DealRow(stringResource(R.string.deal_market_reference), referencePrice?.let { "₹${format(it.minRatePerKg)}–₹${format(it.maxRatePerKg)} / kg" } ?: stringResource(R.string.deal_not_available))
            DealRow(stringResource(R.string.deal_offered_rate), "₹${format(quote.pricePerKg)} / kg")
            DealRow(stringResource(R.string.deal_weight), "${format(weight)} kg")
            DealRow(stringResource(R.string.deal_expected_total), "₹${format(amount)}")
            DealRow(stringResource(R.string.deal_transport), stringResource(R.string.deal_not_included))
            if (finalWeightKg != null || finalAmountRupees != null) {
                Text(stringResource(R.string.deal_final_values), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                DealRow(stringResource(R.string.deal_final_weight), "${format(finalWeightKg ?: weight)} kg")
                DealRow(stringResource(R.string.deal_final_amount), "₹${format(finalAmountRupees ?: amount)}")
            }
            Text(stringResource(R.string.deal_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DealRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun format(value: Double): String = com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat.number(value)
