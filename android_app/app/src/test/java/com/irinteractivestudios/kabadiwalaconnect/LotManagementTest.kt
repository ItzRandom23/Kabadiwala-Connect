package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.repository.LotWriter
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotCondition
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotManagementViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotStep
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.Material
import com.irinteractivestudios.kabadiwalaconnect.util.PhotoValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotManagementTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test fun photoValidator_rejectsMissingFile() {
        val result = PhotoValidator.validate("C:/does-not-exist/lot.jpg")
        assertFalse(result.valid)
        assertEquals(com.irinteractivestudios.kabadiwalaconnect.util.Reason.MISSING, result.reason)
    }

    @Test fun weightValidation_rejectsZeroAndAcceptsValidKg() {
        val saved = mutableListOf<Lot>()
        val vm = LotManagementViewModel(writer(saved), "collector", now = { 10L })
        vm.chooseMaterial(Material.CABLES)
        vm.chooseCondition(LotCondition.INTACT)
        vm.setWeight("0")
        vm.confirmWeight()
        assertTrue(vm.state.value.weightError)
        vm.setWeight("12.5")
        vm.confirmWeight()
        assertEquals(LotStep.LOCATION, vm.state.value.step)
    }

    @Test fun save_createsExpectedOfflineLotIdAndFields() = runTest {
        val saved = mutableListOf<Lot>()
        val vm = LotManagementViewModel(writer(saved), "collector-1", now = { 1234L })
        vm.chooseMaterial(Material.PCB)
        vm.chooseCondition(LotCondition.DAMAGED)
        vm.setWeight("3.5")
        vm.confirmWeight()
        vm.setLocation("Pune")
        vm.confirmLocation()
        vm.save()
        advanceUntilIdle()
        assertTrue(saved.single().id.startsWith("LOT-1234-"))
        assertEquals("collector-1", saved.single().collectorId)
        assertEquals("Pune", saved.single().location)
        assertEquals(LotStep.SAVED, vm.state.value.step)
    }

    private fun writer(saved: MutableList<Lot>) = object : LotWriter {
        override fun observeLot(id: String): Flow<Lot?> = emptyFlow()
        override suspend fun save(lot: Lot) { saved += lot }
        override suspend fun cancel(id: String, updatedAt: Long) = true
    }
}
