package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

internal enum class SupplyReadGroup { PICKUPS, INVENTORY, TRADES, MARKET, POOLS, HANDOVERS, PASSPORT, SAFETY }

/** Server side effects determine which cached sections need reconciliation. */
internal fun supplyRefreshDependencies(key: String, role: AccountRole): Set<SupplyReadGroup> {
    if (key.startsWith("more-") || key.startsWith("receive-") || key in setOf("route-advantage", "safety-routing") ||
        key.startsWith("passport-") || key.startsWith("anomalies-")) return emptySet()
    if (role == AccountRole.HOUSEHOLD) return setOf(SupplyReadGroup.PICKUPS)
    return when {
        key.startsWith("complete-") -> setOf(SupplyReadGroup.PICKUPS, SupplyReadGroup.INVENTORY, SupplyReadGroup.PASSPORT)
        key.startsWith("pickup-") || key.startsWith("availability-") || key.startsWith("schedule-") ||
            key.startsWith("status-") || key.startsWith("cancel-collector-") || key.startsWith("reassign-") ||
            key.startsWith("verify-household-qr-") -> setOf(SupplyReadGroup.PICKUPS)
        key == "create-bulk" || key.startsWith("cancel-bulk-") -> setOf(SupplyReadGroup.TRADES, SupplyReadGroup.INVENTORY)
        key.startsWith("offer-") -> if (role == AccountRole.COLLECTOR)
            setOf(SupplyReadGroup.TRADES, SupplyReadGroup.INVENTORY, SupplyReadGroup.HANDOVERS) else setOf(SupplyReadGroup.TRADES)
        key.startsWith("reject-offer-") || key.startsWith("counter-offer-") || key.startsWith("withdraw-offer-") -> setOf(SupplyReadGroup.TRADES)
        key == "create-demand" || key.startsWith("update-demand-") -> setOf(SupplyReadGroup.MARKET, SupplyReadGroup.POOLS)
        key.startsWith("pool-join-") || key.startsWith("pool-leave-") -> setOf(SupplyReadGroup.POOLS, SupplyReadGroup.INVENTORY)
        key.startsWith("pool-create-") || key.startsWith("pool-lock-") -> setOf(SupplyReadGroup.POOLS)
        key.startsWith("handover-pool-") -> setOf(SupplyReadGroup.POOLS, SupplyReadGroup.HANDOVERS)
        key.startsWith("handover-bulk-") -> setOf(SupplyReadGroup.TRADES, SupplyReadGroup.HANDOVERS)
        key.startsWith("collector-confirm-") || key.startsWith("supply-settlement-") ->
            setOf(SupplyReadGroup.HANDOVERS, SupplyReadGroup.TRADES, SupplyReadGroup.INVENTORY, SupplyReadGroup.POOLS)
        key.startsWith("supply-payment-") -> setOf(SupplyReadGroup.HANDOVERS)
        key.startsWith("safety-") -> setOf(SupplyReadGroup.SAFETY, SupplyReadGroup.PASSPORT)
        else -> SupplyReadGroup.entries.toSet() // New mutations remain safe until explicitly classified.
    }
}
