package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.irinteractivestudios.kabadiwalaconnect.util.UiActionTrace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.util.IndiaFormat
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.components.rememberKcResponsiveLayout
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcStatusPill
import com.irinteractivestudios.kabadiwalaconnect.util.ImagePipeline
import com.irinteractivestudios.kabadiwalaconnect.util.AndroidLocationProvider
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.util.Locale
import java.util.UUID

private fun Double.roundMoneyForUi(): Double = (this * 100.0).roundToInt() / 100.0

private const val PICKUP_MIN_LEAD_MINUTES = 15

// Keep this list identical to the backend MaterialCategory enum. Paper/newspaper
// is not currently a first-class backend category, so it is represented by
// OTHER until the contract adds a dedicated category.
private val materials = listOf("PLASTIC", "CABLE", "COPPER", "PCB", "BATTERY", "MOTOR", "MAGNET", "CRT", "LCD_PANEL", "OTHER")

private fun materialName(value: String) = localizedSupplyChainText(value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
private fun statusName(value: String) = localizedSupplyChainText(value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
private fun money(value: Double?) = value?.let { "₹${"%.2f".format(it)}" } ?: localizedSupplyChainText("Pending inspection")

@Composable
private fun TraceBusyFrames(busy: Set<String>) {
    LaunchedEffect(busy) {
        if (busy.isNotEmpty()) withFrameNanos { busy.forEach(UiActionTrace::frameDrawn) }
    }
}

@Composable
fun HouseholdSupplyScreen(
    state: SupplyChainState,
    onRefresh: () -> Unit,
    onCreateListing: () -> Unit,
    onIncreaseRadius: () -> Unit = {},
    onRetryPhoto: () -> Unit = {},
    onRequestPickup: (String, String?) -> Unit,
    onCancelListing: (String) -> Unit = {},
    onCancelPickup: (String) -> Unit = {},
    onReschedulePickup: (String, String) -> Unit = { _, _ -> },
    onDecideSettlement: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    onConfirmPaymentReceived: (String) -> Unit = {},
    pickupQr: HouseholdPickupQrDto? = null,
    pickupQrLoadingId: String? = null,
    pickupQrError: String? = null,
    onLoadPickupQr: (String) -> Unit = {},
    onClearPickupQr: () -> Unit = {},
    onOpenPickupChat: (String) -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    initialArea: String = "",
    busy: Set<String> = emptySet()
) {
    TraceBusyFrames(busy)
    val layout = rememberKcResponsiveLayout()
    val pickupsByListing = remember(state.pickups) { state.pickups.groupBy { it.listingId } }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        item { HouseholdWelcomeHeader(initialArea, onRefresh, state.loading, layout.isCompact) }
        item { Text(stringResource(R.string.ui_copy_c54cc1bc59fc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            HouseholdSalePanel(
                busy = "create-listing" in busy,
                onCreateListing = onCreateListing,
                compact = layout.isCompact
            )
        }
        if (state.initialLoadComplete || state.listings.isNotEmpty() || state.pickups.isNotEmpty()) item {
            val open = state.listings.count { it.status in setOf("POSTED", "PENDING_SYNC") }
            val active = state.pickups.count { it.status !in listOf("COMPLETED", "CANCELLED", "REJECTED") }
            SummaryStrip("$open${if (state.householdListingsNextCursor != null) "+" else ""} open", "$active${if (state.householdPickupsNextCursor != null) "+" else ""} active pickups", layout.isCompact)
        }
        state.error?.let { message -> item { ErrorPanel(message, onRefresh) } }
        if (state.pendingPhotoUpload != null) item {
            OutlinedButton(onClick = onRetryPhoto, enabled = "upload-listing-photo" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if ("upload-listing-photo" in busy) "Uploading photo…" else "Retry photo upload")
            }
        }
        item { Text(stringResource(R.string.ui_copy_2f0993983a8b), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.householdListingsLoading && state.listings.isEmpty()) item { LoadingPanel("Loading your listings…") }
        if (state.householdListingsLoaded && state.listings.isEmpty() && state.error == null) item { EmptyPanel("No listings yet", "Post your first listing to request pickup.") }
        items(state.listings, key = { it.id }) { listing ->
            HouseholdListingCard(
                listing = listing,
                pickups = pickupsByListing[listing.id].orEmpty(),
                pickupPendingSync = listing.id in state.pendingPickupListingIds,
                kabadiwalas = state.kabadiwalas,
                busy = busy,
                onRequestPickup = onRequestPickup,
                radiusKm = state.kabadiwalaRadiusKm,
                onIncreaseRadius = onIncreaseRadius,
                onCancelListing = onCancelListing,
                onCancelPickup = onCancelPickup,
                onReschedulePickup = onReschedulePickup,
                onDecideSettlement = onDecideSettlement,
                onConfirmPaymentReceived = onConfirmPaymentReceived,
                pickupQr = pickupQr,
                pickupQrLoadingId = pickupQrLoadingId,
                pickupQrError = pickupQrError,
                onLoadPickupQr = onLoadPickupQr,
                onClearPickupQr = onClearPickupQr,
                onOpenPickupChat = onOpenPickupChat
            )
        }
        if (state.householdListingsNextCursor != null || state.householdPickupsNextCursor != null) item {
            OutlinedButton(
                onClick = onLoadMoreHistory,
                enabled = "household-history-page" !in busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if ("household-history-page" in busy) "Loading older history…" else "Load older history")
            }
        }
    }
}

@Composable
private fun HouseholdWelcomeHeader(area: String, onRefresh: () -> Unit, loading: Boolean, compact: Boolean) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            if (area.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                        Text(area, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Text(stringResource(R.string.household_home_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.household_home_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh")
        }
    }
}

@Composable
private fun HouseholdSalePanel(busy: Boolean, onCreateListing: () -> Unit, compact: Boolean) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(if (compact) 14.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .72f), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.household_new_pickup), Modifier.padding(horizontal = 11.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(R.string.household_sell_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.household_sell_detail), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onCreateListing,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Posting…" else "Sell scrap")
            }
        }
    }
}

