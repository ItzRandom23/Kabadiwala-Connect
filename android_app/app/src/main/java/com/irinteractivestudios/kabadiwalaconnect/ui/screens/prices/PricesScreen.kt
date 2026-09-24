package com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.util.PriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.util.AndroidLocationProvider
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import java.util.Date
import java.util.Locale
import com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import java.util.concurrent.TimeUnit
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme

@Composable
fun PricesScreen(state: UiState<List<Price>>, vm: PricesViewModel, speaker: PriceSpeaker, demoMode: Boolean = false, modifier: Modifier = Modifier) {
    val location by vm.selectedLocation.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshFailed by vm.refreshFailed.collectAsStateWithLifecycle()
    var selectedMaterial by remember { mutableStateOf("") }
    var locationInput by remember(location) { mutableStateOf(location) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var locationBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { AndroidLocationProvider(context) }
    fun applyLocation(value: String) {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return
        vm.selectLocation(cleaned)
        // selectedLocation changes synchronously, so refresh will request this
        // exact area and the backend will not substitute another city's rate.
        vm.refresh()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) locationError = true else scope.launch {
            locationBusy = true
            val current = runCatching { locationProvider.current() }.getOrNull()
            val place = current?.areaName
            if (place.isNullOrBlank()) locationError = true else {
                locationInput = place
                applyLocation(place)
                locationError = false
            }
            locationBusy = false
        }
    }
    LaunchedEffect(demoMode) { if (!demoMode && location.isNotBlank()) vm.refresh() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(38.dp).height(4.dp)) {}
                Spacer(Modifier.height(9.dp))
                Text(stringResource(R.string.prices_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.prices_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!demoMode) {
                IconButton(onClick = vm::refresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, stringResource(R.string.prices_refresh))
                }
            }
        }
        if (demoMode) DemoDataBanner()
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            if (location.isBlank()) "Choose your market area" else stringResource(R.string.prices_location_context, location),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (BuildConfig.DEBUG) "Live rates appear when available; this development build fills gaps with samples."
                            else "Live local rates appear here when available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .82f)
                        )
                    }
                }
                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it.take(160) },
                    label = { Text("Locality, city, state or PIN code") },
                    placeholder = { Text("e.g. Kothrud, Pune, Maharashtra") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { applyLocation(locationInput) }, enabled = locationInput.isNotBlank() && !refreshing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Search area") }
                    OutlinedButton(onClick = {
                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocation) scope.launch {
                            locationBusy = true
                            val current = runCatching { locationProvider.current() }.getOrNull()
                            val place = current?.areaName
                            if (place.isNullOrBlank()) locationError = true else { locationInput = place; applyLocation(place); locationError = false }
                            locationBusy = false
                        } else showLocationRationale = true
                    }, enabled = !locationBusy && !refreshing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (locationBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.LocationOn, null)
                        Spacer(Modifier.width(8.dp)); Text("Use current location", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (locationError) Text("Could not determine an area. Enter your locality, city or PIN code instead.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (locations.isNotEmpty()) {
            Text("Areas with configured price data", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                locations.take(12).forEach { place -> FilterChip(selected = place == location, onClick = { locationInput = place; applyLocation(place) }, label = { Text(place) }) }
            }
        }
        if (refreshFailed) Text(stringResource(R.string.common_error_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        when (state) {
            is UiState.Loading -> LoadingContent()
            is UiState.Error -> ErrorContent(onRetry = vm::refresh)
            is UiState.Empty -> MarketPriceEmpty(location, refreshing, vm::refresh)
            is UiState.Offline -> if (state.cached.isNullOrEmpty()) MarketPriceEmpty(location, refreshing, vm::refresh) else PriceBoardContent(state.cached, true, selectedMaterial, { selectedMaterial = it }, speaker)
            is UiState.Success -> PriceBoardContent(state.data, false, selectedMaterial, { selectedMaterial = it }, speaker)
            is UiState.Syncing -> if (state.cached.isNullOrEmpty()) LoadingContent() else PriceBoardContent(state.cached, true, selectedMaterial, { selectedMaterial = it }, speaker)
        }
    }
    if (showLocationRationale) AlertDialog(
        onDismissRequest = { showLocationRationale = false },
        title = { Text("Use your current location?") },
        text = { Text("Location helps choose a local price area. You can continue by entering an area or PIN code instead.") },
        confirmButton = { Button(onClick = { showLocationRationale = false; locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showLocationRationale = false }) { Text("Enter area") } }
    )
}

@Composable
private fun PriceBoardContent(prices: List<Price>, cached: Boolean, selected: String, select: (String) -> Unit, speaker: PriceSpeaker) {
    if (prices.any { it.qualityStatus == "DEMO_SAMPLE" }) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("DEVELOPMENT SAMPLE RATES", style = MaterialTheme.typography.labelLarge, color = KcTheme.extended.warning)
                Text("Sample-labeled values and trends are generated for layout testing. They are not live or verified prices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    PriceBoard(prices, cached, selected, select, speaker)
}

@Composable
private fun MarketPriceEmpty(location: String, refreshing: Boolean, onRefresh: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
            Text(stringResource(R.string.prices_empty_title, location), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.prices_empty_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (refreshing) R.string.prices_refreshing else R.string.prices_refresh))
            }
        }
    }
}

