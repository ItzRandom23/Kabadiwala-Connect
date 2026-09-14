package com.irinteractivestudios.kabadiwalaconnect.data.local

import android.content.Context
import com.google.gson.Gson
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkLotDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.BulkOfferDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.CollectorPassportDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PoolOpportunityDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PooledConsignmentDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RouteAdvantageResponseDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SafetyResponseDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SupplyHandoverDto

/**
 * Small account-scoped cache for the formalisation dashboard. It intentionally
 * stores only server evidence and display state, never access tokens or
 * private household addresses. A cached result is always labelled in the UI.
 */
data class FormalisationSnapshot(
    val routeAdvantage: RouteAdvantageResponseDto? = null,
    val poolOpportunities: List<PoolOpportunityDto> = emptyList(),
    val pools: List<PooledConsignmentDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val handovers: List<SupplyHandoverDto> = emptyList(),
    val passport: CollectorPassportDto? = null,
    val safety: SafetyResponseDto? = null,
    val cachedAtEpochMs: Long = 0L
)

class FormalisationCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("formalisation_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(accountId: String?): FormalisationSnapshot? {
        if (accountId.isNullOrBlank()) return null
        val raw = prefs.getString(key(accountId), null) ?: return null
        return runCatching { gson.fromJson(raw, FormalisationSnapshot::class.java) }.getOrNull()
    }

    fun save(accountId: String?, snapshot: FormalisationSnapshot) {
        if (accountId.isNullOrBlank()) return
        prefs.edit().putString(key(accountId), gson.toJson(snapshot.copy(cachedAtEpochMs = System.currentTimeMillis()))).apply()
    }

    fun clear(accountId: String?) {
        if (!accountId.isNullOrBlank()) prefs.edit().remove(key(accountId)).apply()
    }

    private fun key(accountId: String) = "account:$accountId"
}
