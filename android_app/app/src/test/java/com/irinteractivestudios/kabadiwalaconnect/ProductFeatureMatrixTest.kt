package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.SupplyChainState
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.productFeatureMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFeatureMatrixTest {
    @Test
    fun exposes_all_nine_reference_features() {
        val features = productFeatureMatrix(SupplyChainState())

        assertEquals(9, features.size)
        assertEquals(
            listOf(
                "direct_pickup",
                "collector_workflow",
                "route_benefit",
                "material_demand",
                "shared_pooling",
                "offline_paper_trail",
                "fair_payment",
                "growth_tracking",
                "key_languages"
            ),
            features.map { it.key }
        )
        assertTrue(features.all { it.title.isNotBlank() && it.evidence.isNotBlank() })
    }
}
