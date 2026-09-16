package com.irinteractivestudios.kabadiwalaconnect.ui.demo

import android.content.Context
import androidx.annotation.DrawableRes
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Price
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus

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
    /**
     * Stable, presentation-only price data. Demo screens must never depend on
     * a populated Room catalog or an available backend.
     */
    fun prices(context: Context): List<Price> = listOf(
        Price(
            id = "demo-pune-copper",
            materialLabel = context.getString(R.string.demo_price_material_copper),
            ratePerKg = 620.0,
            minRatePerKg = 585.0,
            maxRatePerKg = 650.0,
            updatedAtEpochMs = System.currentTimeMillis(),
            location = context.getString(R.string.demo_pune),
            trend = "up",
            trendPercentage = 3.2,
            history = listOf(570.0, 578.0, 582.0, 590.0, 604.0, 612.0, 620.0),
            source = context.getString(R.string.demo_price_source),
            qualityStatus = "VERIFIED",
            disclaimer = context.getString(R.string.demo_price_disclaimer)
        ),
        Price(
            id = "demo-pune-pcb",
            materialLabel = context.getString(R.string.demo_price_material_pcb),
            ratePerKg = 340.0,
            minRatePerKg = 300.0,
            maxRatePerKg = 380.0,
            updatedAtEpochMs = System.currentTimeMillis(),
            location = context.getString(R.string.demo_pune),
            trend = "stable",
            history = listOf(332.0, 338.0, 335.0, 342.0, 340.0, 341.0, 340.0),
            source = context.getString(R.string.demo_price_source),
            qualityStatus = "VERIFIED",
            disclaimer = context.getString(R.string.demo_price_disclaimer)
        ),
        Price(
            id = "demo-pune-plastic",
            materialLabel = context.getString(R.string.demo_price_material_plastic),
            ratePerKg = 48.0,
            minRatePerKg = 42.0,
            maxRatePerKg = 55.0,
            updatedAtEpochMs = System.currentTimeMillis(),
            location = context.getString(R.string.demo_pune),
            trend = "down",
            trendPercentage = 1.4,
            history = listOf(52.0, 51.0, 50.0, 50.0, 49.0, 48.0, 48.0),
            source = context.getString(R.string.demo_price_source),
            qualityStatus = "VERIFIED",
            disclaimer = context.getString(R.string.demo_price_disclaimer)
        )
    )

    fun profile(role: AccountRole): AccountProfile = when (role) {
        AccountRole.HOUSEHOLD -> AccountProfile(
            id = "demo-household",
            profileId = "demo-household",
            email = "demo.household@kabadiwala.example",
            role = role,
            displayName = "Aarohi Sharma",
            phoneNumber = "+91 98765 43210",
            areaName = "Kothrud, Pune"
        )
        AccountRole.COLLECTOR -> AccountProfile(
            id = "demo-kabadiwala",
            profileId = "demo-kabadiwala",
            email = "demo.kabadiwala@kabadiwala.example",
            role = role,
            displayName = "Pulkit Kabadiwala",
            phoneNumber = "+91 98765 12345",
            areaName = "Kothrud, Pune"
        )
        AccountRole.RECYCLER -> AccountProfile(
            id = "demo-recycler",
            profileId = "demo-recycler",
            email = "demo.recycler@kabadiwala.example",
            role = role,
            displayName = "GreenLoop Materials",
            businessName = "GreenLoop Materials",
            phoneNumber = "+91 98220 45678",
            areaName = "Bhosari, Pune",
            verificationStatus = RecyclerVerificationStatus.VERIFIED
        )
        AccountRole.ADMIN -> AccountProfile(
            id = "demo-admin",
            profileId = "demo-admin",
            email = "demo.operator@kabadiwala.example",
            role = role,
            displayName = "Demo Operator"
        )
    }

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
