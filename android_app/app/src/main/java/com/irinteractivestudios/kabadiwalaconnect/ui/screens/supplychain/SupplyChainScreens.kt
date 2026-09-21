package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.util.ImagePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Keep this list identical to the backend MaterialCategory enum. Paper/newspaper
// is not currently a first-class backend category, so it is represented by
// OTHER until the contract adds a dedicated category.
private val materials = listOf("PLASTIC", "CABLE", "COPPER", "PCB", "BATTERY", "MOTOR", "MAGNET", "CRT", "LCD_PANEL", "OTHER")

private fun materialName(value: String) = localizedSupplyChainText(value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
private fun statusName(value: String) = localizedSupplyChainText(value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
private fun money(value: Double?) = value?.let { "₹${"%.2f".format(it)}" } ?: localizedSupplyChainText("Pending inspection")

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
    initialArea: String = "",
    busy: Set<String> = emptySet()
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader("Sell your scrap", "A nearby Kabadiwala weighs it and confirms payment.", Icons.Filled.Sell, onRefresh, state.loading) }
        item {
            Surface(shape = RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ready to sell?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Share an estimate; your partner confirms weight and price.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = onCreateListing, enabled = "create-listing" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(if ("create-listing" in busy) "Posting…" else "Sell scrap") }
                }
            }
        }
        item { SummaryStrip("${state.listings.count { it.status in setOf("POSTED", "PENDING_SYNC") }} open", "${state.pickups.count { it.status !in listOf("COMPLETED", "CANCELLED", "REJECTED") }} active pickups") }
        state.error?.let { message -> item { ErrorPanel(message, onRefresh) } }
        if (state.pendingPhotoUpload != null) item {
            OutlinedButton(onClick = onRetryPhoto, enabled = "upload-listing-photo" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if ("upload-listing-photo" in busy) "Uploading photo…" else "Retry photo upload")
            }
        }
        item { Text("My listings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.loading && state.listings.isEmpty()) item { LoadingPanel("Loading your listings…") }
        if (!state.loading && state.listings.isEmpty()) item { EmptyPanel("No listings yet", "Post your first listing to request pickup.") }
        items(state.listings, key = { it.id }) { listing ->
            HouseholdListingCard(
                listing = listing,
                pickups = state.pickups.filter { it.listingId == listing.id },
                kabadiwalas = state.kabadiwalas,
                busy = busy,
                onRequestPickup = onRequestPickup,
                radiusKm = state.kabadiwalaRadiusKm,
                onIncreaseRadius = onIncreaseRadius,
                onCancelListing = onCancelListing,
                onCancelPickup = onCancelPickup,
                onReschedulePickup = onReschedulePickup,
                onDecideSettlement = onDecideSettlement
            )
        }
    }
}

/** Live directory for a household. Pickup requests are made from a listing,
 * so the selected buyer and listing ownership remain explicit. */
