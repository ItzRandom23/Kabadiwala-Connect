package com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator
import com.irinteractivestudios.kabadiwalaconnect.data.local.MockPriceData
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

enum class LotStep { PHOTO, MATERIAL, CONDITION, WEIGHT, LOCATION, REVIEW, SAVED }
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
    val weightError: Boolean = false,
    val location: String = "",
    val locationSource: String = "manual",
    val notes: String = "",
    val descriptionSource: String = "USER",
    val descriptionLoading: Boolean = false,
    val materialSuggestion: MaterialSuggestionDto? = null,
    val materialSuggestionLoading: Boolean = false,
    val materialSuggestionError: Boolean = false,
    val valuation: Valuation? = null,
    val savedLotId: String? = null
)

class LotManagementViewModel(
    private val writer: LotWriter,
    private val collectorId: String,
    private val api: ApiService? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    private val _state = MutableStateFlow(LotDraftState())
    val state: StateFlow<LotDraftState> = _state.asStateFlow()
    fun photoCaptured(path: String) {
        val result = PhotoValidator.validate(path)
        _state.value = if (result.valid) _state.value.copy(photoPath = path, photoError = null, photoWarning = result.warning, materialSuggestion = null, materialSuggestionError = false, step = LotStep.MATERIAL) else _state.value.copy(photoError = "invalid", photoWarning = null)
    }
    fun retake() { _state.value = _state.value.copy(step = LotStep.PHOTO, photoError = null) }
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
    fun confirmWeight() { val value = _state.value.weightText.toDoubleOrNull(); _state.value = if (value != null && value > 0 && value < 500) _state.value.copy(step = LotStep.LOCATION, weightError = false) else _state.value.copy(weightError = true) }
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
    fun suggestDescription() {
        val current = _state.value
        if (current.descriptionLoading) return
        _state.value = current.copy(descriptionLoading = true)
        viewModelScope.launch {
            val result = api?.let { service ->
                runCatching { service.suggestLotDescription(DescriptionSuggestionRequestDto(material = current.material?.key, condition = current.condition?.name, weight = current.weightText.toDoubleOrNull(), notes = current.notes)).requireData() }.getOrNull()
            }
            val fallback = "${current.condition?.name?.lowercase() ?: "used"} ${current.material?.key ?: "electronic material"} lot${current.weightText.toDoubleOrNull()?.let { " weighing $it kg" } ?: ""}."
            val suggestion = result?.text?.takeIf { it.isNotBlank() } ?: fallback
            _state.value = _state.value.copy(notes = if (_state.value.notes.isBlank()) suggestion else _state.value.notes, descriptionSource = result?.source ?: "TEMPLATE", descriptionLoading = false)
        }
    }
    fun save() {
        val s = _state.value
        val weight = s.weightText.toDoubleOrNull() ?: return
        val timestamp = now()
        val id = "LOT-$timestamp-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"
        viewModelScope.launch {
            writer.save(Lot(id, collectorId, s.material?.key.orEmpty(), s.condition?.name.orEmpty(), weight, s.photoPath, null, s.valuation?.estimatedValue, null, null, s.location, timestamp, timestamp, LotStatus.SAVED, s.notes, false))
            _state.value = s.copy(step = LotStep.SAVED, savedLotId = id)
        }
    }

    private fun recalc(state: LotDraftState): LotDraftState {
        val price = state.material?.let { material -> MockPriceData.all.firstOrNull { it.materialLabel == material.key } }
        val weight = state.weightText.toDoubleOrNull()
        val condition = state.condition?.let { ConditionMultiplier.valueOf(it.name) }
        return if (price != null && weight != null && condition != null) state.copy(valuation = ValuationCalculator.calculate(price.ratePerKg, weight, condition, minPrice = price.minRatePerKg, maxPrice = price.maxRatePerKg)) else state.copy(valuation = null)
    }

    private fun materialToEnum(category: String?): Material? = when (category?.uppercase()) {
        "CRT" -> Material.CRT
        "LCD_PANEL", "LCD" -> Material.LCD
        "PCB" -> Material.PCB
        "CABLE" -> Material.CABLES
        "BATTERY" -> Material.BATTERY
        "MOTOR" -> Material.MOTOR
        "MAGNET" -> Material.MAGNET
        "PLASTIC" -> Material.PLASTIC
        "OTHER" -> Material.OTHER
        else -> null
    }
}
