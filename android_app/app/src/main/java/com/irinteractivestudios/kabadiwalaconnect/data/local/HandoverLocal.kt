package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.*
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ApiService
import com.irinteractivestudios.kabadiwalaconnect.data.remote.CreateHandoverRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverEvidenceRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.imageMimeType
import com.irinteractivestudios.kabadiwalaconnect.data.remote.HandoverLocationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RemoteApiException
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.data.repository.HandoverRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.io.IOException
import java.io.File
import com.google.gson.JsonObject
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@Entity(tableName = "handovers")
data class HandoverEntity(
    @PrimaryKey val id: String,
    val lotId: String,
    val recyclerId: String,
    val collectorId: String,
    val recyclerName: String,
    val materialLabel: String,
    val weightKg: Double,
    val quotedPriceRupees: Double,
    val collectionLocation: String,
    val handoverLocation: String,
    val handoverLocationType: String,
    val timestampEpochMs: Long,
    val createdAtEpochMs: Long,
    val quoteId: String,
    val status: String,
    val synced: Boolean,
    val actualWeightKg: Double? = null,
    val materialConfirmed: Boolean = false,
    val collectorConfirmed: Boolean = false,
    val scalePhotoPath: String? = null,
    val evidenceUpdatedAtEpochMs: Long? = null,
    val qrCodeData: String? = null,
    val referenceId: String? = null,
    val expiresAtEpochMs: Long? = null
)
@Dao interface HandoverDao {
    @Query("SELECT * FROM handovers ORDER BY timestampEpochMs DESC") fun observeAll(): kotlinx.coroutines.flow.Flow<List<HandoverEntity>>
    @Query("SELECT * FROM handovers WHERE collectorId = :accountId OR recyclerId = :accountId ORDER BY timestampEpochMs DESC") fun observeForAccount(accountId: String): kotlinx.coroutines.flow.Flow<List<HandoverEntity>>
    @Query("SELECT * FROM handovers WHERE id = :id LIMIT 1") fun observe(id: String): kotlinx.coroutines.flow.Flow<HandoverEntity?>
    @Query("SELECT * FROM handovers WHERE id = :id AND (collectorId = :accountId OR recyclerId = :accountId) LIMIT 1") fun observeForAccount(id: String, accountId: String): kotlinx.coroutines.flow.Flow<HandoverEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: HandoverEntity)
    @Query("SELECT * FROM handovers WHERE id = :id LIMIT 1") suspend fun get(id: String): HandoverEntity?
    @Query("SELECT * FROM handovers WHERE id = :id AND (collectorId = :accountId OR recyclerId = :accountId) LIMIT 1") suspend fun getForAccount(id: String, accountId: String): HandoverEntity?
    @Query("UPDATE handovers SET status = 'HANDED_OVER' WHERE id = :id") suspend fun markHandedOver(id: String): Int
    @Query("UPDATE handovers SET status = 'HANDED_OVER' WHERE id = :id AND (collectorId = :accountId OR recyclerId = :accountId)") suspend fun markHandedOverForAccount(id: String, accountId: String): Int
    @Query("UPDATE handovers SET actualWeightKg = :actualWeightKg, materialConfirmed = :materialConfirmed, collectorConfirmed = :collectorConfirmed, scalePhotoPath = :scalePhotoPath, evidenceUpdatedAtEpochMs = :updatedAt WHERE id = :id") suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?, updatedAt: Long): Int
    @Query("UPDATE handovers SET actualWeightKg = :actualWeightKg, materialConfirmed = :materialConfirmed, collectorConfirmed = :collectorConfirmed, scalePhotoPath = :scalePhotoPath, evidenceUpdatedAtEpochMs = :updatedAt WHERE id = :id AND (collectorId = :accountId OR recyclerId = :accountId)") suspend fun updateEvidenceForAccount(id: String, accountId: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?, updatedAt: Long): Int
    @Query("DELETE FROM handovers") suspend fun clearAll()
}
class RoomHandoverRepository(
    private val dao: HandoverDao,
    private val accountId: () -> String? = { null }
) : HandoverRepository {
    override fun observeAll() = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForAccount(it) }?.map { it.map(HandoverEntity::toDomain) }
        ?: flowOf(emptyList())
    override fun observe(id: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForAccount(id, it) }?.map { it?.toDomain() }
        ?: flowOf(null)
    override suspend fun create(lot: Lot, quote: Quote, collectorId: String, locationType: HandoverLocationType, location: String, timestampEpochMs: Long): Handover {
        check(accountId()?.takeIf { it.isNotBlank() } == collectorId) { "Authenticated account required for handover changes" }
        val item = Handover("HOV-${UUID.randomUUID()}", lot.id, quote.recyclerId, collectorId, quote.recyclerName, lot.materialLabel, lot.weightKg, quote.amountRupees, lot.location, location, locationType, timestampEpochMs, timestampEpochMs, quote.id)
        dao.insert(item.toEntity()); return item
    }
    override suspend fun markHandedOver(id: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.markHandedOverForAccount(id, it) > 0 } ?: false
    override suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?): Boolean = accountId()?.takeIf { it.isNotBlank() }?.let { dao.updateEvidenceForAccount(id, it, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, System.currentTimeMillis()) > 0 } ?: false
}

