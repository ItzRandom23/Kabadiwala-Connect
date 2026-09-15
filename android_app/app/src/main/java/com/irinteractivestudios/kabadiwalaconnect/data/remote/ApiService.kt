package com.irinteractivestudios.kabadiwalaconnect.data.remote

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Versioned transport contract for the backend in ../backend.
 *
 * DTOs stay at the network boundary. Local Room models and Compose state do
 * not serialize backend enum names directly, so a server contract change is
 * isolated to this package and its mappers.
 */
interface ApiService {
    @GET("health")
    suspend fun getHealth(): Response<ApiEnvelope<JsonObject>>

    // Explicit supply-chain contract. These endpoints are separate from the
    // legacy collector-to-recycler lot/quote APIs below.
    @POST("household/listings")
    suspend fun createHouseholdListing(@Body body: HouseholdListingCreateDto, @Header("Idempotency-Key") idempotencyKey: String? = null): Response<ApiEnvelope<HouseholdListingDto>>
    @GET("household/listings")
    suspend fun getHouseholdListings(): Response<ApiEnvelope<List<HouseholdListingDto>>>
    @GET("household/listings/{listingId}")
    suspend fun getHouseholdListing(@Path("listingId") listingId: String): Response<ApiEnvelope<JsonObject>>
    @PATCH("household/listings/{listingId}")
    suspend fun updateHouseholdListing(@Path("listingId") listingId: String, @Body body: HouseholdListingUpdateDto): Response<ApiEnvelope<HouseholdListingDto>>
    @GET("household/listings/{listingId}/passport")
    suspend fun getHouseholdListingPassport(@Path("listingId") listingId: String): Response<ApiEnvelope<HouseholdPassportResponseDto>>
    @GET("household/kabadiwalas")
    suspend fun getHouseholdKabadiwalas(): Response<ApiEnvelope<List<KabadiwalaProfileDto>>>
    @POST("household/listings/{listingId}/pickups")
    suspend fun requestHouseholdPickup(@Path("listingId") listingId: String, @Body body: PickupRequestCreateDto, @Header("Idempotency-Key") idempotencyKey: String? = null): Response<ApiEnvelope<PickupRequestDto>>
    @GET("household/pickups")
    suspend fun getHouseholdPickups(): Response<ApiEnvelope<List<PickupRequestDto>>>
    @GET("household/pickups/{pickupId}")
    suspend fun getHouseholdPickup(@Path("pickupId") pickupId: String): Response<ApiEnvelope<JsonObject>>
    @GET("household/pickups/{pickupId}/passport")
    suspend fun getHouseholdPickupPassport(@Path("pickupId") pickupId: String): Response<ApiEnvelope<JsonObject>>
    @POST("household/pickups/{pickupId}/reschedule")
    suspend fun rescheduleHouseholdPickup(@Path("pickupId") pickupId: String, @Body body: PickupRescheduleDto): Response<ApiEnvelope<PickupRequestDto>>
    @POST("household/pickups/{pickupId}/settlement")
    suspend fun decideHouseholdSettlement(@Path("pickupId") pickupId: String, @Body body: SettlementDecisionDto): Response<ApiEnvelope<PickupRequestDto>>
    @POST("household/listings/{listingId}/cancel")
    suspend fun cancelHouseholdListing(@Path("listingId") listingId: String, @Body body: CancellationRequestDto = CancellationRequestDto()): Response<ApiEnvelope<JsonObject>>
    @POST("household/pickups/{pickupId}/cancel")
    suspend fun cancelHouseholdPickup(@Path("pickupId") pickupId: String, @Body body: CancellationRequestDto = CancellationRequestDto()): Response<ApiEnvelope<JsonObject>>
    @GET("kabadiwala/listings")
    suspend fun getKabadiwalaListings(): Response<ApiEnvelope<List<HouseholdListingDto>>>
    @GET("kabadiwala/pickups")
    suspend fun getKabadiwalaPickups(): Response<ApiEnvelope<List<PickupRequestDto>>>
    @POST("kabadiwala/listings/{listingId}/accept")
    suspend fun acceptHouseholdListing(@Path("listingId") listingId: String): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/reject")
    suspend fun rejectKabadiwalaPickup(@Path("pickupId") pickupId: String, @Body body: BulkOfferDecisionDto = BulkOfferDecisionDto()): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/confirm-availability")
    suspend fun confirmPickupAvailability(@Path("pickupId") pickupId: String, @Body body: PickupAvailabilityDto = PickupAvailabilityDto()): Response<ApiEnvelope<PickupRequestDto>>
    @POST("kabadiwala/pickups/{pickupId}/schedule")
    suspend fun schedulePickup(@Path("pickupId") pickupId: String, @Body body: PickupScheduleDto): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/status")
    suspend fun updatePickupStatus(@Path("pickupId") pickupId: String, @Body body: PickupStatusDto): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/cancel")
    suspend fun cancelKabadiwalaPickup(@Path("pickupId") pickupId: String, @Body body: CancellationRequestDto = CancellationRequestDto()): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/reassign")
    suspend fun reassignPickup(@Path("pickupId") pickupId: String, @Body body: PickupReassignDto): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pickups/{pickupId}/complete")
    suspend fun completePickup(@Path("pickupId") pickupId: String, @Body body: PickupCompletionDto): Response<ApiEnvelope<PickupRequestDto>>
    @GET("kabadiwala/inventory")
    suspend fun getKabadiwalaInventory(): Response<ApiEnvelope<List<InventoryBalanceDto>>>
    @GET("kabadiwala/inventory/movements")
    suspend fun getInventoryMovements(@Query("materialCategory") materialCategory: String? = null, @Query("limit") limit: Int = 100): Response<ApiEnvelope<List<InventoryMovementDto>>>
    @POST("kabadiwala/bulk-lots")
    suspend fun createBulkLot(@Body body: BulkLotCreateDto): Response<ApiEnvelope<BulkLotDto>>
    @GET("kabadiwala/bulk-lots")
    suspend fun getKabadiwalaBulkLots(): Response<ApiEnvelope<List<BulkLotDto>>>
    @POST("kabadiwala/bulk-lots/{lotId}/cancel")
    suspend fun cancelBulkLot(@Path("lotId") lotId: String): Response<ApiEnvelope<JsonObject>>
    @GET("kabadiwala/bulk-offers")
    suspend fun getKabadiwalaBulkOffers(): Response<ApiEnvelope<List<BulkOfferDto>>>
    @GET("recycler/bulk-lots")
    suspend fun getRecyclerBulkLots(): Response<ApiEnvelope<List<BulkLotDto>>>
    @GET("recycler/bulk-lots/{lotId}")
    suspend fun getRecyclerBulkLot(@Path("lotId") lotId: String): Response<ApiEnvelope<BulkLotDto>>
    @POST("recycler/bulk-lots/{lotId}/offers")
    suspend fun makeBulkLotOffer(@Path("lotId") lotId: String, @Body body: BulkOfferCreateDto): Response<ApiEnvelope<BulkOfferDto>>
    @GET("recycler/offers")
    suspend fun getRecyclerBulkOffers(): Response<ApiEnvelope<List<BulkOfferDto>>>
    @POST("recycler/offers/{offerId}/withdraw")
    suspend fun withdrawRecyclerOffer(@Path("offerId") offerId: String, @Body body: BulkOfferDecisionDto = BulkOfferDecisionDto()): Response<ApiEnvelope<JsonObject>>
    @GET("kabadiwala/procurement-requirements")
    suspend fun getProcurementRequirements(): Response<ApiEnvelope<List<ProcurementRequirementDto>>>
    @POST("kabadiwala/bulk-offers/{offerId}/accept")
    suspend fun acceptBulkOffer(@Path("offerId") offerId: String): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/bulk-offers/{offerId}/reject")
    suspend fun rejectBulkOffer(@Path("offerId") offerId: String, @Body body: BulkOfferDecisionDto = BulkOfferDecisionDto()): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/bulk-offers/{offerId}/counter")
    suspend fun counterBulkOffer(@Path("offerId") offerId: String, @Body body: BulkOfferCounterDto): Response<ApiEnvelope<JsonObject>>
    @POST("recycler/bulk-lots/{lotId}/receive")
    suspend fun receiveBulkLot(@Path("lotId") lotId: String): Response<ApiEnvelope<JsonObject>>
    @POST("recycler/procurement-requirements")
    suspend fun createProcurementRequirement(@Body body: ProcurementRequirementCreateDto): Response<ApiEnvelope<ProcurementRequirementDto>>
    @PATCH("recycler/procurement-requirements/{requirementId}")
    suspend fun updateProcurementRequirement(@Path("requirementId") requirementId: String, @Body body: ProcurementRequirementUpdateDto): Response<ApiEnvelope<ProcurementRequirementDto>>
    @GET("recycler/procurement-requirements")
    suspend fun getRecyclerProcurementRequirements(): Response<ApiEnvelope<List<ProcurementRequirementDto>>>
    @GET("kabadiwala/route-advantage")
    suspend fun getRouteAdvantage(@Query("materialCategory") materialCategory: String, @Query("quantityKg") quantityKg: Double, @Query("grade") grade: String = "UNSPECIFIED", @Query("areaName") areaName: String? = null): Response<ApiEnvelope<RouteAdvantageResponseDto>>
    @GET("kabadiwala/pool-opportunities")
    suspend fun getPoolOpportunities(): Response<ApiEnvelope<List<PoolOpportunityDto>>>
    @GET("kabadiwala/pools/suggestions")
    suspend fun getPoolSuggestions(): Response<ApiEnvelope<List<PoolSuggestionDto>>>
    @POST("kabadiwala/pools")
    suspend fun createPool(@Body body: PoolCreateRequestDto): Response<ApiEnvelope<PooledConsignmentDto>>
    @GET("kabadiwala/pools")
    suspend fun getKabadiwalaPools(): Response<ApiEnvelope<List<PooledConsignmentDto>>>
    @POST("kabadiwala/pools/{poolId}/join")
    suspend fun joinPool(@Path("poolId") poolId: String, @Body body: PoolJoinRequestDto): Response<ApiEnvelope<PoolContributionDto>>
    @POST("kabadiwala/pools/{poolId}/leave")
    suspend fun leavePool(@Path("poolId") poolId: String): Response<ApiEnvelope<JsonObject>>
    @POST("kabadiwala/pools/{poolId}/lock")
    suspend fun lockPool(@Path("poolId") poolId: String): Response<ApiEnvelope<PooledConsignmentDto>>
    @GET("recycler/pools")
    suspend fun getRecyclerPools(): Response<ApiEnvelope<List<PooledConsignmentDto>>>
    @GET("kabadiwala/demand-intelligence")
    suspend fun getDemandIntelligence(): Response<ApiEnvelope<List<JsonObject>>>
    @GET("kabadiwala/passport")
    suspend fun getCollectorPassport(): Response<ApiEnvelope<CollectorPassportDto>>
    @GET("kabadiwala/safety")
    suspend fun getSafety(): Response<ApiEnvelope<SafetyResponseDto>>
    @GET("safety-routing")
    suspend fun getSafetyRouting(@Query("materialCategory") materialCategory: String?, @Query("condition") condition: String?): Response<ApiEnvelope<SafetyRoutingResponseDto>>
    @POST("kabadiwala/safety/{moduleKey}/acknowledge")
    suspend fun acknowledgeSafety(@Path("moduleKey") moduleKey: String): Response<ApiEnvelope<SafetyProgressDto>>
    @POST("kabadiwala/pools/{poolId}/prepare-handover")
    suspend fun preparePoolHandover(@Path("poolId") poolId: String, @Body body: JsonObject = JsonObject()): Response<ApiEnvelope<SupplyHandoverDto>>
    @POST("kabadiwala/bulk-lots/{lotId}/prepare-handover")
    suspend fun prepareBulkHandover(@Path("lotId") lotId: String, @Body body: JsonObject = JsonObject()): Response<ApiEnvelope<SupplyHandoverDto>>
    @POST("kabadiwala/handovers/{handoverId}/collector-confirm")
    suspend fun confirmCollectorHandover(@Path("handoverId") handoverId: String, @Header("Idempotency-Key") idempotencyKey: String? = null): Response<ApiEnvelope<SupplyHandoverDto>>
    @GET("kabadiwala/handovers")
    suspend fun getKabadiwalaHandovers(): Response<ApiEnvelope<List<SupplyHandoverDto>>>
    @GET("recycler/supply-handovers")
    suspend fun getSupplyHandovers(): Response<ApiEnvelope<List<SupplyHandoverDto>>>
    @POST("recycler/handovers/confirm")
    suspend fun confirmSupplyHandover(@Body body: SupplyHandoverConfirmRequestDto, @Header("Idempotency-Key") idempotencyKey: String? = null): Response<ApiEnvelope<SupplyHandoverDto>>
    @POST("kabadiwala/handovers/{handoverId}/settlement")
    suspend fun decideSupplySettlement(@Path("handoverId") handoverId: String, @Body body: SettlementDecisionDto): Response<ApiEnvelope<SupplyHandoverDto>>
    @GET("kabadiwala/handovers/{handoverId}/passport")
    suspend fun getMaterialPassport(@Path("handoverId") handoverId: String): Response<ApiEnvelope<MaterialPassportResponseDto>>
    @GET("kabadiwala/handovers/{handoverId}/anomalies")
    suspend fun getHandoverAnomalies(@Path("handoverId") handoverId: String): Response<ApiEnvelope<AnomalyResponseDto>>
    @POST("auth/request-otp")
    suspend fun requestOtp(@Body body: OtpRequestDto): Response<ApiEnvelope<OtpRequestedDto>>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequestDto): Response<ApiEnvelope<AuthResponseDto>>

    @POST("auth/signup")
    suspend fun signup(@Body body: EmailAuthRequestDto): Response<ApiEnvelope<AccountAuthResponseDto>>

    @POST("auth/login")
    suspend fun login(@Body body: EmailAuthRequestDto): Response<ApiEnvelope<AccountAuthResponseDto>>

    @POST("auth/admin-login")
    suspend fun adminLogin(@Body body: EmailAuthRequestDto): Response<ApiEnvelope<AdminAuthResponseDto>>

    @GET("auth/profile")
    suspend fun getAccountProfile(): Response<ApiEnvelope<AccountProfileDto>>

    @POST("auth/refresh")
    suspend fun refreshSession(@Body body: RefreshTokenRequestDto): Response<ApiEnvelope<RefreshTokenResponseDto>>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequestDto): Response<ApiEnvelope<LogoutDto>>

    @GET("collectors/me")
    suspend fun getCollector(): Response<ApiEnvelope<CollectorDto>>

    @PUT("collectors/me")
    suspend fun updateCollector(@Body body: CollectorUpdateDto): Response<ApiEnvelope<CollectorDto>>

    @POST("lots")
    suspend fun createLot(@Body body: CreateLotRequestDto): Response<ApiEnvelope<LotDto>>

    @GET("lots")
    suspend fun getLots(@Query("page") page: Int = 1, @Query("limit") limit: Int = 100): Response<ApiEnvelope<LotPageDto>>

    @GET("lots/{lotId}")
    suspend fun getLot(@Path("lotId") lotId: String): Response<ApiEnvelope<LotDto>>
    @GET("lots/{lotId}/photo")
    suspend fun getLotPhoto(@Path("lotId") lotId: String): Response<ApiEnvelope<JsonObject>>

    @PUT("lots/{lotId}")
    suspend fun updateLot(@Path("lotId") lotId: String, @Body body: UpdateLotRequestDto): Response<ApiEnvelope<LotDto>>

    @DELETE("lots/{lotId}")
    suspend fun cancelLot(@Path("lotId") lotId: String): Response<ApiEnvelope<LotDto>>

    @Multipart
    @POST("lots/{lotId}/photo")
    suspend fun uploadLotPhoto(@Path("lotId") lotId: String, @Part photo: MultipartBody.Part): Response<ApiEnvelope<LotDto>>

    @GET("prices/board")
    suspend fun getPriceBoard(@Query("materialCategory") materialCategory: String, @Query("location") location: String?): Response<ApiEnvelope<PriceBoardDto>>

    @GET("prices/history")
    suspend fun getPriceHistory(@Query("materialCategory") materialCategory: String, @Query("location") location: String?, @Query("days") days: Int = 30): Response<ApiEnvelope<PriceHistoryDto>>

    @GET("lots/{lotId}/valuation")
    suspend fun getValuation(@Path("lotId") lotId: String): Response<ApiEnvelope<ValuationDto>>

    @GET("recyclers")
    suspend fun getRecyclers(@Query("location") location: String?, @Query("radius") radiusKm: Int?, @Query("materialCategory") materialCategory: String?, @Query("availability") availability: String?, @Query("sort") sort: String = "proximity", @Query("page") page: Int = 1, @Query("limit") limit: Int = 100, @Query("latitude") latitude: Double? = null, @Query("longitude") longitude: Double? = null): Response<ApiEnvelope<RecyclerPageDto>>

    @GET("recyclers/{recyclerId}")
    suspend fun getRecycler(@Path("recyclerId") recyclerId: String): Response<ApiEnvelope<RecyclerDto>>

    @GET("recycler/profile")
    suspend fun getRecyclerProfile(): Response<ApiEnvelope<RecyclerDto>>

    @POST("recycler/verification-request")
    suspend fun submitRecyclerVerificationRequest(@Body body: RecyclerVerificationRequestDto): Response<ApiEnvelope<RecyclerDto>>

    @PATCH("recycler/profile")
    suspend fun updateRecyclerProfile(@Body body: RecyclerProfileUpdateRequestDto): Response<ApiEnvelope<RecyclerDto>>

    @PUT("recycler/rates")
    suspend fun updateRecyclerRates(@Body body: RecyclerRatesUpdateRequestDto): Response<ApiEnvelope<RecyclerDto>>

    @GET("recyclers/match")
    suspend fun matchRecyclers(@Query("lotId") lotId: String): Response<ApiEnvelope<RecyclerMatchesDto>>

    @POST("quotes/request")
    suspend fun requestQuote(@Body body: QuoteRequestDto): Response<ApiEnvelope<QuoteRequestResponseDto>>

    @POST("quotes/request-batch")
    suspend fun requestQuoteBatch(@Body body: QuoteBatchRequestDto): Response<ApiEnvelope<JsonObject>>

    @GET("quotes/pending")
    suspend fun getPendingQuotes(@Query("lotId") lotId: String?): Response<ApiEnvelope<List<QuoteDto>>>

    @GET("quotes/{quoteId}")
    suspend fun getQuote(@Path("quoteId") quoteId: String): Response<ApiEnvelope<QuoteDto>>

    @POST("quotes/{quoteId}/accept")
    suspend fun acceptQuote(@Path("quoteId") quoteId: String): Response<ApiEnvelope<QuoteDto>>

    @POST("quotes/{quoteId}/reject")
    suspend fun rejectQuote(@Path("quoteId") quoteId: String): Response<ApiEnvelope<QuoteDto>>

    @GET("recycler/quote-requests")
    suspend fun getRecyclerQuoteRequests(): Response<ApiEnvelope<List<RecyclerQuoteRequestDto>>>
    @GET("recycler/quote-requests/{requestId}")
    suspend fun getRecyclerQuoteRequest(@Path("requestId") requestId: String): Response<ApiEnvelope<RecyclerQuoteRequestDto>>

    @POST("recycler/quotes")
    suspend fun submitRecyclerQuote(@Body body: SubmitRecyclerQuoteRequestDto): Response<ApiEnvelope<QuoteDto>>

    @POST("handovers")
    suspend fun createHandover(@Body body: CreateHandoverRequestDto): Response<ApiEnvelope<HandoverDto>>

    @POST("verify/handover")
    suspend fun verifyHandover(@Body body: VerifyHandoverRequestDto): Response<ApiEnvelope<VerifiedHandoverDto>>

    @GET("handovers/{handoverId}")
    suspend fun getHandover(@Path("handoverId") handoverId: String): Response<ApiEnvelope<HandoverDto>>
    @GET("handovers/reference/{referenceId}")
    suspend fun getHandoverByReference(@Path("referenceId") referenceId: String): Response<ApiEnvelope<HandoverDto>>

    @POST("handovers/{handoverId}/mark-handed-over")
    suspend fun markHandover(@Path("handoverId") handoverId: String): Response<ApiEnvelope<HandoverDto>>

    @PUT("handovers/{handoverId}/evidence")
    suspend fun updateHandoverEvidence(@Path("handoverId") handoverId: String, @Body body: HandoverEvidenceRequestDto): Response<ApiEnvelope<HandoverDto>>

    @Multipart
    @POST("handovers/{handoverId}/evidence/photo")
    suspend fun uploadHandoverEvidencePhoto(@Path("handoverId") handoverId: String, @Part photo: MultipartBody.Part): Response<ApiEnvelope<HandoverDto>>

    @POST("handovers/{handoverId}/dispute")
    suspend fun disputeHandover(@Path("handoverId") handoverId: String, @Body body: JsonObject): Response<ApiEnvelope<DisputeDto>>

    @GET("transactions/{lotId}/timeline")
    suspend fun getTransactionTimeline(@Path("lotId") lotId: String): Response<ApiEnvelope<TransactionTimelineDto>>

    @GET("recycler/handovers")
    suspend fun getRecyclerHandovers(): Response<ApiEnvelope<List<HandoverDto>>>
    @GET("recycler/handovers/{handoverId}")
    suspend fun getRecyclerHandover(@Path("handoverId") handoverId: String): Response<ApiEnvelope<HandoverDto>>

    @POST("recycler/handovers/{handoverId}/confirm")
    suspend fun confirmRecyclerHandover(@Path("handoverId") handoverId: String, @Body body: RecyclerHandoverConfirmRequestDto): Response<ApiEnvelope<JsonObject>>

    @POST("recycler/handovers/{handoverId}/reject")
    suspend fun rejectRecyclerHandover(@Path("handoverId") handoverId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonObject>>

    @POST("payments/record")
    suspend fun recordPayment(@Body body: RecordPaymentRequestDto): Response<ApiEnvelope<PaymentDto>>

    @GET("payments")
    suspend fun getPayments(): Response<ApiEnvelope<List<PaymentDto>>>

    @GET("payments/{paymentId}")
    suspend fun getPayment(@Path("paymentId") paymentId: String): Response<ApiEnvelope<PaymentDto>>

    @PUT("payments/{paymentId}")
    suspend fun editPayment(@Path("paymentId") paymentId: String, @Body body: PaymentEditRequestDto): Response<ApiEnvelope<PaymentDto>>

    @POST("payments/{paymentId}/dispute")
    suspend fun disputePayment(@Path("paymentId") paymentId: String, @Body body: PaymentDisputeRequestDto): Response<ApiEnvelope<JsonObject>>

    @GET("earnings/ledger")
    suspend fun getEarnings(): Response<ApiEnvelope<EarningsLedgerDto>>

    @GET("notifications")
    suspend fun getNotifications(@Query("unread") unreadOnly: Boolean = false, @Query("limit") limit: Int = 50): Response<ApiEnvelope<List<NotificationDto>>>

    @GET("notifications/unread-count")
    suspend fun getNotificationUnreadCount(): Response<ApiEnvelope<UnreadCountDto>>

    @POST("notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: String): Response<ApiEnvelope<NotificationReadDto>>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<ApiEnvelope<NotificationReadDto>>

    @GET("activity/changes")
    suspend fun getActivityChanges(@Query("since") since: String? = null): Response<ApiEnvelope<ActivityChangesDto>>

    @POST("sync")
    suspend fun sync(@Body body: SyncBatchRequestDto): Response<ApiEnvelope<SyncBatchResponseDto>>

    @GET("sync/changes")
    suspend fun getChanges(@Query("since") since: String?): Response<ApiEnvelope<SyncChangesDto>>

    @GET("future/preferences")
    suspend fun getPreferences(): Response<ApiEnvelope<PreferencesDto>>

    @PATCH("future/preferences")
    suspend fun updatePreferences(@Body body: PreferencesUpdateDto): Response<ApiEnvelope<PreferencesDto>>

    @GET("future/rewards")
    suspend fun getRewards(): Response<ApiEnvelope<List<RewardLedgerDto>>>

    @GET("future/schemes")
    suspend fun getGovernmentSchemes(): Response<ApiEnvelope<List<GovernmentSchemeDto>>>

    @POST("future/schemes/check")
    suspend fun checkScheme(@Body body: SchemeCheckRequestDto): Response<ApiEnvelope<SchemeCheckResultDto>>

    @GET("future/activities")
    suspend fun getDiyActivities(): Response<ApiEnvelope<List<DiyActivityDto>>>

    @GET("future/activities/{slug}")
    suspend fun getDiyActivity(@Path("slug") slug: String): Response<ApiEnvelope<DiyActivityDto>>

    @POST("future/lots/description-suggestion")
    suspend fun suggestLotDescription(@Body body: DescriptionSuggestionRequestDto): Response<ApiEnvelope<DescriptionSuggestionDto>>

    @Multipart
    @POST("future/lots/material-suggestion")
    suspend fun suggestLotMaterial(@Part photo: MultipartBody.Part, @Part("language") language: RequestBody? = null): Response<ApiEnvelope<MaterialSuggestionDto>>

    @GET("future/recyclers/{recyclerId}/reviews")
    suspend fun getRecyclerReviews(@Path("recyclerId") recyclerId: String): Response<ApiEnvelope<List<RecyclerReviewDto>>>

    @POST("future/reviews")
    suspend fun submitRecyclerReview(@Body body: SubmitReviewRequestDto): Response<ApiEnvelope<RecyclerReviewDto>>

    @GET("future/conversations")
    suspend fun getConversations(): Response<ApiEnvelope<List<ConversationDto>>>

    @POST("future/conversations")
    suspend fun createConversation(@Body body: CreateConversationRequestDto): Response<ApiEnvelope<ConversationDto>>

    @GET("future/conversations/{conversationId}/messages")
    suspend fun getMessages(@Path("conversationId") conversationId: String, @Query("limit") limit: Int = 50): Response<ApiEnvelope<List<ChatMessageDto>>>

    @POST("future/conversations/{conversationId}/messages")
    suspend fun sendMessage(@Path("conversationId") conversationId: String, @Body body: SendMessageRequestDto): Response<ApiEnvelope<ChatMessageDto>>

    @POST("future/conversations/{conversationId}/draft-reply")
    suspend fun draftChatReply(@Path("conversationId") conversationId: String, @Body body: ChatDraftRequestDto): Response<ApiEnvelope<ChatDraftDto>>

    @POST("future/lots/{lotId}/repeat")
    suspend fun repeatLot(@Path("lotId") lotId: String, @Header("Idempotency-Key") operationId: String): Response<ApiEnvelope<LotDto>>

    @GET("future/disputes/analytics")
    suspend fun getDisputeAnalytics(@Query("months") months: Int = 6): Response<ApiEnvelope<DisputeAnalyticsDto>>

    // Capability-gated admin tools. They are deliberately not reachable from
    // household/collector/recycler navigation; the backend remains the final
    // authority for the permission claims on these requests.
    @PUT("admin/prices/{priceId}")
    suspend fun adminUpdatePrice(@Path("priceId") priceId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonObject>>
    @GET("admin/recyclers")
    suspend fun adminRecyclerQueue(@Query("status") status: String? = null): Response<ApiEnvelope<List<JsonObject>>>
    @GET("admin/recyclers/{recyclerId}")
    suspend fun adminRecyclerDetail(@Path("recyclerId") recyclerId: String): Response<ApiEnvelope<JsonObject>>
    @PUT("admin/recyclers/{recyclerId}/authorization")
    suspend fun adminAuthorizeRecycler(@Path("recyclerId") recyclerId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonObject>>
    @GET("admin/disputes")
    suspend fun adminDisputes(@Query("status") status: String? = null): Response<ApiEnvelope<List<JsonObject>>>
    @GET("admin/disputes/{disputeId}")
    suspend fun adminDispute(@Path("disputeId") disputeId: String): Response<ApiEnvelope<JsonObject>>
    @POST("admin/disputes/{disputeId}/resolve")
    suspend fun adminResolveDispute(@Path("disputeId") disputeId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonObject>>
    @GET("admin/payments")
    suspend fun adminPayments(@Query("status") status: String? = null): Response<ApiEnvelope<List<JsonObject>>>
    @GET("admin/payments/{paymentId}")
    suspend fun adminPayment(@Path("paymentId") paymentId: String): Response<ApiEnvelope<JsonObject>>
    @POST("admin/payments/{paymentId}/verify")
    suspend fun adminVerifyPayment(@Path("paymentId") paymentId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonObject>>
    @POST("admin/datasets/prices/import")
    suspend fun adminImportPrices(@Body body: JsonObject): Response<ApiEnvelope<JsonObject>>
    @GET("admin/datasets/export")
    suspend fun adminExportDataset(@Query("from") from: String? = null): Response<ApiEnvelope<JsonObject>>
}

class RemoteApiException(val code: String, override val message: String, val httpCode: Int? = null) : Exception(message)

fun <T> Response<ApiEnvelope<T>>.requireData(): T {
    val body = body()
    if (!isSuccessful) throw RemoteApiException("HTTP_${code()}", errorBody()?.string().orEmpty().ifBlank { "Request failed" }, code())
    return body?.data ?: throw RemoteApiException("EMPTY_RESPONSE", body?.message ?: "The server returned no data", code())
}
