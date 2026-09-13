package com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler
import com.irinteractivestudios.kabadiwalaconnect.data.local.MockRecyclerData
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ErrorContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.util.RecyclerSortMode
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import com.irinteractivestudios.kabadiwalaconnect.util.AndroidLocationProvider
import kotlinx.coroutines.launch
import com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat

@Composable
fun RecyclersScreen(state: UiState<List<Recycler>>, vm: RecyclersViewModel, onOpen: (String) -> Unit, demoMode: Boolean = false, modifier: Modifier = Modifier) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val displayedState = if (demoMode && state is UiState.Empty) UiState.Success(MockRecyclerData.all) else state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { AndroidLocationProvider(context) }
    var locationLabel by remember { mutableStateOf<String?>(null) }
    var locationBusy by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    fun refreshLocation() {
        scope.launch {
            locationBusy = true
            locationError = false
            val current = locationProvider.current()
            if (current == null) {
                locationError = true
            } else {
                locationLabel = current.areaName ?: "Current location"
                vm.refreshCatalogs(current)
            }
            locationBusy = false
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) refreshLocation() else locationError = true
    }
    LaunchedEffect(Unit) {
        vm.refreshCatalogs(null)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) refreshLocation()
        else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { Text(stringResource(R.string.recyclers_title), style = MaterialTheme.typography.headlineLarge) }
        if (demoMode) item { DemoDataBanner() }
        item { Text(stringResource(R.string.recyclers_authorized), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            OutlinedTextField(
                filters.query,
                vm::setQuery,
                label = { Text(stringResource(R.string.recycler_search)) },
                leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("recycler_search")
            )
        }
        item {
            AssistChip(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) refreshLocation() else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                label = {
                    when {
                        locationBusy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        locationError -> Text("Location unavailable · Try again")
                        locationLabel != null -> Text("Near ${locationLabel!!}")
                        else -> Text("Use my current location")
                    }
                },
                leadingIcon = { Icon(Icons.Filled.LocationOn, null) }
            )
        }
        item { Text(stringResource(R.string.recycler_radius), style = MaterialTheme.typography.titleSmall) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 25, 50).forEach { radius ->
                    FilterChip(filters.radiusKm == radius, { vm.setRadius(radius) }, label = { Text(stringResource(R.string.recycler_km, radius)) })
                }
            }
        }
        item { Text(stringResource(R.string.recycler_material), style = MaterialTheme.typography.titleSmall) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "PCB / Circuit Board", "Copper", "Cables", "Battery", "LCD Panel", "Plastic").forEach { material ->
                    FilterChip(filters.material == material, { vm.setMaterial(material) }, label = { Text(material) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(filters.pickupOnly, vm::setPickupOnly)
                Text(stringResource(R.string.recycler_pickup_only), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recycler_sort), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                FilterChip(filters.sort == RecyclerSortMode.PROXIMITY, { vm.setSort(RecyclerSortMode.PROXIMITY) }, label = { Text(stringResource(R.string.recycler_nearby)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(filters.sort == RecyclerSortMode.RATE, { vm.setSort(RecyclerSortMode.RATE) }, label = { Text(stringResource(R.string.recycler_rate)) })
            }
        }
        when (displayedState) {
            is UiState.Loading -> item { LoadingContent(Modifier.fillMaxWidth().height(220.dp)) }
            is UiState.Error -> item { ErrorContent(modifier = Modifier.fillMaxWidth().height(220.dp)) }
            is UiState.Empty -> item { EmptyContent(Modifier.fillMaxWidth().height(220.dp)) }
            is UiState.Offline -> {
                item { CachedRecyclerNotice() }
                items(displayedState.cached.orEmpty(), key = { it.id }) { recycler -> RecyclerCard(recycler, vm, onOpen) }
            }
            is UiState.Success -> items(displayedState.data, key = { it.id }) { recycler -> RecyclerCard(recycler, vm, onOpen) }
            is UiState.Syncing -> {
                item { CachedRecyclerNotice() }
                items(displayedState.cached.orEmpty(), key = { it.id }) { recycler -> RecyclerCard(recycler, vm, onOpen) }
            }
        }
    }
}

@Composable
private fun CachedRecyclerNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = stringResource(R.string.recycler_cached), modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.recycler_cached), modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecyclerCard(recycler: Recycler, vm: RecyclersViewModel, onOpen: (String) -> Unit) {
    Surface(
        onClick = { onOpen(recycler.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("recycler_${recycler.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painterResource(if (recycler.acceptedMaterials.any { it.contains("PCB", true) }) R.drawable.kc_pcb else R.drawable.kc_facility),
                contentDescription = recycler.facility,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recycler.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (recycler.authorized) Icon(Icons.Filled.Verified, stringResource(R.string.recyclers_authorized), tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.recycler_rate_value, recycler.offeredRatePerKg), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                    Text(stringResource(R.string.recyclers_distance_km, recycler.distanceKm?.toString() ?: "?"), modifier = Modifier.padding(start = 5.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(recycler.area, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (recycler.pickupAvailable) Icons.Filled.PedalBike else Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(if (recycler.pickupAvailable) stringResource(R.string.recycler_pickup_yes) else stringResource(R.string.recycler_pickup_no), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.recycler_match, vm.matchScore(recycler).total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun RecyclerDetailScreen(recycler: Recycler, onCall: () -> Unit, onRequestQuote: () -> Unit = {}) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(recycler.name, style = MaterialTheme.typography.headlineLarge)
        Text(recycler.facility, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null); Text(recycler.address, modifier = Modifier.padding(start = 8.dp)) }
        EvidenceSection(
            title = stringResource(R.string.recycler_detail_authorization),
            status = stringResource(if (recycler.authorized) R.string.recyclers_authorized else R.string.recycler_not_verified)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(stringResource(R.string.recycler_detail_authorization), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (recycler.authorized) stringResource(R.string.recyclers_authorized) else stringResource(R.string.recycler_not_verified), color = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.recycler_rate_value, recycler.offeredRatePerKg), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.recycler_materials, recycler.acceptedMaterials.joinToString(", "))); Text(stringResource(R.string.recycler_hours, recycler.operatingHours)); Text(stringResource(R.string.recycler_handover, recycler.typicalHandoverHours)); if (recycler.latitude != null && recycler.longitude != null) Text(stringResource(R.string.recycler_map_placeholder, recycler.latitude, recycler.longitude), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        EvidenceSection(title = stringResource(R.string.recycler_trust_passport)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recycler.authorizationAuthority?.let { Text(stringResource(R.string.recycler_verified_by, it)) }
                recycler.authorizationValidUntilEpochMs?.let { Text(stringResource(R.string.recycler_valid_until, formatTrustDate(it))) }
                recycler.rating?.let { Text(stringResource(R.string.recycler_rating, it, recycler.reviewCount)) }
                recycler.completedHandovers?.let { Text(stringResource(R.string.recycler_completed_handovers, it)) }
                Text(stringResource(R.string.recycler_trust_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onRequestQuote, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.quote_request_button)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onCall, enabled = recycler.contactPhone.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.recycler_call)) }; OutlinedButton(onClick = { val lat = recycler.latitude ?: 0.0; val lon = recycler.longitude ?: 0.0; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=${Uri.encode(recycler.address)}"))) } }, enabled = recycler.latitude != null && recycler.longitude != null, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Filled.Directions, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.recycler_map)) } }
        if (recycler.contactPhone.isNotBlank()) Text(stringResource(R.string.recycler_contact_note, recycler.contactPhone), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTrustDate(epochMs: Long): String = IndiaFormat.date(epochMs)
