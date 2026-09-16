package com.irinteractivestudios.kabadiwalaconnect.ui.demo

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.ui.components.DemoDataBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme

data class DemoKabadiwalaPartner(
    val id: String,
    val name: String,
    val area: String,
    val distance: String,
    val rating: String,
    val specialties: String,
    val connected: Boolean
)

data class DemoRecyclerPartner(
    val id: String,
    val name: String,
    val area: String,
    val rate: String,
    val materials: String,
    val connected: Boolean
)

private val demoKabadiwalas = listOf(
    DemoKabadiwalaPartner(DemoSessionStore.PRIMARY_KABADIWALA_ID, "Rafiq Scrap Services", "Kothrud, Pune", "0.8 km", "4.9", "Paper · Metal · Appliances", true),
    DemoKabadiwalaPartner("demo-kabadiwala-02", "Asha Eco Collect", "Kothrud, Pune", "1.4 km", "4.8", "Plastic · Cardboard · Metal", false),
    DemoKabadiwalaPartner("demo-kabadiwala-03", "Shree Ganesh Raddi", "Shivajinagar, Pune", "2.2 km", "4.7", "Paper · Copper · E-waste", false)
)

private val demoRecyclers = listOf(
    DemoRecyclerPartner(DemoSessionStore.PRIMARY_RECYCLER_ID, "GreenLoop Materials", "Bhosari, Pune", "₹620/kg", "Copper · PCB · Cables", true),
    DemoRecyclerPartner("demo-recycler-02", "Prithvi Circulars", "Hadapsar, Pune", "₹605/kg", "Copper · Metal", false),
    DemoRecyclerPartner("demo-recycler-03", "Nirmal Metals", "Aundh, Pune", "₹590/kg", "Mixed scrap", false)
)