@Composable
private fun PriceBoard(prices: List<Price>, cached: Boolean, selected: String, select: (String) -> Unit, speaker: PriceSpeaker) {
    if (prices.isEmpty()) { EmptyContent(); return }
    val current = prices.firstOrNull { it.materialLabel == selected } ?: prices.first()
    val isSample = current.qualityStatus == "DEMO_SAMPLE"
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.prices_rate_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSample) Text("SAMPLE · NOT LIVE", style = MaterialTheme.typography.labelLarge, color = KcTheme.extended.warning)
            Text(
                stringResource(R.string.prices_rate_value, current.ratePerKg),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prices_range, current.minRatePerKg, current.maxRatePerKg, current.unit.toDisplayUnit()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!isSample) Text(
                    stringResource(R.string.prices_last_updated, IndiaFormat.shortDate(current.updatedAtEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.prices_source, current.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSample) current.disclaimer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!isSample) Row(verticalAlignment = Alignment.CenterVertically) {
                TrendIcon(current.trend)
                Text(
                    trendText(current.trend, current.trendPercentage),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (!isSample && (current.qualityStatus.equals("STALE", ignoreCase = true) || System.currentTimeMillis() - current.updatedAtEpochMs > TimeUnit.DAYS.toMillis(7))) {
                Text(stringResource(R.string.prices_stale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
            current.disclaimer?.takeIf { !isSample && it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    OutlinedButton(onClick = { speaker.speak(current.materialLabel, current.ratePerKg, Locale.getDefault().language) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Filled.GraphicEq, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.prices_hear))
    }
    if (!isSample) {
        Text(stringResource(R.string.prices_history_title), style = MaterialTheme.typography.titleLarge)
        History(current.history)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.prices_recycler_placeholder), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
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
    Icon(when (trend) { "up" -> Icons.Filled.KeyboardArrowUp; "down" -> Icons.Filled.KeyboardArrowDown; else -> Icons.Filled.Remove }, contentDescription = trendText(trend, 0.0), tint = if (trend == "down") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun trendText(trend: String, percentage: Double): String = stringResource(
    when (trend) {
        "up" -> R.string.prices_trend_up
        "down" -> R.string.prices_trend_down
        else -> R.string.prices_trend_stable
    }) + if (percentage == 0.0) "" else " (${"%.1f".format(Locale.US, percentage)}%)"

private fun String.toDisplayUnit() = when (uppercase()) { "GRAM" -> "g"; "PIECE" -> "piece"; else -> "kg" }
