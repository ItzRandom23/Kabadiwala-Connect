package com.irinteractivestudios.kabadiwalaconnect.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus

/** All app destinations. Bottom tabs are the 5 [BottomTab] routes. */
object Destinations {
    const val AUTH = "auth"
    const val CREATE_LOT = "lots/create"
    const val CREATE_HOUSEHOLD_LISTING = "household/listings/create"
    const val MY_LOTS = "lots"
    const val LOT_DETAIL = "lots/detail/{lotId}"
    const val LOT_EDIT = "lots/edit/{lotId}"
    const val TRANSACTION_TIMELINE = "transactions/{lotId}/timeline"
    const val RECYCLER_DETAIL = "recyclers/detail/{recyclerId}"
    const val RECYCLERS_FOR_LOT = "recyclers/match/{lotId}"
    const val QUOTE_REQUEST = "quotes/request/{lotId}/{recyclerId}"
    const val QUOTE_COMPARE = "quotes/compare/{lotId}"
    const val HANDOVER_CREATE = "handovers/create/{lotId}/{quoteId}"
    const val HANDOVER_DOCUMENT = "handovers/document/{handoverId}"
    const val HANDOVER_DISPUTE = "handovers/dispute/{handoverId}"
    const val RATE_HANDOVER = "handovers/rate/{handoverId}"
    const val PAYMENT_CREATE = "payments/create"
    fun lotDetail(id: String) = "lots/detail/$id"
    fun lotEdit(id: String) = "lots/edit/$id"
    fun transactionTimeline(id: String) = "transactions/$id/timeline"
    fun recyclerDetail(id: String) = "recyclers/detail/$id"
    fun recyclersForLot(id: String) = "recyclers/match/$id"
    fun quoteRequest(lotId: String, recyclerId: String) = "quotes/request/$lotId/$recyclerId"
    fun quoteCompare(lotId: String) = "quotes/compare/$lotId"
    fun handoverCreate(lotId: String, quoteId: String) = "handovers/create/$lotId/$quoteId"
    fun handoverDocument(id: String) = "handovers/document/$id"
    fun handoverDispute(id: String) = "handovers/dispute/$id"
    fun rateHandover(id: String) = "handovers/rate/$id"
    const val HOME = "home"
    const val PRICES = "prices"
    const val RECYCLERS = "recyclers"
    const val EARNINGS = "earnings"
    const val SETTINGS = "settings"
    const val PROFILE = "settings/profile"
    const val RECYCLER_MARKETPLACE = "recycler/marketplace"
    const val RECYCLER_ORDERS = "recycler/orders"
    const val RECYCLER_PICKUPS = "recycler/pickups"
    const val RECYCLER_RATES = "recycler/rates"
    const val RECYCLER_PROFILE = "recycler/profile"
    const val RECYCLER_VERIFY = "recycler/verification"
    const val RECYCLER_SCAN = "recycler/scan"
    const val ADMIN_DASHBOARD = "admin/dashboard"
    const val KABADIWALA_INVENTORY = "kabadiwala/inventory"
    const val KABADIWALA_PICKUPS = "kabadiwala/pickups"
    const val KABADIWALA_LOTS = "kabadiwala/lots"
    const val KABADIWALA_TOOLS = "kabadiwala/tools"

    /** Secondary screens, reachable only through Settings (no bottom tab). */
    const val SAFETY = "settings/safety"
    const val HELP = "settings/help"
    const val REWARDS = "settings/rewards"
    const val SCHEMES = "settings/schemes"
    const val ACTIVITIES = "settings/activities"
    const val CHAT = "messages"
    const val NOTIFICATIONS = "notifications"
    const val CHAT_DETAIL = "messages/{conversationId}"
    const val HOUSEHOLD_DEAL = "household/deal/{kabadiwalaId}"
    fun householdDeal(id: String) = "household/deal/$id"
    const val DISPUTE_ANALYTICS = "disputes/analytics"
    fun chatDetail(id: String) = "messages/$id"

    // Legacy demo tabs are retained only for the offline demonstration entry
    // point. Live Kabadiwala sessions use KABADIWALA_TOP_LEVEL below.
    val TOP_LEVEL = listOf(HOME, PRICES, RECYCLERS, EARNINGS, SETTINGS)
    val KABADIWALA_TOP_LEVEL = listOf(HOME, KABADIWALA_INVENTORY, KABADIWALA_PICKUPS, KABADIWALA_LOTS, SETTINGS)
    val RECYCLER_TOP_LEVEL = listOf(RECYCLER_MARKETPLACE, RECYCLER_ORDERS, RECYCLER_PICKUPS, RECYCLER_RATES, RECYCLER_PROFILE)
    const val START = HOME

