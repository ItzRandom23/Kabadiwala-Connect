package com.irinteractivestudios.kabadiwalaconnect.ui.demo

import androidx.annotation.DrawableRes
import com.irinteractivestudios.kabadiwalaconnect.R

/** Presentation-only fixtures for the debug judge journey. They never cross a repository/API boundary. */
data class Money(val rupees: Double) {
    fun formatted(): String = "₹${rupees.toInt().toString().reversed().chunked(3).joinToString(",").reversed()}"
}

data class MaterialPresentation(
    val key: String,
    val label: String,
    val ratePerKg: Money,
    @get:DrawableRes val imageRes: Int
)

data class LotPresentation(
    val id: String,
    val material: MaterialPresentation,
    val weightKg: Double,
    val estimatedValue: Money,
    val location: String,
    val condition: String
)

data class QuotePresentation(
    val recyclerName: String,
    val amount: Money,
    val distanceKm: Double,
    val pickupAvailable: Boolean
)

data class LifecycleEvent(val label: String, val completed: Boolean)

enum class SyncState { SAVED_ON_DEVICE, WAITING_TO_SYNC, SYNCING, SYNCED, SYNC_FAILED }
enum class DataFreshness { LIVE, CACHED, STALE, UNAVAILABLE }

data class DemoScenario(
    val collectorName: String,
    val location: String,
    val todayEarnings: Money,
    val todayDelta: Money,
    val copper: MaterialPresentation,
    val featuredLot: LotPresentation,
    val quotes: List<QuotePresentation>,
    val lifecycle: List<LifecycleEvent>,
    val freshness: DataFreshness = DataFreshness.CACHED
)

object DemoDataProvider {
    val scenario = DemoScenario(
        collectorName = "Pulkit",
        location = "Pune",
        todayEarnings = Money(2450.0),
        todayDelta = Money(680.0),
        copper = MaterialPresentation("COPPER", "Copper", Money(620.0), R.drawable.kc_copper),
        featuredLot = LotPresentation("LOT-248", MaterialPresentation("COPPER", "Copper", Money(620.0), R.drawable.kc_copper), 12.4, Money(7688.0), "Kothrud, Pune", "Good"),
        quotes = listOf(
            QuotePresentation("Prithvi Circulars", Money(7500.0), 2.4, true),
            QuotePresentation("GreenLoop Materials", Money(7688.0), 4.1, true),
            QuotePresentation("Nirmal Metals", Money(7550.0), 3.0, false)
        ),
        lifecycle = listOf(
            LifecycleEvent("Lot posted", true),
            LifecycleEvent("Quotes received", true),
            LifecycleEvent("Recycler confirmed", false),
            LifecycleEvent("Handover", false),
            LifecycleEvent("Payment received", false)
        )
    )

    fun reset(): DemoScenario = scenario
}
