package com.irinteractivestudios.kabadiwalaconnect.data.remote

import com.google.gson.JsonObject

data class ApiErrorEnvelope(val success: Boolean = false, val error: ApiErrorDto? = null)
data class ApiErrorDto(val code: String? = null, val message: String? = null)
data class ApiEnvelope<T>(val success: Boolean = false, val data: T? = null, val message: String? = null)
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
data class AuthResponseDto(val token: String, val collector: CollectorDto? = null, val user: AccountProfileDto? = null)
data class EmailAuthRequestDto(val email: String, val password: String, val role: String? = null, val preferredLanguage: String? = null, val areaName: String? = null, val businessName: String? = null, val authorizationNumber: String? = null, val materialsAccepted: List<String>? = null, val pickupAvailable: Boolean? = null, val serviceRadiusKm: Int? = null)
data class AccountAuthResponseDto(val token: String, val user: AccountProfileDto)
data class AccountProfileDto(val id: String, val email: String? = null, val phone: String? = null, val displayName: String? = null, val areaName: String? = null, val latitude: Double? = null, val longitude: Double? = null, val role: String, val preferredLanguage: String = "ENGLISH", val accountStatus: String = "ACTIVE", val verificationStatus: String = "VERIFIED", val profileId: String = "", val profile: RecyclerDto? = null, val createdAt: String? = null, val updatedAt: String? = null)

data class CollectorDto(val id: String, val phone: String, val email: String? = null, val displayName: String? = null, val preferredLanguage: String? = null, val primaryLocation: LocationDto? = null, val accountStatus: String? = null, val createdAt: String? = null, val lastLoginAt: String? = null)
data class CollectorUpdateDto(val preferredLanguage: String? = null, val primaryLocation: LocationDto? = null, val displayName: String? = null, val email: String? = null)
data class LocationDto(val latitude: Double? = null, val longitude: Double? = null, val areaName: String? = null, val precision: String? = null)

data class CreateLotRequestDto(val materialCategory: String, val materialSubcategory: String? = null, val condition: String, val weight: Double, val collectionLocation: LocationDto, val notes: String? = null)
data class UpdateLotRequestDto(val weight: Double? = null, val condition: String? = null, val notes: String? = null, val version: Int)
data class LotDto(val id: String, val collectorId: String? = null, val materialCategory: String, val materialSubcategory: String? = null, val condition: String, val weight: Double, val photoUrl: String? = null, val estimatedValue: Double? = null, val quotedPrice: Double? = null, val finalPrice: Double? = null, val collectionLocation: LocationDto? = null, val collectionAreaName: String? = null, val status: String, val notes: String? = null, val version: Int = 1, val createdAt: String? = null, val updatedAt: String? = null)
data class PageDto(val page: Int = 1, val limit: Int = 100, val total: Int = 0, val totalPages: Int = 0)
data class LotPageDto(val items: List<LotDto> = emptyList(), val pagination: PageDto = PageDto())

data class PriceBoardDto(val materialCategory: String, val location: String? = null, val priceMin: Double, val priceMax: Double, val marketPrice: Double, val historicalAverage: Double? = null, val trend: TrendDto? = null, val lastUpdated: String? = null, val disclaimer: String? = null)
data class TrendDto(val direction: String = "STABLE", val percentage: Double = 0.0)
data class PriceHistoryDto(val materialCategory: String, val location: String? = null, val days: Int = 30, val history: List<PricePointDto> = emptyList())
data class PricePointDto(val date: String, val marketPrice: Double)
data class ValuationDto(val lotId: String, val basePricePerKg: Double, val weight: Double, val conditionMultiplier: Double, val qualityAdjustment: Double = 1.0, val estimatedValue: Double, val priceDate: String? = null, val disclaimer: String? = null)