@Composable
fun DemoHouseholdHomeScreen(
    onFindKabadiwala: () -> Unit,
    onOpenDeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_household_title), stringResource(R.string.demo_household_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_your_next_sale), Icons.Filled.Inventory2) {
                if (!state.householdLotPosted) {
                    Text(stringResource(R.string.demo_no_lot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_create_lot_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = DemoSessionStore::postHouseholdLot, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_create_lot)) }
                } else {
                    Text(stringResource(R.string.demo_copper_cable_weight), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_estimated_range), color = MaterialTheme.colorScheme.primary)
                    Text(householdStatus(state), color = if (state.householdTransactionConfirmed) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenDeal, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(if (state.selectedKabadiwalaId == DemoSessionStore.PRIMARY_KABADIWALA_ID) R.string.demo_open_connected_pickup else R.string.demo_choose_kabadiwala))
                    }
                }
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_collection_partner), Icons.Filled.LocalShipping) {
                Text(stringResource(R.string.demo_partner_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onFindKabadiwala, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_nearby_kabadiwalas)) }
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_price_before_pickup), Icons.Filled.Payments) {
                Text(stringResource(R.string.demo_copper_rate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_typical_range), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_trust_trail), Icons.Filled.Shield) {
                Text(stringResource(R.string.demo_trust_steps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_trust_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DemoHouseholdKabadiwalasScreen(onInvite: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_kabadiwalas_title), stringResource(R.string.demo_kabadiwalas_subtitle), modifier) {
        items(demoKabadiwalas, key = { it.id }) { partner ->
            DemoSectionCard(stringResource(R.string.demo_partner_rank, demoKabadiwalas.indexOf(partner) + 1, partner.name), Icons.Filled.LocalShipping) {
                Text("${partner.area} · ${partner.distance}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.demo_partner_rating, partner.rating, partner.specialties))
                Text(stringResource(if (partner.connected) R.string.demo_connected_partner else R.string.demo_read_only_partner), color = if (partner.connected) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { DemoSessionStore.selectKabadiwala(partner.id); onInvite(partner.id) },
                    enabled = state.householdLotPosted,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(stringResource(if (partner.connected) R.string.demo_schedule_partner else R.string.demo_preview_partner)) }
            }
        }
    }
}

@Composable
fun DemoHouseholdDealScreen(kabadiwalaId: String, onFindAnother: () -> Unit, modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    val partner = demoKabadiwalas.firstOrNull { it.id == kabadiwalaId } ?: demoKabadiwalas.first()
    val connected = partner.connected
    val householdRating = state.householdRating
    var selectedRating by remember(state.householdRating) { mutableStateOf(state.householdRating ?: 0) }
    LaunchedEffect(kabadiwalaId) { DemoSessionStore.selectKabadiwala(kabadiwalaId) }

    DemoListScreen(stringResource(R.string.demo_pickup_with, partner.name), stringResource(R.string.demo_pickup_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_partner_rating_title), Icons.Filled.Star) {
                Text(if (connected) stringResource(R.string.demo_completed_pickups, "★ ${partner.rating}", 286) else stringResource(R.string.demo_sample_profile, "★ ${partner.rating}"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(if (connected) R.string.demo_rating_detail else R.string.demo_not_connected_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_pickup_payment), Icons.Filled.CheckCircle) {
                if (!connected) {
                    Text(stringResource(R.string.demo_preview_only), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_choose_connected_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onFindAnother, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_choose_connected_partner)) }
                } else if (!state.householdLotPosted) {
                    Text(stringResource(R.string.demo_create_lot_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!state.householdPickupScheduled) {
                    Text(stringResource(R.string.demo_reference_line), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_pickup_slot), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = DemoSessionStore::scheduleHouseholdPickup, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_schedule_pickup)) }
                } else if (!state.householdItemsCollected) {
                    Text(stringResource(R.string.demo_pickup_scheduled), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_waiting_collect_payment), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!state.householdTransactionConfirmed) {
                    Text(stringResource(R.string.demo_items_collected_payment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_scan_qr_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.householdQrCode?.let { DemoQrCard(it, stringResource(R.string.demo_household_confirmation_qr)) }
                    Button(onClick = DemoSessionStore::confirmHouseholdQr, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_confirm_qr_payment)) }
                } else {
                    Text(stringResource(R.string.demo_transaction_complete), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KcTheme.extended.success)
                    Text(stringResource(R.string.demo_confirmed_paid_line), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (householdRating == null) {
                        Text(stringResource(R.string.demo_rate_kabadiwala), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { value -> FilterChip(selected = selectedRating == value, onClick = { selectedRating = value }, label = { Text("★ $value") }) }
                        }
                        Button(onClick = { if (selectedRating > 0) DemoSessionStore.rateKabadiwala(selectedRating) }, enabled = selectedRating > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_save_rating)) }
                    } else {
                        Text(stringResource(R.string.demo_rated_pickup, householdRating), color = KcTheme.extended.success, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (connected && state.householdPickupScheduled) {
            item { DemoChatCard(state.householdMessages, "Household") { DemoSessionStore.addHouseholdMessage("Household", it) } }
        }
    }
}

@Composable
fun DemoKabadiwalaHomeScreen(onInventory: () -> Unit, onPickups: () -> Unit, onLots: () -> Unit, modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    val householdRating = state.householdRating
    DemoListScreen(stringResource(R.string.demo_kabadiwala_title), stringResource(R.string.demo_kabadiwala_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_today_collection_value), Icons.Filled.TrendingUp) {
                Text("₹2,450", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_ahead_usual_day), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DemoAction(stringResource(R.string.demo_open_inventory), Icons.Filled.Inventory2, onInventory, Modifier.fillMaxWidth())
                    DemoAction(stringResource(R.string.demo_review_pickups), Icons.Filled.LocalShipping, onPickups, Modifier.fillMaxWidth())
                    DemoAction(stringResource(R.string.demo_route_buyers), Icons.Filled.Storefront, onLots, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_household_connection), Icons.Filled.LocalShipping) {
                Text(if (state.householdLotPosted && state.selectedKabadiwalaId == DemoSessionStore.PRIMARY_KABADIWALA_ID) stringResource(R.string.demo_household_line) else stringResource(R.string.demo_waiting_household), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(householdStatus(state), color = if (state.householdTransactionConfirmed) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                if (householdRating != null) Text(stringResource(R.string.demo_household_rating, householdRating), color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_formal_route_advantage), Icons.Filled.Route) {
                Text(stringResource(R.string.demo_route_buyer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_route_value), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.demo_route_confidence), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_growth_passport), Icons.Filled.TrendingUp) {
                Text(stringResource(R.string.demo_growth_level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_growth_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_pooling_opportunity), Icons.Filled.Groups) {
                Text(stringResource(R.string.demo_pooling_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_pooling_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DemoKabadiwalaInventoryScreen(modifier: Modifier = Modifier) {
    val inventory = listOf(
        stringResource(R.string.demo_copper_inventory) to stringResource(R.string.demo_copper_inventory_detail),
        stringResource(R.string.demo_pcb_inventory) to stringResource(R.string.demo_pcb_inventory_detail),
        stringResource(R.string.demo_pet_inventory) to stringResource(R.string.demo_pet_inventory_detail)
    )
    DemoListScreen(stringResource(R.string.demo_inventory_title), stringResource(R.string.demo_inventory_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_inventory_integrity), Icons.Filled.Shield) {
                Text(stringResource(R.string.demo_inventory_totals), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_inventory_safe), color = KcTheme.extended.success)
            }
        }
        items(inventory) { (material, detail) -> DemoSectionCard(material, Icons.Filled.Inventory2) { Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
fun DemoKabadiwalaPickupsScreen(modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_pickups_title), stringResource(R.string.demo_pickups_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_household_line), Icons.Filled.LocalShipping) {
                if (!state.householdLotPosted || state.selectedKabadiwalaId != DemoSessionStore.PRIMARY_KABADIWALA_ID) {
                    Text(stringResource(R.string.demo_pickup_waiting_household), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.demo_pickup_detail), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(householdStatus(state), color = if (state.householdTransactionConfirmed) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.householdPickupScheduled && !state.householdItemsCollected) Button(onClick = DemoSessionStore::kabadiwalaCollectHouseholdAndPay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_mark_collected_paid)) }
                    if (state.householdItemsCollected && !state.householdTransactionConfirmed) {
                        state.householdQrCode?.let { DemoQrCard(it, stringResource(R.string.demo_show_qr_household)) }
                        Text(stringResource(R.string.demo_household_must_confirm), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.householdPickupScheduled && state.selectedKabadiwalaId == DemoSessionStore.PRIMARY_KABADIWALA_ID) item { DemoChatCard(state.householdMessages, "Kabadiwala") { DemoSessionStore.addHouseholdMessage("Kabadiwala", it) } }
    }
}

@Composable
fun DemoKabadiwalaLotsScreen(modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_buyers_lots_title), stringResource(R.string.demo_buyers_lots_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_lot_copper), Icons.Filled.Storefront) {
                Text(stringResource(R.string.demo_formal_lot_detail), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!state.recyclerLotPosted) {
                    Text(stringResource(R.string.demo_formal_lot_start_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = DemoSessionStore::postRecyclerLot, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_create_formal_lot)) }
                } else {
                    Text(stringResource(R.string.demo_formal_lot_published), color = MaterialTheme.colorScheme.primary)
                    Text(recyclerStatus(state), color = if (state.recyclerTransactionConfirmed) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.recyclerOfferAccepted && state.recyclerQrCode == null && !state.recyclerTransactionConfirmed) Button(onClick = DemoSessionStore::kabadiwalaHandOverRecyclerAndPay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_mark_handed_paid)) }
                    val recyclerQrCode = state.recyclerQrCode
                    if (recyclerQrCode != null) {
                        DemoQrCard(recyclerQrCode, stringResource(R.string.demo_show_qr_recycler))
                        Text(stringResource(R.string.demo_recycler_confirms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.demo_choose_recycler), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(demoRecyclers, key = { it.id }) { recycler ->
            DemoSectionCard(recycler.name, Icons.Filled.Verified) {
                Text("${recycler.area} · ${recycler.rate}", color = MaterialTheme.colorScheme.primary)
                Text(recycler.materials, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (recycler.connected) R.string.demo_verified_connected_recycler else R.string.demo_read_only_buyer), color = if (recycler.connected) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { DemoSessionStore.selectRecycler(recycler.id) }, enabled = state.recyclerLotPosted, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(if (state.selectedRecyclerId == recycler.id) R.string.demo_selected else R.string.demo_choose_buyer)) }
            }
        }
        if (state.selectedRecyclerId == DemoSessionStore.PRIMARY_RECYCLER_ID && state.recyclerLotPosted) item { DemoChatCard(state.recyclerMessages, "Kabadiwala") { DemoSessionStore.addRecyclerMessage("Kabadiwala", it) } }
    }
}

@Composable
fun DemoRecyclerMarketplaceScreen(onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_recycler_marketplace_title), stringResource(R.string.demo_recycler_marketplace_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_connected_lot), Icons.Filled.Inventory2) {
                if (state.recyclerLotPosted && state.selectedRecyclerId == DemoSessionStore.PRIMARY_RECYCLER_ID) {
                    Text(stringResource(R.string.demo_lot_copper), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.demo_lot_seller_rate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        state.recyclerTransactionConfirmed -> Text(stringResource(R.string.demo_receipt_complete), color = KcTheme.extended.success, fontWeight = FontWeight.Bold)
                        state.recyclerQrCode != null -> { Text(stringResource(R.string.demo_payment_scan_qr), color = MaterialTheme.colorScheme.primary); Button(onClick = onOpenScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_open_qr_scanner)) } }
                        state.recyclerOfferAccepted -> Text(stringResource(R.string.demo_offer_accepted_waiting), color = MaterialTheme.colorScheme.primary)
                        else -> Button(onClick = DemoSessionStore::recyclerAcceptOffer, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_accept_offer)) }
                    }
                } else Text(stringResource(R.string.demo_waiting_publish), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DemoSectionCard(stringResource(R.string.demo_other_market_examples), Icons.Filled.Storefront) {
                Text(stringResource(R.string.demo_sample_lot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_sample_lot_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.recyclerLotPosted && state.selectedRecyclerId == DemoSessionStore.PRIMARY_RECYCLER_ID) item { DemoChatCard(state.recyclerMessages, "Recycler") { DemoSessionStore.addRecyclerMessage("Recycler", it) } }
    }
}

@Composable
fun DemoRecyclerOrdersScreen(onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    DemoListScreen(stringResource(R.string.demo_recycler_orders_title), stringResource(R.string.demo_recycler_orders_subtitle), modifier) {
        item {
            DemoSectionCard("HANDOVER-4096", Icons.Filled.QrCodeScanner) {
                Text(stringResource(R.string.demo_handover_lot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(recyclerStatus(state), color = if (state.recyclerTransactionConfirmed) KcTheme.extended.success else MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.recyclerQrCode != null) Button(onClick = onOpenScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_scan_handover)) }
            }
        }
    }
}

@Composable
fun DemoRecyclerScanScreen(modifier: Modifier = Modifier) {
    val state by DemoSessionStore.state.collectAsStateWithLifecycle()
    val recyclerQrCode = state.recyclerQrCode
    DemoListScreen(stringResource(R.string.demo_scan_title), stringResource(R.string.demo_scan_subtitle), modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_qr_receipt), Icons.Filled.QrCodeScanner) {
                when {
                    state.recyclerTransactionConfirmed -> { Text(stringResource(R.string.demo_receipt_confirmed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KcTheme.extended.success); Text(stringResource(R.string.demo_receipt_confirmed_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    recyclerQrCode != null -> { Text(stringResource(R.string.demo_signed_qr_match), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); DemoQrCard(recyclerQrCode, stringResource(R.string.demo_recycler_confirmation_qr)); Button(onClick = DemoSessionStore::confirmRecyclerQr, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_confirm_receipt)) } }
                    else -> Text(stringResource(R.string.demo_scan_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DemoProfileScreen(role: AccountRole, onExitDemo: () -> Unit, onResetDemo: () -> Unit = {}, modifier: Modifier = Modifier) {
    val profile = DemoDataProvider.profile(role)
    DemoListScreen(stringResource(R.string.demo_profile_title), stringResource(R.string.demo_profile_subtitle), modifier) {
        item {
            DemoSectionCard(profile.displayName.orEmpty(), Icons.Filled.Verified) {
                Text(when (role) { AccountRole.HOUSEHOLD -> stringResource(R.string.demo_household_role); AccountRole.COLLECTOR -> stringResource(R.string.demo_kabadiwala_role); AccountRole.RECYCLER -> stringResource(R.string.demo_recycler_role); AccountRole.ADMIN -> stringResource(R.string.demo_operator_role) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile.areaName ?: stringResource(R.string.demo_pune), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (role == AccountRole.RECYCLER) Text(stringResource(R.string.demo_authorization_verified), color = KcTheme.extended.success)
            }
        }
        item { OutlinedButton(onClick = onResetDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_reset_journey)) } }
        item { OutlinedButton(onClick = onExitDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_exit)) } }
    }
}

@Composable
fun DemoInfoScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    DemoListScreen(title, subtitle, modifier) {
        item {
            DemoSectionCard(stringResource(R.string.demo_presentation_data), Icons.Filled.CheckCircle) {
                Text(stringResource(R.string.demo_offline_safe), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_no_live_request), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DemoListScreen(title: String, subtitle: String, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { DemoDataBanner() }
        item { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, style = MaterialTheme.typography.headlineLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        content()
    }
}

@Composable
private fun DemoSectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text(title, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

@Composable
private fun DemoAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) { Icon(icon, contentDescription = null); Text(label, modifier = Modifier.padding(start = 5.dp)) }
}

@Composable
private fun DemoQrCard(code: String, label: String) {
    val bitmap = remember(code) { createDemoQr(code) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f)), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = label, modifier = Modifier.size(180.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DemoChatCard(messages: List<DemoMessage>, sender: String, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    DemoSectionCard(stringResource(R.string.demo_private_chat), Icons.Filled.Send) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { messages.takeLast(4).forEach { message -> Text("${localizedDemoSender(message.sender)}: ${localizedDemoMessage(message.body)}", style = MaterialTheme.typography.bodyMedium) } }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(240) }, label = { Text(stringResource(R.string.demo_message)) }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { onSend(draft); draft = "" }, enabled = draft.isNotBlank(), modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.demo_send), maxLines = 1) }
        }
    }
}

@Composable
private fun localizedDemoSender(sender: String): String = when (sender) {
    "Household" -> stringResource(R.string.demo_household_role).substringBefore(" /")
    "Kabadiwala" -> stringResource(R.string.demo_kabadiwala_role).substringBefore(" /")
    "Recycler" -> stringResource(R.string.demo_recycler_role).substringBefore(" /")
    else -> sender
}

@Composable
private fun localizedDemoMessage(body: String): String = when (body) {
    "Namaste! I can collect your lot today at 5:30 PM." -> stringResource(R.string.demo_household_message)
    "Your lot is matched to our verified facility." -> stringResource(R.string.demo_recycler_message)
    else -> body
}

@Composable
private fun householdStatus(state: DemoSessionState): String = when {
    state.householdTransactionConfirmed -> stringResource(R.string.demo_household_status_complete)
    state.householdItemsCollected -> stringResource(R.string.demo_household_status_collected)
    state.householdPickupScheduled -> stringResource(R.string.demo_household_status_scheduled)
    state.householdLotPosted -> stringResource(R.string.demo_household_status_posted)
    else -> stringResource(R.string.demo_household_status_create)
}

@Composable
private fun recyclerStatus(state: DemoSessionState): String = when {
    state.recyclerTransactionConfirmed -> stringResource(R.string.demo_recycler_status_complete)
    state.recyclerQrCode != null -> stringResource(R.string.demo_recycler_status_handover)
    state.recyclerOfferAccepted -> stringResource(R.string.demo_recycler_status_accepted)
    state.recyclerLotPosted -> stringResource(R.string.demo_recycler_status_published)
    else -> stringResource(R.string.demo_recycler_status_create)
}

private fun createDemoQr(value: String): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 480, 480)
    Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565).also { bitmap -> for (x in 0 until 480) for (y in 0 until 480) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) }
}.getOrNull()
