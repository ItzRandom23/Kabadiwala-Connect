package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyReadGroup
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.supplyRefreshDependencies
import org.junit.Assert.*
import org.junit.Test

class SupplyRefreshDependenciesTest {
    @Test fun paymentConfirmationDoesNotRefetchMarketplaceOrSafety() {
        assertEquals(setOf(SupplyReadGroup.HANDOVERS), supplyRefreshDependencies("supply-payment-1", AccountRole.COLLECTOR))
        assertEquals(setOf(SupplyReadGroup.PICKUPS), supplyRefreshDependencies("pickup-payment-1", AccountRole.COLLECTOR))
    }
    @Test fun collectorAcceptanceReconcilesReservationAndHandover() {
        assertEquals(setOf(SupplyReadGroup.TRADES, SupplyReadGroup.INVENTORY, SupplyReadGroup.HANDOVERS), supplyRefreshDependencies("offer-1", AccountRole.COLLECTOR))
        assertEquals(setOf(SupplyReadGroup.TRADES), supplyRefreshDependencies("offer-1", AccountRole.RECYCLER))
    }
    @Test fun completionReconcilesPickupInventoryAndTrackRecord() {
        assertEquals(setOf(SupplyReadGroup.PICKUPS, SupplyReadGroup.INVENTORY, SupplyReadGroup.PASSPORT), supplyRefreshDependencies("complete-1", AccountRole.COLLECTOR))
    }
    @Test fun pagingAndReadOnlyActionsDoNotReloadTheDashboard() {
        listOf("more-collector-lots", "more-recycler-offers", "route-advantage", "safety-routing", "passport-1", "anomalies-1", "receive-1").forEach {
            assertTrue(it, supplyRefreshDependencies(it, AccountRole.COLLECTOR).isEmpty())
        }
    }
    @Test fun newUnclassifiedMutationsKeepSafeFullRefresh() {
        assertEquals(SupplyReadGroup.entries.toSet(), supplyRefreshDependencies("future-mutation", AccountRole.COLLECTOR))
    }
}
