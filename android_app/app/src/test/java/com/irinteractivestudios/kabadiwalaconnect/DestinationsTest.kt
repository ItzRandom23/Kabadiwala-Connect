package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.KABADIWALA_BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.recyclerVerificationStatusFromAuthorization
import com.irinteractivestudios.kabadiwalaconnect.domain.model.reconcileRecyclerAccountAuthorization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the 5-tab navigation structure and Home start destination. */
class DestinationsTest {

    @Test
    fun bottomTabs_areExactlyFive() {
        assertEquals(5, BOTTOM_TABS.size)
    }

    @Test
    fun appStartsOnHome() {
        assertEquals(Destinations.HOME, Destinations.START)
        assertTrue(Destinations.TOP_LEVEL.contains(Destinations.START))
    }

    @Test
    fun recyclerAuthorizationStatesNormalizeWithoutTreatingReviewAsVerified() {
        assertEquals(RecyclerVerificationStatus.VERIFIED, recyclerVerificationStatusFromAuthorization("verified"))
        assertEquals(RecyclerVerificationStatus.PENDING, recyclerVerificationStatusFromAuthorization("UNDER_REVIEW"))
        assertEquals(RecyclerVerificationStatus.PENDING, recyclerVerificationStatusFromAuthorization("EXPIRED"))
        assertEquals(RecyclerVerificationStatus.REJECTED, recyclerVerificationStatusFromAuthorization("REVIEW_REQUIRED"))
        assertEquals(RecyclerVerificationStatus.REJECTED, recyclerVerificationStatusFromAuthorization("REVOKED"))
        assertEquals(null, recyclerVerificationStatusFromAuthorization("unknown"))
    }

    @Test
    fun verifiedRecyclerIsRoutedOutOfVerificationAndIntoMarketplace() {
        assertEquals(
            Destinations.RECYCLER_MARKETPLACE,
            Destinations.verifiedRecyclerLanding(Destinations.RECYCLER_VERIFY, RecyclerVerificationStatus.VERIFIED)
        )
        assertEquals(
            null,
            Destinations.verifiedRecyclerLanding(Destinations.RECYCLER_VERIFY, RecyclerVerificationStatus.PENDING)
        )
        assertEquals(
            null,
            Destinations.verifiedRecyclerLanding(Destinations.RECYCLER_MARKETPLACE, RecyclerVerificationStatus.VERIFIED)
        )
    }

    @Test
    fun recyclerSessionStatusReconcilesOnlyForItsOwnProfile() {
        val pending = AccountProfile(
            "account-1", "recycler@example.test", AccountRole.RECYCLER,
            verificationStatus = RecyclerVerificationStatus.PENDING,
            profileId = "recycler-1"
        )
        assertEquals(
            RecyclerVerificationStatus.VERIFIED,
            reconcileRecyclerAccountAuthorization(pending, "recycler-1", "VERIFIED")?.verificationStatus
        )
        assertEquals(null, reconcileRecyclerAccountAuthorization(pending, "other-recycler", "VERIFIED"))
        assertEquals(
            null,
            reconcileRecyclerAccountAuthorization(
                pending.copy(role = AccountRole.HOUSEHOLD), "recycler-1", "VERIFIED"
            )
        )
        assertEquals(
            RecyclerVerificationStatus.PENDING,
            reconcileRecyclerAccountAuthorization(pending.copy(verificationStatus = RecyclerVerificationStatus.VERIFIED), "recycler-1", "unknown")?.verificationStatus
        )
    }

    @Test
    fun topLevel_containsAllFiveTabs() {
        assertEquals(5, Destinations.TOP_LEVEL.size)
        BOTTOM_TABS.forEach { tab ->
            assertTrue(Destinations.TOP_LEVEL.contains(tab.route))
        }
    }

