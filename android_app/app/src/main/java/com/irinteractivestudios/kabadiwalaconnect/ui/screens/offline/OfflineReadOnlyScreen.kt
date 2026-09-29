package com.irinteractivestudios.kabadiwalaconnect.ui.screens.offline

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
            Text("Offline access", style = MaterialTheme.typography.headlineMedium)
            Text("You can view saved account data after unlocking this device. Pickup acceptance, QR, inventory transfer and payments need a connection.")
            if (canUnlock) Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text("Unlock saved data") }
            else Text("Set a device screen lock and sign in online before using offline access.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out and clear saved data") }
        }
        return
    }
    val data = saved
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("offline_saved_data"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Saved account data", style = MaterialTheme.typography.headlineMedium)
            Text("Read-only while offline. Changes and transactions will be available after sign-in is restored.")
            Text(account.businessName ?: account.displayName ?: account.phoneNumber, style = MaterialTheme.typography.titleLarge)
            Text(account.address ?: account.areaName.orEmpty())
            data?.savedAt?.let { Text("Last saved ${DateFormat.getDateTimeInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
        }
        if (data == null) item { Text("Loading saved data…") }
        if (data != null) {
            item { Text("Listings and pickups", style = MaterialTheme.typography.titleLarge) }
            items(data.supply.listings.take(30), key = { "listing-${it.id}" }) { listing ->
                Text("${listing.materialCategory} · ${listing.estimatedWeight} kg · ${listing.status}")
            }
            items(data.supply.pickups.take(30), key = { "pickup-${it.id}" }) { pickup ->
                Text("Pickup · ${pickup.status}${pickup.finalAmount?.let { " · ₹$it" }.orEmpty()}")
            }
            item { Text("Material and market", style = MaterialTheme.typography.titleLarge) }
            items(data.supply.inventory.take(30), key = { "stock-${it.id}" }) { balance ->
                Text("${balance.materialCategory} · ${balance.availableKg} kg available")
            }
            items(data.supply.bulkLots.take(30), key = { "lot-${it.id}" }) { lot ->
                Text("${lot.materialCategory} · ${lot.quantityKg} kg · ${lot.status}")
            }
            items(data.prices, key = { "price-${it.id}-${it.location}" }) { price ->
                Text("${price.materialLabel} · ₹${price.ratePerKg}/kg")
            }
            item { Text("Recent messages and notifications", style = MaterialTheme.typography.titleLarge) }
            items(data.conversations) { message -> Text(message, maxLines = 2) }
            items(data.notifications, key = { "notification-${it.id}" }) { notification ->
                Column { Text(notification.title, style = MaterialTheme.typography.titleSmall); Text(notification.body, maxLines = 2) }
            }
            item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out and clear saved data") } }
        }
    }
}
