package com.irinteractivestudios.kabadiwalaconnect.ui.demo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DemoMessage(val sender: String, val body: String)

data class DemoSessionState(
    val householdLotPosted: Boolean = false,
    val selectedKabadiwalaId: String? = null,
    val householdPickupScheduled: Boolean = false,
    val householdItemsCollected: Boolean = false,
    val householdQrCode: String? = null,
    val householdTransactionConfirmed: Boolean = false,
    val householdRating: Int? = null,
    val householdMessages: List<DemoMessage> = listOf(
        DemoMessage("Kabadiwala", "Namaste! I can collect your lot today at 5:30 PM.")
    ),
    val recyclerLotPosted: Boolean = false,
    val selectedRecyclerId: String? = null,
    val recyclerOfferAccepted: Boolean = false,
    val recyclerQrCode: String? = null,
    val recyclerTransactionConfirmed: Boolean = false,
    val recyclerMessages: List<DemoMessage> = listOf(
        DemoMessage("Recycler", "Your lot is matched to our verified facility.")
    )
)

/**
 * Shared local state for the three-role presentation journey.
 *
 * It is deliberately separate from Retrofit, Room and authentication. The
 * demo role switch can therefore show one pre-created household, one
 * Kabadiwala and one verified Recycler without generating 401/404 requests.
 */
object DemoSessionStore {
    const val PRIMARY_KABADIWALA_ID = "demo-kabadiwala-primary"
    const val PRIMARY_RECYCLER_ID = "demo-recycler-primary"

