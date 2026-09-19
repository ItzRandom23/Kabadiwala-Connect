package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

/**
 * The nine product promises represented in the Kabadiwala Connect comparison.
 *
 * This is deliberately a small, UI-facing index over existing live workflows.
 * It does not turn seeded data into a market claim: the screen still labels
 * estimates, queued evidence, and platform-generated progress honestly.
 */
enum class ProductFeatureState {
    LIVE,
    PILOT
}

data class ProductFeature(
    val key: String,
    val title: String,
    val state: ProductFeatureState,
    val evidence: String
)

fun productFeatureMatrix(state: SupplyChainState): List<ProductFeature> = listOf(
    ProductFeature(
        key = "direct_pickup",
        title = "Direct pickup from your door",
        state = ProductFeatureState.LIVE,
        evidence = "${state.pickups.size} pickup record(s) in this account"
    ),
    ProductFeature(
        key = "collector_workflow",
        title = "Collector-focused ways of working",
        state = ProductFeatureState.LIVE,
        evidence = "${state.inventory.sumOf { it.availableKg + it.reservedKg }.formatKg()} kg in inventory"
    ),
    ProductFeature(
        key = "route_benefit",
        title = "Optimized route benefits",
        state = ProductFeatureState.PILOT,
        evidence = if (state.routeAdvantage == null) "Compare net route value and logistics" else "${state.routeAdvantage.items.size} route option(s) compared"
    ),
    ProductFeature(
        key = "material_demand",
        title = "Factory demand for material data",
        state = ProductFeatureState.PILOT,
        evidence = "${state.requirements.size + state.demandIntelligence.size} demand signal(s) available"
    ),
    ProductFeature(
        key = "shared_pooling",
        title = "Coordinated shared pooling",
        state = ProductFeatureState.LIVE,
        evidence = "${state.pools.size} active pool(s), ${state.poolOpportunities.size} opportunity(ies)"
    ),
    ProductFeature(
        key = "offline_paper_trail",
        title = "Paper trail without internet",
        state = ProductFeatureState.PILOT,
        evidence = if (state.showingCachedEvidence) "Showing account-scoped saved evidence" else "Passport and sync evidence ready"
    ),
    ProductFeature(
        key = "fair_payment",
        title = "Payment fairness & rules",
        state = ProductFeatureState.LIVE,
        evidence = "Settlement review and discrepancy rules enabled"
    ),
    ProductFeature(
        key = "growth_tracking",
        title = "Collector growth tracking",
        state = ProductFeatureState.LIVE,
        evidence = state.passport?.let { "${it.formalHandoverCount} formal handover(s) · ${it.safetyModulesCompleted} safety module(s)" }
            ?: "Growth passport appears after the first formal handover"
    ),
    ProductFeature(
        key = "key_languages",
        title = "Key language availability",
        state = ProductFeatureState.PILOT,
        evidence = "English · Hindi · Marathi"
    )
)

private fun Double.formatKg(): String = "%.1f".format(this)
