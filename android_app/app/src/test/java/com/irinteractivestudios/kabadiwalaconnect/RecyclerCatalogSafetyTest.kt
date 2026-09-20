package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.data.local.shouldSeedSyntheticRecyclerCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecyclerCatalogSafetyTest {
    @Test fun syntheticFacilities_areLimitedToExplicitOfflinePreviewBackend() {
        assertTrue(shouldSeedSyntheticRecyclerCatalog(true, "https://api.invalid/api/v1/"))
        assertFalse(shouldSeedSyntheticRecyclerCatalog(true, "http://140.245.232.208:4000/api/v1/"))
        assertFalse(shouldSeedSyntheticRecyclerCatalog(false, "https://api.invalid/api/v1/"))
        assertFalse(shouldSeedSyntheticRecyclerCatalog(true, "https://api.example.com/api/v1/"))
    }
}
