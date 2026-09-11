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
import androidx.compose.ui.graphics.vector.ImageVector
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

/** All app destinations. Bottom tabs are the 5 [BottomTab] routes. */
object Destinations {
    const val AUTH = "auth"
    const val CREATE_LOT = "lots/create"
    const val MY_LOTS = "lots"
    const val LOT_DETAIL = "lots/detail/{lotId}"
    const val RECYCLER_DETAIL = "recyclers/detail/{recyclerId}"
    const val QUOTE_REQUEST = "quotes/request/{lotId}/{recyclerId}"
    const val QUOTE_COMPARE = "quotes/compare/{lotId}"
    const val HANDOVER_CREATE = "handovers/create/{lotId}/{quoteId}"
    const val HANDOVER_DOCUMENT = "handovers/document/{handoverId}"
    const val HANDOVER_DISPUTE = "handovers/dispute/{handoverId}"
    const val RATE_HANDOVER = "handovers/rate/{handoverId}"
    const val PAYMENT_CREATE = "payments/create"
    fun lotDetail(id: String) = "lots/detail/$id"
    fun recyclerDetail(id: String) = "recyclers/detail/$id"
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

    /** Secondary screens, reachable only through Settings (no bottom tab). */
    const val SAFETY = "settings/safety"
    const val HELP = "settings/help"
    const val REWARDS = "settings/rewards"
    const val SCHEMES = "settings/schemes"
    const val ACTIVITIES = "settings/activities"
    const val CHAT = "messages"
    const val CHAT_DETAIL = "messages/{conversationId}"
    const val HOUSEHOLD_DEAL = "household/deal/{kabadiwalaId}"
    fun householdDeal(id: String) = "household/deal/$id"
    const val DISPUTE_ANALYTICS = "disputes/analytics"
    fun chatDetail(id: String) = "messages/$id"

    val TOP_LEVEL = listOf(HOME, PRICES, RECYCLERS, EARNINGS, SETTINGS)
    val RECYCLER_TOP_LEVEL = listOf(RECYCLER_MARKETPLACE, RECYCLER_ORDERS, RECYCLER_PICKUPS, RECYCLER_RATES, RECYCLER_PROFILE)
    const val START = HOME

    fun topLevelFor(role: AccountRole) = when (role) {
        AccountRole.RECYCLER -> RECYCLER_TOP_LEVEL
        AccountRole.HOUSEHOLD -> HOUSEHOLD_BOTTOM_TABS.map { it.route }
        AccountRole.COLLECTOR -> TOP_LEVEL
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