/**
 * Production transport with a Room read-through cache. The signed QR payload
 * is supplied only by the backend; the app never manufactures a "verified" QR.
 */
class RemoteHandoverRepository(
    private val dao: HandoverDao,
    private val api: ApiService,
    private val accountId: () -> String? = { null }
) : HandoverRepository {
    override fun observeAll() = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForAccount(it) }?.map { rows -> rows.map(HandoverEntity::toDomain) }
        ?: flowOf(emptyList())
    override fun observe(id: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForAccount(id, it) }?.map { it?.toDomain() }
        ?: flowOf(null)

    override suspend fun create(
        lot: Lot,
        quote: Quote,
        collectorId: String,
        locationType: HandoverLocationType,
        location: String,
        timestampEpochMs: Long
    ): Handover {
        check(accountId()?.takeIf { it.isNotBlank() } == collectorId) { "Authenticated account required for handover changes" }
        val dto = api.createHandover(
            CreateHandoverRequestDto(
                lotId = lot.id,
                quoteId = quote.id,
                handoverLocation = HandoverLocationDto(type = locationType.name, address = location),
                timestamp = timestampEpochMs.toIsoTimestamp()
            )
        ).requireData()
        val item = dto.toDomain(lot, quote, collectorId, locationType, location)
        check(!item.qrCodeData.isNullOrBlank()) { "Server handover did not include signed QR data" }
        dao.insert(item.toEntity())
        return item
    }

    override suspend fun markHandedOver(id: String): Boolean {
        val local = scopedObservation(id).firstOrNullValue() ?: return false
        val dto = api.markHandover(id).requireData()
        dao.insert(dto.toDomain(local.toDomain()).toEntity())
        return true
    }

    override suspend fun updateEvidence(
        id: String,
        actualWeightKg: Double,
        materialConfirmed: Boolean,
        collectorConfirmed: Boolean,
        scalePhotoPath: String?
    ): Boolean {
        val local = scopedObservation(id).firstOrNullValue() ?: return false
        var dto = api.updateHandoverEvidence(
            id,
            HandoverEvidenceRequestDto(
                actualWeight = actualWeightKg,
                materialMatch = materialConfirmed,
                // A private device path is not a server image reference. A later
                // evidence upload replaces this with an authenticated object key.
                scalePhotoReference = null,
                collectorConfirmed = collectorConfirmed
            )
        ).requireData()
        // The JSON evidence mutation and the binary upload are separate so a
        // large photo can retry independently without losing the measured
        // weight/material confirmation. The server stores only a private key.
        scalePhotoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                dto = api.uploadHandoverEvidencePhoto(
                    id,
                    MultipartBody.Part.createFormData("photo", file.name, file.asRequestBody(file.imageMimeType().toMediaTypeOrNull()))
                ).requireData()
            }
        }
        val updated = dto.toDomain(local.toDomain()).copy(
            scalePhotoPath = scalePhotoPath,
            evidenceUpdatedAtEpochMs = System.currentTimeMillis()
        )
        dao.insert(updated.toEntity())
        return true
    }

    private fun scopedObservation(id: String) = accountId()?.takeIf { it.isNotBlank() }?.let { dao.observeForAccount(id, it) } ?: flowOf(null)
}

