package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import androidx.compose.runtime.Composable
import com.irinteractivestudios.kabadiwalaconnect.ui.util.RefreshOnResume

@Composable
internal fun TradeRefreshEffect(accountId: String, onRefresh: () -> Unit, onResume: () -> Unit = onRefresh) {
    RefreshOnResume(accountId, onRefresh = onRefresh, onResume = onResume,
        dataPaths = setOf("/household/", "/kabadiwala/", "/recycler/", "/handovers", "/pools", "/settlements", "/sync"))
}
