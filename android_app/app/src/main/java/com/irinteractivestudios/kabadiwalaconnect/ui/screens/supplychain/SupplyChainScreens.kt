package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Keep this list identical to the backend MaterialCategory enum. Paper/newspaper
// is not currently a first-class backend category, so it is represented by
// OTHER until the contract adds a dedicated category.
private val materials = listOf("PLASTIC", "CABLE", "COPPER", "PCB", "BATTERY", "MOTOR", "MAGNET", "CRT", "LCD_PANEL", "OTHER")

private fun materialName(value: String) = value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
private fun statusName(value: String) = value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
private fun money(value: Double?) = value?.let { "₹${"%.2f".format(it)}" } ?: "Pending inspection"

@Composable
fun HouseholdSupplyScreen(
    state: SupplyChainState,
    onRefresh: () -> Unit,
    onCreateListing: (HouseholdListingCreateDto) -> Unit,
    onRequestPickup: (String, String) -> Unit,
    onCancelListing: (String) -> Unit = {},
    onCancelPickup: (String) -> Unit = {},
    initialArea: String = "",
    busy: Set<String> = emptySet()
) {
    var showCreate by remember { mutableStateOf(false) }
    var selectedPhoto by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val photoScope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoScope.launch(Dispatchers.IO) {
            val extension = when (context.contentResolver.getType(uri)?.lowercase(Locale.US)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val target = File(context.filesDir, "household_photos/listing_${System.currentTimeMillis()}.$extension")
            val path = runCatching {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read selected image")
                target.absolutePath
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) { selectedPhoto = path }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceContainerLow))), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader("Sell your scrap", "A nearby Kabadiwala weighs it and confirms the final amount.", Icons.Filled.Sell, onRefresh, state.loading) }
        item {
            Surface(shape = RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Have recyclable material?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Post an approximate listing. Your estimate is a range, never a guaranteed price.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = { selectedPhoto = null; showCreate = true }, enabled = "create-listing" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(if ("create-listing" in busy) "Posting…" else "Sell scrap") }
                }
            }
        }
        item { SupplyChainDemoPanel() }
        item { SummaryStrip("${state.listings.count { it.status == "POSTED" }} open", "${state.pickups.count { it.status !in listOf("COMPLETED", "CANCELLED", "REJECTED") }} active pickups") }
        state.error?.let { message -> item { ErrorPanel(message, onRefresh) } }
        item { Text("Your listings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.loading && state.listings.isEmpty()) item { LoadingPanel("Loading your listings…") }
        if (!state.loading && state.listings.isEmpty()) item { EmptyPanel("No listings yet", "Your first listing will appear here after you post it.") }
        items(state.listings, key = { it.id }) { listing ->
            HouseholdListingCard(
                listing = listing,
                pickups = state.pickups.filter { it.listingId == listing.id },
                kabadiwalas = state.kabadiwalas,
                busy = busy,
                onRequestPickup = onRequestPickup,
                onCancelListing = onCancelListing,
                onCancelPickup = onCancelPickup
            )
        }
    }
    if (showCreate) HouseholdListingDialog(initialArea = initialArea, photoReference = selectedPhoto, onSelectPhoto = { gallery.launch("image/*") }, onClearPhoto = { selectedPhoto = null }, onDismiss = { showCreate = false }, onSubmit = { onCreateListing(it); showCreate = false })
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
        item { RoleHeader("Nearby Kabadiwalas", "Active collection partners who can receive your scrap pickup request.", Icons.Filled.LocalShipping, onRefresh, state.loading) }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        if (!state.loading && state.kabadiwalas.isEmpty()) item { EmptyPanel("No Kabadiwalas nearby", "Try again later or update your pickup area on a listing.") }
        items(state.kabadiwalas, key = { it.id }) { kabadiwala ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(kabadiwala.displayName ?: "Kabadiwala", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text(kabadiwala.areaName.ifBlank { "Area not provided" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Create a Sell Scrap listing to request pickup from this partner.", style = MaterialTheme.typography.bodySmall)
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
    onRequestPickup: (String, String) -> Unit,
    onCancelListing: (String) -> Unit,
    onCancelPickup: (String) -> Unit
) {
    val activePickupStatuses = setOf("REQUESTED", "ACCEPTED", "SCHEDULED", "IN_TRANSIT", "ARRIVED", "WEIGHED")
    val pickup = pickups.firstOrNull { it.status in activePickupStatuses }
    var showCancelListing by remember(listing.id) { mutableStateOf(false) }
    var showCancelPickup by remember(pickup?.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(listing.materialCategory), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(pickup?.status ?: listing.status)) }
            Text("Approx. ${"%.1f".format(listing.estimatedWeight)} kg · ${listing.condition.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(listing.areaName, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!listing.photoReference.isNullOrBlank()) Text("Photo attached · available to the pickup partner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            val minEstimate = listing.estimatedPriceMin
            val maxEstimate = listing.estimatedPriceMax
            if (minEstimate != null && maxEstimate != null) Text("Simulated AI estimate · ₹${"%.0f".format(minEstimate)}–₹${"%.0f".format(maxEstimate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text("Estimate uses demo market data; final value depends on inspection and local rate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pickup == null && listing.status == "POSTED") {
                if (kabadiwalas.isNotEmpty()) {
                    Text("Choose a collection partner", style = MaterialTheme.typography.labelLarge)
                    kabadiwalas.take(4).forEach { kabadiwala ->
                        val requestBusy = "pickup-${listing.id}" in busy
                        OutlinedButton(onClick = { onRequestPickup(listing.id, kabadiwala.id) }, enabled = !requestBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.LocalShipping, null); Spacer(Modifier.width(8.dp)); Text("Request pickup · ${kabadiwala.displayName ?: "Kabadiwala"} (${kabadiwala.areaName})")
                        }
                    }
                } else Text("No active Kabadiwala is available in your area yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showCancelListing = true }, enabled = "cancel-listing-${listing.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel listing") }
            } else if (pickup == null && pickups.any { it.status == "CANCELLED" }) {
                Text("A previous pickup request was cancelled. You can choose another partner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pickup?.let { item ->
                Text("Pickup: ${statusName(item.status)}", fontWeight = FontWeight.SemiBold)
                Text("Progress: request → accepted → scheduled → on the way → arrived → weighed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.scheduledSlot?.let { Text("Scheduled: ${it.take(16).replace('T', ' ')}") }
                if (item.finalAmount != null) Text("Final settlement: ${money(item.finalAmount)} · ${"%.1f".format(item.actualWeight ?: 0.0)} kg", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (item.status in setOf("REQUESTED", "ACCEPTED", "SCHEDULED")) {
                    OutlinedButton(onClick = { showCancelPickup = true }, enabled = "cancel-pickup-${item.id}" !in busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel pickup") }
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
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HouseholdListingDialog(initialArea: String, photoReference: String?, onSelectPhoto: () -> Unit, onClearPhoto: () -> Unit, onDismiss: () -> Unit, onSubmit: (HouseholdListingCreateDto) -> Unit) {
    var material by remember { mutableStateOf(materials.first()) }; var weight by remember { mutableStateOf("") }; var area by remember { mutableStateOf(initialArea) }; var notes by remember { mutableStateOf("") }; var condition by remember { mutableStateOf("INTACT") }; var safetyAcknowledged by remember { mutableStateOf(false) }
    val isHazardous = material in setOf("BATTERY", "CRT", "LCD_PANEL", "PCB")
    val parsedWeight = weight.toDoubleOrNull()
    val estimate = parsedWeight?.let { demoEstimate(material, it, condition) }
    val weightError = weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0 || parsedWeight > 500)
    val areaError = area.isNotBlank() && area.trim().length < 2
    val canSubmit = parsedWeight != null && parsedWeight > 0 && parsedWeight <= 500 && area.trim().isNotEmpty() && (!isHazardous || safetyAcknowledged)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sell scrap") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tell us what you want collected. The partner confirms the final weight and price at pickup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Material", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { materials.forEach { FilterChip(selected = material == it, onClick = { material = it; safetyAcknowledged = false }, label = { Text(materialName(it), maxLines = 1) }) } }
            if (isHazardous) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Handle with care", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Do not dismantle, burn, puncture, or mix this material. Keep batteries and screens away from children, heat, and water; the collector must confirm safe handling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = safetyAcknowledged, onCheckedChange = { safetyAcknowledged = it })
                            Text("I understand the handling warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("INTACT", "DAMAGED", "PARTIAL").forEach { FilterChip(selected = condition == it, onClick = { condition = it }, label = { Text(it.lowercase(), maxLines = 1) }) } }
            OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, modifier = Modifier.fillMaxWidth(), label = { Text("Approximate weight · kg") }, supportingText = { if (weightError) Text("Enter a weight between 0 and 500 kg") }, isError = weightError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(area, { area = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Pickup area") }, supportingText = { if (areaError) Text("Add a little more detail, for example an area or landmark") }, isError = areaError, singleLine = true)
            if (photoReference == null) OutlinedButton(onClick = onSelectPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Attach scrap photo") }
            else {
                PhotoAttachmentPreview(photoReference)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo attached for this prototype listing", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onClearPhoto) { Text("Remove") }
                }
            }
            estimate?.let { (min, max) ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Text("Simulated AI price estimate", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) }
                        Text("₹${"%.0f".format(min)}–₹${"%.0f".format(max)} · demo market data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("This is a prototype range, not a guaranteed offer.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(notes, { notes = it.take(1000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 3, maxLines = 4)
        }
    }, confirmButton = { TextButton(onClick = { parsedWeight?.let { value -> onSubmit(HouseholdListingCreateDto(materialCategory = material, estimatedWeight = value, condition = condition, notes = notes.trim().ifBlank { null }, photoReference = photoReference, areaName = area.trim(), estimatedPriceMin = estimate?.first, estimatedPriceMax = estimate?.second)) } }, enabled = canSubmit) { Text("Post listing") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private data class DemoRateRange(val minPerKg: Double, val maxPerKg: Double)

private val demoRateRanges = mapOf(
    "CRT" to DemoRateRange(35.0, 48.0), "LCD_PANEL" to DemoRateRange(95.0, 128.0),
    "PCB" to DemoRateRange(270.0, 355.0), "CABLE" to DemoRateRange(70.0, 96.0),
    "COPPER" to DemoRateRange(570.0, 665.0), "BATTERY" to DemoRateRange(48.0, 72.0),
    "MOTOR" to DemoRateRange(85.0, 120.0), "MAGNET" to DemoRateRange(140.0, 180.0),
    "PLASTIC" to DemoRateRange(20.0, 35.0), "OTHER" to DemoRateRange(15.0, 35.0)
)

private fun demoEstimate(material: String, weightKg: Double, condition: String): Pair<Double, Double> {
    val multiplier = when (condition) { "DAMAGED" -> .8; "PARTIAL" -> .65; else -> 1.0 }
    val range = demoRateRanges[material] ?: demoRateRanges.getValue("OTHER")
    return (weightKg * range.minPerKg * multiplier) to (weightKg * range.maxPerKg * multiplier)
}

@Composable
private fun PhotoAttachmentPreview(path: String) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, contentDescription = "Attached scrap photo", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Crop)
    else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Text("Photo attached locally", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SupplyChainDemoPanel() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(19.dp)); Text("Prototype demo data", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            Text("Seeded/demo values are labelled. Recycler authorization, rates and demand are platform records; they are not government verification or live market research.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text("Offline coverage: captured lots, past payments, and cached formalisation evidence are viewable without a network. A cached signed recycler receipt can queue for retry; new marketplace mutations require connectivity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text("Safety acknowledgements and the material passport are recorded as platform evidence. Confirm weight, grade and payment at the physical handover.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
fun KabadiwalaSupplyScreen(state: SupplyChainState, section: KabadiwalaSection, onRefresh: () -> Unit, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit, onCreateBulk: (BulkLotCreateDto) -> Unit, onCancelBulk: (String) -> Unit, onAcceptOffer: (String) -> Unit, capturedLots: List<Lot> = emptyList(), currentArea: String = "Current area", currentCollectorId: String = "", onRouteEstimate: (String, Double, String) -> Unit = { _, _, _ -> }, onCreatePool: (String, String) -> Unit = { _, _ -> }, onJoinPool: (String, Double, String, Double?) -> Unit = { _, _, _, _ -> }, onLeavePool: (String) -> Unit = {}, onLockPool: (String) -> Unit = {}, onPreparePoolHandover: (String) -> Unit = {}, onPrepareBulkHandover: (String) -> Unit = {}, onConfirmCollectorHandover: (String) -> Unit = {}, onAcknowledgeSafety: (String) -> Unit = {}, onCreateCapturedLot: () -> Unit = {}) {
    var showBulk by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader(when (section) { KabadiwalaSection.HOME -> "Today's collection desk"; KabadiwalaSection.INVENTORY -> "Scrap inventory"; KabadiwalaSection.PICKUPS -> "Household pickups"; KabadiwalaSection.LOTS -> "Recycler sales" }, "Collect from households · aggregate · sell to verified recyclers", Icons.Filled.Inventory2, onRefresh, state.loading) }
        item { SupplyChainDemoPanel() }
        if (section == KabadiwalaSection.HOME) item {
            OutlinedButton(onClick = onCreateCapturedLot, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Inventory2, null)
                Spacer(Modifier.width(8.dp))
                Text("Record a lot offline")
            }
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
                onAcknowledgeSafety = onAcknowledgeSafety
            )
        }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        when (section) {
            KabadiwalaSection.HOME, KabadiwalaSection.PICKUPS -> {
                item { SummaryStrip("${state.pickups.count { it.status == "REQUESTED" }} requests", "${state.pickups.count { it.status == "SCHEDULED" }} scheduled") }
                item { Text("Pickup queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (!state.loading && state.pickups.isEmpty()) item { EmptyPanel("No household pickups", "New requests from households will appear here.") }
                items(state.pickups, key = { it.id }) { pickup -> PickupCard(pickup, state.listings.firstOrNull { it.id == pickup.listingId }, onAccept, onSchedule, onStatus, onComplete) }
            }
            KabadiwalaSection.INVENTORY -> {
                item { InventoryTotals(state.inventory) }
                if (!state.loading && state.inventory.isEmpty()) item { EmptyPanel("Inventory is empty", "Complete a household pickup to add weighed material.") }
                items(state.inventory, key = { it.id }) { InventoryCard(it) }
                item { Button(onClick = { showBulk = true }, enabled = state.inventory.any { it.availableKg > 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(8.dp)); Text("Create bulk lot for recyclers") } }
            }
            KabadiwalaSection.LOTS -> {
                item { Text("Bulk lots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (!state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No bulk lots yet", "Reserve available inventory when you have enough material.") }
                items(state.bulkLots, key = { it.id }) { lot -> BulkLotCard(lot, onCancelBulk) }
                item { Text("Recycler offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Offers on your listed lots will appear here.") }
                items(state.offers, key = { it.id }) { offer -> OfferCard(offer, onAcceptOffer) }
                val visibleCapturedLots = capturedLots.filter { it.status != LotStatus.CANCELLED }
                if (visibleCapturedLots.isNotEmpty()) {
                    item { Text("Captured lot records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item {
                        Text(
                            "These records are visible for continuity. Complete a household pickup and create a recycler bulk lot from inventory to publish material to recyclers.",
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

enum class KabadiwalaSection { HOME, INVENTORY, PICKUPS, LOTS }

@Composable
private fun PickupCard(pickup: PickupRequestDto, listing: HouseholdListingDto?, onAccept: (String) -> Unit, onSchedule: (String, String) -> Unit, onStatus: (String, String) -> Unit, onComplete: (String, PickupCompletionDto) -> Unit) {
    var showComplete by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(listing?.materialCategory ?: "OTHER"), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(pickup.status)) }
            Text("Approx. ${"%.1f".format(listing?.estimatedWeight ?: 0.0)} kg · ${listing?.areaName ?: "Area unavailable"}")
            when (pickup.status) {
                "REQUESTED" -> Button(onClick = { onAccept(pickup.listingId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Accept pickup") }
                "ACCEPTED" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showSchedule = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Schedule") }
                        Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Start now") }
                    }
                }
                "SCHEDULED" -> Button(onClick = { onStatus(pickup.id, "IN_TRANSIT") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Start trip") }
                "IN_TRANSIT" -> Button(onClick = { onStatus(pickup.id, "ARRIVED") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Mark arrived") }
                "ARRIVED" -> Button(onClick = { showComplete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Record weight and complete") }
                "COMPLETED" -> Text("Added to inventory · ${money(pickup.finalAmount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showSchedule) SchedulePickupDialog(
        onDismiss = { showSchedule = false },
        onSubmit = { onSchedule(pickup.id, it); showSchedule = false }
    )
    if (showComplete) CompletionDialog(pickup, onDismiss = { showComplete = false }, onSubmit = { onComplete(pickup.id, it); showComplete = false })
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
                Text("Choose a time window for this household pickup.", style = MaterialTheme.typography.bodyMedium)
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
                Text("The household sees this calculation immediately.", style = MaterialTheme.typography.bodyMedium)
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
            ) { Text("Complete purchase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InventoryCard(item: InventoryBalanceDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${"%.1f".format(item.availableKg)} kg available", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Text("Grade: ${item.grade}"); Text("Reserved ${"%.1f".format(item.reservedKg)} kg · Sold ${"%.1f".format(item.soldKg)} kg · Purchase cost ${money(item.purchaseCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
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
            Text("$syncLabel · This is a captured record, not a recycler bulk lot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun BulkLotCard(lot: BulkLotDto, onCancel: (String) -> Unit) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(lot.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text("${"%.1f".format(lot.quantityKg)} kg · asking ${money(lot.askingRatePerKg)}/kg"); Text("Reserved inventory · ${lot.areaName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (lot.status == "LISTED") OutlinedButton(onClick = { onCancel(lot.id) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel lot and release stock") } } } }
@Composable private fun OfferCard(offer: BulkOfferDto, onAccept: (String) -> Unit) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Recycler offer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${money(offer.offeredRatePerKg)}/kg · ${statusName(offer.status)}"); if (offer.status == "PENDING") Button(onClick = { onAccept(offer.id) }, modifier = Modifier.fillMaxWidth()) { Text("Accept offer") } } } }
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
            Text("Only available inventory can be reserved. Households and other Kabadiwalas cannot buy this lot.")
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
fun RecyclerSupplyScreen(state: SupplyChainState, onRefresh: () -> Unit, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit, onCreateDemand: (ProcurementRequirementCreateDto) -> Unit) {
    var showDemand by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceContainerLow))), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoleHeader("Procure recyclable material", "Browse Kabadiwala bulk lots and publish what your facility needs.", Icons.Filled.Storefront, onRefresh, state.loading) }
        item { SupplyChainDemoPanel() }
        item { Button(onClick = { showDemand = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Publish procurement requirement") } }
        state.error?.let { item { ErrorPanel(it, onRefresh) } }
        item { Text("Available Kabadiwala lots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (!state.loading && state.bulkLots.isEmpty()) item { EmptyPanel("No bulk lots available", "Kabadiwala lots that match your radius will appear here.") }
        items(state.bulkLots, key = { it.id }) { lot -> RecyclerLotCard(lot, state.offers.firstOrNull { it.bulkLotId == lot.id }, onOffer, onReceive) }
        item { Text("Your procurement offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.offers.isEmpty()) item { EmptyPanel("No offers yet", "Make an offer on an available lot to start procurement.") }
        items(state.offers, key = { it.id }) { OfferCard(it, {}) }
        item { Text("Open market demand", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(state.requirements, key = { it.id }) { requirement -> RequirementCard(requirement) }
        item { Text("Cooperative consignments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.pools.isEmpty()) item { EmptyPanel("No pooled consignments yet", "Kabadiwalas can combine reserved stock against your published demand.") }
        items(state.pools, key = { "pool-${it.id}" }) { pool ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("${materialName(pool.materialCategory)} · ${"%.1f".format(pool.totalReservedKg)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${statusName(pool.status)} · ${pool.contributions.size} collector contributions", style = MaterialTheme.typography.bodySmall); Text("The QR handover and per-contribution settlement remain visible to the participating parties.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
        }
        item { Text("Formal handovers awaiting receipt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.handovers.isEmpty()) item { EmptyPanel("No formal handovers", "A Kabadiwala must meet the threshold and confirm the one-time QR before receipt.") }
        items(state.handovers, key = { "supply-${it.id}" }) { handover ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(handover.referenceId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${materialName(handover.materialCategory)} · ${"%.1f".format(handover.quotedWeightKg)} kg · ${statusName(handover.status)}", style = MaterialTheme.typography.bodyMedium); if (handover.status == "COLLECTOR_CONFIRMED") Text("Open Orders → Scan handover QR to record weight, material match and settlement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }
        }
    }
    if (showDemand) DemandDialog(onDismiss = { showDemand = false }, onSubmit = { onCreateDemand(it); showDemand = false })
}

@Composable private fun RecyclerLotCard(lot: BulkLotDto, offer: BulkOfferDto?, onOffer: (String, Double) -> Unit, onReceive: (String) -> Unit) { var showOffer by remember { mutableStateOf(false) }; Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary); Text(materialName(lot.materialCategory), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(lot.status)) }; Text("${"%.1f".format(lot.quantityKg)} kg · ${money(lot.askingRatePerKg)}/kg asking"); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("${lot.areaName} · Kabadiwala supplier", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (offer == null && lot.status == "LISTED") Button(onClick = { showOffer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Make offer") }; if (offer != null) { Text("Your offer: ${money(offer.offeredRatePerKg)}/kg · ${statusName(offer.status)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); if (offer.status == "ACCEPTED") Text("Offer accepted. Await the Kabadiwala's formal QR handover; receipt is blocked until both parties confirm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; if (showOffer) OfferDialog(lot, onDismiss = { showOffer = false }, onSubmit = { onOffer(lot.id, it); showOffer = false }) }
@Composable private fun OfferDialog(lot: BulkLotDto, onDismiss: () -> Unit, onSubmit: (Double) -> Unit) { var rate by remember { mutableStateOf(lot.askingRatePerKg.toString()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Offer on ${materialName(lot.materialCategory)}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${"%.1f".format(lot.quantityKg)} kg · asking ${money(lot.askingRatePerKg)}/kg"); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Your offer · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { rate.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSubmit) }, enabled = rate.toDoubleOrNull()?.let { it > 0 } == true) { Text("Send offer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun DemandDialog(onDismiss: () -> Unit, onSubmit: (ProcurementRequirementCreateDto) -> Unit) { var material by remember { mutableStateOf("PLASTIC") }; var quantity by remember { mutableStateOf("") }; var minimum by remember { mutableStateOf("") }; var radius by remember { mutableStateOf("50") }; var rate by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Publish procurement demand") }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { materials.take(4).forEach { FilterChip(selected = material == it, onClick = { material = it }, label = { Text(materialName(it)) }) } }; OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(9) }, label = { Text("Needed quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(minimum, { minimum = it.filter(Char::isDigit).take(9) }, label = { Text("Minimum lot · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(4) }, label = { Text("Procurement radius · km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Maximum rate · ₹/kg (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { val q = quantity.toDoubleOrNull(); val m = minimum.toDoubleOrNull(); val r = radius.toDoubleOrNull(); if (q != null && m != null && r != null && q > 0 && m > 0 && r > 0) onSubmit(ProcurementRequirementCreateDto(material, m, q, maxRatePerKg = rate.toDoubleOrNull(), procurementRadiusKm = r)) }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true && minimum.toDoubleOrNull()?.let { it > 0 } == true) { Text("Publish demand") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun RequirementCard(item: ProcurementRequirementDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(materialName(item.materialCategory), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusChip(statusName(item.status)) }; Text("Need ${"%.0f".format(item.requiredQuantityKg)} kg · min lot ${"%.0f".format(item.minimumLotKg)} kg"); Text("Within ${"%.0f".format(item.procurementRadiusKm)} km${item.maxRatePerKg?.let { " · up to ₹${"%.0f".format(it)}/kg" } ?: ""}", style = MaterialTheme.typography.bodySmall) } } }

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
    onAcknowledgeSafety: (String) -> Unit
) {
    var routeMaterial by remember { mutableStateOf("CABLE") }
    var routeQuantity by remember { mutableStateOf("10") }
    var showJoin by remember { mutableStateOf<PoolOpportunityDto?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp, 8.dp, 28.dp, 28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text("Formal route desk", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                Text("Turn a weighed pickup into a verified route, cooperative pool and material passport.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("This is platform evidence for the prototype — not a government certificate or guaranteed price.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary); Text("Formal Route Advantage", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text("Compare verified recycler routes using rate, pickup availability and an explicit logistics estimate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary); Text("Cooperative pooling", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text("Reserve only available stock. The threshold and each collector's contribution stay visible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (state.poolOpportunities.isEmpty() && state.pools.isEmpty()) Text("No current open demand is available. Seeded demand appears only when a Recycler publishes it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.passport?.let { passport ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Collector Growth Passport", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${passport.formalHandoverCount} formal handovers · ${"%.1f".format(passport.formalQuantityKg)} kg recorded · ${passport.safetyModulesCompleted} safety modules", style = MaterialTheme.typography.bodyLarge); Text(passport.platformLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall); Text(passport.disclaimer ?: "Platform-generated evidence profile; not an official certification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .78f)) }
            }
        }
        state.safety?.let { safety ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Safety gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); safety.modules.forEach { module -> val acknowledged = safety.progress.any { it.moduleKey == module.key && it.acknowledged }; Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(module.title, fontWeight = FontWeight.SemiBold); Text(module.whatNotToDo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (acknowledged) StatusChip("Acknowledged") else TextButton(onClick = { onAcknowledgeSafety(module.key) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Acknowledge") } } } }
            }
        }
        state.handovers.take(3).forEach { handover -> SupplyHandoverCard(handover, onPrepareBulkHandover, onConfirmCollectorHandover) }
        if (state.showingCachedEvidence && state.cachedAtEpochMs > 0) Text("Showing saved evidence from this device. Mutations stay disabled until the server is reachable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    showJoin?.let { opportunity -> JoinPoolDialog(opportunity, onDismiss = { showJoin = null }, onSubmit = { quantity, grade, rate -> opportunity.existingPool?.id?.let { onJoinPool(it, quantity, grade, rate) }; showJoin = null }) }
}

@Composable private fun RouteEstimateCard(item: RouteAdvantageDto) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.recyclerName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("₹${"%.0f".format(item.estimatedNetValue)} net", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Text("₹${"%.0f".format(item.offeredRatePerKg)}/kg · logistics ₹${"%.0f".format(item.logisticsCost)} · ${item.confidence.lowercase()} confidence", style = MaterialTheme.typography.bodySmall); Text(if (item.advantageValue != null) "Estimated advantage ${"₹%.0f".format(item.advantageValue)}" else "No savings claim without a baseline", style = MaterialTheme.typography.bodySmall); Text(item.whyThisMatch.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (item.isDemo) Text("Seeded/demo reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun PoolActionCard(pool: PooledConsignmentDto, collectorId: String, onJoin: (String, Double, String, Double?) -> Unit, onLeave: (String) -> Unit, onLock: (String) -> Unit, onPrepare: (String) -> Unit) { val mine = pool.contributions.firstOrNull { it.isMine }; Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Pool · ${materialName(pool.materialCategory)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusChip(statusName(pool.status)) }; Text("${"%.1f".format(pool.totalReservedKg)} / ${"%.1f".format(pool.minimumQuantityKg)} kg threshold · ${pool.contributions.size} contributors", style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { if (pool.createdByCollectorId == collectorId && pool.status == "THRESHOLD_MET") OutlinedButton(onClick = { onLock(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Lock threshold") }; if (pool.createdByCollectorId == collectorId && pool.status in setOf("LOCKED", "THRESHOLD_MET")) Button(onClick = { onPrepare(pool.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Prepare QR") }; if (mine != null && pool.status in setOf("FORMING", "THRESHOLD_MET")) TextButton(onClick = { onLeave(pool.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Leave") } } } } }

@Composable private fun JoinPoolDialog(opportunity: PoolOpportunityDto, onDismiss: () -> Unit, onSubmit: (Double, String, Double?) -> Unit) { var quantity by remember { mutableStateOf("") }; var rate by remember { mutableStateOf(opportunity.requirement.maxRatePerKg?.toString().orEmpty()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Join ${materialName(opportunity.requirement.materialCategory)} pool") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Your stock is reserved until the pooled handover is settled.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Quantity · kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Expected rate · ₹/kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { quantity.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSubmit(it, opportunity.requirement.preferredGrade ?: "UNSPECIFIED", rate.toDoubleOrNull()) } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text("Reserve stock") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable private fun SupplyHandoverCard(handover: SupplyHandoverDto, onPrepareBulk: (String) -> Unit, onConfirmCollector: (String) -> Unit) { val qrBitmap = remember(handover.status, handover.qrCodeData) { if (handover.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) handover.qrCodeData?.let(::createSupplyQr) else null }; Surface(shape = RoundedCornerShape(8.dp, 26.dp, 26.dp, 26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("Material passport handover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${handover.referenceId} · ${materialName(handover.materialCategory)} · ${"%.1f".format(handover.quotedWeightKg)} kg", style = MaterialTheme.typography.bodyLarge); Text("Quoted value ₹${"%.0f".format(handover.quotedValue)} · ${statusName(handover.status)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); qrBitmap?.let { Image(it.asImageBitmap(), "One-time handover QR", Modifier.size(190.dp).align(Alignment.CenterHorizontally)) }; if (handover.status == "PREPARED") Button(onClick = { onConfirmCollector(handover.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Confirm collector side") }; if (handover.status == "COLLECTOR_CONFIRMED") Text("Show this one-time QR to the verified Recycler for scan and receipt.", style = MaterialTheme.typography.bodySmall); if (handover.status == "COMPLETED") Text("Receipt completed. The one-time QR is no longer active; inspect the passport timeline for evidence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (handover.status == "REVIEW_REQUIRED") Text("Settlement needs review: ${handover.reviewReason ?: "variance recorded"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } }

private fun createSupplyQr(value: String): Bitmap? = runCatching { val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 480, 480); Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565).also { bitmap -> for (x in 0 until 480) for (y in 0 until 480) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }.getOrNull()

@Composable private fun RoleHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onRefresh: () -> Unit, loading: Boolean) { Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { BoxRule(); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = onRefresh, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(Icons.Filled.Refresh, "Refresh") } } }
@Composable private fun BoxRule() { Spacer(Modifier.height(4.dp)); Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(36.dp).height(4.dp)) {}; Spacer(Modifier.height(8.dp)) }
@Composable private fun SummaryStrip(left: String, right: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(left, fontWeight = FontWeight.Bold); Text(right, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }
@Composable private fun StatusChip(text: String) { Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.tertiaryContainer) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer) } }
@Composable private fun ErrorPanel(text: String, retry: () -> Unit) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = retry) { Text("Retry") } } } }
@Composable private fun LoadingPanel(text: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun EmptyPanel(title: String, detail: String) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .2f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