    @Test
    fun routes_areUnique() {
        val routes = BOTTOM_TABS.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun testTags_areUnique() {
        val tags = BOTTOM_TABS.map { it.testTag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun secondaryScreens_areOutsideBottomTabs() {
        val tabRoutes = BOTTOM_TABS.map { it.route }.toSet()
        assertTrue(!tabRoutes.contains(Destinations.SAFETY))
        assertTrue(!tabRoutes.contains(Destinations.HELP))
    }

    @Test
    fun privateMessages_areAvailableToLiveHouseholdCollectorAndRecyclerAccounts() {
        listOf(AccountRole.HOUSEHOLD, AccountRole.COLLECTOR, AccountRole.RECYCLER).forEach { role ->
            assertTrue(Destinations.isAllowedForSession(role, Destinations.CHAT, false, null, null))
            assertTrue(Destinations.isAllowedForSession(role, Destinations.CHAT_DETAIL, false, null, null))
        }
    }

    @Test
    fun liveKabadiwalaNavigation_usesSupplyChainTabs() {
        assertEquals(5, KABADIWALA_BOTTOM_TABS.size)
        assertEquals(KABADIWALA_BOTTOM_TABS.map { it.route }, Destinations.topLevelFor(AccountRole.COLLECTOR, newNavigation = true))
        assertTrue(Destinations.KABADIWALA_TOP_LEVEL.contains(Destinations.KABADIWALA_INVENTORY))
    }

    @Test
    fun lotEdit_routeKeepsLotId() {
        assertEquals("lots/edit/LOT-123", Destinations.lotEdit("LOT-123"))
    }

    @Test
    fun resolvedAccountSelectsItsOwnFirstScreen() {
        assertEquals(Destinations.AUTH, Destinations.startForSession(null))
        assertEquals(Destinations.HOME, Destinations.startForSession(AccountProfile("h", "h@example.com", AccountRole.HOUSEHOLD)))
        assertEquals(Destinations.HOME, Destinations.startForSession(AccountProfile("c", "c@example.com", AccountRole.COLLECTOR)))
        assertEquals(Destinations.RECYCLER_MARKETPLACE, Destinations.startForSession(AccountProfile("r", "r@example.com", AccountRole.RECYCLER)))
        assertEquals(Destinations.RECYCLER_VERIFY, Destinations.startForSession(AccountProfile("p", "p@example.com", AccountRole.RECYCLER, verificationStatus = RecyclerVerificationStatus.PENDING)))
        assertEquals(Destinations.ADMIN_DASHBOARD, Destinations.startForSession(AccountProfile("a", "a@example.com", AccountRole.ADMIN)))
    }

    @Test
    fun liveCollectorCanReachExistingLotQuoteHandoverAndPaymentFlows() {
        val routes = listOf(
            Destinations.CREATE_LOT,
            Destinations.MY_LOTS,
            Destinations.LOT_DETAIL,
            Destinations.LOT_EDIT,
            Destinations.TRANSACTION_TIMELINE,
            Destinations.RECYCLERS_FOR_LOT,
            Destinations.QUOTE_REQUEST,
            Destinations.QUOTE_COMPARE,
            Destinations.HANDOVER_CREATE,
            Destinations.HANDOVER_DOCUMENT,
            Destinations.HANDOVER_DISPUTE,
            Destinations.PAYMENT_CREATE,
            Destinations.EARNINGS
        )

        routes.forEach { route ->
            assertTrue(
                "Live collector route should remain reachable: $route",
                Destinations.isAllowedForSession(
                    AccountRole.COLLECTOR,
                    route,
                    demoMode = false,
                    demoRole = null,
                    recyclerVerificationStatus = null
                )
            )
        }
    }

    @Test
    fun notificationRoutesResolveToExistingRoleAuthorizedDestinations() {
        assertEquals(
            Destinations.NOTIFICATIONS,
            Destinations.notificationDestination(Destinations.NOTIFICATIONS, AccountRole.HOUSEHOLD, false, null, null)
        )
        assertEquals(
            Destinations.NOTIFICATIONS,
            Destinations.notificationDestination(Destinations.NOTIFICATIONS, AccountRole.COLLECTOR, false, null, null)
        )
        assertEquals(
            Destinations.NOTIFICATIONS,
            Destinations.notificationDestination(Destinations.NOTIFICATIONS, AccountRole.RECYCLER, false, null, RecyclerVerificationStatus.PENDING)
        )
        assertEquals(
            "quotes/compare/lot-123",
            Destinations.notificationDestination("quotes/compare/lot-123", AccountRole.COLLECTOR, false, null, null)
        )
        assertEquals(
            "handovers/document/hand-123",
            Destinations.notificationDestination("handovers/document/hand-123", AccountRole.COLLECTOR, false, null, null)
        )
        assertEquals(
            Destinations.KABADIWALA_PICKUPS,
            Destinations.notificationDestination("kabadiwala/pickups/pickup-123", AccountRole.COLLECTOR, false, null, null)
        )
        assertEquals(
            Destinations.HOME,
            Destinations.notificationDestination("household/pickups/pickup-123/reassignment-options", AccountRole.HOUSEHOLD, false, null, null)
        )
        assertEquals(
            Destinations.EARNINGS,
            Destinations.notificationDestination("earnings", AccountRole.COLLECTOR, false, null, null)
        )
        assertEquals(
            Destinations.RECYCLER_ORDERS,
            Destinations.notificationDestination("recycler/orders", AccountRole.RECYCLER, false, null, RecyclerVerificationStatus.VERIFIED)
        )
        assertEquals(
            Destinations.RECYCLER_VERIFY,
            Destinations.notificationDestination("recycler/orders", AccountRole.RECYCLER, false, null, RecyclerVerificationStatus.PENDING)
        )
        assertEquals(null, Destinations.notificationDestination("payments/create/extra", AccountRole.COLLECTOR, false, null, null))
        assertEquals(null, Destinations.notificationDestination("admin/dashboard", AccountRole.COLLECTOR, false, null, null))
    }
}
