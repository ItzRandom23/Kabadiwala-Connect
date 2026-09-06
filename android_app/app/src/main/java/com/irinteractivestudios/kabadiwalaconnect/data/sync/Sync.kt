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
        val pending = queue.observeAll().first().take(BATCH_SIZE)
        if (pending.isEmpty()) return Result.success()

        val operations = pending.mapNotNull { item -> item.toOperationOrNull() }
        pending.filter { it.toOperationOrNull() == null }.forEach { queue.remove(it.uid) }
        if (operations.isEmpty()) return Result.success()

        return try {
            val response = app.container.apiService.sync(SyncBatchRequestDto(operations))
            if (!response.isSuccessful) {
                return if (response.code() == 408 || response.code() == 429 || response.code() >= 500) Result.retry() else Result.failure()
            }
            val results = response.body()?.data?.results ?: return Result.retry()
            val resultByOperation = results.associateBy { it.operationId }
            pending.forEach { item ->
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
                    }
                    queue.remove(item.uid)
                }
            }
            if (results.size < operations.size) Result.retry() else Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
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
