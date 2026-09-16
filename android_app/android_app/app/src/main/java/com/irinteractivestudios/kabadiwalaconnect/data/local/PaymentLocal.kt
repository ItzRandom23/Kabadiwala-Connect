package com.irinteractivestudios.kabadiwalaconnect.data.local
import androidx.room.*
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
@Entity(tableName = "payments", indices = [Index(value = ["accountId", "paidAtEpochMs"])]) data class PaymentEntity(@PrimaryKey val id: String, val lotId: String, val handoverId: String?, val amountRupees: Double, val method: String, val paidAtEpochMs: Long, val notes: String, val syncState: String, val recordState: String, val accountId: String? = null)
@Dao interface PaymentDao { @Query("SELECT * FROM payments ORDER BY paidAtEpochMs DESC") fun observeAll(): Flow<List<PaymentEntity>>; @Query("SELECT * FROM payments WHERE accountId = :accountId ORDER BY paidAtEpochMs DESC") fun observeForAccount(accountId: String): Flow<List<PaymentEntity>>; @Query("SELECT * FROM payments WHERE id = :id LIMIT 1") suspend fun findById(id: String): PaymentEntity?; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: PaymentEntity); @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PaymentEntity>); @Query("UPDATE payments SET syncState = 'SYNCED' WHERE id = :id") suspend fun markSynced(id: String): Int; @Query("DELETE FROM payments") suspend fun clearAll() }
class RoomPaymentRepository(private val dao: PaymentDao, private val syncQueue: SyncQueueDao? = null, private val requestSync: (() -> Unit)? = null, private val accountId: () -> String? = { null }) : PaymentRepository {
    override fun observePayments() = accountId()?.let { dao.observeForAccount(it) }?.map { it.map(PaymentEntity::toDomain) } ?: dao.observeAll().map { it.map(PaymentEntity::toDomain) }
    override fun observeSummary() = observePayments().map { payments ->
        val settled = payments.filter { it.recordState != PaymentRecordState.DISCREPANCY && it.syncState == PaymentSyncState.SYNCED }
        val pending = payments.filter { it.recordState != PaymentRecordState.DISCREPANCY && it.syncState != PaymentSyncState.SYNCED }
        val now = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val total = settled.sumOf { it.amountRupees }
        val current = settled.filter { p -> Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply { timeInMillis = p.paidAtEpochMs }.let { it.get(Calendar.MONTH) == now.get(Calendar.MONTH) && it.get(Calendar.YEAR) == now.get(Calendar.YEAR) } }.sumOf { it.amountRupees }
        EarningsSummary(total, pending.sumOf { it.amountRupees }, current, if (settled.isEmpty()) 0.0 else total / settled.size)
    }
    override suspend fun record(payment: Payment) {
        dao.insert(payment.toEntity().copy(accountId = accountId()))
        try {
            syncQueue?.enqueue(
                SyncQueueItemEntity(
                    operation = "RECORD_PAYMENT",
                    payloadJson = com.google.gson.Gson().toJson(payment.toSyncPayload()),
                    createdAtEpochMs = payment.paidAtEpochMs,
                    accountId = accountId()
                )
            )
            requestSync?.invoke()
        } catch (_: Exception) {
            // Keep the payment visible locally even if queue persistence fails.
        }
    }
}
private fun PaymentEntity.toDomain() = Payment(id, lotId, handoverId, amountRupees, runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CASH), paidAtEpochMs, notes, runCatching { PaymentSyncState.valueOf(syncState) }.getOrDefault(PaymentSyncState.WAITING_TO_SYNC), runCatching { PaymentRecordState.valueOf(recordState) }.getOrDefault(PaymentRecordState.NORMAL))
private fun Payment.toEntity() = PaymentEntity(id, lotId, handoverId, amountRupees, method.name, paidAtEpochMs, notes, syncState.name, recordState.name)
private fun Payment.toSyncPayload() = mapOf(
    "id" to id,
    "lotId" to lotId,
    "amount" to amountRupees,
    "method" to method.name,
    "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date(paidAtEpochMs)),
    "time" to SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date(paidAtEpochMs)),
    "notes" to notes.takeIf { it.isNotBlank() }
)