    private const val PREFS = "kabadiwala_demo_session"
    private val lock = Any()
    private var preferences: SharedPreferences? = null
    private val _state = MutableStateFlow(DemoSessionState())
    val state: StateFlow<DemoSessionState> = _state.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return
            preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _state.value = read(preferences!!)
        }
    }

    fun reset() = update { DemoSessionState() }

    fun postHouseholdLot() = update { it.copy(householdLotPosted = true) }

    fun selectKabadiwala(id: String) = update { current ->
        current.copy(
            selectedKabadiwalaId = id,
            householdPickupScheduled = if (id == PRIMARY_KABADIWALA_ID) current.householdPickupScheduled else false,
            householdItemsCollected = if (id == PRIMARY_KABADIWALA_ID) current.householdItemsCollected else false,
            householdQrCode = if (id == PRIMARY_KABADIWALA_ID) current.householdQrCode else null,
            householdTransactionConfirmed = if (id == PRIMARY_KABADIWALA_ID) current.householdTransactionConfirmed else false
        )
    }

    fun scheduleHouseholdPickup() = update { current ->
        if (current.householdLotPosted && current.selectedKabadiwalaId == PRIMARY_KABADIWALA_ID) {
            current.copy(householdPickupScheduled = true)
        } else current
    }

    fun kabadiwalaCollectHouseholdAndPay() = update { current ->
        if (current.householdPickupScheduled && !current.householdItemsCollected) {
            current.copy(
                householdItemsCollected = true,
                householdQrCode = "KC-DEMO-HH-2048"
            )
        } else current
    }

    fun confirmHouseholdQr() = update { current ->
        if (current.householdItemsCollected && !current.householdTransactionConfirmed) {
            current.copy(householdTransactionConfirmed = true, householdQrCode = null)
        } else current
    }

    fun rateKabadiwala(rating: Int) = update { current ->
        if (current.householdTransactionConfirmed) current.copy(householdRating = rating.coerceIn(1, 5)) else current
    }

    fun addHouseholdMessage(sender: String, body: String) = addMessage(
        household = true,
        sender = sender,
        body = body
    )

    fun postRecyclerLot() = update { current ->
        current.copy(recyclerLotPosted = true)
    }

    fun selectRecycler(id: String) = update { current ->
        current.copy(
            selectedRecyclerId = id,
            recyclerOfferAccepted = if (id == PRIMARY_RECYCLER_ID) current.recyclerOfferAccepted else false,
            recyclerQrCode = if (id == PRIMARY_RECYCLER_ID) current.recyclerQrCode else null,
            recyclerTransactionConfirmed = if (id == PRIMARY_RECYCLER_ID) current.recyclerTransactionConfirmed else false
        )
    }

    fun recyclerAcceptOffer() = update { current ->
        if (current.recyclerLotPosted && current.selectedRecyclerId == PRIMARY_RECYCLER_ID) {
            current.copy(recyclerOfferAccepted = true)
        } else current
    }

    fun kabadiwalaHandOverRecyclerAndPay() = update { current ->
        if (current.recyclerOfferAccepted && current.recyclerQrCode == null) {
            current.copy(recyclerQrCode = "KC-DEMO-RC-4096")
        } else current
    }

    fun confirmRecyclerQr() = update { current ->
        if (current.recyclerQrCode != null && !current.recyclerTransactionConfirmed) {
            current.copy(recyclerTransactionConfirmed = true, recyclerQrCode = null)
        } else current
    }

    fun addRecyclerMessage(sender: String, body: String) = addMessage(
        household = false,
        sender = sender,
        body = body
    )

    private fun addMessage(household: Boolean, sender: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        update { current ->
            val next = DemoMessage(sender, trimmed)
            if (household) current.copy(householdMessages = current.householdMessages + next)
            else current.copy(recyclerMessages = current.recyclerMessages + next)
        }
    }

    private fun update(transform: (DemoSessionState) -> DemoSessionState) {
        synchronized(lock) {
            val next = transform(_state.value)
            _state.value = next
            preferences?.let { write(next) }
        }
    }

    private fun read(prefs: SharedPreferences): DemoSessionState {
        val householdMessages = readMessages(prefs, "household", DemoSessionState().householdMessages)
        val recyclerMessages = readMessages(prefs, "recycler", DemoSessionState().recyclerMessages)
        return DemoSessionState(
            householdLotPosted = prefs.getBoolean("householdLotPosted", false),
            selectedKabadiwalaId = prefs.getString("selectedKabadiwalaId", null),
            householdPickupScheduled = prefs.getBoolean("householdPickupScheduled", false),
            householdItemsCollected = prefs.getBoolean("householdItemsCollected", false),
            householdQrCode = prefs.getString("householdQrCode", null),
            householdTransactionConfirmed = prefs.getBoolean("householdTransactionConfirmed", false),
            householdRating = prefs.getInt("householdRating", -1).takeIf { it in 1..5 },
            householdMessages = householdMessages,
            recyclerLotPosted = prefs.getBoolean("recyclerLotPosted", false),
            selectedRecyclerId = prefs.getString("selectedRecyclerId", null),
            recyclerOfferAccepted = prefs.getBoolean("recyclerOfferAccepted", false),
            recyclerQrCode = prefs.getString("recyclerQrCode", null),
            recyclerTransactionConfirmed = prefs.getBoolean("recyclerTransactionConfirmed", false),
            recyclerMessages = recyclerMessages
        )
    }

    private fun write(state: DemoSessionState) {
        val editor = preferences?.edit() ?: return
        editor.putBoolean("householdLotPosted", state.householdLotPosted)
            .putString("selectedKabadiwalaId", state.selectedKabadiwalaId)
            .putBoolean("householdPickupScheduled", state.householdPickupScheduled)
            .putBoolean("householdItemsCollected", state.householdItemsCollected)
            .putString("householdQrCode", state.householdQrCode)
            .putBoolean("householdTransactionConfirmed", state.householdTransactionConfirmed)
            .putInt("householdRating", state.householdRating ?: -1)
            .putBoolean("recyclerLotPosted", state.recyclerLotPosted)
            .putString("selectedRecyclerId", state.selectedRecyclerId)
            .putBoolean("recyclerOfferAccepted", state.recyclerOfferAccepted)
            .putString("recyclerQrCode", state.recyclerQrCode)
            .putBoolean("recyclerTransactionConfirmed", state.recyclerTransactionConfirmed)
        writeMessages(editor, "household", state.householdMessages)
        writeMessages(editor, "recycler", state.recyclerMessages)
        editor.apply()
    }

    private fun readMessages(prefs: SharedPreferences, prefix: String, fallback: List<DemoMessage>): List<DemoMessage> {
        val count = prefs.getInt("${prefix}MessageCount", -1)
        if (count < 0) return fallback
        return (0 until count).map {
            DemoMessage(
                prefs.getString("${prefix}Message${it}Sender", "") ?: "",
                prefs.getString("${prefix}Message${it}Body", "") ?: ""
            )
        }.filter { it.body.isNotBlank() }
    }

    private fun writeMessages(editor: SharedPreferences.Editor, prefix: String, messages: List<DemoMessage>) {
        editor.putInt("${prefix}MessageCount", messages.size)
        messages.forEachIndexed { index, message ->
            editor.putString("${prefix}Message${index}Sender", message.sender)
                .putString("${prefix}Message${index}Body", message.body)
        }
    }
}
