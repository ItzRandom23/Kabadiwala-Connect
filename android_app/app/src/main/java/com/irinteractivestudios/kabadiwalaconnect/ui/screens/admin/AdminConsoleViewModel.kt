package com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class AdminSection(val label: String) {
    RECYCLERS("Recycler review"),
    PARTNERS("Pilot partners"),
    DISPUTES("Disputes"),
    PAYMENTS("Payments"),
    ANOMALIES("Anomalies"),
    TOOLS("Data tools")
}

data class AdminConsoleState(
    val section: AdminSection = AdminSection.RECYCLERS,
    val items: List<JsonObject> = emptyList(),
    val selected: JsonObject? = null,
    val loading: Boolean = false,
    val actionBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class AdminConsoleViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AdminConsoleState())
    val state: StateFlow<AdminConsoleState> = _state.asStateFlow()

    fun selectSection(section: AdminSection) {
        _state.update { it.copy(section = section, items = emptyList(), selected = null, error = null, message = null) }
        if (section != AdminSection.TOOLS) refresh()
    }

    fun refresh() {
        val section = _state.value.section
        if (section == AdminSection.TOOLS) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            runCatching {
                when (section) {
                    AdminSection.RECYCLERS -> api.adminRecyclerQueue("PENDING").requireData()
                    AdminSection.PARTNERS -> api.adminKabadiwalaCohort("PENDING").requireData()
                    AdminSection.DISPUTES -> api.adminDisputes("OPEN").requireData()
                    AdminSection.PAYMENTS -> {
                        val recorded = api.adminPayments("PENDING").requireData()
                        val pickupPayments = api.adminHouseholdPickupPayments("RECORDED").requireData()
                        recorded + pickupPayments
                    }
                    AdminSection.ANOMALIES -> api.adminFormalAnomalies("OPEN").requireData()
                    AdminSection.TOOLS -> emptyList()
                }
            }.onSuccess { data ->
                _state.update { it.copy(loading = false, items = data) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = userFacingError(error, "Could not load operator data")) }
            }
        }
    }

    fun select(item: JsonObject) { _state.update { it.copy(selected = item) } }
    fun clearSelection() { _state.update { it.copy(selected = null) } }

    fun authorizeRecycler(
        recyclerId: String,
        status: String,
        reason: String?,
        authority: String?,
        registrationNumber: String?,
        authorizationType: String?,
        evidenceReference: String?,
        verificationSource: String?,
        validUntil: String?
    ) = action {
        val body = JsonObject().apply {
            addProperty("status", status)
            reason?.takeIf { it.isNotBlank() }?.let { addProperty("reason", it) }
            authority?.takeIf { it.isNotBlank() }?.let { addProperty("authority", it) }
            registrationNumber?.takeIf { it.isNotBlank() }?.let { addProperty("registrationNumber", it) }
            authorizationType?.takeIf { it.isNotBlank() }?.let { addProperty("authorizationType", it) }
            evidenceReference?.takeIf { it.isNotBlank() }?.let { addProperty("evidenceReference", it) }
            verificationSource?.takeIf { it.isNotBlank() }?.let { addProperty("verificationSource", it) }
            validUntil?.takeIf { it.isNotBlank() }?.let { addProperty("validUntil", it) }
        }
        api.adminAuthorizeRecycler(recyclerId, body).requireData()
    }

    fun resolveDispute(disputeId: String, resolution: String, notes: String?) = action {
        val body = JsonObject().apply {
            addProperty("resolution", resolution)
            notes?.takeIf { it.isNotBlank() }?.let { addProperty("notes", it) }
        }
        api.adminResolveDispute(disputeId, body).requireData()
    }

    fun approveKabadiwala(kabadiwalaId: String, notes: String) = action {
        api.adminVerifyKabadiwala(kabadiwalaId, JsonObject().apply {
            addProperty("decision", "APPROVE")
            addProperty("notes", notes)
        }).requireData()
    }

    fun verifyPayment(paymentId: String) = action {
        val pickupPayment = _state.value.items.firstOrNull { it.get("id")?.asString == paymentId }
            ?.get("kind")?.asString == "HOUSEHOLD_PICKUP_SETTLEMENT"
        if (pickupPayment) api.adminReconcileHouseholdPickupPayment(paymentId, JsonObject().apply { addProperty("decision", "VERIFY") }).requireData()
        else api.adminVerifyPayment(paymentId, JsonObject()).requireData()
    }

    fun disputePickupPayment(paymentId: String, notes: String) = action {
        api.adminReconcileHouseholdPickupPayment(paymentId, JsonObject().apply {
            addProperty("decision", "DISPUTE")
            addProperty("notes", notes)
        }).requireData()
    }

    fun reversePayment(paymentId: String, reason: String, provider: String?, externalReference: String?, evidenceReference: String?) = action {
        val body = JsonObject().apply {
            addProperty("reason", reason)
            provider?.takeIf { it.isNotBlank() }?.let { addProperty("provider", it) }
            externalReference?.takeIf { it.isNotBlank() }?.let { addProperty("externalReference", it) }
            evidenceReference?.takeIf { it.isNotBlank() }?.let { addProperty("evidenceReference", it) }
        }
        api.adminReversePayment(paymentId, body).requireData()
    }

    fun resolveAnomaly(flagId: String, resolutionAction: String, resolution: String, evidenceReference: String?) = action {
        val body = JsonObject().apply {
            addProperty("action", resolutionAction)
            addProperty("resolution", resolution)
            evidenceReference?.takeIf { it.isNotBlank() }?.let { addProperty("evidenceReference", it) }
        }
        api.adminResolveFormalAnomaly(flagId, body).requireData()
    }

    fun importPrice(
        externalId: String,
        materialCategory: String,
        city: String,
        priceMin: Double,
        priceMax: Double,
        marketPrice: Double,
        sourceOrganization: String,
        sourceReference: String
    ) = action {
        val row = JsonObject().apply {
            addProperty("externalId", externalId)
            addProperty("materialCategory", materialCategory)
            addProperty("city", city)
            addProperty("priceMin", priceMin)
            addProperty("priceMax", priceMax)
            addProperty("marketPrice", marketPrice)
            addProperty("unit", "KG")
            addProperty("sourceOrganization", sourceOrganization)
            addProperty("sourceReference", sourceReference)
            addProperty("effectiveAt", utcNow())
        }
        val body = JsonObject().apply { add("rows", com.google.gson.JsonArray().apply { add(row) }) }
        api.adminImportPrices(body).requireData()
    }

    fun updatePrice(priceId: String, priceMin: Double, priceMax: Double, marketPrice: Double, reason: String) = action {
        val body = JsonObject().apply {
            addProperty("priceMin", priceMin)
            addProperty("priceMax", priceMax)
            addProperty("marketPrice", marketPrice)
            addProperty("reason", reason)
        }
        api.adminUpdatePrice(priceId, body).requireData()
    }

    fun exportDataset() = action { api.adminExportDataset().requireData() }

    private fun action(block: suspend () -> Any?) {
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true, error = null, message = null) }
            runCatching { block() }
                .onSuccess {
                    _state.update { it.copy(actionBusy = false, message = "Operation completed") }
                    refresh()
                }
                .onFailure { error -> _state.update { it.copy(actionBusy = false, error = userFacingError(error, "Operation failed")) } }
        }
    }

    private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}