    /** Select the first protected screen only from a resolved session profile. */
    fun startForSession(account: AccountProfile?): String = when (account?.role) {
        null -> AUTH
        AccountRole.ADMIN -> ADMIN_DASHBOARD
        AccountRole.RECYCLER -> if (account.verificationStatus == RecyclerVerificationStatus.VERIFIED) RECYCLER_MARKETPLACE else RECYCLER_VERIFY
        AccountRole.HOUSEHOLD, AccountRole.COLLECTOR -> HOME
    }

    fun topLevelFor(role: AccountRole, newNavigation: Boolean = false) = when (role) {
        AccountRole.RECYCLER -> RECYCLER_TOP_LEVEL
        AccountRole.HOUSEHOLD -> HOUSEHOLD_BOTTOM_TABS.map { it.route }
        AccountRole.COLLECTOR -> if (newNavigation) KABADIWALA_TOP_LEVEL else TOP_LEVEL
        AccountRole.ADMIN -> emptyList()
    }
}

/** Bottom navigation tab: icon + label, no deep menus. */
data class BottomTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

val BOTTOM_TABS = listOf(
    BottomTab(Destinations.HOME, R.string.nav_home, Icons.Filled.Home, "nav_home"),
    BottomTab(Destinations.PRICES, R.string.nav_prices, Icons.Filled.CurrencyRupee, "nav_prices"),
    BottomTab(Destinations.RECYCLERS, R.string.nav_recyclers, Icons.Filled.Recycling, "nav_recyclers"),
    BottomTab(Destinations.EARNINGS, R.string.nav_earnings, Icons.Filled.AccountBalanceWallet, "nav_earnings"),
    BottomTab(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
)

val KABADIWALA_BOTTOM_TABS = listOf(
    BottomTab(Destinations.HOME, R.string.nav_home, Icons.Filled.Home, "nav_home"),
    BottomTab(Destinations.KABADIWALA_INVENTORY, R.string.nav_kabadiwala_inventory, Icons.Filled.Inventory2, "nav_inventory"),
    BottomTab(Destinations.KABADIWALA_PICKUPS, R.string.nav_kabadiwala_pickups, Icons.Filled.LocalShipping, "nav_pickups"),
    BottomTab(Destinations.KABADIWALA_LOTS, R.string.nav_kabadiwala_lots, Icons.Filled.Storefront, "nav_bulk_lots"),
    BottomTab(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
)

val HOUSEHOLD_BOTTOM_TABS = listOf(
    BottomTab(Destinations.HOME, R.string.nav_home, Icons.Filled.Home, "nav_home"),
    BottomTab(Destinations.PRICES, R.string.nav_prices, Icons.Filled.CurrencyRupee, "nav_prices"),
    BottomTab(Destinations.RECYCLERS, R.string.recycler_nearby, Icons.Filled.Store, "nav_recyclers"),
    BottomTab(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
)

val RECYCLER_BOTTOM_TABS = listOf(
    BottomTab(Destinations.RECYCLER_MARKETPLACE, R.string.nav_marketplace, Icons.Filled.Storefront, "nav_marketplace"),
    BottomTab(Destinations.RECYCLER_ORDERS, R.string.nav_orders, Icons.Filled.Inventory2, "nav_orders"),
    BottomTab(Destinations.RECYCLER_PICKUPS, R.string.nav_pickups, Icons.Filled.LocalShipping, "nav_pickups"),
    BottomTab(Destinations.RECYCLER_RATES, R.string.nav_rates, Icons.Filled.PriceChange, "nav_rates"),
    BottomTab(Destinations.RECYCLER_PROFILE, R.string.nav_profile, Icons.Filled.Person, "nav_profile")
)

/**
 * A limited workspace for Recycler accounts while authorization is pending.
 * Verification remains the primary destination, but account settings and
 * logout must remain reachable before marketplace access is approved.
 */
val RECYCLER_PENDING_BOTTOM_TABS = listOf(
    BottomTab(Destinations.RECYCLER_VERIFY, R.string.recycler_verification_title, Icons.Filled.Verified, "nav_verification"),
    BottomTab(Destinations.RECYCLER_PROFILE, R.string.nav_profile, Icons.Filled.Person, "nav_profile"),
    BottomTab(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
)