/** Network-first repository with durable local fallback for field connectivity. */
class OfflineFirstHandoverRepository(
    private val local: RoomHandoverRepository,
    private val remote: RemoteHandoverRepository,
    private val queue: SyncQueueDao,
    private val requestSync: () -> Unit
) : HandoverRepository {
    override fun observeAll() = local.observeAll()
    override fun observe(id: String) = local.observe(id)

    override suspend fun create(lot: Lot, quote: Quote, collectorId: String, locationType: HandoverLocationType, location: String, timestampEpochMs: Long): Handover = try {
        remote.create(lot, quote, collectorId, locationType, location, timestampEpochMs)
    } catch (error: Exception) {
        if (!error.isRetryableTransportFailure()) throw error
        val handover = local.create(lot, quote, collectorId, locationType, location, timestampEpochMs)
        enqueue("CREATE_HANDOVER", JsonObject().apply {
            addProperty("id", handover.id); addProperty("lotId", lot.id); addProperty("quoteId", quote.id)
            addProperty("locationType", locationType.name); addProperty("location", location); addProperty("timestampEpochMs", timestampEpochMs); addProperty("collectorId", collectorId)
        }, collectorId)
        handover
    }

    override suspend fun markHandedOver(id: String): Boolean {
        val cached = local.observe(id).firstOrNullValue()
        return try {
        remote.markHandedOver(id)
    } catch (error: Exception) {
        if (!error.isRetryableTransportFailure()) throw error
        val updated = local.markHandedOver(id)
        if (updated) enqueue("MARK_HANDOVER", JsonObject().apply { addProperty("id", id); addProperty("collectorId", cached?.collectorId.orEmpty()) }, cached?.collectorId)
        updated
    }
    }

    override suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?): Boolean {
        val cached = local.observe(id).firstOrNullValue()
        return try {
        remote.updateEvidence(id, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath)
    } catch (error: Exception) {
        if (!error.isRetryableTransportFailure()) throw error
        val updated = local.updateEvidence(id, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath)
        if (updated) enqueue("UPDATE_HANDOVER_EVIDENCE", JsonObject().apply {
            addProperty("id", id); addProperty("actualWeight", actualWeightKg); addProperty("materialMatch", materialConfirmed); addProperty("collectorConfirmed", collectorConfirmed)
            scalePhotoPath?.let { addProperty("scalePhotoPath", it) }
        }, cached?.collectorId)
        updated
    }
    }

    private suspend fun enqueue(operation: String, payload: JsonObject, accountId: String? = null) {
        queue.enqueue(SyncQueueItemEntity(operation = operation, payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = accountId))
        requestSync()
    }
}

