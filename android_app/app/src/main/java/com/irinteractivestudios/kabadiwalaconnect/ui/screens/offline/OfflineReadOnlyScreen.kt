package com.irinteractivestudios.kabadiwalaconnect.ui.screens.offline

import com.irinteractivestudios.kabadiwalaconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.FutureCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.PriceEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomSupplySnapshotStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.SupplySnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.remote.NotificationDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class SavedView(
    val supply: SupplySnapshot,
    val savedAt: Long?,
    val prices: List<PriceEntity>,
    val notifications: List<NotificationDto>,
    val conversations: List<String>
)

/** Deliberately separate from the authenticated NavHost: no API or mutation action is reachable. */
@Composable
fun OfflineReadOnlyScreen(
    account: AccountProfile,
    database: AppDatabase,
    unlocked: Boolean,
    canUnlock: Boolean,
    onUnlock: () -> Unit,
    onSignOut: () -> Unit
) {
    var saved by remember(account.profileId) { mutableStateOf<SavedView?>(null) }
    LaunchedEffect(unlocked, account.profileId) {
        if (!unlocked) {
            saved = null
            return@LaunchedEffect
        }
        saved = withContext(Dispatchers.IO) {
            val supply = RoomSupplySnapshotStore(database.supplySnapshotDao()).load(account.profileId, account.role.name)
            val future = FutureCacheStore(database.futureCacheDao())
            val conversations = future.conversations(account.profileId).take(20).mapNotNull { conversation ->
                future.messages(conversation.id, account.profileId).lastOrNull()?.body
            }
            SavedView(
                supply = supply?.first ?: SupplySnapshot(),
                savedAt = supply?.second,
                prices = database.priceDao().observe(account.areaName.orEmpty()).first().take(30),
                notifications = future.notifications(account.profileId).take(30),
                conversations = conversations
            )
        }
    }
    if (!unlocked) {
        Column(Modifier.fillMaxSize().padding(24.dp).testTag("offline_locked"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ui_copy_7a8288b667c2), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.ui_copy_55fee15d0832))
            if (canUnlock) Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy_f35eaad957ca)) }
            else Text(stringResource(R.string.ui_copy_cea213604e36), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy_ca7e208ae4d3)) }
        }
        return
    }
    val data = saved
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("offline_saved_data"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.ui_copy_1f19f088493c), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.ui_copy_e17be86e664d))
            Text(account.businessName ?: account.displayName ?: account.phoneNumber, style = MaterialTheme.typography.titleLarge)
            Text(account.address ?: account.areaName.orEmpty())
            data?.savedAt?.let { Text(stringResource(R.string.ui_copy_1ec450af9262, DateFormat.getDateTimeInstance().format(Date(it))), style = MaterialTheme.typography.bodySmall) }
        }
        if (data == null) item { Text(stringResource(R.string.ui_copy_3a294227b9ff)) }
        if (data != null) {
            item { Text(stringResource(R.string.ui_copy_87e359ce94d3), style = MaterialTheme.typography.titleLarge) }
            items(data.supply.listings.take(30), key = { "listing-${it.id}" }) { listing ->
                Text(stringResource(R.string.ui_copy_81d93b7191a3, listing.materialCategory, listing.estimatedWeight, listing.status))
            }
            items(data.supply.pickups.take(30), key = { "pickup-${it.id}" }) { pickup ->
                Text("Pickup · ${pickup.status}${pickup.finalAmount?.let { " · ₹$it" }.orEmpty()}")
            }
            item { Text(stringResource(R.string.ui_copy_481197311d6f), style = MaterialTheme.typography.titleLarge) }
            items(data.supply.inventory.take(30), key = { "stock-${it.id}" }) { balance ->
                Text(stringResource(R.string.ui_copy_1a4e077f78c0, balance.materialCategory, balance.availableKg))
            }
            items(data.supply.bulkLots.take(30), key = { "lot-${it.id}" }) { lot ->
                Text(stringResource(R.string.ui_copy_81d93b7191a3, lot.materialCategory, lot.quantityKg, lot.status))
            }
            items(data.prices, key = { "price-${it.id}-${it.location}" }) { price ->
                Text(stringResource(R.string.ui_copy_942b539440f3, price.materialLabel, price.ratePerKg))
            }
            item { Text(stringResource(R.string.ui_copy_267c38602281), style = MaterialTheme.typography.titleLarge) }
            items(data.conversations) { message -> Text(message, maxLines = 2) }
            items(data.notifications, key = { "notification-${it.id}" }) { notification ->
                Column { Text(notification.title, style = MaterialTheme.typography.titleSmall); Text(notification.body, maxLines = 2) }
            }
            item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy_ca7e208ae4d3)) } }
        }
    }
}