data class RecyclerPageDto(val items: List<RecyclerDto> = emptyList(), val pagination: PageDto = PageDto())
data class RecyclerDto(val id: String, val name: String, val facilityLocation: LocationDto? = null, val authorizationStatus: String? = null, val authorizationDetails: AuthorizationDto? = null, val materialsAccepted: List<RecyclerMaterialDto> = emptyList(), val rates: List<RecyclerRateDto> = emptyList(), val pickupAvailability: String? = null, val serviceArea: ServiceAreaDto? = null, val operatingHours: JsonObject? = null, val averageHandoverTime: String? = null, val rating: Double? = null, val reviewCount: Int = 0, val completedHandovers: Int? = null, val lastUpdated: String? = null, val contact: ContactDto? = null, val distanceKm: Double? = null)
data class AuthorizationDto(val authority: String? = null, val validUntil: String? = null)
data class RecyclerMaterialDto(val category: String, val subcategories: List<String> = emptyList(), val minAcceptableWeight: Double? = null, val maxAcceptableWeight: Double? = null)
data class RecyclerRateDto(val materialCategory: String, val pricePerKg: Double, val updatedAt: String? = null)
data class ServiceAreaDto(val maxPickupDistanceKm: Double? = null)
data class ContactDto(val phone: String? = null, val email: String? = null, val alternatePhone: String? = null)
data class RecyclerMatchesDto(val lotId: String, val matches: List<RecyclerMatchDto> = emptyList())
data class RecyclerMatchDto(val recycler: RecyclerDto, val offeredRatePerKg: Double? = null, val matchScore: Int = 0, val explanation: JsonObject? = null)

data class QuoteRequestDto(val lotId: String, val recyclerId: String)
data class RecyclerQuoteRequestDto(val id: String, val lotId: String, val materialCategory: String, val weight: Double, val estimatedValue: Double? = null, val collectionLocation: LocationDto? = null, val createdAt: String? = null, val expiresAt: String? = null, val status: String? = null, val lot: RecyclerLotSummaryDto? = null)
data class RecyclerLotSummaryDto(val id: String, val materialCategory: String, val materialSubcategory: String? = null, val condition: String? = null, val weight: Double, val estimatedValue: Double? = null, val collectionAreaName: String? = null, val status: String? = null, val createdAt: String? = null)
data class SubmitRecyclerQuoteRequestDto(val quoteRequestId: String, val pricePerKg: Double, val validUntil: String? = null, val recyclerNotes: String? = null)
data class QuoteRequestResponseDto(val id: String? = null, val lotId: String? = null, val recyclerId: String? = null, val status: String? = null)
data class QuoteDto(val id: String, val lotId: String, val recyclerId: String, val pricePerKg: Double, val totalQuotedPrice: Double? = null, val totalPrice: Double? = null, val status: String, val validUntil: String? = null, val createdAt: String? = null, val recycler: RecyclerDto? = null, val recyclerNotes: String? = null, val comparison: String? = null, val anomaly: Boolean = false)

data class CreateHandoverRequestDto(val lotId: String, val quoteId: String, val handoverLocation: HandoverLocationDto, val timestamp: String? = null)
data class HandoverLocationDto(val type: String, val latitude: Double? = null, val longitude: Double? = null, val address: String? = null)
data class HandoverDto(val id: String, val referenceId: String? = null, val lotId: String, val status: String, val qrPayload: String? = null, val createdAt: String? = null, val weight: Double? = null, val actualWeight: Double? = null, val materialConfirmedAt: String? = null, val recyclerConfirmedAt: String? = null, val paymentStatus: String? = null)
data class HandoverEvidenceRequestDto(val actualWeight: Double, val materialMatch: Boolean, val scalePhotoReference: String? = null, val collectorConfirmed: Boolean = true)
data class RecyclerHandoverConfirmRequestDto(val actualWeight: Double, val materialMatch: Boolean, val scalePhotoReference: String? = null, val notes: String? = null, val reason: String? = null)
data class DisputeDto(val id: String, val status: String? = null)

data class RecordPaymentRequestDto(val lotId: String, val amount: Double, val method: String, val date: String, val time: String? = null, val notes: String? = null)
data class PaymentDto(val id: String, val lotId: String, val handoverId: String? = null, val amount: Double, val method: String, val date: String? = null, val status: String? = null)
data class EarningsLedgerDto(val total: Double = 0.0, val pending: Double = 0.0, val currentMonth: Double = 0.0, val averagePerLot: Double = 0.0, val payments: List<PaymentDto> = emptyList())

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
