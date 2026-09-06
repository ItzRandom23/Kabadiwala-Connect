package com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.util.PriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PricesScreen(state: UiState<List<Price>>, vm: PricesViewModel, speaker: PriceSpeaker, demoMode: Boolean = false, modifier: Modifier = Modifier) {
    val location by vm.selectedLocation.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var selectedMaterial by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.prices_title), style = MaterialTheme.typography.headlineLarge)
        if (demoMode) DemoDataBanner()
        Text(stringResource(R.string.prices_location_label), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            locations.forEach { place -> FilterChip(selected = place == location, onClick = { vm.selectLocation(place) }, label = { Text(place) }) }
        }
        when (state) {
            is UiState.Loading -> LoadingContent()
            is UiState.Error -> ErrorContent()
            is UiState.Empty -> EmptyContent()
            is UiState.Offline -> PriceBoard(state.cached.orEmpty(), true, selectedMaterial, { selectedMaterial = it }, speaker)
            is UiState.Success -> PriceBoard(state.data, false, selectedMaterial, { selectedMaterial = it }, speaker)
            is UiState.Syncing -> PriceBoard(state.cached.orEmpty(), true, selectedMaterial, { selectedMaterial = it }, speaker)
        }
    }
}

@Composable
private fun PriceBoard(prices: List<Price>, cached: Boolean, selected: String, select: (String) -> Unit, speaker: PriceSpeaker) {
    if (prices.isEmpty()) { EmptyContent(); return }
    val current = prices.firstOrNull { it.materialLabel == selected } ?: prices.first()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        prices.forEach { price -> FilterChip(selected = price.materialLabel == current.materialLabel, onClick = { select(price.materialLabel) }, label = { Text(price.materialLabel) }) }
    }
    if (cached) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Icon(Icons.Filled.CloudOff, contentDescription = stringResource(R.string.prices_cached), modifier = Modifier.size(19.dp))
            Text(stringResource(R.string.prices_cached), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 7.dp))
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.prices_rate_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.prices_rate_value, current.ratePerKg),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendIcon(current.trend)
                Text(
                    trendText(current.trend),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prices_range, current.minRatePerKg, current.maxRatePerKg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.prices_last_updated, DateFormat.getDateInstance(DateFormat.SHORT).format(Date(current.updatedAtEpochMs))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    OutlinedButton(onClick = { speaker.speak(current.materialLabel, current.ratePerKg, Locale.getDefault().language) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Filled.GraphicEq, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.prices_hear))
    }
    Text(stringResource(R.string.prices_history_title), style = MaterialTheme.typography.titleLarge)
    History(current.history)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.prices_recycler_placeholder), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun History(values: List<Double>) {
    Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        values.forEach { value -> Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).height((70 * value / max).dp)) {} }
    }
}

@Composable
private fun TrendIcon(trend: String) {
    Icon(when (trend) { "up" -> Icons.Filled.KeyboardArrowUp; "down" -> Icons.Filled.KeyboardArrowDown; else -> Icons.Filled.Remove }, contentDescription = null, tint = if (trend == "down") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun trendText(trend: String): String = stringResource(
    when (trend) {
        "up" -> R.string.prices_trend_up
        "down" -> R.string.prices_trend_down
        else -> R.string.prices_trend_stable
    }
)
