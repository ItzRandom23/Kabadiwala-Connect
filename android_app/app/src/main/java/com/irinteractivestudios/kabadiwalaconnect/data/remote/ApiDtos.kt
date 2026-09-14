package com.irinteractivestudios.kabadiwalaconnect.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class ApiErrorEnvelope(val success: Boolean = false, val error: ApiErrorDto? = null, val requestId: String? = null)
data class ApiErrorDto(val code: String? = null, val message: String? = null, val details: JsonObject? = null)
data class ApiEnvelope<T>(val success: Boolean = false, val data: T? = null, val message: String? = null)

// Dedicated role-correct supply-chain DTOs. Dates are ISO-8601 strings at the
// transport boundary; the UI formats them locally and never renders nulls.
data class HouseholdListingCreateDto(val materialCategory: String, val estimatedWeight: Double, val condition: String, val notes: String? = null, val photoReference: String? = null, val areaName: String, val latitude: Double? = null, val longitude: Double? = null, val estimatedPriceMin: Double? = null, val estimatedPriceMax: Double? = null)
data class HouseholdListingDto(val id: String = "", val householdId: String = "", val materialCategory: String = "OTHER", val estimatedWeight: Double = 0.0, val condition: String = "INTACT", val notes: String? = null, val photoReference: String? = null, val areaName: String = "", val latitude: Double? = null, val longitude: Double? = null, val estimatedPriceMin: Double? = null, val estimatedPriceMax: Double? = null, val status: String = "POSTED", val createdAt: String? = null, val updatedAt: String? = null)
data class KabadiwalaProfileDto(val id: String = "", val displayName: String? = null, val areaName: String = "", val latitude: Double? = null, val longitude: Double? = null)
data class PickupRequestCreateDto(val kabadiwalaId: String, val requestedSlot: String? = null)
data class CancellationRequestDto(val reason: String? = null)
data class PickupRequestDto(val id: String = "", val listingId: String = "", val householdId: String = "", val kabadiwalaId: String = "", val status: String = "REQUESTED", val requestedSlot: String? = null, val scheduledSlot: String? = null, val actualWeight: Double? = null, val finalCategory: String? = null, val grade: String? = null, val ratePerKg: Double? = null, val finalAmount: Double? = null, val completedAt: String? = null, val createdAt: String? = null, val updatedAt: String? = null)
data class PickupScheduleDto(val scheduledSlot: String)
data class PickupStatusDto(val status: String)
data class PickupCompletionDto(val actualWeight: Double, val finalCategory: String, val grade: String = "UNSPECIFIED", val ratePerKg: Double)
data class InventoryBalanceDto(val id: String = "", val kabadiwalaId: String = "", val materialCategory: String = "OTHER", val grade: String = "UNSPECIFIED", val availableKg: Double = 0.0, val reservedKg: Double = 0.0, val soldKg: Double = 0.0, val purchaseCost: Double = 0.0, val updatedAt: String? = null)
data class BulkLotCreateDto(val materialCategory: String, val grade: String = "UNSPECIFIED", val quantityKg: Double, val askingRatePerKg: Double, val minimumRatePerKg: Double? = null, val areaName: String, val latitude: Double? = null, val longitude: Double? = null, val notes: String? = null)
data class BulkLotDto(val id: String = "", val kabadiwalaId: String = "", val materialCategory: String = "OTHER", val grade: String = "UNSPECIFIED", val quantityKg: Double = 0.0, val askingRatePerKg: Double = 0.0, val minimumRatePerKg: Double? = null, val areaName: String = "", val latitude: Double? = null, val longitude: Double? = null, val notes: String? = null, val status: String = "LISTED", val reservedForId: String? = null, val createdAt: String? = null, val updatedAt: String? = null)
data class BulkOfferCreateDto(val offeredRatePerKg: Double)
data class BulkOfferDto(val id: String = "", val bulkLotId: String = "", val recyclerId: String = "", val offeredRatePerKg: Double = 0.0, val status: String = "PENDING", val createdAt: String? = null, val updatedAt: String? = null)
data class ProcurementRequirementCreateDto(val materialCategory: String, val minimumLotKg: Double, val requiredQuantityKg: Double, val preferredGrade: String? = null, val maxRatePerKg: Double? = null, val procurementRadiusKm: Double, val deadline: String? = null)
data class ProcurementRequirementDto(val id: String = "", val recyclerId: String = "", val materialCategory: String = "OTHER", val minimumLotKg: Double = 0.0, val requiredQuantityKg: Double = 0.0, val preferredGrade: String? = null, val maxRatePerKg: Double? = null, val procurementRadiusKm: Double = 0.0, val deadline: String? = null, val status: String = "OPEN", val createdAt: String? = null, val updatedAt: String? = null)
data class OtpRequestDto(val phone: String)
data class VerifyOtpRequestDto(
    val phone: String,
    val otp: String,
    val role: String? = null,
    val preferredLanguage: String? = null,
    val areaName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val businessName: String? = null,
    val authorizationNumber: String? = null,
    val materialsAccepted: List<String>? = null,
    val pickupAvailable: Boolean? = null,
    val serviceRadiusKm: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
data class OtpRequestedDto(val message: String? = null)
data class LogoutDto(val loggedOut: Boolean = true)
data class AuthResponseDto(val token: String, val refreshToken: String? = null, val collector: CollectorDto? = null, val user: AccountProfileDto? = null)
data class EmailAuthRequestDto(val email: String, val password: String, val role: String? = null, val preferredLanguage: String? = null, val areaName: String? = null, val businessName: String? = null, val authorizationNumber: String? = null, val materialsAccepted: List<String>? = null, val pickupAvailable: Boolean? = null, val serviceRadiusKm: Int? = null)
data class AccountAuthResponseDto(val token: String, val refreshToken: String? = null, val user: AccountProfileDto)
data class RefreshTokenRequestDto(val refreshToken: String)
data class RefreshTokenResponseDto(val token: String, val refreshToken: String)
data class AdminAuthResponseDto(val token: String, val refreshToken: String? = null, val user: AdminProfileDto)
data class AdminProfileDto(val id: String, val email: String, val displayName: String? = null, val role: String = "ADMIN", val permissions: List<String> = emptyList())
data class AccountProfileDto(val id: String, val email: String? = null, val phone: String? = null, val displayName: String? = null, val areaName: String? = null, val latitude: Double? = null, val longitude: Double? = null, val role: String, val preferredLanguage: String = "ENGLISH", val accountStatus: String = "ACTIVE", val verificationStatus: String = "VERIFIED", val profileId: String = "", val profile: RecyclerDto? = null, val createdAt: String? = null, val updatedAt: String? = null)

data class CollectorDto(val id: String, val phone: String, val email: String? = null, val displayName: String? = null, val preferredLanguage: String? = null, val primaryLocation: LocationDto? = null, val accountStatus: String? = null, val createdAt: String? = null, val lastLoginAt: String? = null)
data class CollectorUpdateDto(val preferredLanguage: String? = null, val primaryLocation: LocationDto? = null, val displayName: String? = null, val email: String? = null)
data class LocationDto(val latitude: Double? = null, val longitude: Double? = null, val areaName: String? = null, val precision: String? = null)

data class CreateLotRequestDto(val materialCategory: String, val materialSubcategory: String? = null, val sourceType: String? = null, val wasteRegime: String? = null, val imageProvenance: String? = null, val condition: String, val weight: Double, val weightUnit: String = "KILOGRAM", val collectionLocation: LocationDto, val notes: String? = null)
data class UpdateLotRequestDto(val weight: Double? = null, val condition: String? = null, val notes: String? = null, val version: Int)
data class LotDto(val id: String, val collectorId: String? = null, val materialCategory: String, val materialSubcategory: String? = null, val sourceType: String? = null, val wasteRegime: String? = null, val condition: String, val weight: Double, val weightUnit: String? = null, val originalWeight: Double? = null, val originalWeightUnit: String? = null, val imageProvenance: String? = null, val imageQualityStatus: String? = null, val photoUrl: String? = null, val estimatedValue: Double? = null, val quotedPrice: Double? = null, val finalPrice: Double? = null, val collectionLocation: LocationDto? = null, val collectionAreaName: String? = null, val status: String, val notes: String? = null, val version: Int = 1, val createdAt: String? = null, val updatedAt: String? = null)
data class PageDto(val page: Int = 1, val limit: Int = 100, val total: Int = 0, val totalPages: Int = 0)
data class LotPageDto(val items: List<LotDto> = emptyList(), val pagination: PageDto = PageDto())

data class PriceBoardDto(val materialCategory: String, val location: String? = null, val priceMin: Double? = null, val priceMax: Double? = null, val marketPrice: Double? = null, val historicalAverage: Double? = null, val unit: String = "KILOGRAM", val source: PriceSourceDto? = null, val qualityStatus: String = "UNVERIFIED", val ingestedAt: String? = null, val complianceRegime: String? = null, val trend: TrendDto? = null, val lastUpdated: String? = null, val disclaimer: String? = null, val available: Boolean = true)
data class PriceSourceDto(val type: String? = null, val organization: String? = null, val reference: String? = null)
data class TrendDto(val direction: String = "STABLE", val percentage: Double = 0.0)
data class PriceHistoryDto(val materialCategory: String, val location: String? = null, val days: Int = 30, val history: List<PricePointDto> = emptyList())
data class PricePointDto(val date: String, val marketPrice: Double, val unit: String = "KILOGRAM", val source: String? = null, val qualityStatus: String? = null)
data class ValuationDto(val lotId: String, val basePricePerKg: Double, val weight: Double, val conditionMultiplier: Double, val qualityAdjustment: Double = 1.0, val estimatedValue: Double, val priceDate: String? = null, val disclaimer: String? = null)

data class RecyclerPageDto(val items: List<RecyclerDto> = emptyList(), val pagination: PageDto = PageDto())
data class RecyclerDto(val id: String, val name: String, val facilityLocation: LocationDto? = null, val authorizationStatus: String? = null, val authorizationDetails: AuthorizationDto? = null, val materialsAccepted: List<RecyclerMaterialDto> = emptyList(), val rates: List<RecyclerRateDto> = emptyList(), val pickupAvailability: String? = null, val serviceArea: ServiceAreaDto? = null, val operatingHours: JsonObject? = null, val averageHandoverTime: String? = null, val rating: Double? = null, val reviewCount: Int = 0, val completedHandovers: Int? = null, val lastUpdated: String? = null, val contact: ContactDto? = null, val distanceKm: Double? = null)
data class AuthorizationDto(
    val authority: String? = null,
    val type: String? = null,
    val registrationNumber: String? = null,
    val evidenceReference: String? = null,
    val verificationSource: String? = null,
    val verifiedBy: String? = null,
    val verifiedAt: String? = null,
    val reviewReason: String? = null,
    val validUntil: String? = null
)
data class RecyclerMaterialDto(val category: String, val subcategories: List<String> = emptyList(), val minAcceptableWeight: Double? = null, val maxAcceptableWeight: Double? = null)
data class RecyclerRateDto(val materialCategory: String, val pricePerKg: Double, val updatedAt: String? = null)
data class RecyclerRateUpdateDto(val materialCategory: String, val pricePerKg: Double)
data class RecyclerRatesUpdateRequestDto(val rates: List<RecyclerRateUpdateDto>)
data class RecyclerProfileUpdateRequestDto(val pickupAvailability: String? = null, val maxPickupDistanceKm: Double? = null, val operatingHours: JsonObject? = null)
data class RecyclerVerificationRequestDto(
    val authority: String,
    val registrationNumber: String,
    val authorizationType: String,
    val evidenceReference: String,
    val verificationSource: String,
    val validUntil: String
)
data class ServiceAreaDto(val maxPickupDistanceKm: Double? = null)
data class ContactDto(val phone: String? = null, val email: String? = null, val alternatePhone: String? = null)
data class RecyclerMatchesDto(val lotId: String, val matches: List<RecyclerMatchDto> = emptyList())
data class RecyclerMatchDto(val recycler: RecyclerDto, val offeredRatePerKg: Double? = null, val matchScore: Int = 0, val explanation: JsonObject? = null)

data class QuoteRequestDto(val lotId: String, val recyclerId: String)
data class QuoteBatchRequestDto(val lotId: String, val recyclerIds: List<String>)
data class RecyclerQuoteRequestDto(val id: String, val lotId: String, val materialCategory: String, val weight: Double, val estimatedValue: Double? = null, val quotedPrice: Double? = null, val collectionLocation: LocationDto? = null, val createdAt: String? = null, val expiresAt: String? = null, val status: String? = null, val lot: RecyclerLotSummaryDto? = null)
data class RecyclerLotSummaryDto(val id: String, val materialCategory: String, val materialSubcategory: String? = null, val condition: String? = null, val weight: Double, val estimatedValue: Double? = null, val quotedPrice: Double? = null, val collectionAreaName: String? = null, val status: String? = null, val createdAt: String? = null)
data class SubmitRecyclerQuoteRequestDto(val quoteRequestId: String, val pricePerKg: Double, val validUntil: String? = null, val recyclerNotes: String? = null)
data class QuoteRequestResponseDto(val id: String? = null, val lotId: String? = null, val recyclerId: String? = null, val status: String? = null)
data class QuoteDto(val id: String, val lotId: String, val recyclerId: String, val pricePerKg: Double, val totalQuotedPrice: Double? = null, val totalPrice: Double? = null, val status: String, val validUntil: String? = null, val createdAt: String? = null, val recycler: RecyclerDto? = null, val recyclerNotes: String? = null, val comparison: String? = null, val anomaly: Boolean = false)

data class TransactionTimelineDto(val schemaVersion: Int = 1, val lotId: String, val status: String, val finalValue: Double? = null, val quotedValue: Double? = null, val events: List<TransactionEventDto> = emptyList())
data class TransactionEventDto(val type: String, val at: String, val status: String, val data: JsonObject = JsonObject())

data class CreateHandoverRequestDto(val lotId: String, val quoteId: String, val clientHandoverId: String? = null, val handoverLocation: HandoverLocationDto, val timestamp: String? = null)
data class HandoverLocationDto(val type: String, val latitude: Double? = null, val longitude: Double? = null, val address: String? = null)
data class HandoverDto(
    val id: String,
    val referenceId: String? = null,
    val lotId: String,
    val quoteId: String? = null,
    val recyclerId: String? = null,
    val collectorId: String? = null,
    val status: String,
    val qrCodeData: String? = null,
    val createdAt: String? = null,
    val timestamp: String? = null,
    val expiresAt: String? = null,
    val weight: Double? = null,
    val quotedPrice: Double? = null,
    val actualWeight: Double? = null,
    val materialCategory: String? = null,
    val materialDescription: String? = null,
    val collectionLocation: LocationDto? = null,
    val handoverLocation: HandoverLocationDto? = null,
    val materialConfirmedAt: String? = null,
    val collectorConfirmedAt: String? = null,
    val recyclerConfirmedAt: String? = null,
    val paymentStatus: String? = null,
    val acceptedRatePerKg: Double? = null,
    val finalAmount: Double? = null,
    val variancePercent: Double? = null
)
data class HandoverEvidenceRequestDto(val actualWeight: Double, val materialMatch: Boolean, val scalePhotoReference: String? = null, val collectorConfirmed: Boolean = true)
data class RecyclerHandoverConfirmRequestDto(val actualWeight: Double, val materialMatch: Boolean, val scalePhotoReference: String? = null, val notes: String? = null, val reason: String? = null)
data class VerifyHandoverRequestDto(val qrCodeData: String)
data class VerifiedHandoverDto(
    val valid: Boolean = false,
    val handoverId: String = "",
    val referenceId: String = "",
    val materialCategory: String = "",
    val declaredWeight: Double = 0.0,
    val actualWeight: Double? = null,
    val timestamp: String? = null,
    val status: String = "",
    val recycler: RecyclerVerificationDto? = null
)
data class RecyclerVerificationDto(val name: String = "", val authorizationStatus: String = "", val authorizationAuthority: String? = null, val licenseNumber: String? = null, val authorizationValidUntil: String? = null)
data class DisputeDto(val id: String, val status: String? = null)

data class RecordPaymentRequestDto(val lotId: String, val amount: Double, val method: String, val date: String, val time: String? = null, val notes: String? = null)
data class PaymentEditRequestDto(val amount: Double, val method: String, val notes: String? = null)
data class PaymentDisputeRequestDto(val reason: String, val description: String)
data class PaymentDto(val id: String, val lotId: String, val handoverId: String? = null, val amount: Double, @SerializedName(value = "method", alternate = ["paymentMethod"]) val method: String, @SerializedName(value = "date", alternate = ["recordedAt"]) val date: String? = null, val status: String? = null)
data class EarningsLedgerDto(val total: Double = 0.0, val pending: Double = 0.0, val currentMonth: Double = 0.0, val averagePerLot: Double = 0.0, @SerializedName(value = "payments", alternate = ["transactions"]) val payments: List<PaymentDto> = emptyList())
data class NotificationDto(val id: String = "", val accountId: String = "", val type: String = "", val title: String = "", val body: String = "", val route: String? = null, val readAt: String? = null, val createdAt: String? = null)
data class UnreadCountDto(val count: Int = 0)
data class NotificationReadDto(val marked: Boolean = false, val count: Int? = null)
data class ActivityChangeSetDto(
    val lotIds: List<String> = emptyList(),
    val quoteIds: List<String> = emptyList(),
    val handoverIds: List<String> = emptyList(),
    val paymentIds: List<String> = emptyList()
)
data class ActivityChangesDto(
    val serverTime: String? = null,
    val notifications: List<NotificationDto> = emptyList(),
    val changed: ActivityChangeSetDto = ActivityChangeSetDto()
)

data class SyncOperationDto(val operationId: String, val operationType: String, val entityType: String, val entityId: String, val payload: JsonObject, val clientCreatedAt: String? = null)
data class SyncBatchRequestDto(val operations: List<SyncOperationDto>)
data class SyncOperationResultDto(val operationId: String, val status: String, val entityType: String? = null, val entityId: String? = null, val errorCode: String? = null)
data class SyncBatchResponseDto(val results: List<SyncOperationResultDto> = emptyList())
data class SyncChangesDto(val serverTime: String? = null, val changes: SyncChangeSetDto = SyncChangeSetDto())
data class SyncChangeSetDto(val lots: List<LotDto> = emptyList(), val payments: List<PaymentDto> = emptyList(), val handovers: List<HandoverDto> = emptyList())

data class PreferencesDto(val preferredLanguage: String = "ENGLISH", val appearanceMode: String = "SYSTEM")
data class PreferencesUpdateDto(val preferredLanguage: String? = null, val appearanceMode: String? = null)
data class RewardProgramDto(val id: String = "", val title: String = "", val description: String = "", val rewardType: String = "", val thresholdKg: Double? = null, val thresholdRupees: Double? = null, val rewardAmount: Double = 0.0, val terms: String = "", val startsAt: String? = null, val endsAt: String? = null)
data class RewardLedgerDto(val id: String = "", val programId: String = "", val periodKey: String = "", val qualifyingKg: Double = 0.0, val qualifyingRupees: Double = 0.0, val rewardAmount: Double = 0.0, val status: String = "PROGRESS", val earnedAt: String? = null, val expiresAt: String? = null, val program: RewardProgramDto? = null)
data class GovernmentSchemeDto(val id: String = "", val slug: String = "", val title: String = "", val description: String = "", val eligibilityRules: JsonObject? = null, val requiredDocuments: List<String> = emptyList(), val sourceUrl: String = "", val lastVerifiedAt: String? = null, val supportedLanguages: List<String> = emptyList())
data class SchemeCheckRequestDto(val schemeId: String, val answers: Map<String, Any?>)
data class SchemeCheckResultDto(val schemeId: String = "", val result: String = "NOT_ENOUGH_INFORMATION", val sourceUrl: String? = null, val lastVerifiedAt: String? = null)
data class DiyActivityDto(val id: String = "", val slug: String = "", val title: String = "", val description: String = "", val materials: List<String> = emptyList(), val steps: List<String> = emptyList(), val safetyWarnings: List<String> = emptyList(), val difficulty: String = "", val minutes: Int = 0, val imageUrl: String? = null)
data class DescriptionSuggestionRequestDto(val lotId: String? = null, val material: String? = null, val condition: String? = null, val weight: Double? = null, val notes: String? = null, val language: String? = null)
data class DescriptionSuggestionDto(val text: String = "", val source: String = "TEMPLATE", val model: String? = null)
data class MaterialSuggestionDto(
    val materialCategory: String = "OTHER",
    val confidence: Double = 0.0,
    val alternatives: List<String> = emptyList(),
    val rationale: String = "",
    val source: String = "TEMPLATE",
    val model: String? = null
)
data class ChatDraftRequestDto(val language: String? = null, val instruction: String? = null)
data class ChatDraftDto(val text: String = "", val source: String = "TEMPLATE", val model: String? = null)
data class RecyclerReviewDto(val id: String = "", val rating: Int = 0, val pickupReliability: Int? = null, val paymentClarity: Int? = null, val comment: String? = null, val createdAt: String? = null, val verified: Boolean = true)
data class SubmitReviewRequestDto(val handoverId: String, val rating: Int, val pickupReliability: Int? = null, val paymentClarity: Int? = null, val comment: String? = null)
data class ConversationDto(val id: String = "", val lotId: String = "", val quoteId: String? = null, val collectorId: String = "", val recyclerId: String = "", val status: String = "OPEN", val lastMessageAt: String? = null)
data class CreateConversationRequestDto(val lotId: String, val quoteId: String? = null, val collectorId: String? = null, val recyclerId: String? = null)
data class ChatMessageDto(val id: String = "", val conversationId: String = "", val senderId: String = "", val senderRole: String = "", val clientMessageId: String = "", val body: String = "", val status: String = "SENT", val createdAt: String? = null, val readAt: String? = null)
data class SendMessageRequestDto(val clientMessageId: String, val body: String)
data class DisputeAnalyticsDto(val months: Int = 6, val total: Int = 0, val byType: Map<String, Int> = emptyMap(), val byStatus: Map<String, Int> = emptyMap(), val byMonth: Map<String, Int> = emptyMap(), val resolutions: Map<String, Int> = emptyMap(), val averageResolutionHours: Double? = null, val insufficientData: Boolean = true)
