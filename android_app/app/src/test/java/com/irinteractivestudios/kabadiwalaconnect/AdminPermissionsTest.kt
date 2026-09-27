package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin.AdminSection
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin.adminSectionsForPermissions
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminPermissionsTest {
    @Test
    fun scopedDatasetAndPriceAdminStartsWithOnlyTools() {
        assertEquals(
            listOf(AdminSection.TOOLS),
            adminSectionsForPermissions(setOf("DATASET_EXPORT", "PRICE_MANAGEMENT"))
        )
    }

    @Test
    fun sectionsRequireTheirOwnCapabilities() {
        assertEquals(listOf(AdminSection.RECYCLERS), adminSectionsForPermissions(setOf("RECYCLER_REVIEW")))
        assertEquals(
            listOf(AdminSection.DISPUTES, AdminSection.ANOMALIES),
            adminSectionsForPermissions(setOf("DISPUTE_RESOLUTION"))
        )
        assertEquals(listOf(AdminSection.PAYMENTS), adminSectionsForPermissions(setOf("PAYMENT_VERIFICATION")))
    }

    @Test
    fun wildcardAdminCanAccessEverySection() {
        assertEquals(AdminSection.values().toList(), adminSectionsForPermissions(setOf("*")))
    }

    @Test
    fun emptyPermissionsDoNotExposeAdminSections() {
        assertEquals(emptyList<AdminSection>(), adminSectionsForPermissions(emptySet()))
    }
}
