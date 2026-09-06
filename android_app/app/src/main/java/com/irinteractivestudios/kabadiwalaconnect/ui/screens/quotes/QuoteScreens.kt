package com.irinteractivestudios.kabadiwalaconnect.ui.screens.quotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Quote
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DealSheetCard
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ProofRow
import com.irinteractivestudios.kabadiwalaconnect.data.repository.QuoteRepository
import kotlinx.coroutines.launch

@Composable
fun QuoteRequestScreen(lots: List<Lot>, recyclers: List<Recycler>, presetLotId: String, presetRecyclerId: String, repo: QuoteRepository, onSubmitted: (String) -> Unit) {
    var lotId by remember { mutableStateOf(presetLotId.takeUnless { it == "none" } ?: lots.firstOrNull()?.id.orEmpty()) }
    var recyclerId by remember { mutableStateOf(presetRecyclerId.takeUnless { it == "none" } ?: recyclers.firstOrNull()?.id.orEmpty()) }
    var submitted by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lot = lots.firstOrNull { it.id == lotId }
    val recycler = recyclers.firstOrNull { it.id == recyclerId }
    if (lots.isEmpty() || recyclers.isEmpty()) { EmptyContent(); return }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quote_request_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(if (BuildConfig.DEBUG) R.string.quote_request_detail else R.string.quote_request_detail_live), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.quote_choose_lot), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { lots.forEach { item -> FilterChip(item.id == lotId, { lotId = item.id }, label = { Text(item.materialLabel) }) } }
        Text(stringResource(R.string.quote_choose_recycler), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { recyclers.forEach { item -> FilterChip(item.id == recyclerId, { recyclerId = item.id }, label = { Text(item.name) }) } }
        lot?.let { selectedLot -> recycler?.let { selectedRecycler ->
            EvidenceSection(title = stringResource(R.string.quote_summary), status = stringResource(R.string.quote_saved)) {
                ProofRow(stringResource(R.string.lot_material_label), selectedLot.materialLabel)
                ProofRow(stringResource(R.string.handover_weight), stringResource(R.string.lot_weight_value, selectedLot.weightKg.toString()))
                ProofRow(stringResource(R.string.quote_choose_recycler), selectedRecycler.name)
                Text(stringResource(R.string.quote_estimated, selectedLot.estimatedValueRupees ?: 0.0), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
            Text(stringResource(R.string.quote_confirmation), style = MaterialTheme.typography.bodyLarge)
            if (submitError) Text(stringResource(R.string.quote_submit_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { submitError = false; scope.launch { runCatching { repo.submitRequest(selectedLot, selectedRecycler) }.onSuccess { submitted = true; onSubmitted(selectedLot.id) }.onFailure { submitError = true } } }, enabled = !submitted, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("quote_submit")) { Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (submitted) R.string.quote_saved else R.string.quote_submit)) }
        } }
    }
}

@Composable
fun QuoteComparisonScreen(quotes: List<Quote>, lot: Lot?, referencePrice: Price? = null, repo: QuoteRepository, onAccepted: (String, String) -> Unit) {
    if (quotes.isEmpty()) { EmptyContent(); return }
    val best = quotes.filter { it.status.name != "EXPIRED" }.maxByOrNull { it.pricePerKg }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.quote_compare_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.quote_delivery_state, deliveryLabel(quotes.first())), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(quotes, key = { it.id }) { quote -> QuoteCard(quote, lot, referencePrice, quote.id == best?.id, repo, onAccepted) } }
    }
}

@Composable private fun QuoteCard(quote: Quote, lot: Lot?, referencePrice: Price?, best: Boolean, repo: QuoteRepository, onAccepted: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    EvidenceSection(
        title = quote.recyclerName,
        status = if (best) stringResource(R.string.quote_best) else stringResource(R.string.quote_status, quote.status.name)
    ) {
            Text(stringResource(R.string.quote_total, quote.amountRupees), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            ProofRow(stringResource(R.string.quote_price, quote.pricePerKg), stringResource(R.string.quote_market_compare, quote.marketRatePerKg))
            ProofRow(stringResource(R.string.quote_distance, quote.distanceKm), if (quote.pickupAvailable) stringResource(R.string.recycler_pickup_yes) else stringResource(R.string.recycler_pickup_no))
            lot?.let { DealSheetCard(it, quote, referencePrice) }
            Text(stringResource(R.string.quote_validity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAccepted(quote.id, quote.lotId) }, enabled = quote.status.name == "PENDING", modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.quote_accept)) }
                OutlinedButton(onClick = { scope.launch { repo.reject(quote.id) } }, enabled = quote.status.name == "PENDING", modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.quote_reject)) }
            }
    }
}
private fun deliveryLabel(quote: Quote) = quote.deliveryState.name.replace('_', ' ')
