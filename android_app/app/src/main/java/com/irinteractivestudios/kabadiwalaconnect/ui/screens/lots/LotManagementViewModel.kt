package com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PriceCatalogRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.util.ConditionMultiplier
import com.irinteractivestudios.kabadiwalaconnect.util.Valuation
import com.irinteractivestudios.kabadiwalaconnect.util.ValuationCalculator
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DescriptionSuggestionRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.MaterialSuggestionDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale

enum class LotStep { PHOTO, MATERIAL, CONDITION, WEIGHT, LOCATION, REVIEW, SAVED }
enum class WeightUnit { KG, GRAMS }
enum class Material(val key: String, val hazardous: Boolean) { CRT("CRT", true), LCD("LCD Panel", false), PCB("PCB / Circuit Board", true), CABLES("Cables", false), COPPER("Copper", false), BATTERY("Battery", true), MOTOR("Motor", false), MAGNET("Magnet", false), PLASTIC("Plastic", false), OTHER("Other", false) }
enum class LotCondition { INTACT, DAMAGED, PARTIAL }

data class LotDraftState(
    val step: LotStep = LotStep.PHOTO,
    val photoPath: String? = null,
    val photoError: String? = null,
    val photoWarning: PhotoValidator.Warning? = null,
    val material: Material? = null,
    val condition: LotCondition? = null,
    val weightText: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weightError: Boolean = false,
    val location: String = "",
    val locationSource: String = "manual",
    val notes: String = "",
    val quotePriceText: String = "",
    val quotePriceError: Boolean = false,
    val descriptionSource: String = "USER",
    val descriptionLoading: Boolean = false,
    val materialSuggestion: MaterialSuggestionDto? = null,
    val materialSuggestionLoading: Boolean = false,
    val materialSuggestionError: Boolean = false,
    val valuation: Valuation? = null,
    val savedLotId: String? = null,
    val isSaving: Boolean = false,
    val saveError: Boolean = false
)