/** Live directory for a household. Pickup requests are made from a listing,
 * so the selected buyer and listing ownership remain explicit. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HouseholdKabadiwalasScreen(
    state: SupplyChainState,
    onRefresh: () -> Unit,
    onSearch: (String, Int) -> Unit,
    onUseLocation: (CurrentLocation, Int) -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { AndroidLocationProvider(context) }
    var area by remember(state.kabadiwalaAreaQuery) { mutableStateOf(state.kabadiwalaAreaQuery) }
    var radiusKm by remember(state.kabadiwalaRadiusKm) { mutableStateOf(state.kabadiwalaRadiusKm) }
    var locationBusy by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var gpsAccuracyMeters by remember { mutableStateOf<Float?>(null) }
    var gpsStreetAddressUnavailable by remember { mutableStateOf(false) }
    var showLocationRationale by remember { mutableStateOf(false) }
    fun loadCurrentLocation() {
        scope.launch {
            locationBusy = true
            locationError = false
            val current = runCatching { locationProvider.current() }.getOrNull()
            if (current == null) {
                locationError = true
                gpsAccuracyMeters = null
                gpsStreetAddressUnavailable = false
            } else {
                gpsAccuracyMeters = current.accuracyMeters
                gpsStreetAddressUnavailable = current.formattedAddress == null
                (current.formattedAddress ?: current.areaName)?.let { area = it }
                onUseLocation(current, radiusKm)
            }
            locationBusy = false
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) loadCurrentLocation() else locationError = true
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxRule()
                Text(stringResource(R.string.ui_copy_fc38ee6380fb), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_b944a83236e6), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ui_copy_117d564cbafe), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_cd59058314ca), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = area,
                onValueChange = {
                    area = it.take(160)
                    gpsAccuracyMeters = null
                    gpsStreetAddressUnavailable = false
                },
                label = { Text(stringResource(R.string.ui_copy_4b373d484f61)) },
                leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(stringResource(R.string.ui_copy_8df84e7acd3d), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(5, 10, 25, 50, 100, 200).forEach { distance ->
                    FilterChip(
                        selected = radiusKm == distance,
                        onClick = { radiusKm = distance },
                        label = { Text(stringResource(R.string.ui_copy_982c8e50d607, distance), maxLines = 1) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    gpsAccuracyMeters = null
                    gpsStreetAddressUnavailable = false
                    onSearch(area.trim(), radiusKm)
                }, enabled = !state.kabadiwalaLoading && area.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.ui_copy_f4fd898b8fb9)) }
                OutlinedButton(onClick = {
                    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasLocation) loadCurrentLocation() else showLocationRationale = true
                }, enabled = !locationBusy && !state.kabadiwalaLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (locationBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.LocationOn, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_copy_622c745f6090), maxLines = 1)
                }
            }
            if (locationError) Text(stringResource(R.string.ui_copy_947e7e7a11c1), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            gpsAccuracyMeters?.let { accuracy ->
                Text(stringResource(R.string.ui_copy_40dd5197b0c9, accuracy.roundToInt().coerceAtLeast(1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (gpsStreetAddressUnavailable) Text(stringResource(R.string.ui_copy_27efb33a8815), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        if (state.kabadiwalaLoading) item { CircularProgressIndicator(Modifier.size(26.dp)) }
        if (state.initialLoadComplete && !state.loading && !state.kabadiwalaLoading && state.kabadiwalas.isEmpty()) item {
            EmptyPanel(
                if (state.kabadiwalaRequiresLocation && area.isBlank()) "Choose an area to begin" else "No active Kabadiwalas serving this area yet",
                if (state.kabadiwalaRequiresLocation && area.isBlank()) "Use your location or enter a locality, city, state or PIN code." else "Try a nearby area or a wider radius."
            )
        }
        items(state.kabadiwalas, key = { it.id }) { kabadiwala ->
            Surface(shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(15.dp, 15.dp, 15.dp, 4.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(kabadiwala.displayName ?: "Kabadiwala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(kabadiwala.areaName.ifBlank { "Area not provided" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(if (kabadiwala.acceptingPickups) "Accepting" else "Busy today")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        kabadiwala.distanceKm?.let { Text(stringResource(R.string.recyclers_distance_km, "%.1f".format(it)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        if (kabadiwala.ratingAverage != null && kabadiwala.reviewCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(17.dp))
                                Text(stringResource(R.string.ui_copy_f1e76e0f8207, "%.1f".format(kabadiwala.ratingAverage), kabadiwala.reviewCount), style = MaterialTheme.typography.labelMedium)
                            }
                        } else Text(stringResource(R.string.ui_copy_143533681319), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.ui_copy_b4c5c8f56a08, kabadiwala.completedPickupCount, "%.1f".format(kabadiwala.acceptedWeightKg)), style = MaterialTheme.typography.bodySmall)
                    if (kabadiwala.collectedMaterials.isNotEmpty()) Text(kabadiwala.collectedMaterials.joinToString(" · ") { materialName(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onOpenProfile(kabadiwala.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_8d3a84d16704)) }
                }
            }
        }
        if (state.kabadiwalaHasMore) item { OutlinedButton(onClick = onLoadMore, enabled = !state.kabadiwalaLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_76dee9398c79)) } }
    }
    if (showLocationRationale) AlertDialog(
        onDismissRequest = { showLocationRationale = false },
        title = { Text(stringResource(R.string.ui_copy_c46c55963043)) },
        text = { Text(stringResource(R.string.ui_copy_9ff28e725543)) },
        confirmButton = { Button(onClick = { showLocationRationale = false; locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(stringResource(R.string.payment_save)) } },
        dismissButton = { TextButton(onClick = { showLocationRationale = false }) { Text(stringResource(R.string.ui_copy_fe837a0b12cf)) } }
    )
}

@Composable
fun KabadiwalaPublicProfileScreen(
    profile: KabadiwalaPublicProfileDto?,
    loading: Boolean,
    pickups: List<PickupRequestDto>,
    error: String?,
    onRatePickup: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_copy_bc485bb987b7)) }
        Text(profile?.displayName ?: "Kabadiwala profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                profile?.let { item ->
                    Text(item.areaName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.distanceKm?.let { Text(stringResource(R.string.ui_copy_14d1fa260b92, "%.1f".format(it)), color = MaterialTheme.colorScheme.primary) }
                    StatusChip(if (item.acceptingPickups) "Accepting pickups" else "No slots available today")
                    item.pickupPricing?.let { pricing ->
                        Text(if (pricing.feePerKm == 0.0) "Free pickup up to ${pricing.maxDistanceKm.toInt()} km" else "Free within ${pricing.freeRadiusKm.toInt()} km · ${money(pricing.feePerKm)} per km beyond", style = MaterialTheme.typography.bodyMedium)
                        pricing.estimatedFee?.let { Text(stringResource(R.string.ui_copy_b2f1f1ffaddf, money(it)), color = MaterialTheme.colorScheme.primary) }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.ui_copy_fc6b8a65ae7d), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_copy_cd66b6bb18bc, item.completedPickupCount, "%.1f".format(item.acceptedWeightKg)))
                    if (item.collectedMaterials.isNotEmpty()) Text("Materials: ${item.collectedMaterials.joinToString(" · ") { materialName(it) }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.ratingAverage != null && item.reviewCount > 0) Text(stringResource(R.string.ui_copy_16ad6e9c02a8, "%.1f".format(item.ratingAverage), item.reviewCount))
                    else Text(stringResource(R.string.ui_copy_6283b7bd2692), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text(stringResource(R.string.ui_copy_3b39ed848820), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (pickups.isEmpty()) Text(stringResource(R.string.ui_copy_6feb5061a510), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pickups.forEach { pickup ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.ui_copy_3786e15cb669, materialName(pickup.finalCategory ?: "OTHER"), "%.1f".format(pickup.actualWeight ?: 0.0)), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_copy_83cd624d2e1a), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                (1..5).forEach { rating ->
                                    IconButton(onClick = { onRatePickup(pickup.id, rating) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Star, contentDescription = "Rate $rating stars", tint = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KabadiwalaPickupPricingScreen(pricing: PickupPricingDto?, saving: Boolean, error: String?, saved: Boolean, onBack: () -> Unit, onSave: (PickupPricingDto) -> Unit) {
    var free by rememberSaveable(pricing) { mutableStateOf(pricing?.freeRadiusKm?.toString() ?: "5") }
    var perKm by rememberSaveable(pricing) { mutableStateOf(pricing?.feePerKm?.toString() ?: "0") }
    var maximum by rememberSaveable(pricing) { mutableStateOf(pricing?.maxDistanceKm?.toString() ?: "25") }
    val freeValue = free.toDoubleOrNull()
    val perKmValue = perKm.toDoubleOrNull()
    val maxValue = maximum.toDoubleOrNull()
    val valid = freeValue != null && perKmValue != null && maxValue != null && freeValue >= 0 && perKmValue >= 0 && maxValue > 0 && maxValue <= 200 && freeValue <= maxValue
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.ui_copy_d0fcd2732aba)) }
        Text(stringResource(R.string.ui_copy_321def52e839), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.ui_copy_78a7d9b54038), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(free, { free = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_0a60314504b2)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(perKm, { perKm = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_d93e4ec937b6)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maximum, { maximum = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_576bfbc1605e)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (saved) Text(stringResource(R.string.ui_copy_fdee150f212e), color = MaterialTheme.colorScheme.primary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (valid) onSave(PickupPricingDto(freeValue, perKmValue, maxValue)) }, enabled = valid && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (saving) "Saving…" else "Save pickup charges") }
    }
}

@Composable
private fun HouseholdListingCard(
    listing: HouseholdListingDto,
    pickups: List<PickupRequestDto>,
    pickupPendingSync: Boolean,
    kabadiwalas: List<KabadiwalaProfileDto>,
    busy: Set<String>,
    onRequestPickup: (String, String?) -> Unit,
    radiusKm: Int,
    onIncreaseRadius: () -> Unit,
    onCancelListing: (String) -> Unit,
    onCancelPickup: (String) -> Unit,
    onReschedulePickup: (String, String) -> Unit,
    onDecideSettlement: (String, String, String?, String?) -> Unit,
    onConfirmPaymentReceived: (String) -> Unit,
    pickupQr: HouseholdPickupQrDto?,
    pickupQrLoadingId: String?,
    pickupQrError: String?,
    onLoadPickupQr: (String) -> Unit,
    onClearPickupQr: () -> Unit,
    onOpenPickupChat: (String) -> Unit
) {
    val activePickupStatuses = setOf("WAITING_FOR_PICKUP", "REQUESTED", "ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED", "WEIGHED")
    val pickup = pickups.firstOrNull { it.status in activePickupStatuses }
        ?: pickups.firstOrNull { it.status == "COMPLETED" }
    var showCancelListing by remember(listing.id) { mutableStateOf(false) }
    var showCancelPickup by remember(pickup?.id) { mutableStateOf(false) }
    var showReschedule by remember(pickup?.id) { mutableStateOf(false) }
    var showSettlement by remember(pickup?.id) { mutableStateOf(false) }
    var showPaymentConfirmation by remember(pickup?.id) { mutableStateOf(false) }
    var showPickupQr by remember(pickup?.id) { mutableStateOf(false) }
    var showPartnerChoices by remember(listing.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.primary); Text(friendlyMaterial(listing.materialCategory).title, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(pickup?.status ?: if (pickupPendingSync) "PENDING_SYNC" else listing.status)) }
            Text(stringResource(R.string.ui_copy_505420b9e7e6, "%.1f".format(listing.estimatedWeight), listing.condition.lowercase()), style = MaterialTheme.typography.bodyMedium)
            val pickupAddress = listing.pickupAddress?.takeIf(String::isNotBlank) ?: listing.areaName
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.ui_copy_5ba89bef4034, pickupAddress), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (listing.photoAttached == true || !listing.photoReference.isNullOrBlank() || listing.photoReferences.isNotEmpty()) Text(stringResource(R.string.ui_copy_6b2f1c4c133c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            val minEstimate = listing.estimatedPriceMin
            val maxEstimate = listing.estimatedPriceMax
            if (minEstimate != null && maxEstimate != null) Text(stringResource(R.string.ui_copy_d3c1c4fc6bd6, "%.0f".format(minEstimate), "%.0f".format(maxEstimate)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (pickup == null && listing.status == "POSTED") {
                if (pickupPendingSync) {
                    Text(stringResource(R.string.ui_copy_d9ebcab9261d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.ui_copy_096cb1118d2e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onRequestPickup(listing.id, null) }, enabled = "pickup-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_567da6ba2dee))
                    }
                }
                if (!pickupPendingSync && kabadiwalas.isNotEmpty()) {
                    TextButton(onClick = { showPartnerChoices = !showPartnerChoices }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (showPartnerChoices) "Hide partner choices" else "Choose a specific Kabadiwala")
                    }
                    if (showPartnerChoices) {
                    kabadiwalas.take(4).forEach { kabadiwala ->
                        val requestBusy = "pickup-${listing.id}" in busy
                        OutlinedButton(onClick = { onRequestPickup(listing.id, kabadiwala.id) }, enabled = !requestBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.LocalShipping, null); Spacer(Modifier.width(8.dp)); Text("Request pickup · ${kabadiwala.displayName ?: "Kabadiwala"}")
                        }
                    }
                    }
                } else if (!pickupPendingSync && kabadiwalas.isEmpty()) {
                    Text(stringResource(R.string.ui_copy_c9c7bbf7cec9, radiusKm), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (radiusKm < 20) OutlinedButton(onClick = onIncreaseRadius, enabled = "pickup-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_52508dd7b5ae, if (radiusKm == 5) 10 else 25))
                    }
                }
                TextButton(onClick = { showCancelListing = true }, enabled = "cancel-listing-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_c3be5e282d6f)) }
            } else if (pickup == null && pickups.any { it.status == "CANCELLED" }) {
                Text(stringResource(R.string.ui_copy_7105c5c6087c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pickup?.let { item ->
                PickupJourneyTimeline(item)
                item.scheduledSlot?.let { Text("Scheduled: ${IndiaFormat.dateTimeIso(it) ?: "Time unavailable"}") }
                if ((item.pickupCharge ?: 0.0) > 0 && item.finalAmount == null) Text(stringResource(R.string.ui_copy_c2ca2a97b68e, money(item.pickupCharge)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.status == "SCHEDULED") Text(stringResource(R.string.ui_copy_97df03205744), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.status == "IN_TRANSIT") Text(stringResource(R.string.ui_copy_0264bf4f33e6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.kabadiwalaId != null && item.status in setOf("ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED", "WEIGHED")) {
                    OutlinedButton(onClick = { onOpenPickupChat(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_copy_9b1318f8a1b8))
                    }
                }
                if (item.status == "ARRIVED") {
                    if (item.householdQrScannedAt != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.ui_copy_f8755d7001f5), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { showPickupQr = true; onLoadPickupQr(item.id) },
                            enabled = pickupQrLoadingId != item.id,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) {
                            if (pickupQrLoadingId == item.id) CircularProgressIndicator(Modifier.size(20.dp))
                            else Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (pickupQrLoadingId == item.id) "Preparing pickup QR…" else "Show pickup QR to Kabadiwala")
                        }
                    }
                }
                if (item.finalAmount != null) {
                    Text(stringResource(R.string.ui_copy_15f2de309c67, money(item.finalAmount), "%.1f".format(item.actualWeight ?: 0.0)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if ((item.pickupCharge ?: 0.0) > 0) Text(stringResource(R.string.ui_copy_2e3e55b0fa5a, money(item.grossMaterialAmount), money(item.pickupCharge)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.status in setOf("WAITING_FOR_PICKUP", "REQUESTED", "ACCEPTED", "SCHEDULED")) {
                    OutlinedButton(onClick = { showCancelPickup = true }, enabled = "cancel-pickup-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_34d25e7def08)) }
                }
                if (item.status in setOf("ACCEPTED", "SCHEDULED")) {
                    OutlinedButton(onClick = { showReschedule = true }, enabled = "reschedule-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_d442fe995b0c)) }
                }
                if (item.settlementStatus == "PENDING_HOUSEHOLD_CONFIRMATION") {
                    Text(stringResource(R.string.ui_copy_6f446a4c3eaa), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { showSettlement = true }, enabled = "settlement-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_f17b42d2c414, money(item.finalAmount))) }
                } else if (item.settlementStatus == "ACCEPTED" && item.settlementPayment == null) {
                    Text(stringResource(R.string.ui_copy_a728f47870a7, money(item.finalAmount)), style = MaterialTheme.typography.bodyMedium)
                } else if (item.settlementPayment?.status == "RECORDED" && item.settlementPayment.householdReceivedAt == null) {
                    Text(stringResource(R.string.ui_copy_0b9fc10a8f1f, money(item.settlementPayment.amount), paymentMethodName(item.settlementPayment.paymentMethod)), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { showPaymentConfirmation = true }, enabled = "payment-received-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_f41e8e49f77b, money(item.settlementPayment.amount))) }
                } else if (item.settlementPayment?.householdReceivedAt != null) {
                    Text(stringResource(R.string.ui_copy_49be55c8ac2e, money(item.settlementPayment.amount), statusName(item.settlementPayment.status)), color = MaterialTheme.colorScheme.primary)
                } else if (item.settlementStatus == "DISPUTED") {
                    Text("Settlement is under review${item.settlementReasonCode?.let { " · $it" } ?: ""}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showCancelListing) AlertDialog(
        onDismissRequest = { showCancelListing = false },
        title = { Text(stringResource(R.string.ui_copy_0a67db09b076)) },
        text = { Text(stringResource(R.string.ui_copy_00d5c0a78c94)) },
        confirmButton = { TextButton(onClick = { showCancelListing = false; onCancelListing(listing.id) }) { Text(stringResource(R.string.ui_copy_c3be5e282d6f)) } },
        dismissButton = { TextButton(onClick = { showCancelListing = false }) { Text(stringResource(R.string.ui_copy_7ba68e2a27c8)) } }
    )
    pickup?.let { item ->
        if (showPickupQr && item.status == "ARRIVED" && item.householdQrScannedAt == null) {
            val qr = pickupQr?.takeIf { it.pickupId == item.id }
            Dialog(onDismissRequest = { showPickupQr = false; onClearPickupQr() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Text(stringResource(R.string.ui_copy_95510e2a9b00), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        Text(stringResource(R.string.ui_copy_cca8bd689033), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
                        when {
                            qr != null -> {
                                val bitmap = remember(qr.qrCodeData) { createSupplyQr(qr.qrCodeData) }
                                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.ui_copy_5a010fda197e), modifier = Modifier.size(260.dp), contentScale = ContentScale.Fit)
                                else Text(stringResource(R.string.ui_copy_a859c25730d6), color = MaterialTheme.colorScheme.error)
                                Text(stringResource(R.string.ui_copy_271560c5fa94), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                                OutlinedButton(onClick = { onLoadPickupQr(item.id) }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 50.dp)) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_32ece51bb874)) }
                            }
                            pickupQrLoadingId == item.id -> CircularProgressIndicator(Modifier.padding(24.dp))
                            pickupQrError != null -> {
                                Text(pickupQrError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton(onClick = { onLoadPickupQr(item.id) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 50.dp)) { Text(stringResource(R.string.common_retry)) }
                            }
                        }
                        TextButton(onClick = { showPickupQr = false; onClearPickupQr() }, modifier = Modifier.padding(top = 18.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_bbfa773e5a63)) }
                    }
                }
            }
        }
        if (showCancelPickup) AlertDialog(
            onDismissRequest = { showCancelPickup = false },
            title = { Text(stringResource(R.string.ui_copy_cbfb65200498)) },
            text = { Text(stringResource(R.string.ui_copy_eada4e0172d3)) },
            confirmButton = { TextButton(onClick = { showCancelPickup = false; onCancelPickup(item.id) }) { Text(stringResource(R.string.ui_copy_34d25e7def08)) } },
            dismissButton = { TextButton(onClick = { showCancelPickup = false }) { Text(stringResource(R.string.ui_copy_f7c449371849)) } }
        )
        if (showReschedule) ReschedulePickupDialog(
            onDismiss = { showReschedule = false },
            onSubmit = { slot -> showReschedule = false; onReschedulePickup(item.id, slot) }
        )
        if (showSettlement) SettlementDecisionDialog(
            title = "Collect ${money(item.finalAmount)} from Kabadiwala",
            acceptLabel = "Agree to ${money(item.finalAmount)}",
            onDismiss = { showSettlement = false },
            onSubmit = { decision, reason, notes -> showSettlement = false; onDecideSettlement(item.id, decision, reason, notes) }
        )
        if (showPaymentConfirmation) AlertDialog(
            onDismissRequest = { showPaymentConfirmation = false },
            title = { Text(stringResource(R.string.ui_copy_a2b007791f23)) },
            text = { Text(stringResource(R.string.ui_copy_0aa81585fb02, money(item.settlementPayment?.amount))) },
            confirmButton = { TextButton(onClick = { showPaymentConfirmation = false; onConfirmPaymentReceived(item.id) }) { Text(stringResource(R.string.ui_copy_3d977c25cb73)) } },
            dismissButton = { TextButton(onClick = { showPaymentConfirmation = false }) { Text(stringResource(R.string.ui_copy_8de95fd5b412)) } }
        )
    }
}

@Composable
private fun HouseholdPhotoActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        MaterialText(localizedSupplyChainText(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HouseholdListingCreateScreen(
    state: SupplyChainState,
    initialArea: String,
    initialPickupAddress: String = "",
    savedAccountLatitude: Double? = null,
    savedAccountLongitude: Double? = null,
    onBack: () -> Unit,
    onSuggestMaterial: (String) -> Unit,
    onClearMaterialSuggestion: () -> Unit,
    onCreateListing: (HouseholdListingCreateDto, List<String>, String) -> Unit,
    busy: Set<String> = emptySet(),
    onEstimateHouseholdPrice: (String, Double, String, String) -> Unit = { _, _, _, _ -> },
    onClearHouseholdPriceEstimate: () -> Unit = {}
) {
    // This is a multi-step, network-backed form. Save the draft through
    // Activity recreation so rotation, permission prompts, and keyboard
    // changes do not discard the user's photos or typed details.
    var material by rememberSaveable { mutableStateOf("") }
    var materialChosenManually by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable { mutableStateOf("") }
    var pickupAddress by rememberSaveable(initialPickupAddress, initialArea) { mutableStateOf(initialPickupAddress.ifBlank { initialArea }) }
    var pickupAreaName by rememberSaveable(initialArea) { mutableStateOf(initialArea) }
    var pickupLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickupLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickupLocationConfirmed by rememberSaveable { mutableStateOf(false) }
    var pickupLocationAccuracy by rememberSaveable { mutableStateOf<Float?>(null) }
    var locationError by rememberSaveable { mutableStateOf<String?>(null) }
    var detectingPickupLocation by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("INTACT") }
    var safetyAcknowledged by rememberSaveable { mutableStateOf(false) }
    var dataBearingDevice by rememberSaveable { mutableStateOf(false) }
    var ownerPreparationCompleted by rememberSaveable { mutableStateOf(false) }
    var dataDestructionRequested by rememberSaveable { mutableStateOf(false) }
    var photoPaths by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Stable for this draft (including retries and Activity recreation), unique
    // for the next listing form so idempotency keys never collide across lots.
    var listingDraftId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var lastDetectionPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoError by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf(false) }
    var showCameraPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionBlocked by rememberSaveable { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ioScope = rememberCoroutineScope()
    val pickupLocationProvider = remember(context) { AndroidLocationProvider(context) }
    val detectPickupLocation: () -> Unit = {
        if (!detectingPickupLocation) {
            detectingPickupLocation = true
            locationError = null
            ioScope.launch {
                val fix = runCatching { pickupLocationProvider.current() }.getOrNull()
                detectingPickupLocation = false
                if (fix == null) {
                    locationError = "Couldn't get a location. Turn on Location and try again, or use the saved account location if available."
                    pickupLocationConfirmed = false
                    pickupLatitude = null
                    pickupLongitude = null
                } else {
                    val detectedAddress = fix.formattedAddress?.takeIf(String::isNotBlank)
                        ?: fix.areaName?.takeIf(String::isNotBlank)
                    if (detectedAddress == null) {
                        locationError = "Location found, but Android couldn't find its address. Add the pickup address below and retry GPS at the pickup place."
                        pickupLatitude = null
                        pickupLongitude = null
                        pickupLocationConfirmed = false
                    } else {
                        pickupAddress = detectedAddress
                        pickupAreaName = fix.areaName?.takeIf(String::isNotBlank) ?: detectedAddress
                        pickupLatitude = fix.latitude
                        pickupLongitude = fix.longitude
                        pickupLocationAccuracy = fix.accuracyMeters
                        pickupLocationConfirmed = false
                    }
                }
            }
        }
        Unit
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.values.any { it } ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) detectPickupLocation() else locationError = "Location permission is needed to attach the pickup coordinates."
    }
    val requestPickupLocation = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) detectPickupLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        Unit
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val capturedPath = pendingCameraPath
        pendingCameraPath = null
        if (captured && capturedPath != null) {
            ioScope.launch(Dispatchers.IO) {
                val normalized = runCatching {
                    ImagePipeline.prepareForUpload(File(capturedPath), File(context.filesDir, "household_photos"))
                }.getOrNull()
                File(capturedPath).delete()
                withContext(Dispatchers.Main.immediate) {
                    if (normalized != null) {
                        val previousFirstPhoto = photoPaths.firstOrNull()
                        val updatedPaths = (photoPaths + normalized.absolutePath).distinct().take(6)
                        photoPaths = updatedPaths
                        photoError = false
                        cameraError = false
                        cameraPermissionDenied = false
                        cameraPermissionBlocked = false
                        updatedPaths.firstOrNull()?.takeIf { it != previousFirstPhoto }?.let(onSuggestMaterial)
                    } else {
                        cameraError = true
                    }
                }
            }
        } else {
            capturedPath?.let { File(it).delete() }
        }
    }
    val launchCamera: () -> Unit = {
        if (photoPaths.size < 6) {
            val directory = File(context.filesDir, "household_photos").apply { mkdirs() }
            val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
            runCatching {
                pendingCameraPath = file.absolutePath
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            }.onFailure {
                pendingCameraPath = null
                file.delete()
                cameraPermissionDenied = false
                cameraPermissionBlocked = false
                cameraError = true
            }
        }
        Unit
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionRequested = true
        if (granted) {
            cameraError = false
            cameraPermissionDenied = false
            cameraPermissionBlocked = false
            launchCamera()
        } else {
            cameraPermissionDenied = true
            val activity = context as? android.app.Activity
            cameraPermissionBlocked = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            cameraError = true
        }
    }
    val requestCamera = {
        if (photoPaths.size < 6) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraPermissionDenied = false
                cameraPermissionBlocked = false
                launchCamera()
            } else if (cameraPermissionRequested && (context as? android.app.Activity)?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true) {
                cameraPermissionBlocked = true
                showCameraPermissionDialog = true
            } else {
                cameraPermissionBlocked = false
                showCameraPermissionDialog = true
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        Log.d("HouseholdMaterialAI", "Gallery returned ${uris.size} photo(s)")
        ioScope.launch(Dispatchers.IO) {
            val paths = uris.mapNotNull { uri ->
                runCatching {
                    ImagePipeline.importUri(context, uri, File(context.filesDir, "household_photos")).absolutePath
                }.getOrNull()
            }
            withContext(Dispatchers.Main.immediate) {
                val previousFirstPhoto = photoPaths.firstOrNull()
                val updatedPaths = (photoPaths + paths).distinct().take(6)
                photoPaths = updatedPaths
                photoError = paths.size < uris.size
                cameraError = false
                cameraPermissionDenied = false
                cameraPermissionBlocked = false
                val detectionPath = updatedPaths.firstOrNull()?.takeIf { it != previousFirstPhoto }
                Log.d("HouseholdMaterialAI", "Imported ${paths.size} photo(s); new first photo=${detectionPath != null}")
                detectionPath?.let(onSuggestMaterial)
            }
        }
    }
    val firstPhotoPath = photoPaths.firstOrNull()
    LaunchedEffect(firstPhotoPath, state.materialDetectionPath, state.materialDetectionStatus) {
        if (firstPhotoPath != lastDetectionPhotoPath) {
            lastDetectionPhotoPath = firstPhotoPath
            materialChosenManually = false
            material = ""
            safetyAcknowledged = false
        }
        if (firstPhotoPath.isNullOrBlank()) {
            if (state.materialDetectionPath != null || state.materialSuggestion != null || state.materialDetectionStatus != HouseholdMaterialDetectionStatus.IDLE) {
                onClearMaterialSuggestion()
            }
        } else if (state.materialDetectionPath != firstPhotoPath || state.materialDetectionStatus == HouseholdMaterialDetectionStatus.IDLE) {
            Log.d("HouseholdMaterialAI", "Auto-detection requested for selected photo; status=${state.materialDetectionStatus}")
            onSuggestMaterial(firstPhotoPath)
        }
    }
    LaunchedEffect(firstPhotoPath, state.materialDetectionPath, state.materialSuggestion?.materialCategory, state.materialDetectionStatus) {
        if (firstPhotoPath != null && state.materialDetectionPath == firstPhotoPath && state.materialDetectionStatus == HouseholdMaterialDetectionStatus.SUCCESS && !materialChosenManually) {
            state.materialSuggestion?.materialCategory?.takeIf { friendlyMaterials.any { item -> item.key == it } }?.let { material = it; safetyAcknowledged = false }
        }
    }
    var observedInitialNotice by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice) {
        if (!observedInitialNotice) {
            // This ViewModel is shared with Household home. Ignore the notice
            // already in state when the form opens; it may belong to an older
            // listing and must not pop a fresh draft before it can be posted.
            observedInitialNotice = true
            return@LaunchedEffect
        }
        if (state.notice?.startsWith("Listing") == true) onBack()
    }
    val isHazardous = friendlyMaterial(material).hazardous
    val parsedWeight = weight.toDoubleOrNull()
    val weightError = weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0 || parsedWeight > 500)
    val addressError = pickupAddress.isNotBlank() && pickupAddress.trim().length < 2
    val priceArea = initialArea.trim()
    val localPriceEstimate = state.householdPriceEstimate?.takeIf {
        it.materialCategory == material && it.weightKg == parsedWeight && it.condition == condition && it.areaName == priceArea.trim()
    }
    val aiPriceEstimate = state.materialSuggestion?.let { suggestion ->
        val minPerKg = suggestion.estimatedPriceMinPerKg
        val maxPerKg = suggestion.estimatedPriceMaxPerKg
        if (suggestion.source.equals("AI", ignoreCase = true) && suggestion.materialCategory == material && parsedWeight != null && parsedWeight > 0 && parsedWeight <= 500 && minPerKg != null && maxPerKg != null && minPerKg > 0 && maxPerKg >= minPerKg) {
            val conditionMultiplier = when (condition) { "DAMAGED" -> 0.7; "PARTIAL" -> 0.4; else -> 1.0 }
            HouseholdPriceEstimate(
                materialCategory = material,
                weightKg = parsedWeight,
                condition = condition,
                areaName = priceArea,
                minimum = (minPerKg * parsedWeight * conditionMultiplier).roundMoneyForUi(),
                maximum = (maxPerKg * parsedWeight * conditionMultiplier).roundMoneyForUi(),
                disclaimer = "AI estimate only, based on the detected material. Confirm the final price after weighing.",
                source = "AI_INDICATIVE"
            )
        } else null
    }
    // OTHER mixes very different items; a detected device's own AI range is
    // more relevant than a generic OTHER price-board row.
    val currentPriceEstimate = if (material == "OTHER") aiPriceEstimate ?: localPriceEstimate else localPriceEstimate ?: aiPriceEstimate
    LaunchedEffect(material, parsedWeight, condition, priceArea) {
        if (material.isNotBlank() && parsedWeight != null && parsedWeight > 0 && parsedWeight <= 500) {
            onEstimateHouseholdPrice(material, parsedWeight, condition, priceArea)
        } else {
            onClearHouseholdPriceEstimate()
        }
    }
    val canSubmit = photoPaths.isNotEmpty() && material.isNotBlank() && parsedWeight != null && parsedWeight > 0 && parsedWeight <= 500 && pickupAddress.trim().isNotEmpty() && pickupLatitude != null && pickupLongitude != null && pickupLocationConfirmed && (!isHazardous || safetyAcknowledged) && "create-listing" !in busy
    val missingPostRequirements = buildList {
        if (photoPaths.isEmpty()) add("add a photo")
        if (material.isBlank()) add("choose a material")
        if (parsedWeight == null || parsedWeight <= 0 || parsedWeight > 500) add("enter a weight from 0–500 kg")
        if (pickupAddress.trim().isEmpty()) add("add a pickup address")
        if (pickupLatitude == null || pickupLongitude == null) add("choose GPS or your saved account location")
        else if (!pickupLocationConfirmed) add("confirm this is where the scrap will be collected")
        if (isHazardous && !safetyAcknowledged) add("confirm safe handling")
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.ui_copy_cddcbc052f05),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_copy_e47f65514817), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.ui_copy_e0ee14da7a77), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (photoPaths.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photoPaths, key = { it }) { path ->
                                    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.size(96.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize()) {
                                            if (bitmap != null) Image(bitmap, "Scrap photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            IconButton(onClick = {
                                                val remaining = photoPaths - path
                                                val removedFirstPhoto = path == firstPhotoPath
                                                photoPaths = remaining
                                                if (remaining.isEmpty() || removedFirstPhoto) onClearMaterialSuggestion()
                                            }, modifier = Modifier.size(48.dp).align(Alignment.TopEnd)) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.inverseSurface,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.ui_copy_c8f5eda8ace9),
                                                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                                                        modifier = Modifier.padding(5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val photosEnabled = photoPaths.size < 6
                            val openGallery = {
                                runCatching { gallery.launch("image/*") }
                                    .onFailure { photoError = true }
                                Unit
                            }
                            if (maxWidth < 280.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HouseholdPhotoActionButton(requestCamera, photosEnabled, Icons.Filled.CameraAlt, "Camera", Modifier.fillMaxWidth())
                                    HouseholdPhotoActionButton(openGallery, photosEnabled, Icons.Filled.AddPhotoAlternate, if (photoPaths.isEmpty()) "Gallery" else "Add more", Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HouseholdPhotoActionButton(requestCamera, photosEnabled, Icons.Filled.CameraAlt, "Camera", Modifier.weight(1f))
                                    HouseholdPhotoActionButton(openGallery, photosEnabled, Icons.Filled.AddPhotoAlternate, if (photoPaths.isEmpty()) "Gallery" else "Add more", Modifier.weight(1f))
                                }
                            }
                        }
                        if (photoPaths.isEmpty()) Text(stringResource(R.string.ui_copy_2b8e24e8e891), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (photoError) Text(stringResource(R.string.ui_copy_71cbf209bd34), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (cameraError) Text(
                            when {
                                cameraPermissionBlocked -> "Camera access is blocked. Allow it in Settings, or choose a photo from your gallery."
                                cameraPermissionDenied -> "Camera permission is needed to take a photo. Allow access or choose one from your gallery."
                                else -> "Camera couldn't start. Try again or choose a photo from your gallery."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.ui_copy_a981b2ac7aeb), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_ecce877a2443), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (state.materialDetectionStatus) {
                    HouseholdMaterialDetectionStatus.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(stringResource(R.string.ui_copy_a572ecef3372), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall) }
                    HouseholdMaterialDetectionStatus.SUCCESS -> {
                        state.materialSuggestion?.let {
                            val item = it.itemName?.takeIf(String::isNotBlank)?.let { name -> "$name · " }.orEmpty()
                            Text(stringResource(R.string.ui_copy_f15ef7469817, item, friendlyMaterial(it.materialCategory).title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            val minPerKg = it.estimatedPriceMinPerKg
                            val maxPerKg = it.estimatedPriceMaxPerKg
                            if (minPerKg != null && maxPerKg != null && minPerKg > 0 && maxPerKg >= minPerKg) {
                                Text(
                                    "AI indicative rate: ₹${String.format(Locale.forLanguageTag("en-IN"), "%,.0f", minPerKg)}–₹${String.format(Locale.forLanguageTag("en-IN"), "%,.0f", maxPerKg)}/kg. ${if (parsedWeight == null) "Add weight for a total estimate." else "See the total estimate below."}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } ?: Text(stringResource(R.string.ui_copy_28e9b3ba50b6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    HouseholdMaterialDetectionStatus.LOW_CONFIDENCE -> {
                        state.materialSuggestion?.let { suggestion ->
                            Text(stringResource(R.string.ui_copy_f7095c693613, friendlyMaterial(suggestion.materialCategory).title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        } ?: Text(stringResource(R.string.ui_copy_642193879ff0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE, HouseholdMaterialDetectionStatus.NETWORK_ERROR, HouseholdMaterialDetectionStatus.SERVICE_ERROR -> Text(state.materialDetectionMessage ?: "Photo detection is unavailable right now. You can still choose the material below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    HouseholdMaterialDetectionStatus.IDLE -> if (photoPaths.isNotEmpty()) {
                        Text(stringResource(R.string.ui_copy_fc547bf26724), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.ui_copy_5d4adccbe1c5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if ((state.materialDetectionStatus in setOf(HouseholdMaterialDetectionStatus.IDLE, HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE, HouseholdMaterialDetectionStatus.NETWORK_ERROR, HouseholdMaterialDetectionStatus.SERVICE_ERROR) ||
                        state.materialDetectionStatus == HouseholdMaterialDetectionStatus.SUCCESS && state.materialSuggestion == null) && photoPaths.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            Log.d("HouseholdMaterialAI", "Manual photo-detection retry tapped")
                            photoPaths.firstOrNull()?.let(onSuggestMaterial)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.ui_copy_82eaf0734632))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_copy_ddab1d36ac2b))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    friendlyMaterials.sortedBy { it.title.lowercase(Locale.ROOT) }.forEach { option ->
                        val selected = material == option.key
                        Card(onClick = { material = option.key; materialChosenManually = true; safetyAcknowledged = false }, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow), border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold); Text(option.examples, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (isHazardous) item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(stringResource(R.string.lot_hazard), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer); Text(stringResource(R.string.ui_copy_48b42de8981f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(safetyAcknowledged, { safetyAcknowledged = it }); Text(stringResource(R.string.ui_copy_842a9bdd000f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) } } }
            }
            item { Text(stringResource(R.string.lot_condition_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("INTACT", "DAMAGED", "PARTIAL").forEach { FilterChip(selected = condition == it, onClick = { condition = it }, label = { Text(it.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
            item { OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_copy_619d7c8212c8)) }, supportingText = { if (weightError) Text(stringResource(R.string.ui_copy_9aed921864c5)) }, isError = weightError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (currentPriceEstimate != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.ui_copy_961a0d35f11e), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        when {
                            currentPriceEstimate != null -> {
                                Text(
                                    "₹${String.format(Locale.forLanguageTag("en-IN"), "%,.0f", currentPriceEstimate.minimum)}–₹${String.format(Locale.forLanguageTag("en-IN"), "%,.0f", currentPriceEstimate.maximum)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(if (currentPriceEstimate.source == "AI_INDICATIVE") "AI indicative estimate for ${currentPriceEstimate.weightKg} kg. Use it as a rough guide." else "Based on ${currentPriceEstimate.weightKg} kg, condition, and current ${if (currentPriceEstimate.areaName.isBlank()) "material" else "local"} buying rates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(currentPriceEstimate.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            state.householdPriceEstimateLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.ui_copy_debb0d64d730), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                            else -> Text(
                                if (state.materialSuggestion?.source.equals("AI", ignoreCase = true) &&
                                    state.materialSuggestion?.materialCategory == material &&
                                    state.householdPriceEstimateMessage?.startsWith("No current verified price range") == true
                                ) {
                                    "AI identified the item, but could not estimate a scrap range from this photo. Your Kabadiwala can quote after weighing."
                                } else {
                                    state.householdPriceEstimateMessage ?: "Enter an approximate weight to see an available local price range. This estimate does not affect whether you can post."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = pickupAddress,
                    onValueChange = { value ->
                        val updated = value.take(240)
                        if (updated != pickupAddress) {
                            pickupAddress = updated
                            pickupLocationConfirmed = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { if (!imeVisible) Text(stringResource(R.string.ui_copy_6ceee0a8f70e)) },
                    placeholder = { if (imeVisible) Text(stringResource(R.string.ui_copy_6ceee0a8f70e)) },
                    supportingText = {
                        if (imeVisible) Text(if (addressError) "Add a complete address" else "Street/block and house number", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else if (addressError) Text(stringResource(R.string.ui_copy_2d5bd7acc987))
                        else Text(stringResource(R.string.ui_copy_4d62f4b69c3b))
                    },
                    isError = addressError,
                    minLines = if (imeVisible) 1 else 2,
                    maxLines = 3
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_copy_33db3aa77038), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_copy_66c07170ab8f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = requestPickupLocation,
                        enabled = !detectingPickupLocation,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (detectingPickupLocation) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (detectingPickupLocation) "Finding current location…" else "Use current GPS location")
                    }
                    if (savedAccountLatitude != null && savedAccountLongitude != null && initialPickupAddress.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                pickupAddress = initialPickupAddress
                                pickupAreaName = initialArea
                                pickupLatitude = savedAccountLatitude
                                pickupLongitude = savedAccountLongitude
                                pickupLocationAccuracy = null
                                locationError = null
                                pickupLocationConfirmed = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.ui_copy_1eb5d1831201, initialPickupAddress.take(64)))
                        }
                    }
                    if (pickupLatitude != null && pickupLongitude != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    if (pickupLocationAccuracy != null) "Location attached · accuracy about ${pickupLocationAccuracy!!.toInt()} m"
                                    else "Saved account coordinates attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = pickupLocationConfirmed, onCheckedChange = { pickupLocationConfirmed = it })
                                    Text(stringResource(R.string.ui_copy_01ddf58eeaa3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                    locationError?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(stringResource(R.string.ui_copy_70260f2b9579), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer); Text(stringResource(R.string.ui_copy_c8bc68e4cd1d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dataBearingDevice, { dataBearingDevice = it; if (!it) { ownerPreparationCompleted = false; dataDestructionRequested = false } }); Text(stringResource(R.string.ui_copy_af0f616254a6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }; if (dataBearingDevice) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ownerPreparationCompleted, { ownerPreparationCompleted = it }); Text(stringResource(R.string.ui_copy_fcdad39da790), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dataDestructionRequested, { dataDestructionRequested = it }); Text(stringResource(R.string.ui_copy_f59c3807a845), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) } } } } }
            item { OutlinedTextField(notes, { notes = it.take(1000) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.lot_notes_label)) }, minLines = 3, maxLines = 4) }
        }
         state.error?.let { message ->
             Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                 Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
             }
         }
         if (missingPostRequirements.isNotEmpty() && "create-listing" !in busy && !imeVisible) {
             Text(stringResource(R.string.ui_copy_305aa35012c1, missingPostRequirements.joinToString()),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
             )
         }
         Button(onClick = {
             parsedWeight?.let { value -> onCreateListing(HouseholdListingCreateDto(
                 materialCategory = material,
                 estimatedWeight = value,
                 condition = condition,
                 notes = notes.trim().ifBlank { null },
                 areaName = pickupAreaName.ifBlank { pickupAddress.trim() },
                 pickupAddress = pickupAddress.trim(),
                 latitude = pickupLatitude,
                 longitude = pickupLongitude,
                 estimatedPriceMin = currentPriceEstimate?.minimum,
                 estimatedPriceMax = currentPriceEstimate?.maximum,
                 dataBearingDevice = dataBearingDevice,
                 ownerPreparationCompleted = ownerPreparationCompleted,
                 dataDestructionRequested = dataDestructionRequested
             ), photoPaths, listingDraftId) }
         }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 54.dp)) { Text(if ("create-listing" in busy) "Posting…" else "Post scrap listing") }
     }
     if (showCameraPermissionDialog) {
         AlertDialog(
             onDismissRequest = { showCameraPermissionDialog = false },
             title = { MaterialText(if (cameraPermissionBlocked) "Camera access is blocked" else "Allow camera access") },
             text = {
                 MaterialText(
                     if (cameraPermissionBlocked) "Camera permission was denied earlier. Open Settings and allow Camera for Kabadiwala Connect, or choose a photo from your gallery."
                     else "Take a clear photo of the scrap from different angles. You can also choose photos from your gallery."
                 )
             },
             confirmButton = {
                 TextButton(onClick = {
                     showCameraPermissionDialog = false
                     if (cameraPermissionBlocked) {
                         context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                             data = Uri.parse("package:${context.packageName}")
                         })
                     } else {
                         cameraPermission.launch(Manifest.permission.CAMERA)
                     }
                 }) { MaterialText(if (cameraPermissionBlocked) "Open Settings" else "Allow") }
             },
             dismissButton = {
                 TextButton(onClick = { showCameraPermissionDialog = false }) { MaterialText("Not now") }
             }
         )
     }
 }


@Composable
fun KabadiwalaSupplyScreen(state: SupplyChainState, section: KabadiwalaSection, onRefresh: () -> Unit, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit, onCreateBulk: (BulkLotCreateDto) -> Unit, onCancelBulk: (String) -> Unit, onAcceptOffer: (String) -> Unit, capturedLots: List<Lot> = emptyList(), currentArea: String = "Current area", currentCollectorId: String = "", listingPhotos: Map<String, List<ByteArray>> = emptyMap(), listingPhotoErrors: Map<String, String> = emptyMap(), onLoadListingPhotos: (String, Int) -> Unit = { _, _ -> }, onRouteEstimate: (String, Double, String) -> Unit = { _, _, _ -> }, onCreatePool: (String, String) -> Unit = { _, _ -> }, onJoinPool: (String, Double, String, Double?) -> Unit = { _, _, _, _ -> }, onLeavePool: (String) -> Unit = {}, onLockPool: (String) -> Unit = {}, onPreparePoolHandover: (String) -> Unit = {}, onPrepareBulkHandover: (String) -> Unit = {}, onConfirmCollectorHandover: (String) -> Unit = {}, onAcknowledgeSafety: (String) -> Unit = {}, onCreateCapturedLot: () -> Unit = {}, onOpenTools: () -> Unit = {}, onOpenPickups: () -> Unit = {}, onOpenInventory: () -> Unit = {}, onRejectPickup: (String, String) -> Unit = { _, _ -> }, onCancelPickup: (String, String?) -> Unit = { _, _ -> }, onReassignPickup: (String, String, Boolean) -> Unit = { _, _, _ -> }, onRejectOffer: (String, String) -> Unit = { _, _ -> }, onCounterOffer: (String, Double, String?) -> Unit = { _, _, _ -> }, onLoadSafetyRouting: (String, String) -> Unit = { _, _ -> }, onLoadMaterialPassport: (String) -> Unit = {}, onLoadAnomalies: (String) -> Unit = {}, onDecideSupplySettlement: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> }, onRecordPickupPayment: (String, PickupSettlementPaymentRequestDto) -> Unit = { _, _ -> }, onVerifyHouseholdPickupQr: (String, String) -> Unit = { _, _ -> }, onOpenPickupChat: (String) -> Unit = {}, onOpenBulkChat: (String, String) -> Unit = { _, _ -> }, onLoadMorePickups: () -> Unit = {}, onLoadMoreLots: () -> Unit = {}, onLoadMoreOffers: () -> Unit = {}) {
    val layout = rememberKcResponsiveLayout()
    TraceBusyFrames(state.busy)
    val listingsById = remember(state.listings) { state.listings.associateBy { it.id } }
    var showBulk by remember { mutableStateOf(false) }
    var lotsMode by rememberSaveable(section) { mutableStateOf("LOTS") }
    val title = when (section) {
        KabadiwalaSection.HOME -> "Collection desk"
        KabadiwalaSection.INVENTORY -> "Scrap inventory"
        KabadiwalaSection.PICKUPS -> "Household pickups"
        KabadiwalaSection.LOTS -> "Recycler sales"
        KabadiwalaSection.TOOLS -> "Field tools"
    }
    val subtitle = when (section) {
        KabadiwalaSection.HOME -> "Your next pickup, stock and earnings in one view"
        KabadiwalaSection.INVENTORY -> "Available stock ready for a buyer"
        KabadiwalaSection.PICKUPS -> "Requests waiting for your next move"
        KabadiwalaSection.LOTS -> "Turn collected stock into buyer offers"
        KabadiwalaSection.TOOLS -> "Routes, shared stock and safe handling"
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            if (section == KabadiwalaSection.HOME) CollectorDeskHeader(onRefresh, state.loading)
            else if (section == KabadiwalaSection.TOOLS) FieldToolsHeader(onRefresh, state.loading)
            else RoleHeader(title, subtitle, Icons.Filled.Inventory2, onRefresh, state.loading)
        }
        val hasCollectorSnapshot = state.initialLoadComplete || state.cachedAtEpochMs > 0L || state.pickups.isNotEmpty() || state.inventory.isNotEmpty()
        if (!hasCollectorSnapshot && state.loading) item { LoadingPanel("Loading your collection desk…") }
        if (state.showingCachedEvidence && hasCollectorSnapshot) item {
            Text(stringResource(R.string.ui_copy_08484d351755), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (section == KabadiwalaSection.HOME && hasCollectorSnapshot) item {
            CollectorOverview(state, onOpenInventory, onOpenPickups)
        }
        if (section == KabadiwalaSection.HOME || section == KabadiwalaSection.PICKUPS) item {
            Text(stringResource(R.string.ui_copy_c0593d30aadf), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        when (section) {
            KabadiwalaSection.HOME, KabadiwalaSection.PICKUPS -> {
                if (section == KabadiwalaSection.PICKUPS) {
                    item { SummaryStrip("${state.pickups.count { it.status == "REQUESTED" || it.status == "WAITING_FOR_PICKUP" }}${if (state.collectorAssignedNextCursor != null || state.collectorWaitingNextCursor != null) "+" else ""} waiting", "${state.pickups.count { it.status == "SCHEDULED" }}${if (state.collectorAssignedNextCursor != null) "+" else ""} scheduled", layout.isCompact) }
                }
                item { Text(if (section == KabadiwalaSection.HOME) "Next pickups" else "Pickup queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (state.initialLoadComplete && !state.loading && state.pickups.isEmpty()) item { EmptyPanel("No household pickups", "New requests will appear here.") }
                items(if (section == KabadiwalaSection.HOME) state.pickups.take(2) else state.pickups, key = { it.id }) { pickup -> PickupCard(pickup, listingsById[pickup.listingId], listingPhotos[pickup.listingId].orEmpty(), listingPhotoErrors[pickup.listingId], "photos-${pickup.listingId}" in state.busy, state.busy, onLoadListingPhotos, onAccept, onSchedule, onStatus, onComplete, onRejectPickup, onCancelPickup, onReassignPickup, onRecordPickupPayment, onVerifyHouseholdPickupQr, onOpenPickupChat) }
                if (section == KabadiwalaSection.HOME && state.pickups.size > 2) item {
                    TextButton(onClick = onOpenPickups, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ui_copy_e1ebb3edfe78))
                    }
                }
                if (section == KabadiwalaSection.PICKUPS && (state.collectorAssignedNextCursor != null || state.collectorWaitingNextCursor != null || state.collectorHistoryNextCursor != null)) item {
                    TextButton(onClick = onLoadMorePickups, enabled = "collector-pickups-page" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if ("collector-pickups-page" in state.busy) R.string.collector_loading_older_pickups else R.string.collector_load_older_pickups))
                    }
                }
            }
            KabadiwalaSection.INVENTORY -> {
                item { InventoryTotals(state.inventory) }
                if (state.initialLoadComplete && !state.loading && state.inventory.isEmpty()) item { EmptyPanel("Inventory is empty", "Complete a pickup to add weighed material.") }
                items(state.inventory, key = { it.id }) { InventoryCard(it) }
                item { Button(onClick = { showBulk = true }, enabled = state.inventory.any { it.availableKg > 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_587862aa9efd)) } }
            }
            KabadiwalaSection.LOTS -> {
                val visibleCapturedLots = capturedLots.filter { it.status != LotStatus.CANCELLED }
                item {
                    LotsModeSelector(
                        selected = lotsMode,
                        onSelect = { lotsMode = it }
                    )
                }
                when (lotsMode) {
                    "LOTS" -> {
                        item { Text(stringResource(R.string.ui_copy_f9f6a58c24f9), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        if (state.initialLoadComplete && !state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No bulk lots yet", "Reserve available inventory when ready.", actionLabel = "Open inventory", onAction = onOpenInventory) }
                        items(state.bulkLots, key = { it.id }) { lot -> BulkLotCard(lot, onCancelBulk, onPrepareBulkHandover, onOpenBulkChat) }
                        if (state.collectorLotsNextCursor != null) item {
                            TextButton(onClick = onLoadMoreLots, enabled = "more-collector-lots" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if ("more-collector-lots" in state.busy) "Loading older lots…" else "Load older lots") }
                        }
                        val bulkHandovers = state.handovers.filter { it.bulkLotId != null && it.status in setOf("PREPARED", "COLLECTOR_CONFIRMED", "REVIEW_REQUIRED") }
                        if (bulkHandovers.isNotEmpty()) item { Text(stringResource(R.string.ui_copy_d4d63ac52b21), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        items(bulkHandovers, key = { "bulk-handover-${it.id}" }) { handover ->
                            SupplyHandoverCard(handover, state.materialPassports[handover.id], state.anomalies[handover.id], onPrepareBulkHandover, onConfirmCollectorHandover, onLoadMaterialPassport, onLoadAnomalies, onDecideSupplySettlement)
                        }
                    }
                    "OFFERS" -> {
                        item { Text(stringResource(R.string.ui_copy_8e79f9d8555e), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        if (state.initialLoadComplete && state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Offers on your listed lots appear here.", actionLabel = "View lots", onAction = { lotsMode = "LOTS" }) }
                        items(state.offers, key = { it.id }) { offer -> OfferCard(offer, onAcceptOffer, onRejectOffer, onCounterOffer, onOpenBulkChat) }
                        if (state.collectorOffersNextCursor != null) item {
                            TextButton(onClick = onLoadMoreOffers, enabled = "more-collector-offers" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if ("more-collector-offers" in state.busy) "Loading older offers…" else "Load older offers") }
                        }
                    }
                    else -> {
                        item { Text(stringResource(R.string.ui_copy_a21cd26a5d59), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        item {
                            Text(stringResource(R.string.ui_copy_c4dbfff0d52d),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (visibleCapturedLots.isEmpty()) item { EmptyPanel("No saved records", "Photo records for direct recycler quotes will appear here.", actionLabel = "Create photo record", onAction = onCreateCapturedLot) }
                        items(visibleCapturedLots, key = { "captured-${it.id}" }) { lot -> CapturedLotCard(lot) }
                    }
                }
            }
            KabadiwalaSection.TOOLS -> {
                item {
                    FormalisationDashboard(
                        state = state,
                        currentArea = currentArea,
                        currentCollectorId = currentCollectorId,
                        onRouteEstimate = onRouteEstimate,
                        onCreatePool = onCreatePool,
                        onJoinPool = onJoinPool,
                        onLeavePool = onLeavePool,
                        onLockPool = onLockPool,
                        onPreparePoolHandover = onPreparePoolHandover,
                        onPrepareBulkHandover = onPrepareBulkHandover,
                        onConfirmCollectorHandover = onConfirmCollectorHandover,
                        onAcknowledgeSafety = onAcknowledgeSafety,
                        onLoadSafetyRouting = onLoadSafetyRouting,
                        onLoadMaterialPassport = onLoadMaterialPassport,
                        onLoadAnomalies = onLoadAnomalies,
                        onDecideSupplySettlement = onDecideSupplySettlement
                    )
                }
            }
        }
        if (section == KabadiwalaSection.HOME) {
            item {
                SupplyChainToolsShortcut(onOpenTools)
            }
        }
    }
    if (showBulk) BulkLotDialog(state.inventory, currentArea, onDismiss = { showBulk = false }, onSubmit = { onCreateBulk(it); showBulk = false })
}

@Composable
private fun LotsModeSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = selected == "LOTS",
            onClick = { onSelect("LOTS") },
            label = { Text(stringResource(R.string.ui_copy_79029175a3c3), maxLines = 1) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selected == "OFFERS",
            onClick = { onSelect("OFFERS") },
            label = { Text(stringResource(R.string.ui_copy_43c94306c1e7), maxLines = 1) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selected == "RECORDS",
            onClick = { onSelect("RECORDS") },
            label = { Text(stringResource(R.string.ui_copy_e51c55255be9), maxLines = 1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CollectorOverview(state: SupplyChainState, onOpenInventory: () -> Unit, onOpenPickups: () -> Unit) {
    val waiting = state.pickups.count { it.status == "REQUESTED" || it.status == "WAITING_FOR_PICKUP" }
    val scheduled = state.pickups.count { it.status == "SCHEDULED" }
    val stockKg = state.inventory.sumOf { it.availableKg + it.reservedKg }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.ui_copy_07e282127341), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_c931788532ce), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.ui_copy_13ce8d4c7378), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
                    Text(waiting.toString(), style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_copy_1686d8a80a77), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
                }
                Column(Modifier.width(126.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    CollectorMetric(scheduled.toString(), "Scheduled", Modifier.fillMaxWidth())
                    CollectorMetric("${"%.1f".format(stockKg)} kg", "In stock", Modifier.fillMaxWidth())
                }
            }
            Button(onClick = if (state.inventory.any { it.availableKg > 0 }) onOpenInventory else onOpenPickups, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Inventory2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.inventory.any { it.availableKg > 0 }) "List stock for recyclers" else "Open pickups")
            }
            Text(if (state.inventory.any { it.availableKg > 0 }) "Create a recycler lot from weighed stock." else "Complete a household pickup with its QR and final weight to add stock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
        }
    }
}

@Composable
private fun CollectorDeskHeader(onRefresh: () -> Unit, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.ui_copy_ab467fb3d8d1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_9b7cbf9a74b9), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_6dc7aca25dea), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh")
        }
    }
}

@Composable
private fun FieldToolsHeader(onRefresh: () -> Unit, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ui_copy_be16e37870c1),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh")
        }
    }
}

@Composable
private fun CollectorMetric(value: String, label: String, modifier: Modifier = Modifier) {
    val layout = rememberKcResponsiveLayout()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = if (layout.isCompact) 2 else 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (layout.isCompact) 2 else 1)
        }
    }
}

@Composable
private fun SupplyChainToolsShortcut(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Recycling, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_field_tools), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_58bac9913ad3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.ui_copy_cf9b77061f7b), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class KabadiwalaSection { HOME, INVENTORY, PICKUPS, LOTS, TOOLS }

@Composable
private fun PickupJourneyTimeline(pickup: PickupRequestDto) {
    if (pickup.status in setOf("CANCELLED", "REJECTED", "REASSIGNMENT_REQUIRED")) return
    val columns = if (rememberKcResponsiveLayout().isCompact) 2 else 3
    val stages = listOf("Requested", "Accepted", "Arrived", "Weighed", "Payment", "Complete")
    val received = pickup.settlementPayment?.householdReceivedAt != null
    val current = when {
        received -> 5
        pickup.settlementStatus == "ACCEPTED" || pickup.settlementPayment != null -> 4
        pickup.actualWeight != null || pickup.status in setOf("WEIGHED", "COMPLETED") -> 3
        pickup.status == "ARRIVED" -> 2
        pickup.status in setOf("ACCEPTED", "SCHEDULED", "IN_TRANSIT") -> 1
        else -> 0
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ui_copy_9b5a90f1411b, stages[current]), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        stages.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * columns + columnIndex
                    val reached = index <= current
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (reached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        ) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) } }
                        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall,
                            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        pickup.finalAmount?.let { amount ->
            Text(if (received) "Household received ${money(amount)}" else "Final amount · ${money(amount)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PickupCard(pickup: PickupRequestDto, listing: HouseholdListingDto?, loadedPhotos: List<ByteArray>, photoError: String?, photoLoading: Boolean, busy: Set<String>, onLoadPhotos: (String, Int) -> Unit, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit, onReject: (String, String) -> Unit, onCancel: (String, String?) -> Unit, onReassign: (String, String, Boolean) -> Unit, onRecordPayment: (String, PickupSettlementPaymentRequestDto) -> Unit, onVerifyHouseholdQr: (String, String) -> Unit, onOpenPickupChat: (String) -> Unit) {
    val context = LocalContext.current
    var showComplete by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showReject by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var showPhotos by remember(pickup.id) { mutableStateOf(false) }
    var photoLoadAttempted by remember(pickup.id) { mutableStateOf(false) }
    var selectedPhotoIndex by remember(pickup.id) { mutableStateOf<Int?>(null) }
    var showPayment by remember(pickup.id) { mutableStateOf(false) }
    var showMoreActions by remember(pickup.id) { mutableStateOf(false) }
    var scannerLaunchError by rememberSaveable(pickup.id) { mutableStateOf(false) }
    val pickupScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf(String::isNotBlank)?.let { qrData ->
            scannerLaunchError = false
            onVerifyHouseholdQr(pickup.id, qrData)
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                Text(materialName(listing?.materialCategory ?: "OTHER"), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val accepting = "pickup-decision-${pickup.id}" in busy
                StatusChip(if (accepting && pickup.status in setOf("REQUESTED", "WAITING_FOR_PICKUP")) "Accepting · syncing" else statusName(pickup.status))
                if (pickup.status in setOf("ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED")) {
                    Box {
                        IconButton(onClick = { showMoreActions = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.ui_copy_0bdafff306d6))
                        }
                        DropdownMenu(expanded = showMoreActions, onDismissRequest = { showMoreActions = false }) {
                            if (pickup.status in setOf("ACCEPTED", "SCHEDULED")) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_copy_34d25e7def08)) },
                                    onClick = { showMoreActions = false; showCancel = true }
                                )
                            }
                            if (pickup.status in setOf("IN_TRANSIT", "ARRIVED")) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_copy_d5d1482a618f)) },
                                    onClick = { showMoreActions = false; showReassign = true }
                                )
                            }
                        }
                    }
                }
            }
            val confirmedPickupAddress = listing?.pickupAddress?.takeIf { it.isNotBlank() && pickup.status != "WAITING_FOR_PICKUP" }
            val pickupLocation = confirmedPickupAddress ?: listing?.areaName ?: "Area unavailable"
            Text(stringResource(R.string.ui_copy_505420b9e7e6, "%.1f".format(listing?.estimatedWeight ?: 0.0), pickupLocation))
            PickupJourneyTimeline(pickup)
            val directionsDestination = if (pickup.status in setOf("ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED")) pickupDirectionsDestination(listing) else null
            if (directionsDestination != null) {
                OutlinedButton(
                    onClick = {
                        if (!openPickupDirections(context, directionsDestination)) {
                            Toast.makeText(context, "Google Maps directions could not be opened.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_copy_e36720dff49c))
                }
            }
            if (pickup.kabadiwalaId != null && pickup.status in setOf("ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED", "WEIGHED")) {
                OutlinedButton(onClick = { onOpenPickupChat(pickup.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_copy_519770e958fa))
                }
            }
            val photoCount = listing?.photoCount ?: 0
            if (photoCount > 0 && pickup.status != "WAITING_FOR_PICKUP") {
                OutlinedButton(
                    onClick = {
                        showPhotos = true
                        if (loadedPhotos.isEmpty() && !photoLoading && photoError == null) {
                            photoLoadAttempted = true
                            onLoadPhotos(pickup.listingId, photoCount)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = stringResource(R.string.ui_copy_bb4c02e2725c))
                    Spacer(Modifier.width(8.dp))
                    Text(if (photoCount == 1) "View scrap photo" else "View $photoCount scrap angles")
                }
                if (showPhotos) ListingPhotoStrip(
                    loadedPhotos,
                    photoCount,
                    photoError,
                    photoLoading,
                    photoLoadAttempted,
                    onRetry = {
                        photoLoadAttempted = true
                        onLoadPhotos(pickup.listingId, photoCount)
                    },
                    onPhotoClick = { index -> selectedPhotoIndex = index }
                )
            }
            when (pickup.status) {
                "WAITING_FOR_PICKUP" -> Button(onClick = { onAccept(pickup.listingId) }, enabled = "pickup-decision-${pickup.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { if ("pickup-decision-${pickup.id}" in busy) Text(stringResource(R.string.ui_copy_25b55e8298c7)) else Text(stringResource(R.string.ui_copy_c3c7836a6362)) }
                "REQUESTED" -> {
                    val decisionPending = "pickup-decision-${pickup.id}" in busy
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onAccept(pickup.listingId) }, enabled = !decisionPending, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text(if (decisionPending) "Accepting…" else "Accept pickup") }
                        OutlinedButton(onClick = { showReject = true }, enabled = !decisionPending, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_b59cf9ed55bb)) }
                    }
                }
                "ACCEPTED" -> {
                    val schedulePending = "schedule-${pickup.id}" in busy
                    Button(onClick = { showSchedule = true }, enabled = !schedulePending, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { if (schedulePending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.demo_schedule_pickup)) }
                    if (isPickupWorkTimeNow()) {
                        Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, enabled = "status-${pickup.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if ("status-${pickup.id}" in busy) "Starting…" else "Start now") }
                    } else {
                        Text(stringResource(R.string.ui_copy_1a95258c37c6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "SCHEDULED" -> if (isPickupWorkTimeNow()) {
                    Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, enabled = "status-${pickup.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if ("status-${pickup.id}" in busy) "Starting…" else "Start trip") }
                } else {
                    Text(stringResource(R.string.ui_copy_3e37016ef70a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "IN_TRANSIT" -> {
                    Text(stringResource(R.string.ui_copy_01eeeae331be), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onStatus(pickup.id, "ARRIVED") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_0828af7f409b)) }
                }
                "ARRIVED" -> {
                    if (pickup.householdQrScannedAt == null) {
                        Text(stringResource(R.string.ui_copy_648e93d69590), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                scannerLaunchError = false
                                runCatching {
                                    pickupScanner.launch(
                                        ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setBeepEnabled(false)
                                            .setPrompt("Scan household pickup QR")
                                    )
                                }.onFailure { scannerLaunchError = true }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) { Icon(Icons.Filled.QrCodeScanner, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_32de422fb324)) }
                        if (scannerLaunchError) Text(stringResource(R.string.ui_copy_cfb8f707ce75), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(onClick = { showComplete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_e18355dc201a)) }
                    }
                }
                "COMPLETED" -> {
                    Text(stringResource(R.string.ui_copy_be735b699c73, money(pickup.finalAmount)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if ((pickup.pickupCharge ?: 0.0) > 0) Text(stringResource(R.string.ui_copy_66a5223089da, money(pickup.grossMaterialAmount), money(pickup.pickupCharge)), style = MaterialTheme.typography.bodySmall)
                    when (pickup.settlementPayment?.status) {
                        "RECORDED" -> Text("${paymentMethodName(pickup.settlementPayment.paymentMethod)} payment recorded · ${if (pickup.settlementPayment.householdReceivedAt == null) "awaiting household receipt confirmation" else "awaiting operator reconciliation"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        "VERIFIED" -> Text(stringResource(R.string.ui_copy_2e78eb6961ed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        "DISPUTED" -> Text(stringResource(R.string.ui_copy_d3227fded149), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        else -> if (pickup.settlementStatus == "ACCEPTED") Button(onClick = { showPayment = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_78a8f977fecf)) }
                    }
                }
            }
        }
    }
    if (showSchedule) SchedulePickupDialog(
        onDismiss = { showSchedule = false },
        confirmLabel = "Schedule pickup",
        acceptedAt = pickup.acceptedAt,
        onSubmit = { onSchedule(pickup.id, it); showSchedule = false }
    )
    if (showComplete && pickup.householdQrScannedAt != null) CompletionDialog(pickup, listing, onDismiss = { showComplete = false }, onSubmit = { onComplete(pickup.id, it); showComplete = false })
    if (showReject) ReasonDialog(title = "Decline pickup", confirmLabel = "Decline", onDismiss = { showReject = false }, onSubmit = { onReject(pickup.id, it); showReject = false })
    if (showCancel) ReasonDialog(title = "Cancel pickup", confirmLabel = "Cancel", onDismiss = { showCancel = false }, onSubmit = { onCancel(pickup.id, it); showCancel = false })
    if (showReassign) ReassignDialog(onDismiss = { showReassign = false }, onSubmit = { reason, noShow -> onReassign(pickup.id, reason, noShow); showReassign = false })
    if (showPayment) PickupSettlementPaymentDialog(pickup, onDismiss = { showPayment = false }, onSubmit = { payment -> onRecordPayment(pickup.id, payment); showPayment = false })
    selectedPhotoIndex?.let { index ->
        loadedPhotos.getOrNull(index)?.let { photo ->
            FullScreenListingPhotoDialog(
                photo = photo,
                photoIndex = index,
                photoCount = loadedPhotos.size,
                onDismiss = { selectedPhotoIndex = null }
            )
        }
    }
}

private fun paymentMethodName(value: String) = when (value) { "UPI", "DIGITAL_WALLET" -> "UPI"; "CASH" -> "Cash"; else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } }

private fun isPickupWorkTimeNow(): Boolean {
    val indiaNow = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
    val minutesSinceMidnight = indiaNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + indiaNow.get(java.util.Calendar.MINUTE)
    return minutesSinceMidnight in (7 * 60 + 30)..(18 * 60 + 30)
}

@Composable
private fun PickupSettlementPaymentDialog(pickup: PickupRequestDto, onDismiss: () -> Unit, onSubmit: (PickupSettlementPaymentRequestDto) -> Unit) {
    var amount by remember(pickup.id) { mutableStateOf(pickup.finalAmount?.toString().orEmpty()) }
    var method by remember(pickup.id) { mutableStateOf("CASH") }
    var reference by remember(pickup.id) { mutableStateOf("") }
    var notes by remember(pickup.id) { mutableStateOf("") }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = parsedAmount != null && parsedAmount > 0 && (pickup.finalAmount == null || parsedAmount <= pickup.finalAmount + 0.01) && (method != "UPI" || reference.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_copy_1737d7242229)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.ui_copy_105715a89d94), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(amount, { amount = it.filter { character -> character.isDigit() || character == '.' }.take(12) }, label = { Text(stringResource(R.string.payment_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH" to "Cash", "UPI" to "UPI").forEach { (key, label) -> FilterChip(selected = method == key, onClick = { method = key }, label = { Text(label) }) }
                }
                if (method == "UPI") OutlinedTextField(reference, { reference = it.take(200) }, label = { Text(stringResource(R.string.ui_copy_cef40c71ff47)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.ui_copy_cb6c116d80b8)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (parsedAmount != null && pickup.finalAmount != null && parsedAmount > pickup.finalAmount + 0.01) Text(stringResource(R.string.ui_copy_d547f2275b0b, money(pickup.finalAmount)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(PickupSettlementPaymentRequestDto(amount = parsedAmount!!, method = method, reference = reference.trim().ifBlank { null }, notes = notes.trim().ifBlank { null })) }, enabled = valid) { Text(stringResource(R.string.payment_record)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ListingPhotoStrip(photos: List<ByteArray>, expectedCount: Int, photoError: String?, loading: Boolean, loadAttempted: Boolean, onRetry: () -> Unit, onPhotoClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (photos.isEmpty()) {
            if (photoError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(photoError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.future_retry)) }
                }
            } else if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.ui_copy_65602cee4fd9), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (loadAttempted) "The photo did not appear. Try again." else "Photo is ready to view.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.future_retry)) }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos) { index, bytes ->
                    val bitmap = remember(bytes) { decodeListingPhoto(bytes, 512) }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(width = 144.dp, height = 112.dp).clickable { onPhotoClick(index) }) {
                        if (bitmap != null) Image(bitmap, "Open scrap photo ${index + 1} full screen", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            if (photoError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(photoError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.future_retry)) }
                }
            } else {
                Text(stringResource(R.string.ui_copy_db44daebec30, photos.size, expectedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FullScreenListingPhotoDialog(
    photo: ByteArray,
    photoIndex: Int,
    photoCount: Int,
    onDismiss: () -> Unit
) {
    val bitmap = remember(photo) {
        decodeListingPhoto(photo, 2048)
    }
    var scale by remember(photo) { mutableStateOf(1f) }
    var offsetX by remember(photo) { mutableStateOf(0f) }
    var offsetY by remember(photo) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        if (nextScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Scrap photo ${photoIndex + 1} of $photoCount",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            } else {
                MaterialText(
                    "This photo could not be opened.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_copy_34f64ffd9ab1), tint = Color.White)
            }
            MaterialText(
                "Photo ${photoIndex + 1} of $photoCount · Pinch to zoom",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                color = Color.White
            )
        }
    }
}

private fun decodeListingPhoto(bytes: ByteArray, maxSidePx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxSidePx) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

@Composable
private fun SchedulePickupDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    confirmLabel: String = "Confirm time",
    acceptedAt: String? = null
) {
    // Offer the next three valid slots inside the server's India-time window.
    val slots = remember {
        val indiaTimeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val now = java.util.Calendar.getInstance(indiaTimeZone)
        val leadMillis = PICKUP_MIN_LEAD_MINUTES * 60_000L
        val nowMillis = System.currentTimeMillis()
        val acceptedMillis = acceptedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: nowMillis
        val clockSkewBufferMillis = 60_000L
        val earliestAllowedTime = maxOf(nowMillis, acceptedMillis) + leadMillis + clockSkewBufferMillis
        val candidates = mutableListOf<java.util.Date>()
        for (dayOffset in 0..14) {
            val day = java.util.Calendar.getInstance(indiaTimeZone).apply {
                timeInMillis = now.timeInMillis
                add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
            }
            for (minutesOfDay in (7 * 60 + 30)..(18 * 60 + 30) step 15) {
                val slot = (day.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, minutesOfDay / 60)
                    set(java.util.Calendar.MINUTE, minutesOfDay % 60)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (slot.timeInMillis >= earliestAllowedTime) candidates += slot.time
                if (candidates.size == 3) break
            }
            if (candidates.size == 3) break
        }
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val displayFormatter = java.text.SimpleDateFormat("EEE, d MMM · h:mm a", java.util.Locale.getDefault()).apply {
            timeZone = indiaTimeZone
        }
        candidates.map { time -> formatter.format(time) to displayFormatter.format(time) }
    }
    val firstSlot = slots.firstOrNull()?.first
    var selected by remember(firstSlot) { mutableStateOf(firstSlot.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_copy_46fbf4d17eaa)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ui_copy_73e4b32b8063, PICKUP_MIN_LEAD_MINUTES), style = MaterialTheme.typography.bodyMedium)
                if (slots.isEmpty()) Text(stringResource(R.string.ui_copy_91e5717496f5), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                slots.forEach { slot ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        androidx.compose.material3.RadioButton(selected = selected == slot.first, onClick = { selected = slot.first })
                        Text(slot.second, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }, enabled = selected.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ReschedulePickupDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    SchedulePickupDialog(onDismiss = onDismiss, onSubmit = onSubmit)
}

@Composable
private fun ReasonDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(reason, { reason = it.take(500) }, label = { Text(stringResource(R.string.ui_copy_f6826f8fc9b4)) }, minLines = 2) },
        confirmButton = { TextButton(onClick = { onSubmit(reason.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_copy_f7c449371849)) } }
    )
}

@Composable
private fun ReassignDialog(onDismiss: () -> Unit, onSubmit: (String, Boolean) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var noShow by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_copy_9998c4509375)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text(stringResource(R.string.ui_copy_552812ff2a96)) }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(noShow, { noShow = it }); Text(stringResource(R.string.ui_copy_75602c52a54b)) }
            }
        },
        confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onSubmit(reason.trim(), noShow) }) { Text(stringResource(R.string.ui_copy_55843fb075e2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.perm_not_now)) } }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettlementDecisionDialog(
    title: String,
    acceptLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String?, String?) -> Unit
) {
    var raiseIssue by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("WEIGHT_DIFFERENCE") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ui_copy_67d24b1a4f48), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = raiseIssue, onCheckedChange = { raiseIssue = it })
                    Text(stringResource(R.string.ui_copy_b3e35833f144))
                }
                if (raiseIssue) {
                    Text(stringResource(R.string.ui_copy_f219cc0614ae), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("WEIGHT_DIFFERENCE", "PRICE_DIFFERENCE", "MATERIAL_MISMATCH", "OTHER").forEach { option ->
                            FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.ui_copy_2183134d6c0e)) }, minLines = 2)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(if (raiseIssue) "RAISE_ISSUE" else "ACCEPT", if (raiseIssue) reason else null, notes.ifBlank { null }) }) {
                Text(if (raiseIssue) "Submit issue" else acceptLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.perm_not_now)) } }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CompletionDialog(pickup: PickupRequestDto, listing: HouseholdListingDto?, onDismiss: () -> Unit, onSubmit: (PickupCompletionDto) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(listing?.materialCategory ?: pickup.finalCategory ?: "OTHER") }
    var grade by remember { mutableStateOf("UNSPECIFIED") }
    var reasonCode by remember { mutableStateOf<String?>(null) }
    var waivePickupCharge by remember { mutableStateOf(false) }
    val total = (weight.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    val charge = if (waivePickupCharge) 0.0 else pickup.pickupCharge ?: 0.0
    val actualWeight = weight.toDoubleOrNull()
    val referenceValue = listing?.estimatedPriceMax ?: listing?.estimatedPriceMin
    val weightChanged = listing != null && actualWeight != null && kotlin.math.abs(actualWeight - listing.estimatedWeight) / listing.estimatedWeight > 0.2
    val valueChanged = referenceValue != null && total > 0 && kotlin.math.abs(total - referenceValue) / referenceValue.coerceAtLeast(1.0) > 0.2
    val materialChanged = listing != null && category != listing.materialCategory
    val reasonRequired = listing == null || materialChanged || weightChanged || valueChanged
    val reasonOptions = listOf(
        "MATERIAL_MISMATCH" to "Material differs",
        "WEIGHT_VARIANCE" to "Weight differs",
        "VALUE_VARIANCE" to "Value differs",
        "OTHER_INSPECTION" to "Other inspection reason"
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_copy_5593428c6ab6), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.ui_copy_eef4d8744af3, pickup.id.takeLast(7)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_copy_5feb7763bbb8)) }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(stringResource(R.string.ui_copy_9b6e56ce8dad), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listing?.let { Text(stringResource(R.string.ui_copy_d8d4daa3983b, materialName(it.materialCategory), "%.1f".format(it.estimatedWeight)), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.ui_copy_852d87b759bb)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_c7545418054d)) }, supportingText = { Text(stringResource(R.string.ui_copy_ffa763d18edb)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ui_copy_e2009b04b506), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        materials.forEach { option -> FilterChip(selected = category == option, onClick = { category = option }, label = { Text(materialName(option), maxLines = 2) }) }
                    }
                    OutlinedTextField(grade, { grade = it.take(80) }, label = { Text(stringResource(R.string.ui_copy_aecc49c15355)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (reasonRequired) {
                        Text(stringResource(R.string.ui_copy_7d9a1e45d574), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            reasonOptions.forEach { (code, label) -> FilterChip(selected = reasonCode == code, onClick = { reasonCode = code }, label = { Text(label) }) }
                        }
                    }
                    if ((pickup.pickupCharge ?: 0.0) > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = waivePickupCharge, onCheckedChange = { waivePickupCharge = it })
                        Text(stringResource(R.string.ui_copy_59f7c4f53552, money(pickup.pickupCharge)))
                    }
                    if (total > 0) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.ui_copy_a6ddd991eeaf), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.ui_copy_2e3e55b0fa5a, money(total), money(charge)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.ui_copy_174f79312fae, money(total - charge)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (total > 0 && total <= charge) Text(stringResource(R.string.ui_copy_ba2429bdadbf), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.ui_copy_ef4d411a058d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            val w = weight.toDoubleOrNull()
                            val r = rate.toDoubleOrNull()
                            if (w != null && r != null && w > 0 && w <= 500 && r > 0 && total > charge && (!reasonRequired || reasonCode != null)) {
                                onSubmit(PickupCompletionDto(w, category, grade.ifBlank { "UNSPECIFIED" }, r, reasonCode.takeIf { reasonRequired }, waivePickupCharge = waivePickupCharge))
                            }
                        },
                        enabled = weight.toDoubleOrNull()?.let { it > 0 && it <= 500 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && total > charge && (!reasonRequired || reasonCode != null),
                        modifier = Modifier.weight(1.3f).heightIn(min = 52.dp)
                    ) { Text(stringResource(R.string.ui_copy_2d2dcdbfaa4b)) }
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(item: InventoryBalanceDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.ui_copy_be59d2416bfb, "%.1f".format(item.availableKg)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; if (item.grade.isNotBlank() && item.grade != "UNSPECIFIED") Text(stringResource(R.string.ui_copy_99033ef1e4b7, item.grade)); Text(stringResource(R.string.ui_copy_572644e2221f, "%.1f".format(item.reservedKg), "%.1f".format(item.soldKg), money(item.purchaseCost)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun InventoryTotals(items: List<InventoryBalanceDto>) { val available = items.sumOf { it.availableKg }; val reserved = items.sumOf { it.reservedKg }; Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Available", "%.1f kg".format(available)); Metric("Reserved", "%.1f kg".format(reserved)); Metric("Materials", items.size.toString()) } } }
@Composable private fun Metric(label: String, value: String) { Column { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun CapturedLotCard(lot: Lot) {
    val syncLabel = if (lot.synced) "Saved on server" else "Waiting to sync"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lot.materialLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(if (lot.synced) "Saved" else "Pending")
            }
            Text(stringResource(R.string.ui_copy_ae71e136f5c0, "%.1f".format(lot.weightKg), statusName(lot.status.name)))
            Text(stringResource(R.string.ui_copy_94a9b6bd4fee, syncLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun BulkLotCard(lot: BulkLotDto, onCancel: (String) -> Unit, onPrepareHandover: (String) -> Unit, onOpenBulkChat: (String, String) -> Unit = { _, _ -> }) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(lot.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text(stringResource(R.string.ui_copy_c0d278e12f90, "%.1f".format(lot.quantityKg), money(lot.askingRatePerKg))); Text(stringResource(R.string.ui_copy_1547fd202254, lot.areaName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (lot.status == "LISTED") OutlinedButton(onClick = { onCancel(lot.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy_689b5f9c7355)) }; if (lot.status == "RESERVED") { Text(stringResource(R.string.ui_copy_3ca3617baca2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = { onPrepareHandover(lot.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_6f8511b380c6)) }; if (!lot.reservedForId.isNullOrBlank()) OutlinedButton(onClick = { onOpenBulkChat(lot.id, lot.reservedForId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_e68ddcadeb21)) } } } } }
@Composable
private fun OfferCard(offer: BulkOfferDto, onAccept: (String) -> Unit, onReject: (String, String) -> Unit = { _, _ -> }, onCounter: (String, Double, String?) -> Unit = { _, _, _ -> }, onOpenBulkChat: (String, String) -> Unit = { _, _ -> }) {
    var showReject by remember(offer.id) { mutableStateOf(false) }
    var showCounter by remember(offer.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.deal_offered_rate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_d250ca739d6d, money(offer.offeredRatePerKg), statusName(offer.status)))
            if (offer.status == "ACCEPTED") {
                OutlinedButton(onClick = { onOpenBulkChat(offer.bulkLotId, offer.recyclerId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_e68ddcadeb21)) }
            }
            if (offer.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onAccept(offer.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.quote_accept)) }
                    OutlinedButton(onClick = { showCounter = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_f4018045cfb4)) }
                }
                OutlinedButton(onClick = { showReject = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_4f811190f75e)) }
            }
        }
    }
    if (showReject) ReasonDialog(title = "Reject recycler offer", confirmLabel = "Reject", onDismiss = { showReject = false }, onSubmit = { onReject(offer.id, it); showReject = false })
    if (showCounter) CounterOfferDialog(initialRate = offer.offeredRatePerKg, onDismiss = { showCounter = false }, onSubmit = { rate, notes -> onCounter(offer.id, rate, notes); showCounter = false })
}

@Composable
private fun CounterOfferDialog(initialRate: Double, onDismiss: () -> Unit, onSubmit: (Double, String?) -> Unit) {
    var rate by remember { mutableStateOf(initialRate.toString()) }
    var notes by remember { mutableStateOf("") }
    val parsed = rate.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_copy_4848554818d3)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_9b0fd7b576ba)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.ui_copy_4e39567064b6)) }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.takeIf { it > 0 }?.let { onSubmit(it, notes.ifBlank { null }) } }, enabled = parsed?.let { it > 0 } == true) { Text(stringResource(R.string.ui_copy_6c7444259930)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
@Composable private fun BulkLotDialog(inventory: List<InventoryBalanceDto>, currentArea: String, onDismiss: () -> Unit, onSubmit: (BulkLotCreateDto) -> Unit) {
    val options = inventory.filter { it.availableKg > 0 }
    var selectedId by remember { mutableStateOf(options.firstOrNull()?.id) }
    val item = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()
    var quantity by remember(item?.id) { mutableStateOf(item?.availableKg?.toString().orEmpty()) }
    var rate by remember { mutableStateOf("") }
    var minimumRate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_copy_332b62cec05e)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ui_copy_cd0080fd8abd))
            if (options.size > 1) {
                Text(stringResource(R.string.lot_material_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.take(6).forEach { candidate ->
                        FilterChip(selected = candidate.id == item?.id, onClick = { selectedId = candidate.id }, label = { Text(materialName(candidate.materialCategory)) })
                    }
                }
            }
            Text(stringResource(R.string.ui_copy_9043837aacba, materialName(item?.materialCategory ?: "OTHER"), "%.1f".format(item?.availableKg ?: 0.0)))
            OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_49b0cf9c4005)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_fdfd1e13dea3)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(minimumRate, { minimumRate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_287ecfcf652b)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.ui_copy_3b575a691c82)) }, minLines = 2)
        }
    }, confirmButton = { TextButton(onClick = { val q = quantity.toDoubleOrNull(); val r = rate.toDoubleOrNull(); val min = minimumRate.toDoubleOrNull(); if (item != null && q != null && r != null && q > 0 && q <= item.availableKg && r > 0 && (min == null || (min > 0 && min <= r))) onSubmit(BulkLotCreateDto(item.materialCategory, item.grade, q, r, min, currentArea.ifBlank { "Current area" }, notes = notes.ifBlank { null })) }, enabled = item != null && quantity.toDoubleOrNull()?.let { it > 0 && it <= (item.availableKg) } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && (minimumRate.toDoubleOrNull() == null || minimumRate.toDoubleOrNull()?.let { it > 0 && it <= (rate.toDoubleOrNull() ?: 0.0) } == true)) { Text(stringResource(R.string.ui_copy_4023cf765ef2)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun RecyclerSupplyScreen(state: SupplyChainState, onRefresh: () -> Unit, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit, onOpenDemand: () -> Unit, onWithdrawOffer: (String, String?) -> Unit = { _, _ -> }, onUpdateRequirement: (String, ProcurementRequirementUpdateDto) -> Unit = { _, _ -> }, onOpenHandoverScanner: () -> Unit = {}, onOpenBulkChat: (String, String) -> Unit = { _, _ -> }, onLoadMoreLots: () -> Unit = {}, onLoadMoreOffers: () -> Unit = {}, onLoadMoreDemand: () -> Unit = {}) {
    TraceBusyFrames(state.busy)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader("Buy recyclable material", "Browse bulk lots or publish facility demand.", Icons.Filled.Storefront, onRefresh, state.loading) }
        item { Text(stringResource(R.string.ui_copy_392366d76c17), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = onOpenDemand, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_copy_ea21894118d3)) } }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        item { Text(stringResource(R.string.ui_copy_fa3f1b42d9f8), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (!state.initialLoadComplete && state.loading) item { LoadingPanel("Loading recycler marketplace…") }
        if (state.initialLoadComplete && !state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No lots available", "Matching lots will appear here.") }
        items(state.bulkLots, key = { it.id }) { lot -> RecyclerLotCard(lot, state.offers.firstOrNull { it.bulkLotId == lot.id }, onOffer, onReceive) }
        if (state.recyclerLotsNextCursor != null) item { TextButton(onClick = onLoadMoreLots, enabled = "more-recycler-lots" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.recycler_load_older_lots)) } }
        item { Text(stringResource(R.string.ui_copy_2de7f18bf0a2), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.initialLoadComplete && state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Make an offer on a listed lot.") }
        items(state.offers, key = { it.id }) { RecyclerOfferCard(it, onWithdraw = onWithdrawOffer, onOpenBulkChat = { onOpenBulkChat(it.bulkLotId, state.bulkLots.firstOrNull { lot -> lot.id == it.bulkLotId }?.kabadiwalaId.orEmpty()) }) }
        if (state.recyclerOffersNextCursor != null) item { TextButton(onClick = onLoadMoreOffers, enabled = "more-recycler-offers" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.recycler_load_older_offers)) } }
        item { Text(stringResource(R.string.ui_copy_75c051c4d3df), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.requirements, key = { it.id }) { requirement -> RequirementCard(requirement, onUpdate = onUpdateRequirement) }
        if (state.recyclerRequirementsNextCursor != null) item { TextButton(onClick = onLoadMoreDemand, enabled = "more-recycler-demands" !in state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.recycler_load_older_demand)) } }
        item { Text(stringResource(R.string.ui_copy_2fd96da4e4b5), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.initialLoadComplete && state.pools.isEmpty()) item { EmptyPanel("No pools yet", "Collectors can combine reserved stock for your demand.") }
        items(state.pools, key = { "pool-${it.id}" }) { pool ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(stringResource(R.string.ui_copy_3786e15cb669, materialName(pool.materialCategory), "%.1f".format(pool.totalReservedKg)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.ui_copy_4990508ddf1a, statusName(pool.status), pool.contributions.size), style = MaterialTheme.typography.bodySmall); Text(stringResource(R.string.ui_copy_0428be7b0804), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
        }
        item { Text(stringResource(R.string.ui_copy_9c15758d98d2), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.initialLoadComplete && state.handovers.isEmpty()) item { EmptyPanel("No handovers yet", "Confirmed QR handovers will appear here.") }
        items(state.handovers, key = { "supply-${it.id}" }) { handover ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(handover.referenceId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.ui_copy_81d93b7191a3, materialName(handover.materialCategory), "%.1f".format(handover.quotedWeightKg), statusName(handover.status)), style = MaterialTheme.typography.bodyMedium); if (handover.status == "COLLECTOR_CONFIRMED") Button(onClick = onOpenHandoverScanner, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_fd35edb7df56)) } } }
        }
    }
}

@Composable private fun RecyclerLotCard(lot: BulkLotDto, offer: BulkOfferDto?, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit) { var showOffer by remember { mutableStateOf(false) }; Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(lot.materialCategory), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text(stringResource(R.string.ui_copy_f5540311aef0, "%.1f".format(lot.quantityKg), money(lot.askingRatePerKg))); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.ui_copy_053ef1155917, lot.areaName), Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (offer == null && lot.status == "LISTED") Button(onClick = { showOffer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_674e5f553edc)) }; if (offer != null) { Text(stringResource(R.string.ui_copy_236524a7db4b, money(offer.offeredRatePerKg), statusName(offer.status)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); if (offer.status == "ACCEPTED") Text(stringResource(R.string.ui_copy_3e63b4adde6f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; if (showOffer) OfferDialog(lot, onDismiss = { showOffer = false }, onSubmit = { onOffer(lot.id, it); showOffer = false }) }
@Composable
private fun RecyclerOfferCard(offer: BulkOfferDto, onWithdraw: (String, String?) -> Unit, onOpenBulkChat: () -> Unit = {}) {
    var showWithdraw by remember(offer.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ui_copy_b3a2c273ef01), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_d250ca739d6d, money(offer.offeredRatePerKg), statusName(offer.status)))
            if (offer.status == "ACCEPTED") OutlinedButton(onClick = onOpenBulkChat, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_9b1318f8a1b8)) }
            if (offer.status == "PENDING") OutlinedButton(onClick = { showWithdraw = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_f4c4aa99a043)) }
        }
    }
    if (showWithdraw) ReasonDialog(title = "Withdraw offer", confirmLabel = "Withdraw", onDismiss = { showWithdraw = false }, onSubmit = { onWithdraw(offer.id, it.ifBlank { null }); showWithdraw = false })
}
@Composable private fun OfferDialog(lot: BulkLotDto, onDismiss: () -> Unit, onSubmit: (Double) -> Unit) { var rate by remember { mutableStateOf(lot.askingRatePerKg.toString()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_copy_bf9efaf0c05f, materialName(lot.materialCategory))) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.ui_copy_c0d278e12f90, "%.1f".format(lot.quantityKg), money(lot.askingRatePerKg))); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_b76ac98c768f)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { rate.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSubmit) }, enabled = rate.toDoubleOrNull()?.let { it > 0 } == true) { Text(stringResource(R.string.ui_copy_af851d7fddc7)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) }
@Composable
fun RecyclerDemandCreateScreen(supportedMaterials: List<String>, isSubmitting: Boolean, error: String?, onBack: () -> Unit, onSubmit: (ProcurementRequirementCreateDto) -> Unit) {
    var material by rememberSaveable { mutableStateOf("PLASTIC") }
    var showMaterials by rememberSaveable { mutableStateOf(false) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var minimum by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("50") }
    var rate by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(supportedMaterials) { if (supportedMaterials.isNotEmpty() && material !in supportedMaterials) material = supportedMaterials.first() }
    val q = quantity.toDoubleOrNull()
    val m = minimum.toDoubleOrNull()
    val r = radius.toDoubleOrNull()
    val maxRate = rate.toDoubleOrNull()
    val valid = material in supportedMaterials && q != null && q > 0 && m != null && m > 0 && m <= q && r != null && r > 0 && (rate.isBlank() || maxRate != null && maxRate > 0)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !isSubmitting) { Text(stringResource(R.string.ui_copy_e9a92db0d837)) }
        Text(stringResource(R.string.ui_copy_f01621d2ab89), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.ui_copy_46630d46e31d), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.ui_copy_991bc3071258), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { showMaterials = true }, enabled = supportedMaterials.isNotEmpty() && !isSubmitting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (material in supportedMaterials) materialName(material) else "Choose material")
        }
        if (supportedMaterials.isEmpty()) Text(stringResource(R.string.ui_copy_3fa1f03ef37f), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text(stringResource(R.string.ui_copy_d663f7cb830e)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(minimum, { minimum = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text(stringResource(R.string.ui_copy_3c4f2138238a)) }, supportingText = { Text(stringResource(R.string.ui_copy_7857b6bf671d)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(radius, { radius = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_9e9419bb1b40)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_6c4883f1deaa)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("${materialName(material)} · ${q?.let { "${"%.1f".format(it)} kg" } ?: "Enter quantity"} · ${r?.let { "within ${"%.0f".format(it)} km" } ?: "Enter radius"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { if (valid) onSubmit(ProcurementRequirementCreateDto(material, m, q, maxRatePerKg = maxRate, procurementRadiusKm = r)) }, enabled = valid && !isSubmitting, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (isSubmitting) "Publishing…" else "Publish demand") }
    }
    if (showMaterials) AlertDialog(
        onDismissRequest = { showMaterials = false },
        title = { Text(stringResource(R.string.ui_copy_c8f6fdb65d74)) },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(supportedMaterials, key = { it }) { category ->
                FilterChip(selected = material == category, onClick = { material = category; showMaterials = false }, label = { Text(materialName(category)) }, modifier = Modifier.fillMaxWidth())
            }
        } },
        confirmButton = { TextButton(onClick = { showMaterials = false }) { Text(stringResource(R.string.ui_copy_bbfa773e5a63)) } }
    )
}
@Composable private fun RequirementCard(item: ProcurementRequirementDto, onUpdate: (String, ProcurementRequirementUpdateDto) -> Unit = { _, _ -> }) {
    var showEdit by remember(item.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(item.status)) }
            Text(stringResource(R.string.ui_copy_7ba5a4bcd530, "%.0f".format(item.requiredQuantityKg), "%.0f".format(item.minimumLotKg)))
            Text("Within ${"%.0f".format(item.procurementRadiusKm)} km${item.maxRatePerKg?.let { " · up to ₹${"%.0f".format(it)}/kg" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            if (item.status == "OPEN") OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_802650f17d8d)) }
        }
    }
    if (showEdit) RequirementEditDialog(item, onDismiss = { showEdit = false }, onSubmit = { input -> onUpdate(item.id, input); showEdit = false })
}

@Composable
private fun RequirementEditDialog(item: ProcurementRequirementDto, onDismiss: () -> Unit, onSubmit: (ProcurementRequirementUpdateDto) -> Unit) {
    var quantity by remember { mutableStateOf(item.requiredQuantityKg.toString()) }
    var minimum by remember { mutableStateOf(item.minimumLotKg.toString()) }
    var radius by remember { mutableStateOf(item.procurementRadiusKm.toString()) }
    var rate by remember { mutableStateOf(item.maxRatePerKg?.toString().orEmpty()) }
    val q = quantity.toDoubleOrNull(); val m = minimum.toDoubleOrNull(); val r = radius.toDoubleOrNull(); val rateValue = rate.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_copy_9c1f49f175a0)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(materialName(item.materialCategory), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, label = { Text(stringResource(R.string.ui_copy_59c3af4e55dd)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(minimum, { minimum = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, label = { Text(stringResource(R.string.ui_copy_3c4f2138238a)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(radius, { radius = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.ui_copy_18d8b6308c1e)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_95fe40431b7a)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { if (q != null && m != null && r != null && q > 0 && m > 0 && r > 0) onSubmit(ProcurementRequirementUpdateDto(minimumLotKg = m, requiredQuantityKg = q, maxRatePerKg = rateValue, procurementRadiusKm = r)) }, enabled = q?.let { it > 0 } == true && m?.let { it > 0 } == true && r?.let { it > 0 } == true) { Text(stringResource(R.string.ui_copy_c72cc238ad05)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun FormalisationDashboard(
    state: SupplyChainState,
    currentArea: String,
    currentCollectorId: String,
    onRouteEstimate: (String, Double, String) -> Unit,
    onCreatePool: (String, String) -> Unit,
    onJoinPool: (String, Double, String, Double?) -> Unit,
    onLeavePool: (String) -> Unit,
    onLockPool: (String) -> Unit,
    onPreparePoolHandover: (String) -> Unit,
    onPrepareBulkHandover: (String) -> Unit,
    onConfirmCollectorHandover: (String) -> Unit,
    onAcknowledgeSafety: (String) -> Unit,
    onLoadSafetyRouting: (String, String) -> Unit,
    onLoadMaterialPassport: (String) -> Unit,
    onLoadAnomalies: (String) -> Unit,
    onDecideSupplySettlement: (String, String, String?, String?, String?) -> Unit
) {
    var selectedToolsTab by rememberSaveable { mutableStateOf("ROUTES") }
    var routeMaterial by remember { mutableStateOf("CABLE") }
    var routeQuantity by remember { mutableStateOf("10") }
    var routeCondition by remember { mutableStateOf("INTACT") }
    var showJoin by remember { mutableStateOf<PoolOpportunityDto?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ROUTES" to "Routes", "POOLING" to "Pooling", "SAFETY" to "Safety", "RECORDS" to "Records").forEach { (key, label) ->
                FilterChip(selected = selectedToolsTab == key, onClick = { selectedToolsTab = key }, label = { Text(label) })
            }
        }
        if (selectedToolsTab == "ROUTES") Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.ui_copy_6044e5e342b3), Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text(stringResource(R.string.ui_copy_805c884d4202), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = routeMaterial == "CABLE", onClick = { routeMaterial = "CABLE" }, label = { Text(stringResource(R.string.ui_copy_ea1bd070cdc7)) })
                    FilterChip(selected = routeMaterial == "PCB", onClick = { routeMaterial = "PCB" }, label = { Text(stringResource(R.string.ui_copy_70606d6bfa77)) })
                    OutlinedTextField(routeQuantity, { routeQuantity = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text(stringResource(R.string.ui_copy_1389845b02c0)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(onClick = { routeQuantity.toDoubleOrNull()?.takeIf { it > 0 }?.let { onRouteEstimate(routeMaterial, it, "UNSPECIFIED") } }, enabled = routeQuantity.toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_4ff6bb8dcd8a)) }
                state.routeAdvantage?.let { result ->
                    result.baseline?.let { baseline -> Text("Reference baseline: ₹${"%.0f".format(baseline.marketPrice)}/kg · ${baseline.source ?: "source not stated"} · ${if (baseline.isDemo) "seeded/demo" else "recorded"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    result.items.take(3).forEach { RouteEstimateCard(it) }
                    Text(result.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selectedToolsTab == "SAFETY") Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ui_copy_f4fb787f252c), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_copy_118451b37dcf), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("INTACT", "DAMAGED", "PARTIAL").forEach { option -> FilterChip(selected = routeCondition == option, onClick = { routeCondition = option }, label = { Text(statusName(option)) }) }
                }
                OutlinedButton(onClick = { onLoadSafetyRouting(routeMaterial, routeCondition) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_10f74ba3f5fb)) }
                state.safetyRouting?.let { route ->
                    StatusChip("${statusName(route.hazardLevel)} hazard")
                    Text(route.recommendedRouting, style = MaterialTheme.typography.bodyMedium)
                    Text("${route.handlingWarningCode} · ${route.requiredRecyclerCapability ?: "standard recycler capability"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selectedToolsTab == "POOLING") Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.ui_copy_6ede03ad7103), Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text(stringResource(R.string.ui_copy_5d6b5bd09396), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.poolOpportunities.take(3).forEach { opportunity ->
                    val pool = opportunity.existingPool
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(materialName(opportunity.requirement.materialCategory), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.ui_copy_eab4610e8b0c, "%.0f".format(opportunity.requirement.minimumLotKg), "%.1f".format(opportunity.clusterAvailableKg), "%.1f".format(opportunity.supplyGapKg)), style = MaterialTheme.typography.bodySmall)
                            if (pool == null) Button(onClick = { onCreatePool(opportunity.requirement.id, currentArea.ifBlank { "Current area" }) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_6d86ac18d4ca)) }
                            else {
                                Text(stringResource(R.string.ui_copy_e141828e628c, statusName(pool.status), "%.1f".format(pool.totalReservedKg)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                val currentPool = state.pools.firstOrNull { it.id == pool.id }
                                val canJoin = pool.status in setOf("FORMING", "THRESHOLD_MET") && (currentPool == null || currentPool.contributions.none { it.isMine })
                                if (canJoin) OutlinedButton(onClick = { showJoin = opportunity }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_c5e68b9544b4)) }
                            }
                        }
                    }
                }
                state.pools.take(3).forEach { pool -> PoolActionCard(pool, currentCollectorId, onJoinPool, onLeavePool, onLockPool, onPreparePoolHandover) }
                if (state.poolSuggestions.isNotEmpty()) {
                    Text(stringResource(R.string.ui_copy_485ee2f3f68b), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    state.poolSuggestions.take(3).forEach { suggestion ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.ui_copy_d98dc65fe50a, materialName(suggestion.materialCategory), "%.0f".format(suggestion.requiredKg)), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.ui_copy_9be4f0d6091d, "%.0f".format(suggestion.eligibleSupplyKg), suggestion.contributorsNeeded), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (state.poolOpportunities.isEmpty() && state.pools.isEmpty()) Text(stringResource(R.string.ui_copy_617a09fbaca1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedToolsTab == "POOLING" && state.demandIntelligence.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ui_copy_b0bca5f0b39c), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.demandIntelligence.take(3).forEach { item ->
                        Text("${jsonText(item, "materialCategory") ?: "Material opportunity"} · ${jsonNumber(item, "eligibleSupplyKg") ?: jsonNumber(item, "requiredQuantityKg") ?: "updated"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.ui_copy_a43083d5904c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f))
                }
            }
        }
        if (selectedToolsTab == "RECORDS") state.passport?.let { passport ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(stringResource(R.string.ui_copy_42c4016d1fad), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.ui_copy_d7cc4ca9839b, passport.formalHandoverCount, "%.1f".format(passport.formalQuantityKg), passport.safetyModulesCompleted), style = MaterialTheme.typography.bodyLarge); Text(passport.platformLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall); Text(passport.disclaimer ?: "Platform evidence profile; not official certification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .78f)) }
            }
        }
        if (selectedToolsTab == "SAFETY") state.safety?.let { safety ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.ui_copy_e3f988950d15), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); safety.modules.forEach { module -> val acknowledged = safety.progress.any { it.moduleKey == module.key && it.acknowledged }; Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(module.title, fontWeight = FontWeight.SemiBold); Text(module.whatNotToDo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (acknowledged) StatusChip("Acknowledged") else TextButton(onClick = { onAcknowledgeSafety(module.key) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.ui_copy_9beb96dac88f)) } } } }
            }
        }
        if (selectedToolsTab == "RECORDS") {
            state.handovers.take(3).forEach { handover -> SupplyHandoverCard(handover, state.materialPassports[handover.id], state.anomalies[handover.id], onPrepareBulkHandover, onConfirmCollectorHandover, onLoadMaterialPassport, onLoadAnomalies, onDecideSupplySettlement) }
            if (state.showingCachedEvidence && state.cachedAtEpochMs > 0) Text(stringResource(R.string.ui_copy_a02eabdb37a0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    showJoin?.let { opportunity -> JoinPoolDialog(opportunity, onDismiss = { showJoin = null }, onSubmit = { quantity, grade, rate -> opportunity.existingPool?.id?.let { onJoinPool(it, quantity, grade, rate) }; showJoin = null }) }
}

@Composable private fun RouteEstimateCard(item: RouteAdvantageDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.recyclerName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.ui_copy_7b7a5929fa3b, "%.0f".format(item.estimatedNetValue)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Text(stringResource(R.string.ui_copy_b92030736331, "%.0f".format(item.offeredRatePerKg), "%.0f".format(item.logisticsCost), item.confidence.lowercase()), style = MaterialTheme.typography.bodySmall); Text(if (item.advantageValue != null) "Advantage ${"₹%.0f".format(item.advantageValue)}" else "Baseline unavailable", style = MaterialTheme.typography.bodySmall); Text(item.whyThisMatch.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.isDemo) Text(stringResource(R.string.ui_copy_623e5656f220), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

private fun jsonText(value: JsonObject, key: String): String? = value.get(key)?.takeIf { !it.isJsonNull }?.asString
private fun jsonNumber(value: JsonObject, key: String): String? = value.get(key)?.takeIf { !it.isJsonNull }?.let { element -> runCatching { "${"%.1f".format(element.asDouble)} kg" }.getOrNull() }

@Composable private fun PoolActionCard(pool: PooledConsignmentDto, collectorId: String, onJoin: (String, Double, String, Double?) -> Unit, onLeave: (String) -> Unit, onLock: (String) -> Unit, onPrepare: (String) -> Unit) { val mine = pool.contributions.firstOrNull { it.isMine }; Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.ui_copy_269d45a84851, materialName(pool.materialCategory)), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusChip(statusName(pool.status)) }; Text(stringResource(R.string.ui_copy_da9c6e2d32dc, "%.1f".format(pool.totalReservedKg), "%.1f".format(pool.minimumQuantityKg), pool.contributions.size), style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { if (pool.createdByCollectorId == collectorId && pool.status == "THRESHOLD_MET") OutlinedButton(onClick = { onLock(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_ed38460f5343)) }; if (pool.createdByCollectorId == collectorId && pool.status in setOf("LOCKED", "PICKUP_SCHEDULED")) Button(onClick = { onPrepare(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (pool.status == "PICKUP_SCHEDULED") "Refresh handover QR" else "Prepare QR") }; if (mine != null && pool.status in setOf("FORMING", "THRESHOLD_MET")) TextButton(onClick = { onLeave(pool.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_7e3520a97331)) } } } } }

@Composable private fun JoinPoolDialog(opportunity: PoolOpportunityDto, onDismiss: () -> Unit, onSubmit: (Double, String, Double?) -> Unit) { var quantity by remember { mutableStateOf("") }; var rate by remember { mutableStateOf(opportunity.requirement.maxRatePerKg?.toString().orEmpty()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_copy_0d1f8e9d6403, materialName(opportunity.requirement.materialCategory))) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.ui_copy_9c24a114fceb), style = MaterialTheme.typography.bodySmall); OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_49b0cf9c4005)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(stringResource(R.string.ui_copy_05dd0f9d8eaa)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { quantity.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSubmit(it, opportunity.requirement.preferredGrade ?: "UNSPECIFIED", rate.toDoubleOrNull()) } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text(stringResource(R.string.ui_copy_31f351109b91)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) }

@Composable
private fun SupplyHandoverCard(handover: SupplyHandoverDto, passport: MaterialPassportResponseDto?, anomaly: AnomalyResponseDto?, onPrepareBulk: (String) -> Unit, onConfirmCollector: (String) -> Unit, onLoadPassport: (String) -> Unit, onLoadAnomalies: (String) -> Unit, onDecideSettlement: (String, String, String?, String?, String?) -> Unit) {
    var showSettlement by remember(handover.id) { mutableStateOf(false) }
    var nowEpochMs by remember(handover.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(handover.id, handover.expiresAt) {
        while (true) { delay(30_000); nowEpochMs = System.currentTimeMillis() }
    }
    val expiresEpochMs = remember(handover.expiresAt) { handover.expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } }
    val qrExpired = expiresEpochMs != null && nowEpochMs >= expiresEpochMs
    val qrBitmap = remember(handover.status, handover.qrCodeData, qrExpired) { if (!qrExpired && handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) handover.qrCodeData?.let(::createSupplyQr) else null }
    Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.ui_copy_c7e82e2b7e50), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_copy_69ec2efba154, handover.referenceId, materialName(handover.materialCategory), "%.1f".format(handover.quotedWeightKg)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ui_copy_74fe1047f504, "%.0f".format(handover.quotedValue), statusName(handover.status)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            handover.expiresAt?.let { Text("QR expires: ${IndiaFormat.dateTimeIso(it) ?: "Time unavailable"} India time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (qrExpired && handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) Text(stringResource(R.string.ui_copy_799eb717ba03), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            qrBitmap?.let { Image(it.asImageBitmap(), "One-time handover QR", Modifier.size(190.dp).align(Alignment.CenterHorizontally)) }
            if (handover.status == "PREPARED" && !qrExpired) Button(onClick = { onConfirmCollector(handover.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_7be330b874d6)) }
            if (handover.status == "COLLECTOR_CONFIRMED" && !qrExpired) Text(stringResource(R.string.ui_copy_fecc7ddf5e07), style = MaterialTheme.typography.bodySmall)
            if (qrExpired && handover.bulkLotId != null) Button(onClick = { onPrepareBulk(handover.bulkLotId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.ui_copy_154331c55f35)) }
            if (handover.status == "REVIEW_REQUIRED") {
                Text("Settlement needs review: ${handover.reviewReason ?: "variance recorded"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showSettlement = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_f2c80879b0e5)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onLoadPassport(handover.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_d5ed1ca0e308)) }
                OutlinedButton(onClick = { onLoadAnomalies(handover.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_copy_024790205e21)) }
            }
            passport?.let { Text("${it.events.size} passport events · ${it.contribution?.quantityKg?.let { kg -> "${"%.1f".format(kg)} kg contributed" } ?: "direct lot handover"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            anomaly?.let { Text(stringResource(R.string.ui_copy_a7018b763900, statusName(it.riskLevel), it.flags.size), style = MaterialTheme.typography.bodySmall, color = if (it.riskLevel == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (handover.status == "COMPLETED") Text(stringResource(R.string.ui_copy_a0355a13d447), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showSettlement) SettlementDecisionDialog(title = "Review formal settlement", acceptLabel = "Accept settlement", onDismiss = { showSettlement = false }, onSubmit = { decision, reason, notes -> showSettlement = false; onDecideSettlement(handover.id, decision, reason, null, notes) })
}

private fun createSupplyQr(value: String): Bitmap? = runCatching { val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 480, 480); Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565).also { bitmap -> for (x in 0 until 480) for (y in 0 until 480) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }.getOrNull()

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip
) {
    MaterialText(
        text = localizedSupplyChainText(text),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        minLines = minLines,
        style = style,
        textAlign = textAlign,
        overflow = overflow
    )
}

@Composable private fun RoleHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onRefresh: () -> Unit, loading: Boolean) { Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { BoxRule(); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = onRefresh, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh") } } }
@Composable private fun BoxRule() { Spacer(Modifier.height(4.dp)); Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(36.dp).height(4.dp)) {}; Spacer(Modifier.height(8.dp)) }
@Composable private fun SummaryStrip(left: String, right: String, compact: Boolean) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(left, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2); Text(right, Modifier.weight(1.6f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 2) } } }
@Composable private fun StatusChip(text: String) { KcStatusPill(text, compact = true) }
@Composable
private fun ErrorPanel(text: String, retry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = retry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(stringResource(R.string.future_retry)) }
        }
    }
}
@Composable private fun LoadingPanel(text: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun EmptyPanel(title: String, detail: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .2f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) } } } }
