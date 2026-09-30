package com.irinteractivestudios.kabadiwalaconnect.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DataChangeEvents
import com.irinteractivestudios.kabadiwalaconnect.notifications.PushRefreshEvents
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Visible screens react to confirmed writes, background replay and remote changes. */
@OptIn(FlowPreview::class)
@Composable
internal fun RefreshOnResume(
    accountId: String,
    onRefresh: () -> Unit,
    onResume: () -> Unit = onRefresh,
    dataPaths: Set<String> = emptySet()
) {
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    val resume by rememberUpdatedState(onResume)
    val paths by rememberUpdatedState(dataPaths)
    LaunchedEffect(owner, accountId) {
        if (accountId.isBlank()) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            resume()
            merge(
                DataChangeEvents.events.filter {
                    it.accountId == accountId && (paths.isEmpty() || it.path.endsWith("/activity/changes") || paths.any(it.path::contains))
                }.map { Unit },
                PushRefreshEvents.events.filter { it.accountId == accountId }.map { Unit }
            ).debounce(200).collect { refresh() }
        }
    }
}
