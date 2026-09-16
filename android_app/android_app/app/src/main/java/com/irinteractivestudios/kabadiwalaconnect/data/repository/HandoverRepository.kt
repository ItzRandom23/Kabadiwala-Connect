package com.irinteractivestudios.kabadiwalaconnect.data.repository

import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.Flow

interface HandoverRepository {
    fun observeAll(): Flow<List<Handover>>
    fun observe(id: String): Flow<Handover?>
    suspend fun create(lot: Lot, quote: Quote, collectorId: String, locationType: HandoverLocationType, location: String, timestampEpochMs: Long = System.currentTimeMillis()): Handover
    suspend fun markHandedOver(id: String): Boolean
    suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String? = null): Boolean
}
