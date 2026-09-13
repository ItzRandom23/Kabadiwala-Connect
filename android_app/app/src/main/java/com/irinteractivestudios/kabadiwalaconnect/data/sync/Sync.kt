package com.irinteractivestudios.kabadiwalaconnect.data.sync

import android.content.Context
import com.google.gson.JsonParser
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.irinteractivestudios.kabadiwalaconnect.KabadiwalaApp
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SyncBatchRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SyncOperationDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.io.File
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.toDomain
import com.irinteractivestudios.kabadiwalaconnect.data.local.toEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.CreateHandoverRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverEvidenceRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverLocationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.QuoteRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SendMessageRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.domain.model.QuoteStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Uploads supported offline operations in small, idempotent batches. */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? KabadiwalaApp ?: return Result.failure()
        val token = app.container.secureStorage.get(com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage.AUTH_TOKEN)
        if (token.isNullOrBlank()) return Result.success()

        val queue = app.container.database.syncQueueDao()
        val accountId = app.container.secureStorage.get(com.irinteractivestudios.kabadiwalaconnect.util.SecureStorage.ACCOUNT_PROFILE_ID)
        if (accountId.isNullOrBlank()) return Result.success()
        // A worker can outlive the account that scheduled it. Only replay
        // rows owned by the currently authenticated account; legacy rows with
        // no owner remain visible as unresolved instead of crossing accounts.
        val pending = queue.observePendingForAccount(accountId).first().take(BATCH_SIZE)
        if (pending.isEmpty()) return pullChanges(app)
        val pendingCreateLotIds = pending
            .filter { it.operation == "CREATE_LOT" }
            .mapNotNull { item -> runCatching { JsonParser.parseString(item.payloadJson).asJsonObject.get("id")?.asString }.getOrNull() }
            .toSet()
        var deferredOperation = false

        // Operations with dedicated idempotent API contracts are replayed
        // before the legacy batch. They retain their queue row on any
        // uncertain response so WorkManager can safely retry them.
        for (item in pending.filterNot { it.operation == "CREATE_LOT" || it.operation == "RECORD_PAYMENT" }) {
            if (item.operation == "REQUEST_QUOTE") {
                val quotePayload = runCatching { JsonParser.parseString(item.payloadJson).asJsonObject }.getOrNull()
                val localLot = quotePayload?.get("lotId")?.asString?.let { app.container.database.lotDao().findByIdForCollector(it, accountId) }
                when {
                    // A queued quote can depend on a lot queued in the same
                    // worker run. Let the CREATE_LOT batch complete first so
                    // a temporary server 404 is not recorded as permanent.
                    localLot != null && !localLot.synced && localLot.status != "CANCELLED" && localLot.id in pendingCreateLotIds -> {
                        deferredOperation = true
                        continue
                    }
                    // Cancellation removes an unsent lot's create operation;
                    // its dependent quote request must not be resurrected.
                    localLot?.status == "CANCELLED" -> {
                        queue.remove(item.uid)
                        continue
                    }
                }
            }
            when (processExtended(app, item)) {
                QueueResult.APPLIED -> queue.remove(item.uid)
                QueueResult.RETRY -> { queue.incrementAttempts(item.uid, retryAt(item.attempts)); return Result.retry() }
                QueueResult.REJECTED -> {
                    queue.markFailed(item.uid, "EXTENDED_OPERATION_REJECTED", retryAt(item.attempts))
                    return Result.failure()
                }
            }
        }

        val batchPending = pending.filter { it.operation == "CREATE_LOT" || it.operation == "RECORD_PAYMENT" }
        val operations = batchPending.mapNotNull { item -> item.toOperationOrNull() }
        if (operations.isEmpty()) return if (deferredOperation) Result.retry() else pullChanges(app)

        return try {
            val response = app.container.apiService.sync(SyncBatchRequestDto(operations))
            if (!response.isSuccessful) {
                return if (response.code() == 408 || response.code() == 429 || response.code() >= 500) Result.retry() else Result.failure()
            }
            val results = response.body()?.data?.results ?: return Result.retry()
            val resultByOperation = results.associateBy { it.operationId }
            batchPending.forEach { item ->
                val operation = item.toOperationOrNull() ?: return@forEach
                val result = resultByOperation[operation.operationId] ?: return@forEach
                if (result.status in TERMINAL_STATUSES) {
                    if (result.status == "APPLIED" || result.status == "ALREADY_APPLIED") {
                        when (operation.entityType) {
                            "LOT" -> {
                                val photoResult = uploadLotPhotoIfPresent(app, operation)
                                if (photoResult == PhotoUploadResult.RETRY) return Result.retry()
                                // A lot is not considered fully synchronized until its
                                // attached photo is accepted. Keeping the queue item on a
                                // permanent upload failure is safer than showing a false
                                // "synced" state or silently dropping the evidence.
                                if (photoResult == PhotoUploadResult.PERMANENT_FAILURE) return Result.failure()
                                app.container.database.lotDao().markSynced(operation.entityId)
                            }
                            "PAYMENT" -> app.container.database.paymentDao().markSynced(operation.entityId)
                        }
                        queue.remove(item.uid)
                    } else {
                        // Keep rejected/conflicting work visible locally instead of
                        // silently dropping the user's action. The pending query
                        // stops automatic retries after three attempts.
                        queue.markFailed(item.uid, result.errorCode ?: "SYNC_${result.status}", retryAt(item.attempts))
                    }
                }
            }
            if (deferredOperation || results.size < operations.size) Result.retry() else pullChanges(app)
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private suspend fun pullChanges(app: KabadiwalaApp): Result = try {
        app.container.reconcileChanges()
        Result.success()
    } catch (_: IOException) {
        Result.retry()
    } catch (error: RemoteApiException) {
        // Authentication refresh is handled by Retrofit. Other transient
        // server responses should remain recoverable through WorkManager.
        if (error.httpCode == 408 || error.httpCode == 429 || (error.httpCode ?: 0) >= 500) Result.retry()
        else Result.failure()
    } catch (_: Exception) {
        Result.retry()
    }

    private suspend fun processExtended(app: KabadiwalaApp, item: SyncQueueItemEntity): QueueResult {
        val payload = runCatching { JsonParser.parseString(item.payloadJson).asJsonObject }.getOrNull() ?: return QueueResult.REJECTED
        return try {
            when (item.operation) {
                "REQUEST_QUOTE" -> {
                    app.container.apiService.requestQuote(QuoteRequestDto(payload.string("lotId"), payload.string("recyclerId"))).requireData()
                    app.container.database.quoteDao().delete(payload.string("id"))
                }
                "ACCEPT_QUOTE" -> {
                    val quote = app.container.apiService.acceptQuote(payload.string("id")).requireData()
                    app.container.database.quoteDao().updateStatus(quote.id, QuoteStatus.ACCEPTED.name)
                }
                "REJECT_QUOTE" -> {
                    val quote = app.container.apiService.rejectQuote(payload.string("id")).requireData()
                    app.container.database.quoteDao().updateStatus(quote.id, QuoteStatus.REJECTED.name)
                }
                "CANCEL_LOT" -> {
                    app.container.apiService.cancelLot(payload.string("id")).requireData()
                }
                "CREATE_HANDOVER" -> {
                    val id = payload.string("id")
                    val fallback = app.container.database.handoverDao().get(id)?.toDomain() ?: return QueueResult.REJECTED
                    val dto = app.container.apiService.createHandover(CreateHandoverRequestDto(
                        lotId = payload.string("lotId"), quoteId = payload.string("quoteId"), clientHandoverId = id,
                        handoverLocation = HandoverLocationDto(payload.string("locationType"), address = payload.string("location")),
                        timestamp = payload.long("timestampEpochMs").toIsoTimestamp()
                    )).requireData()
                    app.container.database.handoverDao().insert(dto.toDomain(fallback).toEntity())
                }
                "MARK_HANDOVER" -> {
                    val id = payload.string("id")
                    val fallback = app.container.database.handoverDao().get(id)?.toDomain() ?: return QueueResult.REJECTED
                    val dto = app.container.apiService.markHandover(id).requireData()
                    app.container.database.handoverDao().insert(dto.toDomain(fallback).toEntity())
                }
                "UPDATE_HANDOVER_EVIDENCE" -> {
                    val id = payload.string("id")
                    val fallback = app.container.database.handoverDao().get(id)?.toDomain() ?: return QueueResult.REJECTED
                    var dto = app.container.apiService.updateHandoverEvidence(id, HandoverEvidenceRequestDto(payload.double("actualWeight"), payload.boolean("materialMatch"), collectorConfirmed = payload.boolean("collectorConfirmed"))).requireData()
                    payload.get("scalePhotoPath")?.takeUnless { it.isJsonNull }?.asString?.let { path ->
                        val file = File(path)
                        if (!file.exists()) return QueueResult.REJECTED
                        val response = app.container.apiService.uploadHandoverEvidencePhoto(id, MultipartBody.Part.createFormData("photo", file.name, file.asRequestBody("image/*".toMediaTypeOrNull())))
                        dto = response.requireData()
                    }
                    app.container.database.handoverDao().insert(dto.toDomain(fallback).toEntity())
                }
                "SEND_CHAT_MESSAGE" -> {
                    val clientId = payload.string("clientMessageId")
                    val message = app.container.apiService.sendMessage(payload.string("conversationId"), SendMessageRequestDto(clientId, payload.string("body"))).requireData()
                    FutureCacheStore(app.container.database.futureCacheDao()).apply { deleteMessage("local-$clientId"); saveMessages(listOf(message)) }
                }
                "MARK_NOTIFICATION_READ" -> app.container.apiService.markNotificationRead(payload.string("id")).requireData()
                "MARK_ALL_NOTIFICATIONS_READ" -> app.container.apiService.markAllNotificationsRead().requireData()
                "CREATE_DISPUTE" -> {
                    val localId = payload.string("id")
                    val handoverId = payload.string("handoverId")
                    val body = payload.deepCopy().apply { addProperty("clientDisputeId", localId); remove("id"); remove("handoverId") }
                    val dispute = app.container.apiService.disputeHandover(handoverId, body).requireData()
                    app.container.database.disputeDao().markSynced(localId, dispute.id)
                }
                else -> return QueueResult.REJECTED
            }
            QueueResult.APPLIED
        } catch (error: com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException) {
            if (error.httpCode == 409 && alreadyApplied(app, item, payload)) QueueResult.APPLIED
            else if (error.httpCode == 408 || error.httpCode == 429 || (error.httpCode ?: 0) >= 500) QueueResult.RETRY else QueueResult.REJECTED
        } catch (_: IOException) {
            QueueResult.RETRY
        } catch (_: Exception) {
            QueueResult.REJECTED
        }
    }

    private suspend fun alreadyApplied(app: KabadiwalaApp, item: SyncQueueItemEntity, payload: JsonObject): Boolean = runCatching {
        when (item.operation) {
            "ACCEPT_QUOTE" -> app.container.apiService.getQuote(payload.string("id")).requireData().status == "ACCEPTED"
            "REJECT_QUOTE" -> app.container.apiService.getQuote(payload.string("id")).requireData().status == "REJECTED"
            "MARK_HANDOVER" -> app.container.apiService.getHandover(payload.string("id")).requireData().collectorConfirmedAt != null
            "UPDATE_HANDOVER_EVIDENCE" -> app.container.apiService.getHandover(payload.string("id")).requireData().actualWeight == payload.double("actualWeight")
            "CANCEL_LOT" -> app.container.apiService.getLot(payload.string("id")).requireData().status == "CANCELLED"
            else -> false
        }
    }.getOrDefault(false)

    private enum class QueueResult { APPLIED, RETRY, REJECTED }

    private fun retryAt(attempts: Int): Long {
        val exponent = attempts.coerceIn(0, 6)
        return System.currentTimeMillis() + (30_000L * (1L shl exponent)).coerceAtMost(30L * 60L * 1000L)
    }

    private suspend fun uploadLotPhotoIfPresent(app: KabadiwalaApp, operation: SyncOperationDto): PhotoUploadResult {
        if (operation.entityType != "LOT") return PhotoUploadResult.NOT_NEEDED
        val path = operation.payload.get("photoPath")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (path.isBlank()) return PhotoUploadResult.NOT_NEEDED
        val file = File(path)
        if (!file.exists()) return PhotoUploadResult.PERMANENT_FAILURE
        return try {
            val body = file.asRequestBody("image/*".toMediaTypeOrNull())
            val response = app.container.apiService.uploadLotPhoto(operation.entityId, MultipartBody.Part.createFormData("photo", file.name, body))
            when {
                response.isSuccessful -> PhotoUploadResult.UPLOADED
                response.code() == 408 || response.code() == 429 || response.code() >= 500 -> PhotoUploadResult.RETRY
                else -> PhotoUploadResult.PERMANENT_FAILURE
            }
        } catch (_: IOException) {
            PhotoUploadResult.RETRY
        }
    }

    private enum class PhotoUploadResult { UPLOADED, NOT_NEEDED, RETRY, PERMANENT_FAILURE }

    private fun SyncQueueItemEntity.toOperationOrNull(): SyncOperationDto? {
        val (operationType, entityType) = when (operation) {
            "CREATE_LOT" -> "CREATE" to "LOT"
            "RECORD_PAYMENT" -> "CREATE" to "PAYMENT"
            else -> return null
        }
        val payload = runCatching { JsonParser.parseString(payloadJson).asJsonObject }.getOrNull() ?: return null
        val entityId = payload.get("id")?.asString ?: return null
        return SyncOperationDto("local-$uid", operationType, entityType, entityId, payload)
    }

    companion object {
        private const val BATCH_SIZE = 100
        private val TERMINAL_STATUSES = setOf("APPLIED", "ALREADY_APPLIED", "CONFLICT", "REJECTED", "INVALID")
    }
}

private fun JsonObject.string(name: String): String = get(name)?.asString ?: error("Missing $name")
private fun JsonObject.long(name: String): Long = get(name)?.asLong ?: error("Missing $name")
private fun JsonObject.double(name: String): Double = get(name)?.asDouble ?: error("Missing $name")
private fun JsonObject.boolean(name: String): Boolean = get(name)?.asBoolean ?: error("Missing $name")
private fun Long.toIsoTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(this))

class SyncScheduler(private val context: Context) {

    /** Queues a sync attempt for when connectivity returns. */
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_TAG = "kabadiwala-sync"
    }
}
