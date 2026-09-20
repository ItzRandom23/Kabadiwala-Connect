package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.HandoverDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.HandoverEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomHandoverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression coverage for the shared-device account privacy boundary. */
class AccountScopedCacheTest {

    @Test
    fun handoverCache_onlyExposesRowsOwnedByCurrentAccount() = runTest {
        val dao = FakeHandoverDao(
            listOf(
                handover("mine", collectorId = "collector-a", recyclerId = "recycler-x"),
                handover("foreign", collectorId = "collector-b", recyclerId = "recycler-y")
            )
        )
        val repository = RoomHandoverRepository(dao) { "collector-a" }

        assertEquals(listOf("mine"), repository.observeAll().first().map { it.id })
        assertNull(repository.observe("foreign").first())
        assertEquals(false, repository.markHandedOver("foreign"))
        assertEquals(1, dao.accountScopedMutationCalls)
        assertEquals(0, dao.unscopedMutationCalls)
    }

    @Test
    fun handoverCache_withoutAnAuthenticatedAccountIsEmptyAndReadOnly() = runTest {
        val dao = FakeHandoverDao(listOf(handover("foreign", "collector-a", "recycler-a")))
        val repository = RoomHandoverRepository(dao) { null }

        assertEquals(emptyList<String>(), repository.observeAll().first().map { it.id })
        assertNull(repository.observe("foreign").first())
        assertEquals(false, repository.markHandedOver("foreign"))
        assertEquals(0, dao.accountScopedMutationCalls)
        assertEquals(0, dao.unscopedMutationCalls)
    }

    private fun handover(id: String, collectorId: String, recyclerId: String) = HandoverEntity(
        id = id,
        lotId = "lot-$id",
        recyclerId = recyclerId,
        collectorId = collectorId,
        recyclerName = "Recycler",
        materialLabel = "Copper",
        weightKg = 2.0,
        quotedPriceRupees = 100.0,
        collectionLocation = "Pune",
        handoverLocation = "Pune",
        handoverLocationType = "COLLECTOR_LOCATION",
        timestampEpochMs = 1L,
        createdAtEpochMs = 1L,
        quoteId = "quote-$id",
        status = "SAVED_LOCALLY",
        synced = true
    )

    private class FakeHandoverDao(initial: List<HandoverEntity>) : HandoverDao {
        private val rows = MutableStateFlow(initial)
        var accountScopedMutationCalls = 0
        var unscopedMutationCalls = 0

        override fun observeAll(): Flow<List<HandoverEntity>> = rows
        override fun observeForAccount(accountId: String): Flow<List<HandoverEntity>> =
            rows.mapRows { it.collectorId == accountId || it.recyclerId == accountId }
        override fun observe(id: String): Flow<HandoverEntity?> = rows.mapValue { it.id == id }
        override fun observeForAccount(id: String, accountId: String): Flow<HandoverEntity?> =
            rows.mapValue { it.id == id && (it.collectorId == accountId || it.recyclerId == accountId) }
        override suspend fun insert(item: HandoverEntity) {
            rows.value = rows.value.filterNot { it.id == item.id } + item
        }
        override suspend fun get(id: String): HandoverEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getForAccount(id: String, accountId: String): HandoverEntity? =
            rows.value.firstOrNull { it.id == id && (it.collectorId == accountId || it.recyclerId == accountId) }
        override suspend fun markHandedOver(id: String): Int { unscopedMutationCalls++; return 0 }
        override suspend fun markHandedOverForAccount(id: String, accountId: String): Int { accountScopedMutationCalls++; return 0 }
        override suspend fun updateEvidence(id: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?, updatedAt: Long): Int = 0
        override suspend fun updateEvidenceForAccount(id: String, accountId: String, actualWeightKg: Double, materialConfirmed: Boolean, collectorConfirmed: Boolean, scalePhotoPath: String?, updatedAt: Long): Int = 0
        override suspend fun clearAll() { rows.value = emptyList() }
    }
}

private fun <T> MutableStateFlow<List<T>>.mapRows(predicate: (T) -> Boolean): Flow<List<T>> =
    map { rows -> rows.filter(predicate) }

private fun <T> MutableStateFlow<List<T>>.mapValue(predicate: (T) -> Boolean): Flow<T?> =
    map { rows -> rows.firstOrNull(predicate) }
