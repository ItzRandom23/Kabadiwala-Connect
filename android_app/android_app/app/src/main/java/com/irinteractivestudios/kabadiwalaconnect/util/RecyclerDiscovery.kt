package com.irinteractivestudios.kabadiwalaconnect.util

import com.irinteractivestudios.kabadiwalaconnect.domain.model.Recycler

enum class RecyclerSortMode { PROXIMITY, RATE }
data class RecyclerFilters(val query: String = "", val radiusKm: Int = 50, val material: String = "All", val pickupOnly: Boolean = false, val sort: RecyclerSortMode = RecyclerSortMode.PROXIMITY)

object RecyclerFilterEngine {
    fun apply(items: List<Recycler>, filters: RecyclerFilters): List<Recycler> {
        val query = filters.query.trim().lowercase()
        return items.filter { item ->
            (query.isBlank() || item.name.lowercase().contains(query) || item.area.lowercase().contains(query)) &&
                (item.distanceKm == null || item.distanceKm <= filters.radiusKm) &&
                (filters.material == "All" || item.acceptedMaterials.contains(filters.material)) &&
                (!filters.pickupOnly || item.pickupAvailable)
        }.let { values -> when (filters.sort) { RecyclerSortMode.PROXIMITY -> values.sortedBy { it.distanceKm ?: Double.MAX_VALUE }; RecyclerSortMode.RATE -> values.sortedByDescending { it.offeredRatePerKg } } }
    }
}
