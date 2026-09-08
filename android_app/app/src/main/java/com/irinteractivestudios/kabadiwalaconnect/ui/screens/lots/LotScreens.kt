package com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EmptyContent
import com.irinteractivestudios.kabadiwalaconnect.ui.components.EvidenceSection
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcPrimaryButton
import com.irinteractivestudios.kabadiwalaconnect.ui.components.PermissionRationaleDialog
import com.irinteractivestudios.kabadiwalaconnect.ui.components.ProofRow
import com.irinteractivestudios.kabadiwalaconnect.ui.components.WorkflowProgress
import com.irinteractivestudios.kabadiwalaconnect.util.FeaturePermission
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LotRoute(vm: LotManagementViewModel, onSafety: () -> Unit = {}, onHome: () -> Unit = {}, demoMode: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gpsSaved = stringResource(R.string.lot_gps_saved)
    var pendingPath by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok && pendingPath != null) vm.photoCaptured(pendingPath!!) }
    val launchCamera = {
        val dir = File(context.filesDir, "lot_photos").apply { mkdirs() }
        val file = File(dir, "lot_${System.currentTimeMillis()}.jpg")
        pendingPath = file.absolutePath
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    var showCameraRationale by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val requestCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else showCameraRationale = true
    }
    val ioScope = androidx.compose.runtime.rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "lot_photos").apply { mkdirs() }
            val path = File(dir, "gallery_${System.currentTimeMillis()}.image").absolutePath
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(path).outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read selected image")
                path
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) { vm.photoCaptured(copied ?: path) }
        }
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setLocation(if (granted) gpsSaved else "", if (granted) "gps" else "manual") }
    
    val useDemoPhoto: (() -> Unit)? = if (demoMode) {
        {
            val file = File(context.filesDir, "demo_copper.webp")
            if (!file.exists()) context.resources.openRawResource(R.raw.kc_copper).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            vm.demoPhotoCaptured(file.absolutePath)
        }
    } else null
    LotScreen(state, vm, onTakePhoto = requestCamera, onSelectPhoto = { gallery.launch("image/*") }, onRequestLocation = { location.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, onSafety = onSafety, onHome = onHome, onUseDemoPhoto = useDemoPhoto)
    if (showCameraRationale) {
        PermissionRationaleDialog(
            permission = FeaturePermission.CAMERA,
            onAllow = { showCameraRationale = false; cameraPermission.launch(Manifest.permission.CAMERA) },
            onDismiss = { showCameraRationale = false }
        )
    }
}

@Composable
fun LotScreen(state: LotDraftState, vm: LotManagementViewModel, onTakePhoto: () -> Unit, onRequestLocation: () -> Unit, onSafety: () -> Unit = {}, onHome: () -> Unit = {}, onUseDemoPhoto: (() -> Unit)? = null, onSelectPhoto: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, state.step) {
        if (imeBottom > 0) {
            delay(200)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.step != LotStep.SAVED) {
            WorkflowProgress(
                current = state.step.ordinal + 1,
                total = 7,
                label = stringResource(R.string.lot_step, state.step.ordinal + 1, 7)
            )
            if (state.step != LotStep.PHOTO) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = vm::goBack,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("lot_back")
                    ) { Text(stringResource(R.string.common_back)) }
                    OutlinedButton(
                        onClick = vm::startOver,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("lot_start_over")
                    ) { Text(stringResource(R.string.lot_start_over)) }
                }
            }
        }
        when (state.step) {
            LotStep.PHOTO -> PhotoStep(state, vm, onTakePhoto, onSelectPhoto, onHome, onUseDemoPhoto)
            LotStep.MATERIAL -> MaterialStep(state, vm, onSafety)
            LotStep.CONDITION -> ConditionStep(state, vm)
            LotStep.WEIGHT -> WeightStep(state, vm)
            LotStep.LOCATION -> LocationStep(state, vm, onRequestLocation)
            LotStep.REVIEW -> ReviewStep(state, vm)
            LotStep.SAVED -> SavedStep(state, onHome)
        }
    }
}