private fun Throwable.isRetryableTransportFailure(): Boolean = when (this) {
    is IOException -> true
    is RemoteApiException -> httpCode == 408 || httpCode == 429 || (httpCode ?: 0) >= 500
    else -> false
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNullValue(): T? =
    firstOrNull()

private fun HandoverDto.toDomain(
    lot: Lot,
    quote: Quote,
    collector: String,
    locationType: HandoverLocationType,
    location: String
): Handover {
    val created = createdAt?.let(::parseHandoverTimestamp) ?: System.currentTimeMillis()
    return Handover(
        id = id,
        lotId = lotId,
        recyclerId = recyclerId ?: quote.recyclerId,
        collectorId = collectorId ?: collector,
        recyclerName = quote.recyclerName,
        materialLabel = materialCategory?.toDisplayLabel() ?: lot.materialLabel,
        weightKg = weight ?: lot.weightKg,
        quotedPriceRupees = quotedPrice ?: quote.amountRupees,
        collectionLocation = collectionLocation?.areaName ?: lot.location,
        handoverLocation = handoverLocation?.address ?: location,
        handoverLocationType = handoverLocation?.type?.let { runCatching { HandoverLocationType.valueOf(it) }.getOrNull() } ?: locationType,
        timestampEpochMs = timestamp?.let(::parseHandoverTimestamp) ?: created,
        createdAtEpochMs = created,
        quoteId = quoteId ?: quote.id,
        status = status.toLocalHandoverStatus(),
        synced = true,
        actualWeightKg = actualWeight,
        materialConfirmed = materialConfirmedAt != null,
        collectorConfirmed = collectorConfirmedAt != null,
        qrCodeData = qrCodeData,
        referenceId = referenceId,
        expiresAtEpochMs = expiresAt?.let(::parseHandoverTimestamp)
    )
}

internal fun HandoverDto.toDomain(fallback: Handover) = fallback.copy(
    id = id,
    lotId = lotId,
    quoteId = quoteId ?: fallback.quoteId,
    recyclerId = recyclerId ?: fallback.recyclerId,
    collectorId = collectorId ?: fallback.collectorId,
    materialLabel = materialCategory?.toDisplayLabel() ?: fallback.materialLabel,
    weightKg = weight ?: fallback.weightKg,
    quotedPriceRupees = quotedPrice ?: fallback.quotedPriceRupees,
    collectionLocation = collectionLocation?.areaName ?: fallback.collectionLocation,
    handoverLocation = handoverLocation?.address ?: fallback.handoverLocation,
    timestampEpochMs = timestamp?.let(::parseHandoverTimestamp) ?: fallback.timestampEpochMs,
    createdAtEpochMs = createdAt?.let(::parseHandoverTimestamp) ?: fallback.createdAtEpochMs,
    status = status.toLocalHandoverStatus(),
    synced = true,
    actualWeightKg = actualWeight ?: fallback.actualWeightKg,
    materialConfirmed = materialConfirmedAt != null || fallback.materialConfirmed,
    collectorConfirmed = collectorConfirmedAt != null || fallback.collectorConfirmed,
    qrCodeData = qrCodeData ?: fallback.qrCodeData,
    referenceId = referenceId ?: fallback.referenceId,
    expiresAtEpochMs = expiresAt?.let(::parseHandoverTimestamp) ?: fallback.expiresAtEpochMs
)

/**
 * Builds a complete Room row for a server delta when no local handover row
 * exists yet. Delta reconciliation must not silently discard a handover just
 * because the user cleared a stale cache or restored a new device.
 */
internal fun HandoverDto.toSyncEntity(): HandoverEntity {
    val created = createdAt?.let(::parseHandoverTimestamp) ?: System.currentTimeMillis()
    return HandoverEntity(
        id = id,
        lotId = lotId,
        recyclerId = recyclerId.orEmpty(),
        collectorId = collectorId.orEmpty(),
        recyclerName = "Recycler",
        materialLabel = materialCategory?.toDisplayLabel().orEmpty(),
        weightKg = weight ?: 0.0,
        quotedPriceRupees = quotedPrice ?: 0.0,
        collectionLocation = collectionLocation?.areaName.orEmpty(),
        handoverLocation = handoverLocation?.address.orEmpty(),
        handoverLocationType = handoverLocation?.type ?: HandoverLocationType.COLLECTOR_LOCATION.name,
        timestampEpochMs = timestamp?.let(::parseHandoverTimestamp) ?: created,
        createdAtEpochMs = created,
        quoteId = quoteId.orEmpty(),
        status = status.toLocalHandoverStatus().name,
        synced = true,
        actualWeightKg = actualWeight,
        materialConfirmed = materialConfirmedAt != null,
        collectorConfirmed = collectorConfirmedAt != null,
        qrCodeData = qrCodeData,
        referenceId = referenceId,
        expiresAtEpochMs = expiresAt?.let(::parseHandoverTimestamp)
    )
}

private fun String.toLocalHandoverStatus() = when (this) {
    "CONFIRMED_BY_RECYCLER", "COMPLETED", "PAID" -> HandoverStatus.HANDED_OVER
    else -> HandoverStatus.SAVED_LOCALLY
}

private fun String.toDisplayLabel() = replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun Long.toIsoTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(this))

private fun parseHandoverTimestamp(value: String): Long? = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssX"
).firstNotNullOfOrNull { pattern ->
    runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time }.getOrNull()
}
internal fun HandoverEntity.toDomain() = Handover(id, lotId, recyclerId, collectorId, recyclerName, materialLabel, weightKg, quotedPriceRupees, collectionLocation, handoverLocation, runCatching { HandoverLocationType.valueOf(handoverLocationType) }.getOrDefault(HandoverLocationType.COLLECTOR_LOCATION), timestampEpochMs, createdAtEpochMs, quoteId, runCatching { HandoverStatus.valueOf(status) }.getOrDefault(HandoverStatus.SAVED_LOCALLY), synced, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, evidenceUpdatedAtEpochMs, qrCodeData, referenceId, expiresAtEpochMs)
internal fun Handover.toEntity() = HandoverEntity(id, lotId, recyclerId, collectorId, recyclerName, materialLabel, weightKg, quotedPriceRupees, collectionLocation, handoverLocation, handoverLocationType.name, timestampEpochMs, createdAtEpochMs, quoteId, status.name, synced, actualWeightKg, materialConfirmed, collectorConfirmed, scalePhotoPath, evidenceUpdatedAtEpochMs, qrCodeData, referenceId, expiresAtEpochMs)
