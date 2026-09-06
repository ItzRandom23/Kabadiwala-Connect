package com.irinteractivestudios.kabadiwalaconnect.ui.screens.quotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler

@Composable
fun LotQuoteEntryScreen(lot: Lot, onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(lot.materialLabel, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.quote_lot_summary, lot.materialLabel, lot.weightKg))
        Text(stringResource(R.string.quote_estimated, lot.estimatedValueRupees ?: 0.0), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.quote_request_button)) }
    }
}

@Composable
fun RecyclerQuoteEntryScreen(recycler: Recycler, onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(recycler.name, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Text(recycler.facility)
        Text(recycler.address)
        Text(if (recycler.authorized) stringResource(R.string.recyclers_authorized) else stringResource(R.string.recycler_not_verified))
        Text(stringResource(R.string.recycler_rate_value, recycler.offeredRatePerKg), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.recycler_materials, recycler.acceptedMaterials.joinToString(", ")))
        Text(stringResource(R.string.recycler_hours, recycler.operatingHours))
        Text(stringResource(R.string.recycler_handover, recycler.typicalHandoverHours))
        Text(stringResource(R.string.recycler_map_placeholder, recycler.latitude ?: 0.0, recycler.longitude ?: 0.0))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.quote_request_button)) }
    }
}