@Composable private fun PhotoStep(s: LotDraftState, vm: LotManagementViewModel, take: () -> Unit, select: () -> Unit, onHome: () -> Unit, useDemoPhoto: (() -> Unit)?) {
    Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(58.dp))
    Text(stringResource(R.string.lot_photo_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.lot_photo_detail), style = MaterialTheme.typography.bodyLarge)
    s.photoPath?.let { path -> val bitmap = remember(path) { decodeSampledBitmap(path) }; if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop) }
    s.photoError?.let { Text(stringResource(R.string.lot_photo_error), color = MaterialTheme.colorScheme.error) }
    s.photoWarning?.let { warning ->
        Text(
            stringResource(
                when (warning) {
                    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.TOO_DARK -> R.string.lot_photo_too_dark
                    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.TOO_BRIGHT -> R.string.lot_photo_too_bright
                    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.LOW_DETAIL -> R.string.lot_photo_low_detail
                }
            ),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    KcPrimaryButton(stringResource(if (s.photoPath == null) R.string.lot_take_photo else R.string.lot_retake), take, icon = Icons.Filled.CameraAlt, testTag = "lot_take_photo")
    OutlinedButton(onClick = select, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_choose_photo")) { Icon(Icons.Filled.CropSquare, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lot_choose_photo)) }
    if (useDemoPhoto != null && s.photoPath == null) OutlinedButton(onClick = useDemoPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_demo_photo")) { Icon(Icons.Filled.Recycling, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lot_use_demo_photo)) }
    if (s.photoPath != null) OutlinedButton(onClick = { vm.photoCaptured(s.photoPath) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_confirm_photo")) { Text(stringResource(R.string.lot_confirm_photo)) }
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("lot_cancel_to_home")) { Text(stringResource(R.string.lot_cancel)) }
}
@Composable private fun MaterialStep(s: LotDraftState, vm: LotManagementViewModel, onSafety: () -> Unit) {
    val context = LocalContext.current
    val tts = remember(context) { TextToSpeech(context) { } }
    val safetyAudioText = stringResource(materialSafetyAudioRes(s.material ?: Material.OTHER))
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    Text(stringResource(R.string.lot_material_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.lot_material_detail), style = MaterialTheme.typography.bodyLarge)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lot_material_suggest_detail), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = vm::suggestMaterial,
                enabled = s.photoPath != null && !s.materialSuggestionLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (s.materialSuggestionLoading) R.string.lot_material_suggest_loading else R.string.lot_material_suggest))
            }
            s.materialSuggestion?.let { suggestion ->
                val confidence = (suggestion.confidence.coerceIn(0.0, 1.0) * 100).toInt()
                val suggestedLabel = when (suggestion.materialCategory.uppercase()) {
                    "CRT" -> stringResource(R.string.lot_material_crt)
                    "LCD_PANEL", "LCD" -> stringResource(R.string.lot_material_lcd)
                    "PCB" -> stringResource(R.string.lot_material_pcb)
                    "CABLE" -> stringResource(R.string.lot_material_cables)
                    "COPPER" -> stringResource(R.string.lot_material_copper)
                    "BATTERY" -> stringResource(R.string.lot_material_battery)
                    "MOTOR" -> stringResource(R.string.lot_material_motor)
                    "MAGNET" -> stringResource(R.string.lot_material_magnet)
                    "PLASTIC" -> stringResource(R.string.lot_material_plastic)
                    else -> stringResource(R.string.lot_material_other)
                }
                Text(stringResource(R.string.lot_material_suggested, suggestedLabel, confidence), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (suggestion.rationale.isNotBlank()) Text(suggestion.rationale, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::applyMaterialSuggestion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.lot_material_use_suggestion)) }
            }
            if (s.materialSuggestionError) Text(stringResource(R.string.lot_material_suggest_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    s.photoWarning?.let { warning ->
        Text(
            stringResource(photoWarningRes(warning)),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (s.material?.hazardous == true) {
        OutlinedButton(onClick = onSafety, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lot_safety_open)) }
OutlinedButton(onClick = { tts.speak(safetyAudioText, TextToSpeech.QUEUE_FLUSH, null, "lot-safety") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.safety_hear)) }
    }
    Material.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { material ->
                val selected = material == s.material
                Card(
                    onClick = { vm.chooseMaterial(material) },
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 100.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            material.hazardous -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(materialIcon(material), contentDescription = stringResource(materialLabelRes(material)), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                        Text(stringResource(materialLabelRes(material)), style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                        Text(if (material.hazardous) stringResource(R.string.lot_hazard) else stringResource(R.string.lot_non_hazard), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
@Composable private fun ConditionStep(s: LotDraftState, vm: LotManagementViewModel) {
    Text(stringResource(R.string.lot_condition_title), style = MaterialTheme.typography.headlineMedium)
    LotCondition.entries.forEach { condition -> FilterChip(selected = s.condition == condition, onClick = { vm.chooseCondition(condition) }, label = { Text(stringResource(conditionLabelRes(condition))) }, leadingIcon = { Icon(Icons.Filled.Category, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_condition_${condition.name}")) }
}
@Composable private fun WeightStep(s: LotDraftState, vm: LotManagementViewModel) {
    Text(stringResource(R.string.lot_weight_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.lot_weight_detail), style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = s.weightUnit == WeightUnit.KG,
            onClick = { vm.setWeightUnit(WeightUnit.KG) },
            label = { Text(stringResource(R.string.lot_weight_unit_kg)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = s.weightUnit == WeightUnit.GRAMS,
            onClick = { vm.setWeightUnit(WeightUnit.GRAMS) },
            label = { Text(stringResource(R.string.lot_weight_unit_grams)) },
            modifier = Modifier.weight(1f)
        )
    }
    OutlinedTextField(
        value = s.weightText,
        onValueChange = vm::setWeight,
        label = { Text(stringResource(R.string.lot_weight_label)) },
        suffix = { Text(if (s.weightUnit == WeightUnit.GRAMS) "g" else "kg") },
        textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = s.weightError,
        supportingText = { Text(if (s.weightError) stringResource(R.string.lot_weight_error) else stringResource(R.string.lot_weight_slider_hint)) },
        modifier = Modifier.fillMaxWidth().testTag("lot_weight")
    )
    val sliderMax = if (s.weightUnit == WeightUnit.GRAMS) 10_000f else 500f
    val value = s.weightText.toFloatOrNull()?.coerceIn(0f, sliderMax) ?: 0f
    Slider(
        value = value,
        onValueChange = { next ->
            vm.setWeight(
                if (s.weightUnit == WeightUnit.GRAMS) next.roundToInt().toString()
                else String.format(Locale.US, "%.1f", next)
            )
        },
        valueRange = 0f..sliderMax,
        steps = 99,
        modifier = Modifier.testTag("lot_weight_slider")
    )
    KcPrimaryButton(stringResource(R.string.lot_next), vm::confirmWeight, icon = Icons.Filled.CheckCircle, testTag = "lot_weight_next")
}
@Composable private fun LocationStep(s: LotDraftState, vm: LotManagementViewModel, gps: () -> Unit) {
    Text(stringResource(R.string.lot_location_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.lot_location_detail), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(s.location, { vm.setLocation(it) }, label = { Text(stringResource(R.string.lot_area_label)) }, leadingIcon = { Icon(Icons.Filled.EditLocation, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("lot_location"))
    OutlinedButton(onClick = gps, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lot_use_gps)) }
    KcPrimaryButton(stringResource(R.string.lot_confirm_location), vm::confirmLocation, icon = Icons.Filled.CheckCircle, enabled = s.location.isNotBlank(), testTag = "lot_location_next")
}
@Composable private fun ReviewStep(s: LotDraftState, vm: LotManagementViewModel) {
    Text(stringResource(R.string.lot_review_title), style = MaterialTheme.typography.headlineMedium)
    s.photoPath?.let { path -> decodeSampledBitmap(path)?.let { Image(it, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) } }
    EvidenceSection(title = stringResource(R.string.lot_review_title)) {
        ProofRow(stringResource(R.string.lot_material_label), s.material?.key.orEmpty())
        ProofRow(stringResource(R.string.lot_condition_label), s.condition?.name.orEmpty())
        ProofRow(stringResource(R.string.lot_weight_label), "${s.weightText} ${if (s.weightUnit == WeightUnit.GRAMS) "g" else "kg"}")
        ProofRow(stringResource(R.string.lot_area_label), s.location)
    }
    s.valuation?.let { valuation ->
        EvidenceSection(title = stringResource(R.string.lot_value_placeholder), status = stringResource(R.string.quote_saved)) {
                Text(stringResource(R.string.lot_value_placeholder), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.lot_price_range, valuation.typicalMin, valuation.typicalMax), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.lot_estimate_basis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lot_estimate_warning), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text(stringResource(R.string.lot_estimate_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = s.quotePriceText,
        onValueChange = vm::setQuotePrice,
        label = { Text(stringResource(R.string.lot_user_price_label)) },
        supportingText = { Text(if (s.quotePriceError) stringResource(R.string.lot_user_price_error) else stringResource(R.string.lot_user_price_detail)) },
        leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = s.quotePriceError,
        modifier = Modifier.fillMaxWidth().testTag("lot_user_price")
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.lot_description_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = vm::suggestDescription, enabled = !s.descriptionLoading) { Text(if (s.descriptionLoading) stringResource(R.string.lot_description_generating) else stringResource(R.string.lot_description_refresh)) }
    }
    OutlinedTextField(s.notes, vm::setNotes, label = { Text(stringResource(R.string.lot_description_label)) }, supportingText = { Text(if (s.descriptionSource == "AI") stringResource(R.string.lot_description_ai) else stringResource(R.string.lot_description_fallback)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
    if (s.saveError) Text(stringResource(R.string.lot_save_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    KcPrimaryButton(
        stringResource(if (s.isSaving) R.string.lot_saving else R.string.lot_save),
        vm::save,
        icon = Icons.Filled.CheckCircle,
        enabled = !s.isSaving,
        testTag = "lot_save"
    )
}
@Composable private fun ReviewRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.titleMedium) } }
@Composable private fun SavedStep(s: LotDraftState, onHome: () -> Unit) {
    EvidenceSection(title = stringResource(R.string.lot_saved_title), status = stringResource(R.string.quote_saved)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Text(stringResource(R.string.lot_saved_id, s.savedLotId.orEmpty()), style = MaterialTheme.typography.bodyLarge)
            KcPrimaryButton(stringResource(R.string.lot_back_home), onHome, icon = Icons.Filled.CheckCircle, testTag = "lot_back_home")
        }
    }
}

@Composable fun LotsScreen(lots: List<Lot>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (lots.isEmpty()) EmptyContent(modifier)
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.padding(16.dp)) { items(lots, key = { it.id }) { lot ->
        Surface(
            onClick = { onOpen(lot.id) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().testTag("lot_${lot.id}")
        ) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(46.dp)) { Icon(Icons.Filled.Recycling, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(lot.materialLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.lot_weight_value, lot.weightKg.toString()), style = MaterialTheme.typography.bodyMedium); lot.estimatedValueRupees?.let { Text(stringResource(R.string.lot_estimated_value, it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }; Text(lot.status.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    } }
}

@Composable fun LotDetailScreen(lot: Lot, onCancel: () -> Unit, onRepeat: () -> Unit = {}) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { lot.localPhotoPath?.let { decodeSampledBitmap(it)?.let { image -> Image(image, null, Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Crop) } }; Text(lot.materialLabel, style = MaterialTheme.typography.headlineMedium); ReviewRow(stringResource(R.string.lot_condition_label), lot.condition); ReviewRow(stringResource(R.string.lot_weight_label), stringResource(R.string.lot_weight_value, lot.weightKg.toString())); ReviewRow(stringResource(R.string.lot_area_label), lot.location); lot.estimatedValueRupees?.let { Text(stringResource(R.string.lot_estimated_value, it), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }; lot.quoteRupees?.let { ReviewRow(stringResource(R.string.lot_user_price_label), "₹%.0f".format(it)) }; Text(stringResource(R.string.lot_estimate_disclaimer), style = MaterialTheme.typography.bodyMedium); Text(stringResource(R.string.lot_timeline), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.lot_created_timeline), style = MaterialTheme.typography.bodyLarge); if (lot.status == LotStatus.PAID) OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_repeat")) { Text(stringResource(R.string.lot_repeat)) }; if (lot.status == LotStatus.SAVED) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lot_cancel")) { Text(stringResource(R.string.lot_cancel)) } } }

private fun decodeSampledBitmap(path: String, maxDimension: Int = 1200): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    })?.asImageBitmap()
}

private fun materialLabelRes(material: Material) = when (material) { Material.CRT -> R.string.lot_material_crt; Material.LCD -> R.string.lot_material_lcd; Material.PCB -> R.string.lot_material_pcb; Material.CABLES -> R.string.lot_material_cables; Material.COPPER -> R.string.lot_material_copper; Material.BATTERY -> R.string.lot_material_battery; Material.MOTOR -> R.string.lot_material_motor; Material.MAGNET -> R.string.lot_material_magnet; Material.PLASTIC -> R.string.lot_material_plastic; Material.OTHER -> R.string.lot_material_other }
private fun conditionLabelRes(condition: LotCondition) = when (condition) { LotCondition.INTACT -> R.string.lot_condition_intact; LotCondition.DAMAGED -> R.string.lot_condition_damaged; LotCondition.PARTIAL -> R.string.lot_condition_partial }
private fun materialIcon(material: Material) = when (material) { Material.BATTERY -> Icons.Filled.BatteryAlert; Material.CABLES, Material.COPPER -> Icons.Filled.Cable; Material.PCB -> Icons.Filled.Memory; Material.MOTOR -> Icons.Filled.Motorcycle; Material.CRT, Material.LCD -> Icons.Filled.CropSquare; Material.MAGNET, Material.PLASTIC, Material.OTHER -> Icons.Filled.Category }
private fun materialSafetyAudioRes(material: Material) = when (material) {
    Material.BATTERY -> R.string.safety_battery_audio
    Material.CRT -> R.string.safety_crt_audio
    Material.PCB, Material.MOTOR, Material.OTHER -> R.string.safety_other_audio
    else -> R.string.safety_message
}

private fun photoWarningRes(warning: com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning) = when (warning) {
    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.TOO_DARK -> R.string.lot_photo_too_dark
    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.TOO_BRIGHT -> R.string.lot_photo_too_bright
    com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator.Warning.LOW_DETAIL -> R.string.lot_photo_low_detail
}
