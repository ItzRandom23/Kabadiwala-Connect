package com.irinteractivestudios.kabadiwalaconnect.domain.model

enum class AccountRole { COLLECTOR, RECYCLER }

enum class RecyclerVerificationStatus { PENDING, VERIFIED, REJECTED, SUSPENDED }

data class AccountProfile(
    val id: String,
    val email: String,
    val role: AccountRole,
    val preferredLanguage: String = "en",
    val accountStatus: String = "ACTIVE",
    val verificationStatus: RecyclerVerificationStatus = RecyclerVerificationStatus.VERIFIED,
    val profileId: String = id,
    val businessName: String? = null,
    val phoneNumber: String = "",
    val displayName: String? = null,
    val areaName: String? = null
)

/**
 * Domain models for the collector experience.
 *
 * These models are intentionally separate from backend DTOs. Network
 * mappings live in the remote package so local enum names do not become wire
 * contracts, and Room remains usable when the phone is offline.
 */

/** Logged-in collector. Full profile arrives with auth (Phase 2+). */
data class CollectorProfile(
    val id: String = "",
    val phoneNumber: String = "",
    val preferredLanguage: String = "en",
    val primaryLocation: String = "",
    val locationSource: String = "manual",
    val createdAtEpochMs: Long = 0L,
    val lastLoginEpochMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** One digital lot of collected e-waste. Created in Phase 2+. */
enum class LotStatus { DRAFT, SAVED, COLLECTOR_CONFIRMED, PAID, CANCELLED }

data class Lot(
    val id: String,
    val collectorId: String = "",
    val materialLabel: String,
    val condition: String = "",
    val weightKg: Double,
    val localPhotoPath: String? = null,
    val serverPhotoUrl: String? = null,
    val estimatedValueRupees: Double? = null,
    val quoteRupees: Double? = null,
    val finalValueRupees: Double? = null,
    val location: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val status: LotStatus = LotStatus.SAVED,
    val notes: String = "",
    val synced: Boolean = false
)

/** Market price for a material, shown per kg. Priced feed arrives Phase 2+. */
data class Price(
    val id: String,
    val materialLabel: String,
    val ratePerKg: Double,
    val updatedAtEpochMs: Long = 0L,
    val location: String = "Pune",
    val minRatePerKg: Double = ratePerKg,
    val maxRatePerKg: Double = ratePerKg,
    val trend: String = "stable",
    val history: List<Double> = emptyList()
)

/** Authorized recycler. Discovery/quote flow arrives Phase 3+. */
data class Recycler(
    val id: String,
    val name: String,
    val authorized: Boolean = true,
    val distanceKm: Double? = null,
    val area: String = "",
    val facility: String = "",
    val address: String = "",
    val acceptedMaterials: List<String> = emptyList(),
    val offeredRatePerKg: Double = 0.0,
    val pickupAvailable: Boolean = false,
    val operatingHours: String = "",
    val typicalHandoverHours: Int = 24,
    val contactPhone: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val authorizationAuthority: String? = null,
    val authorizationValidUntilEpochMs: Long? = null,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val completedHandovers: Int? = null,
    val lastUpdatedEpochMs: Long? = null
)

/** Recycler quote for a lot. Phase 3+. Placeholder. */
enum class QuoteStatus { DRAFT, PENDING, ACCEPTED, REJECTED, EXPIRED }
enum class QuoteDeliveryState { SAVED_LOCALLY, WAITING_TO_SEND, SENT, RESPONSE_RECEIVED }
data class Quote(
    val id: String = "",
    val recyclerId: String = "",
    val lotId: String = "",
    val amountRupees: Double = 0.0,
    val recyclerName: String = "",
    val pricePerKg: Double = 0.0,
    val marketRatePerKg: Double = 0.0,
    val distanceKm: Double = 0.0,
    val pickupAvailable: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val deliveryState: QuoteDeliveryState = QuoteDeliveryState.SAVED_LOCALLY
)

enum class HandoverLocationType { COLLECTOR_LOCATION, RECYCLER_FACILITY, THIRD_PARTY }
enum class HandoverStatus { SAVED_LOCALLY, HANDED_OVER }

data class Handover(
    val id: String = "",
    val lotId: String = "",
    val recyclerId: String = "",
    val collectorId: String = "",
    val recyclerName: String = "",
    val materialLabel: String = "",
    val weightKg: Double = 0.0,
    val quotedPriceRupees: Double = 0.0,
    val collectionLocation: String = "",
    val handoverLocation: String = "",
    val handoverLocationType: HandoverLocationType = HandoverLocationType.COLLECTOR_LOCATION,
    val timestampEpochMs: Long = 0L,
    val createdAtEpochMs: Long = 0L,
    val quoteId: String = "",
    val status: HandoverStatus = HandoverStatus.SAVED_LOCALLY,
    val synced: Boolean = false,
    val actualWeightKg: Double? = null,
    val materialConfirmed: Boolean = false,
    val collectorConfirmed: Boolean = false,
    val scalePhotoPath: String? = null,
    val evidenceUpdatedAtEpochMs: Long? = null
)

enum class DisputeType { WEIGHT_DISCREPANCY, MATERIAL_MISMATCH, PRICE_DISAGREEMENT, OTHER }
enum class DisputeStatus { SAVED_LOCALLY, OPEN, UNDER_REVIEW, RESOLVED, REJECTED }

data class Dispute(
    val id: String = "",
    val handoverId: String = "",
    val lotId: String = "",
    val collectorId: String = "",
    val recyclerId: String = "",
    val type: DisputeType = DisputeType.OTHER,
    val description: String = "",
    val claimedWeightKg: Double? = null,
    val actualWeightKg: Double? = null,
    val status: DisputeStatus = DisputeStatus.SAVED_LOCALLY,
    val createdAtEpochMs: Long = 0L,
    val synced: Boolean = false,
    val remoteId: String? = null
)

/** A payment (received or pending). Tracking UI arrives Phase 4+. */
enum class PaymentMethod { CASH, BANK_TRANSFER, DIGITAL_WALLET }
enum class PaymentSyncState { SAVED_LOCALLY, WAITING_TO_SYNC }
enum class PaymentRecordState { NORMAL, ALREADY_RECORDED, DISCREPANCY }
data class Payment(val id: String = "", val lotId: String = "", val handoverId: String? = null, val amountRupees: Double = 0.0, val method: PaymentMethod = PaymentMethod.CASH, val paidAtEpochMs: Long = 0L, val notes: String = "", val syncState: PaymentSyncState = PaymentSyncState.SAVED_LOCALLY, val recordState: PaymentRecordState = PaymentRecordState.NORMAL)

/** Earnings summary shown on the Earnings tab (local-only in Phase 1). */
data class EarningsSummary(
    val totalRupees: Double = 0.0,
    val pendingRupees: Double = 0.0,
    val thisMonthRupees: Double = 0.0,
    val averageLotValueRupees: Double = 0.0
)

/**
 * Offline operation waiting in [com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity].
 * Real synchronization runs in a later phase.
 */
enum class SyncOperation {
    CREATE_LOT,
    RECORD_PAYMENT,
    REQUEST_QUOTE,
    CONFIRM_HANDOVER
}
