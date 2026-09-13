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

    @POST("recycler/quotes")
    suspend fun submitRecyclerQuote(@Body body: SubmitRecyclerQuoteRequestDto): Response<ApiEnvelope<QuoteDto>>

    @POST("handovers")
    suspend fun createHandover(@Body body: CreateHandoverRequestDto): Response<ApiEnvelope<HandoverDto>>

    @POST("verify/handover")
    suspend fun verifyHandover(@Body body: VerifyHandoverRequestDto): Response<ApiEnvelope<VerifiedHandoverDto>>

    @GET("handovers/{handoverId}")
    suspend fun getHandover(@Path("handoverId") handoverId: String): Response<ApiEnvelope<HandoverDto>>

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

    @PATCH("payments/{paymentId}")
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
}

class RemoteApiException(val code: String, override val message: String, val httpCode: Int? = null) : Exception(message)

fun <T> Response<ApiEnvelope<T>>.requireData(): T {
    val body = body()
    if (!isSuccessful) throw RemoteApiException("HTTP_${code()}", errorBody()?.string().orEmpty().ifBlank { "Request failed" }, code())
    return body?.data ?: throw RemoteApiException("EMPTY_RESPONSE", body?.message ?: "The server returned no data", code())
}
