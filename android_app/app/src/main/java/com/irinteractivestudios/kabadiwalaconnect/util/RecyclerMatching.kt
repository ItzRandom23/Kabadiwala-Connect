package com.irinteractivestudios.kabadiwalaconnect.util

import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler

data class MatchBreakdown(val total: Int, val material: Int, val distance: Int, val availability: Int, val rate: Int, val verified: Int)

object RecyclerMatcher {
    fun score(recycler: Recycler, material: String? = null): MatchBreakdown {
        val materialScore = if (material.isNullOrBlank() || material == "All") 20 else if (recycler.acceptedMaterials.contains(material)) 30 else 0
        val distanceScore = (20 - ((recycler.distanceKm ?: 50.0) / 50.0 * 20)).toInt().coerceIn(0, 20)
        val availabilityScore = if (recycler.pickupAvailable) 15 else 0
        val rateScore = (recycler.offeredRatePerKg.coerceAtMost(400.0) / 400.0 * 15).toInt()
        val verifiedScore = if (recycler.authorized) 20 else 0
        return MatchBreakdown(materialScore + distanceScore + availabilityScore + rateScore + verifiedScore, materialScore, distanceScore, availabilityScore, rateScore, verifiedScore)
    }
}