@Composable
fun HouseholdKabadiwalasScreen(state: SupplyChainState, onRefresh: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { RoleHeader("Nearby Kabadiwalas", "Choose a nearby collection partner.", Icons.Filled.LocalShipping, onRefresh, state.loading) }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        if (!state.loading && state.kabadiwalas.isEmpty()) item { EmptyPanel("No partners nearby", "Try another area or refresh later.") }
        items(state.kabadiwalas, key = { it.id }) { kabadiwala ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(kabadiwala.displayName ?: "Kabadiwala", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text(kabadiwala.areaName.ifBlank { "Area not provided" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Create a listing to request pickup.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HouseholdListingCard(
    listing: HouseholdListingDto,
    pickups: List<PickupRequestDto>,
    kabadiwalas: List<KabadiwalaProfileDto>,
    busy: Set<String>,
    onRequestPickup: (String, String?) -> Unit,
    radiusKm: Int,
    onIncreaseRadius: () -> Unit,
    onCancelListing: (String) -> Unit,
    onCancelPickup: (String) -> Unit,
    onReschedulePickup: (String, String) -> Unit,
    onDecideSettlement: (String, String, String?, String?) -> Unit
) {
    val activePickupStatuses = setOf("WAITING_FOR_PICKUP", "REQUESTED", "ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED", "WEIGHED")
    val pickup = pickups.firstOrNull { it.status in activePickupStatuses }
    var showCancelListing by remember(listing.id) { mutableStateOf(false) }
    var showCancelPickup by remember(pickup?.id) { mutableStateOf(false) }
    var showReschedule by remember(pickup?.id) { mutableStateOf(false) }
    var showSettlement by remember(pickup?.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.primary); Text(friendlyMaterial(listing.materialCategory).title, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(pickup?.status ?: listing.status)) }
            Text("Approx. ${"%.1f".format(listing.estimatedWeight)} kg · ${listing.condition.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(listing.areaName, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (listing.photoAttached == true || !listing.photoReference.isNullOrBlank() || listing.photoReferences.isNotEmpty()) Text("Photo attached · visible to partner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            val minEstimate = listing.estimatedPriceMin
            val maxEstimate = listing.estimatedPriceMax
            if (minEstimate != null && maxEstimate != null) Text("Estimate · ₹${"%.0f".format(minEstimate)}–₹${"%.0f".format(maxEstimate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (pickup == null && listing.status == "POSTED") {
                if (kabadiwalas.isNotEmpty()) {
                    Text("Choose a partner", style = MaterialTheme.typography.labelLarge)
                    kabadiwalas.take(4).forEach { kabadiwala ->
                        val requestBusy = "pickup-${listing.id}" in busy
                        OutlinedButton(onClick = { onRequestPickup(listing.id, kabadiwala.id) }, enabled = !requestBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.LocalShipping, null); Spacer(Modifier.width(8.dp)); Text("Request pickup · ${kabadiwala.displayName ?: "Kabadiwala"}")
                        }
                    }
                } else {
                    Text("No Kabadiwala is available within ${radiusKm} km right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (radiusKm < 20) OutlinedButton(onClick = onIncreaseRadius, enabled = "pickup-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Increase radius to ${if (radiusKm == 5) 10 else 20} km")
                    }
                    OutlinedButton(onClick = { onRequestPickup(listing.id, null) }, enabled = "pickup-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Notify nearby Kabadiwalas")
                    }
                }
                OutlinedButton(onClick = { showCancelListing = true }, enabled = "cancel-listing-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel listing") }
            } else if (pickup == null && pickups.any { it.status == "CANCELLED" }) {
                Text("A previous pickup request was cancelled. You can choose another partner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pickup?.let { item ->
                Text("Pickup: ${statusName(item.status)}", fontWeight = FontWeight.SemiBold)
                Text("Stage: request → scheduled → weighed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.scheduledSlot?.let { Text("Scheduled: ${it.take(16).replace('T', ' ')}") }
                if (item.finalAmount != null) Text("Final settlement: ${money(item.finalAmount)} · ${"%.1f".format(item.actualWeight ?: 0.0)} kg", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (item.status in setOf("WAITING_FOR_PICKUP", "REQUESTED", "ACCEPTED", "SCHEDULED")) {
                    OutlinedButton(onClick = { showCancelPickup = true }, enabled = "cancel-pickup-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel pickup") }
                }
                if (item.status in setOf("ACCEPTED", "SCHEDULED")) {
                    OutlinedButton(onClick = { showReschedule = true }, enabled = "reschedule-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose another time") }
                }
                if (item.settlementStatus == "PENDING_HOUSEHOLD_CONFIRMATION") {
                    Text("Settlement review: final value ${money(item.finalAmount)} for ${"%.1f".format(item.actualWeight ?: 0.0)} kg", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { showSettlement = true }, enabled = "settlement-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Review settlement") }
                } else if (item.settlementStatus == "DISPUTED") {
                    Text("Settlement is under review${item.settlementReasonCode?.let { " · $it" } ?: ""}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showCancelListing) AlertDialog(
        onDismissRequest = { showCancelListing = false },
        title = { Text("Cancel this listing?") },
        text = { Text("The listing and any pending pickup requests will be cancelled. You can create a new listing later.") },
        confirmButton = { TextButton(onClick = { showCancelListing = false; onCancelListing(listing.id) }) { Text("Cancel listing") } },
        dismissButton = { TextButton(onClick = { showCancelListing = false }) { Text("Keep listing") } }
    )
    pickup?.let { item ->
        if (showCancelPickup) AlertDialog(
            onDismissRequest = { showCancelPickup = false },
            title = { Text("Cancel this pickup?") },
            text = { Text("The request will be closed and this listing will be available again if the partner has not started the trip.") },
            confirmButton = { TextButton(onClick = { showCancelPickup = false; onCancelPickup(item.id) }) { Text("Cancel pickup") } },
            dismissButton = { TextButton(onClick = { showCancelPickup = false }) { Text("Keep pickup") } }
        )
        if (showReschedule) ReschedulePickupDialog(
            onDismiss = { showReschedule = false },
            onSubmit = { slot -> showReschedule = false; onReschedulePickup(item.id, slot) }
        )
        if (showSettlement) SettlementDecisionDialog(
            title = "Review pickup settlement",
            acceptLabel = "Accept amount",
            onDismiss = { showSettlement = false },
            onSubmit = { decision, reason, notes -> showSettlement = false; onDecideSettlement(item.id, decision, reason, notes) }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HouseholdListingCreateScreen(
    state: SupplyChainState,
    initialArea: String,
    onBack: () -> Unit,
    onSuggestMaterial: (String) -> Unit,
    onClearMaterialSuggestion: () -> Unit,
    onCreateListing: (HouseholdListingCreateDto, List<String>) -> Unit,
    busy: Set<String> = emptySet()
) {
    var material by remember { mutableStateOf(friendlyMaterials.first().key) }
    var weight by remember { mutableStateOf("") }
    var area by remember(initialArea) { mutableStateOf(initialArea) }
    var notes by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("INTACT") }
    var safetyAcknowledged by remember { mutableStateOf(false) }
    var dataBearingDevice by remember { mutableStateOf(false) }
    var ownerPreparationCompleted by remember { mutableStateOf(false) }
    var dataDestructionRequested by remember { mutableStateOf(false) }
    var photoPaths by remember { mutableStateOf(emptyList<String>()) }
    var photoError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ioScope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        ioScope.launch(Dispatchers.IO) {
            val paths = uris.mapNotNull { uri ->
                runCatching {
                    ImagePipeline.importUri(context, uri, File(context.filesDir, "household_photos")).absolutePath
                }.getOrNull()
            }
            withContext(Dispatchers.Main.immediate) {
                val wasEmpty = photoPaths.isEmpty()
                photoPaths = (photoPaths + paths).distinct().take(6)
                photoError = paths.size < uris.size
                if (wasEmpty) photoPaths.firstOrNull()?.let(onSuggestMaterial)
            }
        }
    }
    LaunchedEffect(state.materialSuggestion?.materialCategory, state.materialDetectionStatus) {
        if (state.materialDetectionStatus == HouseholdMaterialDetectionStatus.SUCCESS) {
            state.materialSuggestion?.materialCategory?.takeIf { friendlyMaterials.any { item -> item.key == it } }?.let { material = it; safetyAcknowledged = false }
        }
    }
    LaunchedEffect(state.notice) { if (state.notice?.startsWith("Listing") == true) onBack() }
    val isHazardous = friendlyMaterial(material).hazardous
    val parsedWeight = weight.toDoubleOrNull()
    val weightError = weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0 || parsedWeight > 500)
    val areaError = area.isNotBlank() && area.trim().length < 2
    val canSubmit = parsedWeight != null && parsedWeight > 0 && parsedWeight <= 500 && area.trim().isNotEmpty() && (!isHazardous || safetyAcknowledged) && "create-listing" !in busy
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(
                    "Add photos and a few simple details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Show the scrap clearly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Add up to 6 photos from different angles. Clear photos help your Kabadiwala prepare.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (photoPaths.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photoPaths, key = { it }) { path ->
                                    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
                                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(96.dp)) {
                                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                                            if (bitmap != null) Image(bitmap, "Scrap photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            IconButton(onClick = { photoPaths = photoPaths - path; if (photoPaths.isEmpty()) onClearMaterialSuggestion() }, modifier = Modifier.size(36.dp).align(Alignment.TopEnd)) { Icon(Icons.Filled.Close, "Remove photo") }
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.AddPhotoAlternate, "Add photos"); Spacer(Modifier.width(8.dp)); Text(if (photoPaths.isEmpty()) "Add scrap photos" else "Add more photos") }
                        if (photoError) Text("Some photos could not be processed. Please choose a JPEG, PNG, or WebP image.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Text("What are you selling?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pick the closest everyday description. You can change the AI suggestion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (state.materialDetectionStatus) {
                    HouseholdMaterialDetectionStatus.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Checking the first photo…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall) }
                    HouseholdMaterialDetectionStatus.SUCCESS -> state.materialSuggestion?.let { Text("We think this may be ${friendlyMaterial(it.materialCategory).title}. Please check it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    HouseholdMaterialDetectionStatus.LOW_CONFIDENCE -> Text("We couldn't identify this confidently. Please choose below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE, HouseholdMaterialDetectionStatus.NETWORK_ERROR, HouseholdMaterialDetectionStatus.SERVICE_ERROR -> Text("Photo detection is unavailable right now. You can still choose the material below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    HouseholdMaterialDetectionStatus.IDLE -> Unit
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    friendlyMaterials.forEach { option ->
                        val selected = material == option.key
                        Card(onClick = { material = option.key; safetyAcknowledged = false }, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow), border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold); Text(option.examples, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (isHazardous) item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Handle with care", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer); Text("Do not dismantle, burn, puncture or mix it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(safetyAcknowledged, { safetyAcknowledged = it }); Text("I understand", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) } } }
            }
            item { Text("Condition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("INTACT", "DAMAGED", "PARTIAL").forEach { FilterChip(selected = condition == it, onClick = { condition = it }, label = { Text(it.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
            item { OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, modifier = Modifier.fillMaxWidth(), label = { Text("Approximate weight · kg") }, supportingText = { if (weightError) Text("Enter a weight between 0 and 500 kg") }, isError = weightError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item { OutlinedTextField(area, { area = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Pickup area") }, supportingText = { if (areaError) Text("Add an area or nearby landmark") }, isError = areaError, singleLine = true) }
            item { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text("Phone, laptop or storage device?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer); Text("Tell us if it may contain personal data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dataBearingDevice, { dataBearingDevice = it; if (!it) { ownerPreparationCompleted = false; dataDestructionRequested = false } }); Text("May contain personal data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }; if (dataBearingDevice) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ownerPreparationCompleted, { ownerPreparationCompleted = it }); Text("I removed my account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dataDestructionRequested, { dataDestructionRequested = it }); Text("Request destruction evidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) } } } } }
            item { OutlinedTextField(notes, { notes = it.take(1000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 3, maxLines = 4) }
        }
        Button(onClick = { parsedWeight?.let { value -> onCreateListing(HouseholdListingCreateDto(materialCategory = material, estimatedWeight = value, condition = condition, notes = notes.trim().ifBlank { null }, areaName = area.trim(), dataBearingDevice = dataBearingDevice, ownerPreparationCompleted = ownerPreparationCompleted, dataDestructionRequested = dataDestructionRequested), photoPaths) } }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 54.dp)) { Text(if ("create-listing" in busy) "Posting…" else "Post scrap listing") }
    }
}


@Composable
fun KabadiwalaSupplyScreen(state: SupplyChainState, section: KabadiwalaSection, onRefresh: () -> Unit, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit, onCreateBulk: (BulkLotCreateDto) -> Unit, onCancelBulk: (String) -> Unit, onAcceptOffer: (String) -> Unit, capturedLots: List<Lot> = emptyList(), currentArea: String = "Current area", currentCollectorId: String = "", listingPhotos: Map<String, List<ByteArray>> = emptyMap(), onLoadListingPhotos: (String, Int) -> Unit = { _, _ -> }, onRouteEstimate: (String, Double, String) -> Unit = { _, _, _ -> }, onCreatePool: (String, String) -> Unit = { _, _ -> }, onJoinPool: (String, Double, String, Double?) -> Unit = { _, _, _, _ -> }, onLeavePool: (String) -> Unit = {}, onLockPool: (String) -> Unit = {}, onPreparePoolHandover: (String) -> Unit = {}, onPrepareBulkHandover: (String) -> Unit = {}, onConfirmCollectorHandover: (String) -> Unit = {}, onAcknowledgeSafety: (String) -> Unit = {}, onCreateCapturedLot: () -> Unit = {}, onRejectPickup: (String, String) -> Unit = { _, _ -> }, onConfirmAvailability: (String, String?) -> Unit = { _, _ -> }, onCancelPickup: (String, String?) -> Unit = { _, _ -> }, onReassignPickup: (String, String, Boolean) -> Unit = { _, _, _ -> }, onRejectOffer: (String, String) -> Unit = { _, _ -> }, onCounterOffer: (String, Double, String?) -> Unit = { _, _, _ -> }, onLoadSafetyRouting: (String, String) -> Unit = { _, _ -> }, onLoadMaterialPassport: (String) -> Unit = {}, onLoadAnomalies: (String) -> Unit = {}, onDecideSupplySettlement: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> }) {
    var showBulk by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader(when (section) { KabadiwalaSection.HOME -> "Collection desk"; KabadiwalaSection.INVENTORY -> "Scrap inventory"; KabadiwalaSection.PICKUPS -> "Household pickups"; KabadiwalaSection.LOTS -> "Recycler sales" }, "Households → inventory → verified recyclers", Icons.Filled.Inventory2, onRefresh, state.loading) }
        if (section == KabadiwalaSection.HOME) item {
            OutlinedButton(onClick = onCreateCapturedLot, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Inventory2, null)
                Spacer(Modifier.width(8.dp))
                Text("Record lot")
            }
            ProductFeaturePanel(state)
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
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        when (section) {
            KabadiwalaSection.HOME, KabadiwalaSection.PICKUPS -> {
                item { SummaryStrip("${state.pickups.count { it.status == "REQUESTED" }} requests", "${state.pickups.count { it.status == "SCHEDULED" }} scheduled") }
                item { Text("Pickup queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (!state.loading && state.pickups.isEmpty()) item { EmptyPanel("No household pickups", "New requests will appear here.") }
                items(state.pickups, key = { it.id }) { pickup -> PickupCard(pickup, state.listings.firstOrNull { it.id == pickup.listingId }, listingPhotos[pickup.listingId].orEmpty(), onLoadListingPhotos, onAccept, onSchedule, onStatus, onComplete, onRejectPickup, onConfirmAvailability, onCancelPickup, onReassignPickup) }
            }
            KabadiwalaSection.INVENTORY -> {
                item { InventoryTotals(state.inventory) }
                if (!state.loading && state.inventory.isEmpty()) item { EmptyPanel("Inventory is empty", "Complete a pickup to add weighed material.") }
                items(state.inventory, key = { it.id }) { InventoryCard(it) }
                item { Button(onClick = { showBulk = true }, enabled = state.inventory.any { it.availableKg > 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(8.dp)); Text("Create recycler lot") } }
            }
            KabadiwalaSection.LOTS -> {
                item { Text("Bulk lots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (!state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No bulk lots yet", "Reserve available inventory when ready.") }
                items(state.bulkLots, key = { it.id }) { lot -> BulkLotCard(lot, onCancelBulk) }
                item { Text("Recycler offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Offers on listed lots appear here.") }
                items(state.offers, key = { it.id }) { offer -> OfferCard(offer, onAcceptOffer, onRejectOffer, onCounterOffer) }
                val visibleCapturedLots = capturedLots.filter { it.status != LotStatus.CANCELLED }
                if (visibleCapturedLots.isNotEmpty()) {
                    item { Text("Captured lot records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item {
                        Text(
                            "Saved records stay visible. Use weighed inventory to publish a recycler lot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(visibleCapturedLots, key = { "captured-${it.id}" }) { lot -> CapturedLotCard(lot) }
                }
            }
        }
    }
    if (showBulk) BulkLotDialog(state.inventory, currentArea, onDismiss = { showBulk = false }, onSubmit = { onCreateBulk(it); showBulk = false })
}

@Composable
private fun ProductFeaturePanel(state: SupplyChainState) {
    val features = remember(state) { productFeatureMatrix(state) }
    Surface(
        shape = RoundedCornerShape(28.dp, 8.dp, 28.dp, 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Recycling, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Kabadiwala Connect advantage", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusChip("9 features")
            }
            Text(
                "One field workflow from doorstep pickup to fair, traceable recycler settlement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            features.forEach { feature ->
                ProductFeatureRow(feature)
            }
            Text(
                "LIVE = connected workflow is available. PILOT = the workflow is built, but estimates or connectivity still matter.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProductFeatureRow(feature: ProductFeature) {
    val isLive = feature.state == ProductFeatureState.LIVE
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isLive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isLive) Icons.Filled.CheckCircle else Icons.Filled.LocationOn,
                contentDescription = null,
                tint = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(feature.title, fontWeight = FontWeight.SemiBold)
                Text(feature.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (isLive) "LIVE" else "PILOT")
        }
    }
}

enum class KabadiwalaSection { HOME, INVENTORY, PICKUPS, LOTS }

@Composable
private fun PickupCard(pickup: PickupRequestDto, listing: HouseholdListingDto?, loadedPhotos: List<ByteArray>, onLoadPhotos: (String, Int) -> Unit, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit, onReject: (String, String) -> Unit, onConfirmAvailability: (String, String?) -> Unit, onCancel: (String, String?) -> Unit, onReassign: (String, String, Boolean) -> Unit) {
    var showComplete by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showAvailability by remember { mutableStateOf(false) }
    var showReject by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var showPhotos by remember(pickup.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(listing?.materialCategory ?: "OTHER"), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(pickup.status)) }
            Text("Approx. ${"%.1f".format(listing?.estimatedWeight ?: 0.0)} kg · ${listing?.areaName ?: "Area unavailable"}")
            val photoCount = listing?.photoCount ?: 0
            if (photoCount > 0 && pickup.status != "WAITING_FOR_PICKUP") {
                OutlinedButton(onClick = { showPhotos = true; onLoadPhotos(pickup.listingId, photoCount) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "View scrap photos")
                    Spacer(Modifier.width(8.dp))
                    Text(if (photoCount == 1) "View scrap photo" else "View $photoCount scrap angles")
                }
                if (showPhotos) ListingPhotoStrip(loadedPhotos, photoCount)
            }
            when (pickup.status) {
                "WAITING_FOR_PICKUP" -> Button(onClick = { onAccept(pickup.listingId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Claim pickup") }
                "REQUESTED" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onAccept(pickup.listingId) }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Accept pickup") }
                        OutlinedButton(onClick = { showReject = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Decline") }
                    }
                }
                "ACCEPTED" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAvailability = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Confirm ready") }
                        OutlinedButton(onClick = { showSchedule = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Schedule") }
                    }
                    Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Start now") }
                }
                "SCHEDULED" -> Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Start trip") }
                "IN_TRANSIT" -> Button(onClick = { onStatus(pickup.id, "ARRIVED") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Mark arrived") }
                "ARRIVED" -> Button(onClick = { showComplete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Record weight") }
                "COMPLETED" -> Text("Added to inventory · ${money(pickup.finalAmount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (pickup.status in setOf("ACCEPTED", "SCHEDULED")) OutlinedButton(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel pickup") }
            if (pickup.status in setOf("IN_TRANSIT", "ARRIVED")) OutlinedButton(onClick = { showReassign = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Report issue / reassign") }
        }
    }
    if (showSchedule) SchedulePickupDialog(
        onDismiss = { showSchedule = false },
        onSubmit = { onSchedule(pickup.id, it); showSchedule = false }
    )
    if (showComplete) CompletionDialog(pickup, onDismiss = { showComplete = false }, onSubmit = { onComplete(pickup.id, it); showComplete = false })
    if (showAvailability) SchedulePickupDialog(onDismiss = { showAvailability = false }, onSubmit = { onConfirmAvailability(pickup.id, it); showAvailability = false })
    if (showReject) ReasonDialog(title = "Decline pickup", confirmLabel = "Decline", onDismiss = { showReject = false }, onSubmit = { onReject(pickup.id, it); showReject = false })
    if (showCancel) ReasonDialog(title = "Cancel pickup", confirmLabel = "Cancel", onDismiss = { showCancel = false }, onSubmit = { onCancel(pickup.id, it); showCancel = false })
    if (showReassign) ReassignDialog(onDismiss = { showReassign = false }, onSubmit = { reason, noShow -> onReassign(pickup.id, reason, noShow); showReassign = false })
}

@Composable
private fun ListingPhotoStrip(photos: List<ByteArray>, expectedCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (photos.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                MaterialText("Loading scrap photos…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos) { index, bytes ->
                    val bitmap = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(width = 144.dp, height = 112.dp)) {
                        if (bitmap != null) Image(bitmap, "Scrap angle ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            if (photos.size < expectedCount) MaterialText("Some additional angles could not be loaded. Retry to continue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else MaterialText("${photos.size} angle${if (photos.size == 1) "" else "s"} available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SchedulePickupDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    // Near-term choices keep the field flow fast while the API still receives
    // a real ISO-8601 timestamp rather than a display-only label.
    val slots = remember {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val displayFormatter = java.text.SimpleDateFormat("EEE, d MMM · h:mm a", java.util.Locale.getDefault())
        listOf(2, 4, 6).map { hours ->
            java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.HOUR_OF_DAY, hours)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time.let { time -> formatter.format(time) to displayFormatter.format(time) }
        }
    }
    var selected by remember { mutableStateOf(slots.first().first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a time for this pickup.", style = MaterialTheme.typography.bodyMedium)
                slots.forEach { slot ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        androidx.compose.material3.RadioButton(selected = selected == slot.first, onClick = { selected = slot.first })
                        Text(slot.second, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected.toString()) }) { Text("Confirm time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        text = { OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("Reason (optional)") }, minLines = 2) },
        confirmButton = { TextButton(onClick = { onSubmit(reason.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep pickup") } }
    )
}

@Composable
private fun ReassignDialog(onDismiss: () -> Unit, onSubmit: (String, Boolean) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var noShow by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return for reassignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("Why reassign?") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(noShow, { noShow = it }); Text("No-show") }
            }
        },
        confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onSubmit(reason.trim(), noShow) }) { Text("Reassign") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
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
                Text("Check weight and value before accepting.", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = raiseIssue, onCheckedChange = { raiseIssue = it })
                    Text("Raise an issue instead")
                }
                if (raiseIssue) {
                    Text("Reason", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("WEIGHT_DIFFERENCE", "PRICE_DIFFERENCE", "MATERIAL_MISMATCH", "OTHER").forEach { option ->
                            FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("What should be reviewed?") }, minLines = 2)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(if (raiseIssue) "RAISE_ISSUE" else "ACCEPT", if (raiseIssue) reason else null, notes.ifBlank { null }) }) {
                Text(if (raiseIssue) "Submit issue" else acceptLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CompletionDialog(pickup: PickupRequestDto, onDismiss: () -> Unit, onSubmit: (PickupCompletionDto) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(pickup.finalCategory ?: "PLASTIC") }
    var grade by remember { mutableStateOf("UNSPECIFIED") }
    val total = (weight.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final weighing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The household sees this amount immediately.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text("Actual weight · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Final material", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    materials.forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(materialName(option), maxLines = 1) })
                    }
                }
                OutlinedTextField(grade, { grade = it.take(80) }, label = { Text("Grade") }, singleLine = true)
                if (total > 0) Text("Settlement preview · ₹${"%.2f".format(total)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { val w = weight.toDoubleOrNull(); val r = rate.toDoubleOrNull(); if (w != null && r != null && w > 0 && w <= 500 && r > 0) onSubmit(PickupCompletionDto(w, category, grade.ifBlank { "UNSPECIFIED" }, r)) },
                enabled = weight.toDoubleOrNull()?.let { it > 0 && it <= 500 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Complete pickup") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InventoryCard(item: InventoryBalanceDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${"%.1f".format(item.availableKg)} kg free", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Text("Grade: ${item.grade}"); Text("Reserved ${"%.1f".format(item.reservedKg)} · sold ${"%.1f".format(item.soldKg)} kg · cost ${money(item.purchaseCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
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
            Text("${"%.1f".format(lot.weightKg)} kg · ${statusName(lot.status.name)}")
            Text("$syncLabel · captured record, not a recycler lot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun BulkLotCard(lot: BulkLotDto, onCancel: (String) -> Unit) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(lot.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text("${"%.1f".format(lot.quantityKg)} kg · asking ${money(lot.askingRatePerKg)}/kg"); Text("Reserved inventory · ${lot.areaName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (lot.status == "LISTED") OutlinedButton(onClick = { onCancel(lot.id) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel lot and release stock") } } } }
@Composable
private fun OfferCard(offer: BulkOfferDto, onAccept: (String) -> Unit, onReject: (String, String) -> Unit = { _, _ -> }, onCounter: (String, Double, String?) -> Unit = { _, _, _ -> }) {
    var showReject by remember(offer.id) { mutableStateOf(false) }
    var showCounter by remember(offer.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recycler offer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${money(offer.offeredRatePerKg)}/kg · ${statusName(offer.status)}")
            if (offer.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onAccept(offer.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Accept") }
                    OutlinedButton(onClick = { showCounter = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Counter") }
                }
                OutlinedButton(onClick = { showReject = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reject offer") }
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
        title = { Text("Counter-offer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Note (optional)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.takeIf { it > 0 }?.let { onSubmit(it, notes.ifBlank { null }) } }, enabled = parsed?.let { it > 0 } == true) { Text("Send counter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create recycler bulk lot") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Only available inventory can be reserved. This lot is for verified recyclers.")
            if (options.size > 1) {
                Text("Material", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.take(6).forEach { candidate ->
                        FilterChip(selected = candidate.id == item?.id, onClick = { selectedId = candidate.id }, label = { Text(materialName(candidate.materialCategory)) })
                    }
                }
            }
            Text("${materialName(item?.materialCategory ?: "OTHER")} · available ${"%.1f".format(item?.availableKg ?: 0.0)} kg")
            OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Asking rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(minimumRate, { minimumRate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Minimum acceptable rate · ₹/kg (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notes for recyclers (optional)") }, minLines = 2)
        }
    }, confirmButton = { TextButton(onClick = { val q = quantity.toDoubleOrNull(); val r = rate.toDoubleOrNull(); val min = minimumRate.toDoubleOrNull(); if (item != null && q != null && r != null && q > 0 && q <= item.availableKg && r > 0 && (min == null || (min > 0 && min <= r))) onSubmit(BulkLotCreateDto(item.materialCategory, item.grade, q, r, min, currentArea.ifBlank { "Current area" }, notes = notes.ifBlank { null })) }, enabled = item != null && quantity.toDoubleOrNull()?.let { it > 0 && it <= (item.availableKg) } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && (minimumRate.toDoubleOrNull() == null || minimumRate.toDoubleOrNull()?.let { it > 0 && it <= (rate.toDoubleOrNull() ?: 0.0) } == true)) { Text("List for recyclers") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun RecyclerSupplyScreen(state: SupplyChainState, onRefresh: () -> Unit, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit, onCreateDemand: (ProcurementRequirementCreateDto) -> Unit, onWithdrawOffer: (String, String?) -> Unit = { _, _ -> }, onUpdateRequirement: (String, ProcurementRequirementUpdateDto) -> Unit = { _, _ -> }, onOpenHandoverScanner: () -> Unit = {}) {
    var showDemand by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader("Buy recyclable material", "Browse bulk lots or publish facility demand.", Icons.Filled.Storefront, onRefresh, state.loading) }
        item { Button(onClick = { showDemand = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Publish demand") } }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        item { Text("Available collector lots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (!state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No lots available", "Matching lots will appear here.") }
        items(state.bulkLots, key = { it.id }) { lot -> RecyclerLotCard(lot, state.offers.firstOrNull { it.bulkLotId == lot.id }, onOffer, onReceive) }
        item { Text("My offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Make an offer on a listed lot.") }
        items(state.offers, key = { it.id }) { RecyclerOfferCard(it, onWithdraw = onWithdrawOffer) }
        item { Text("My demand", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.requirements, key = { it.id }) { requirement -> RequirementCard(requirement, onUpdate = onUpdateRequirement) }
        item { Text("Pools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.pools.isEmpty()) item { EmptyPanel("No pools yet", "Collectors can combine reserved stock for your demand.") }
        items(state.pools, key = { "pool-${it.id}" }) { pool ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("${materialName(pool.materialCategory)} · ${"%.1f".format(pool.totalReservedKg)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${statusName(pool.status)} · ${pool.contributions.size} collector contributions", style = MaterialTheme.typography.bodySmall); Text("The QR handover and per-contribution settlement remain visible to the participating parties.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
        }
        item { Text("Awaiting receipts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.handovers.isEmpty()) item { EmptyPanel("No handovers yet", "Confirmed QR handovers will appear here.") }
        items(state.handovers, key = { "supply-${it.id}" }) { handover ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(handover.referenceId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${materialName(handover.materialCategory)} · ${"%.1f".format(handover.quotedWeightKg)} kg · ${statusName(handover.status)}", style = MaterialTheme.typography.bodyMedium); if (handover.status == "COLLECTOR_CONFIRMED") Button(onClick = onOpenHandoverScanner, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Scan QR") } } }
        }
    }
    if (showDemand) DemandDialog(onDismiss = { showDemand = false }, onSubmit = { onCreateDemand(it); showDemand = false })
}

@Composable private fun RecyclerLotCard(lot: BulkLotDto, offer: BulkOfferDto?, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit) { var showOffer by remember { mutableStateOf(false) }; Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(lot.materialCategory), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text("${"%.1f".format(lot.quantityKg)} kg · ${money(lot.askingRatePerKg)}/kg asking"); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("${lot.areaName} · collector lot", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (offer == null && lot.status == "LISTED") Button(onClick = { showOffer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Make offer") }; if (offer != null) { Text("Your offer: ${money(offer.offeredRatePerKg)}/kg · ${statusName(offer.status)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); if (offer.status == "ACCEPTED") Text("Accepted · waiting for QR handover.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; if (showOffer) OfferDialog(lot, onDismiss = { showOffer = false }, onSubmit = { onOffer(lot.id, it); showOffer = false }) }
@Composable
private fun RecyclerOfferCard(offer: BulkOfferDto, onWithdraw: (String, String?) -> Unit) {
    var showWithdraw by remember(offer.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your offer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${money(offer.offeredRatePerKg)}/kg · ${statusName(offer.status)}")
            if (offer.status == "PENDING") OutlinedButton(onClick = { showWithdraw = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Withdraw offer") }
        }
    }
    if (showWithdraw) ReasonDialog(title = "Withdraw offer", confirmLabel = "Withdraw", onDismiss = { showWithdraw = false }, onSubmit = { onWithdraw(offer.id, it.ifBlank { null }); showWithdraw = false })
}
@Composable private fun OfferDialog(lot: BulkLotDto, onDismiss: () -> Unit, onSubmit: (Double) -> Unit) { var rate by remember { mutableStateOf(lot.askingRatePerKg.toString()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Offer on ${materialName(lot.materialCategory)}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${"%.1f".format(lot.quantityKg)} kg · asking ${money(lot.askingRatePerKg)}/kg"); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Your offer · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { rate.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSubmit) }, enabled = rate.toDoubleOrNull()?.let { it > 0 } == true) { Text("Send offer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun DemandDialog(onDismiss: () -> Unit, onSubmit: (ProcurementRequirementCreateDto) -> Unit) { var material by remember { mutableStateOf("PLASTIC") }; var quantity by remember { mutableStateOf("") }; var minimum by remember { mutableStateOf("") }; var radius by remember { mutableStateOf("50") }; var rate by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Publish procurement demand") }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { materials.take(4).forEach { FilterChip(selected = material == it, onClick = { material = it }, label = { Text(materialName(it)) }) } }; OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(9) }, label = { Text("Needed quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(minimum, { minimum = it.filter(Char::isDigit).take(9) }, label = { Text("Minimum lot · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(4) }, label = { Text("Procurement radius · km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Maximum rate · ₹/kg (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { val q = quantity.toDoubleOrNull(); val m = minimum.toDoubleOrNull(); val r = radius.toDoubleOrNull(); if (q != null && m != null && r != null && q > 0 && m > 0 && r > 0) onSubmit(ProcurementRequirementCreateDto(material, m, q, maxRatePerKg = rate.toDoubleOrNull(), procurementRadiusKm = r)) }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true && minimum.toDoubleOrNull()?.let { it > 0 } == true) { Text("Publish demand") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun RequirementCard(item: ProcurementRequirementDto, onUpdate: (String, ProcurementRequirementUpdateDto) -> Unit = { _, _ -> }) {
    var showEdit by remember(item.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(item.status)) }
            Text("Need ${"%.0f".format(item.requiredQuantityKg)} kg · min lot ${"%.0f".format(item.minimumLotKg)} kg")
            Text("Within ${"%.0f".format(item.procurementRadiusKm)} km${item.maxRatePerKg?.let { " · up to ₹${"%.0f".format(it)}/kg" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            if (item.status == "OPEN") OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Update demand") }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Update procurement demand") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(materialName(item.materialCategory), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, label = { Text("Required quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(minimum, { minimum = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, label = { Text("Minimum lot · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(radius, { radius = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text("Radius · km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Maximum rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { if (q != null && m != null && r != null && q > 0 && m > 0 && r > 0) onSubmit(ProcurementRequirementUpdateDto(minimumLotKg = m, requiredQuantityKg = q, maxRatePerKg = rateValue, procurementRadiusKm = r)) }, enabled = q?.let { it > 0 } == true && m?.let { it > 0 } == true && r?.let { it > 0 } == true) { Text("Save demand") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    var routeMaterial by remember { mutableStateOf("CABLE") }
    var routeQuantity by remember { mutableStateOf("10") }
    var routeCondition by remember { mutableStateOf("INTACT") }
    var showJoin by remember { mutableStateOf<PoolOpportunityDto?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp, 8.dp, 28.dp, 28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text("Formal route desk", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                Text("Compare routes, pools and passport evidence.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Platform evidence—not a government certificate or guaranteed price.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary); Text("Formal Route Advantage", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text("Compare rate, pickup readiness and logistics cost.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = routeMaterial == "CABLE", onClick = { routeMaterial = "CABLE" }, label = { Text("Cable") })
                    FilterChip(selected = routeMaterial == "PCB", onClick = { routeMaterial = "PCB" }, label = { Text("PCB") })
                    OutlinedTextField(routeQuantity, { routeQuantity = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text("kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(onClick = { routeQuantity.toDoubleOrNull()?.takeIf { it > 0 }?.let { onRouteEstimate(routeMaterial, it, "UNSPECIFIED") } }, enabled = routeQuantity.toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Compare verified routes") }
                state.routeAdvantage?.let { result ->
                    result.baseline?.let { baseline -> Text("Reference baseline: ₹${"%.0f".format(baseline.marketPrice)}/kg · ${baseline.source ?: "source not stated"} · ${if (baseline.isDemo) "seeded/demo" else "recorded"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    result.items.take(3).forEach { RouteEstimateCard(it) }
                    Text(result.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safety-aware routing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Check handling before moving hazardous or data-bearing material.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("INTACT", "DAMAGED", "PARTIAL").forEach { option -> FilterChip(selected = routeCondition == option, onClick = { routeCondition = option }, label = { Text(statusName(option)) }) }
                }
                OutlinedButton(onClick = { onLoadSafetyRouting(routeMaterial, routeCondition) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Check handling route") }
                state.safetyRouting?.let { route ->
                    StatusChip("${statusName(route.hazardLevel)} hazard")
                    Text(route.recommendedRouting, style = MaterialTheme.typography.bodyMedium)
                    Text("${route.handlingWarningCode} · ${route.requiredRecyclerCapability ?: "standard recycler capability"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text("Cooperative pooling", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text("Reserve available stock; thresholds and contributions stay visible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.poolOpportunities.take(3).forEach { opportunity ->
                    val pool = opportunity.existingPool
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(materialName(opportunity.requirement.materialCategory), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Need ${"%.0f".format(opportunity.requirement.minimumLotKg)} kg · visible cluster ${"%.1f".format(opportunity.clusterAvailableKg)} kg · gap ${"%.1f".format(opportunity.supplyGapKg)} kg", style = MaterialTheme.typography.bodySmall)
                            if (pool == null) Button(onClick = { onCreatePool(opportunity.requirement.id, currentArea.ifBlank { "Current area" }) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open pool") }
                            else {
                                Text("Pool ${statusName(pool.status)} · ${"%.1f".format(pool.totalReservedKg)} kg reserved", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                val currentPool = state.pools.firstOrNull { it.id == pool.id }
                                val canJoin = pool.status in setOf("FORMING", "THRESHOLD_MET") && (currentPool == null || currentPool.contributions.none { it.isMine })
                                if (canJoin) OutlinedButton(onClick = { showJoin = opportunity }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Join with reserved stock") }
                            }
                        }
                    }
                }
                state.pools.take(3).forEach { pool -> PoolActionCard(pool, currentCollectorId, onJoinPool, onLeavePool, onLockPool, onPreparePoolHandover) }
                if (state.poolSuggestions.isNotEmpty()) {
                    Text("Demand-triggered suggestions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    state.poolSuggestions.take(3).forEach { suggestion ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${materialName(suggestion.materialCategory)} · ${"%.0f".format(suggestion.requiredKg)} kg needed", fontWeight = FontWeight.SemiBold)
                                Text("${"%.0f".format(suggestion.eligibleSupplyKg)} kg eligible supply · ${suggestion.contributorsNeeded} contributors suggested", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (state.poolOpportunities.isEmpty() && state.pools.isEmpty()) Text("No open demand. New pools appear after a Recycler publishes demand.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.demandIntelligence.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Demand intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.demandIntelligence.take(3).forEach { item ->
                        Text("${jsonText(item, "materialCategory") ?: "Material opportunity"} · ${jsonNumber(item, "eligibleSupplyKg") ?: jsonNumber(item, "requiredQuantityKg") ?: "updated"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Platform signal, not a guaranteed sale.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f))
                }
            }
        }
        state.passport?.let { passport ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Collector Growth Passport", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${passport.formalHandoverCount} handovers · ${"%.1f".format(passport.formalQuantityKg)} kg · ${passport.safetyModulesCompleted} safety modules", style = MaterialTheme.typography.bodyLarge); Text(passport.platformLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall); Text(passport.disclaimer ?: "Platform evidence profile; not official certification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .78f)) }
            }
        }
        state.safety?.let { safety ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Safety gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); safety.modules.forEach { module -> val acknowledged = safety.progress.any { it.moduleKey == module.key && it.acknowledged }; Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(module.title, fontWeight = FontWeight.SemiBold); Text(module.whatNotToDo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (acknowledged) StatusChip("Acknowledged") else TextButton(onClick = { onAcknowledgeSafety(module.key) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Acknowledge") } } } }
            }
        }
        state.handovers.take(3).forEach { handover -> SupplyHandoverCard(handover, state.materialPassports[handover.id], state.anomalies[handover.id], onPrepareBulkHandover, onConfirmCollectorHandover, onLoadMaterialPassport, onLoadAnomalies, onDecideSupplySettlement) }
        if (state.showingCachedEvidence && state.cachedAtEpochMs > 0) Text("Showing saved evidence. Changes wait for the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    showJoin?.let { opportunity -> JoinPoolDialog(opportunity, onDismiss = { showJoin = null }, onSubmit = { quantity, grade, rate -> opportunity.existingPool?.id?.let { onJoinPool(it, quantity, grade, rate) }; showJoin = null }) }
}

@Composable private fun RouteEstimateCard(item: RouteAdvantageDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.recyclerName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("₹${"%.0f".format(item.estimatedNetValue)} net", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Text("₹${"%.0f".format(item.offeredRatePerKg)}/kg · logistics ₹${"%.0f".format(item.logisticsCost)} · ${item.confidence.lowercase()} confidence", style = MaterialTheme.typography.bodySmall); Text(if (item.advantageValue != null) "Advantage ${"₹%.0f".format(item.advantageValue)}" else "Baseline unavailable", style = MaterialTheme.typography.bodySmall); Text(item.whyThisMatch.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.isDemo) Text("Demo reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

private fun jsonText(value: JsonObject, key: String): String? = value.get(key)?.takeIf { !it.isJsonNull }?.asString
private fun jsonNumber(value: JsonObject, key: String): String? = value.get(key)?.takeIf { !it.isJsonNull }?.let { element -> runCatching { "${"%.1f".format(element.asDouble)} kg" }.getOrNull() }

@Composable private fun PoolActionCard(pool: PooledConsignmentDto, collectorId: String, onJoin: (String, Double, String, Double?) -> Unit, onLeave: (String) -> Unit, onLock: (String) -> Unit, onPrepare: (String) -> Unit) { val mine = pool.contributions.firstOrNull { it.isMine }; Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Pool · ${materialName(pool.materialCategory)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusChip(statusName(pool.status)) }; Text("${"%.1f".format(pool.totalReservedKg)} / ${"%.1f".format(pool.minimumQuantityKg)} kg · ${pool.contributions.size} contributors", style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { if (pool.createdByCollectorId == collectorId && pool.status == "THRESHOLD_MET") OutlinedButton(onClick = { onLock(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Lock pool") }; if (pool.createdByCollectorId == collectorId && pool.status in setOf("LOCKED", "THRESHOLD_MET")) Button(onClick = { onPrepare(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Prepare QR") }; if (mine != null && pool.status in setOf("FORMING", "THRESHOLD_MET")) TextButton(onClick = { onLeave(pool.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Leave") } } } } }

@Composable private fun JoinPoolDialog(opportunity: PoolOpportunityDto, onDismiss: () -> Unit, onSubmit: (Double, String, Double?) -> Unit) { var quantity by remember { mutableStateOf("") }; var rate by remember { mutableStateOf(opportunity.requirement.maxRatePerKg?.toString().orEmpty()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Join ${materialName(opportunity.requirement.materialCategory)} pool") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Reserved until the pooled handover is settled.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Expected rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { quantity.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSubmit(it, opportunity.requirement.preferredGrade ?: "UNSPECIFIED", rate.toDoubleOrNull()) } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text("Reserve stock") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable
private fun SupplyHandoverCard(handover: SupplyHandoverDto, passport: MaterialPassportResponseDto?, anomaly: AnomalyResponseDto?, onPrepareBulk: (String) -> Unit, onConfirmCollector: (String) -> Unit, onLoadPassport: (String) -> Unit, onLoadAnomalies: (String) -> Unit, onDecideSettlement: (String, String, String?, String?, String?) -> Unit) {
    var showSettlement by remember(handover.id) { mutableStateOf(false) }
    val qrBitmap = remember(handover.status, handover.qrCodeData) { if (handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) handover.qrCodeData?.let(::createSupplyQr) else null }
    Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Material passport handover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${handover.referenceId} · ${materialName(handover.materialCategory)} · ${"%.1f".format(handover.quotedWeightKg)} kg", style = MaterialTheme.typography.bodyLarge)
            Text("Quoted value ₹${"%.0f".format(handover.quotedValue)} · ${statusName(handover.status)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            qrBitmap?.let { Image(it.asImageBitmap(), "One-time handover QR", Modifier.size(190.dp).align(Alignment.CenterHorizontally)) }
            if (handover.status == "PREPARED") Button(onClick = { onConfirmCollector(handover.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Confirm collector side") }
            if (handover.status == "COLLECTOR_CONFIRMED") Text("Show this one-time QR to the verified Recycler for scan and receipt.", style = MaterialTheme.typography.bodySmall)
            if (handover.status == "REVIEW_REQUIRED") {
                Text("Settlement needs review: ${handover.reviewReason ?: "variance recorded"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showSettlement = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Review settlement") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onLoadPassport(handover.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Passport") }
                OutlinedButton(onClick = { onLoadAnomalies(handover.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Risk flags") }
            }
            passport?.let { Text("${it.events.size} passport events · ${it.contribution?.quantityKg?.let { kg -> "${"%.1f".format(kg)} kg contributed" } ?: "direct lot handover"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            anomaly?.let { Text("Risk: ${statusName(it.riskLevel)} · ${it.flags.size} deterministic flag(s)", style = MaterialTheme.typography.bodySmall, color = if (it.riskLevel == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (handover.status == "COMPLETED") Text("Receipt completed. The one-time QR is no longer active; inspect the passport timeline for evidence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    MaterialText(
        text = localizedSupplyChainText(text),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}

@Composable private fun RoleHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onRefresh: () -> Unit, loading: Boolean) { Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { BoxRule(); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = onRefresh, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh") } } }
@Composable private fun BoxRule() { Spacer(Modifier.height(4.dp)); Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(36.dp).height(4.dp)) {}; Spacer(Modifier.height(8.dp)) }
@Composable private fun SummaryStrip(left: String, right: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(left, fontWeight = FontWeight.Bold); Text(right, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }
@Composable private fun StatusChip(text: String) { Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ErrorPanel(text: String, retry: () -> Unit) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = retry) { Text("Retry") } } } }
@Composable private fun LoadingPanel(text: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun EmptyPanel(title: String, detail: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .2f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