class LotManagementViewModel(
    private val writer: LotWriter,
    private val collectorId: String,
    private val api: ApiService? = null,
    private val priceCatalog: PriceCatalogRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    private val _state = MutableStateFlow(LotDraftState())
    val state: StateFlow<LotDraftState> = _state.asStateFlow()
    private var currentPrices: List<Price> = emptyList()

    init {
        priceCatalog?.let { catalog ->
            viewModelScope.launch {
                catalog.observePrices().collect { prices ->
                    currentPrices = prices
                    _state.value = recalc(_state.value)
                }
            }
        }
    }
    fun photoCaptured(path: String) {
        val result = PhotoValidator.validate(path)
        _state.value = if (result.valid) _state.value.copy(photoPath = path, photoError = null, photoWarning = result.warning, materialSuggestion = null, materialSuggestionError = false, step = LotStep.MATERIAL) else _state.value.copy(photoError = "invalid", photoWarning = null)
    }
    fun demoPhotoCaptured(path: String) {
        photoCaptured(path)
        if (_state.value.photoPath == path) {
            _state.value = _state.value.copy(
                materialSuggestion = MaterialSuggestionDto(
                    materialCategory = "COPPER",
                    confidence = 1.0,
                    rationale = "Demo image recognized as copper.",
                    source = "DEMO"
                ),
                materialSuggestionLoading = false,
                materialSuggestionError = false
            )
            // Demo mode is offline and unauthenticated, so it must not depend
            // on the collector-only Gemini endpoint to complete the flow.
            chooseMaterial(Material.COPPER)
        }
    }
    fun retake() { _state.value = _state.value.copy(step = LotStep.PHOTO, photoError = null) }
    fun goBack() {
        val current = _state.value
        val previous = when (current.step) {
            LotStep.MATERIAL -> LotStep.PHOTO
            LotStep.CONDITION -> LotStep.MATERIAL
            LotStep.WEIGHT -> LotStep.CONDITION
            LotStep.LOCATION -> LotStep.WEIGHT
            LotStep.REVIEW -> LotStep.LOCATION
            LotStep.PHOTO, LotStep.SAVED -> current.step
        }
        _state.value = current.copy(step = previous, saveError = false, isSaving = false)
    }
    fun startOver() { _state.value = LotDraftState() }
    fun chooseMaterial(material: Material) { _state.value = recalc(_state.value.copy(material = material, step = LotStep.CONDITION)) }
    fun applyMaterialSuggestion() {
        val suggested = materialToEnum(_state.value.materialSuggestion?.materialCategory) ?: return
        chooseMaterial(suggested)
    }
    fun suggestMaterial() {
        val current = _state.value
        val path = current.photoPath ?: return
        val service = api ?: return
        if (current.materialSuggestionLoading) return
        _state.value = current.copy(materialSuggestionLoading = true, materialSuggestionError = false)
        viewModelScope.launch {
            val suggestion = runCatching {
                val body = File(path).asRequestBody("image/*".toMediaTypeOrNull())
                service.suggestLotMaterial(okhttp3.MultipartBody.Part.createFormData("photo", File(path).name, body)).requireData()
            }.getOrNull()
            _state.value = _state.value.copy(materialSuggestion = suggestion, materialSuggestionLoading = false, materialSuggestionError = suggestion == null)
        }
    }
    fun chooseCondition(condition: LotCondition) { _state.value = recalc(_state.value.copy(condition = condition, step = LotStep.WEIGHT)) }
    fun setWeight(value: String) { _state.value = recalc(_state.value.copy(weightText = value.filter { it.isDigit() || it == '.' }.take(7), weightError = false)) }
    fun setWeightUnit(unit: WeightUnit) {
        val current = _state.value
        val value = current.weightText.toDoubleOrNull()
        val converted = if (value == null) {
            current.weightText
        } else if (unit == WeightUnit.GRAMS) {
            String.format(Locale.US, "%.0f", if (current.weightUnit == WeightUnit.KG) value * 1000 else value)
        } else {
            String.format(Locale.US, "%.3f", if (current.weightUnit == WeightUnit.GRAMS) value / 1000 else value)
                .trimEnd('0').trimEnd('.')
        }
        _state.value = recalc(current.copy(weightUnit = unit, weightText = converted, weightError = false))
    }
    fun confirmWeight() {
        val value = _state.value.weightKgOrNull()
        _state.value = if (value != null && value > 0 && value <= 500) _state.value.copy(step = LotStep.LOCATION, weightError = false) else _state.value.copy(weightError = true)
    }
    fun setLocation(value: String, source: String = "manual") { _state.value = _state.value.copy(location = value, locationSource = source) }
    fun confirmLocation() { if (_state.value.location.isNotBlank()) { _state.value = _state.value.copy(step = LotStep.REVIEW); suggestDescription() } }
    fun setNotes(value: String) {
        _state.value = _state.value.copy(
            notes = value,
            // Once the collector edits the suggestion, the saved copy is
            // user-authored. This keeps provenance honest in the audit trail.
            descriptionSource = "USER"
        )
    }
    fun setQuotePrice(value: String) {
        _state.value = _state.value.copy(
            quotePriceText = value.filter { it.isDigit() || it == '.' }.take(9),
            quotePriceError = false
        )
    }
    fun suggestDescription() {
        val current = _state.value
        if (current.descriptionLoading) return
        _state.value = current.copy(descriptionLoading = true)
        viewModelScope.launch {
            val result = api?.let { service ->
                runCatching { service.suggestLotDescription(DescriptionSuggestionRequestDto(material = current.material?.key, condition = current.condition?.name, weight = current.weightKgOrNull(), notes = current.notes)).requireData() }.getOrNull()
            }
            val fallback = "${current.condition?.name?.lowercase() ?: "used"} ${current.material?.key ?: "electronic material"} lot${current.weightKgOrNull()?.let { " weighing $it kg" } ?: ""}."
            val suggestion = result?.text?.takeIf { it.isNotBlank() } ?: fallback
            _state.value = _state.value.copy(notes = if (_state.value.notes.isBlank()) suggestion else _state.value.notes, descriptionSource = result?.source ?: "TEMPLATE", descriptionLoading = false)
        }
    }
    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val weight = s.weightKgOrNull()
        if (weight == null || s.material == null || s.condition == null || s.location.isBlank()) {
            _state.value = s.copy(saveError = true)
            return
        }
        val quotePrice = s.quotePriceOrNull()
        if (s.quotePriceText.isNotBlank() && quotePrice == null) {
            _state.value = s.copy(quotePriceError = true)
            return
        }
        val timestamp = now()
        val id = "LOT-$timestamp-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"
        _state.value = s.copy(isSaving = true, saveError = false)
        viewModelScope.launch {
            try {
                writer.save(Lot(id = id, collectorId = collectorId, materialLabel = s.material.key, condition = s.condition.name, weightKg = weight, localPhotoPath = s.photoPath, estimatedValueRupees = s.valuation?.estimatedValue, quoteRupees = quotePrice, location = s.location, createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp, status = LotStatus.SAVED, notes = s.notes, synced = false, sourceType = "FIELD_CAPTURE", wasteRegime = if (s.material.hazardous && s.material == Material.BATTERY) "BATTERY_WASTE" else "E_WASTE", originalWeight = s.weightText.toDoubleOrNull(), originalWeightUnit = s.weightUnit.toBackendUnit(), locationPrecision = "MANUAL"))
                _state.value = _state.value.copy(step = LotStep.SAVED, savedLotId = id, isSaving = false, saveError = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(isSaving = false, saveError = true)
            }
        }
    }

    private fun recalc(state: LotDraftState): LotDraftState {
        val price = state.material?.let { material -> currentPrices.firstOrNull { it.materialLabel == material.key } }
        val weight = state.weightKgOrNull()
        val condition = state.condition?.let { ConditionMultiplier.valueOf(it.name) }
        return if (price != null && weight != null && condition != null) state.copy(valuation = ValuationCalculator.calculate(price.ratePerKg, weight, condition, minPrice = price.minRatePerKg, maxPrice = price.maxRatePerKg)) else state.copy(valuation = null)
    }

    private fun materialToEnum(category: String?): Material? = when (category?.uppercase()) {
        "CRT" -> Material.CRT
        "LCD_PANEL", "LCD" -> Material.LCD
        "PCB" -> Material.PCB
        "CABLE" -> Material.CABLES
        "COPPER" -> Material.COPPER
        "BATTERY" -> Material.BATTERY
        "MOTOR" -> Material.MOTOR
        "MAGNET" -> Material.MAGNET
        "PLASTIC" -> Material.PLASTIC
        "OTHER" -> Material.OTHER
        else -> null
    }
}

private fun WeightUnit.toBackendUnit() = when (this) {
    WeightUnit.KG -> "KILOGRAM"
    WeightUnit.GRAMS -> "GRAM"
}

fun LotDraftState.weightKgOrNull(): Double? {
    val value = weightText.toDoubleOrNull() ?: return null
    return if (weightUnit == WeightUnit.GRAMS) value / 1000.0 else value
}

fun LotDraftState.quotePriceOrNull(): Double? {
    val value = quotePriceText.toDoubleOrNull() ?: return null
    return value.takeIf { it > 0 && it < 1_000_000 }
}
