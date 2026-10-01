package com.irinteractivestudios.kabadiwalaconnect.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.irinteractivestudios.kabadiwalaconnect.ui.util.RefreshOnResume
import com.irinteractivestudios.kabadiwalaconnect.ui.util.accountFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.util.AccountFeatureScopeViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.composable
import com.irinteractivestudios.kabadiwalaconnect.di.KcViewModelFactory
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import com.irinteractivestudios.kabadiwalaconnect.data.remote.isRetryableTransportFailure
import com.irinteractivestudios.kabadiwalaconnect.util.userFacingError
import com.irinteractivestudios.kabadiwalaconnect.util.localizedUserFacingError
import kotlinx.coroutines.CancellationException
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeNextAction
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.household.HouseholdDealScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.household.HouseholdHomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.household.NearbyKabadiwalasScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices.PricesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.prices.PricesViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers.RecyclersScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers.RecyclerDetailScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recyclers.RecyclersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.HelpScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SafetyScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings.SettingsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.ProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.profile.AccountProfileViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingRoute
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotManagementViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotRoute
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotDetailScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotEditScreen
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.quotes.QuoteRequestScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.quotes.QuoteComparisonScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.handovers.HandoverCreateScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.handovers.HandoverDocumentScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.handovers.DisputeCenterScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.payments.PaymentRecordScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerMarketplaceScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerOrdersScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerPickupsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerRatesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerVerificationScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerProfileViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerMarketplaceViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerOrdersViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.FutureFeatureViewModel
import com.irinteractivestudios.kabadiwalaconnect.notifications.PushRefreshEvents
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.RewardsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.SchemesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.DiyActivitiesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.ChatListScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.ChatDetailScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.DisputeAnalyticsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.VerifiedRatingScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.NotificationsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions.TransactionTimelineScreen
import com.irinteractivestudios.kabadiwalaconnect.util.ImagePipeline
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions.TransactionTimelineViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.supplychain.*
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin.AdminConsoleScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.admin.AdminConsoleViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoDataProvider
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoInfoScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoKabadiwalaHomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoKabadiwalaInventoryScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoKabadiwalaLotsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoKabadiwalaPickupsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoHouseholdHomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoHouseholdKabadiwalasScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoHouseholdDealScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoProfileScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoRecyclerMarketplaceScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoRecyclerOrdersScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoRecyclerScanScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoSessionStore
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SubmitReviewRequestDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.PreferencesUpdateDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Dispute
import com.irinteractivestudios.kabadiwalaconnect.domain.model.DisputeStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
import com.irinteractivestudios.kabadiwalaconnect.util.UiState
import com.irinteractivestudios.kabadiwalaconnect.util.DemoModePolicy
import java.io.File
import androidx.core.content.FileProvider

/**
 * Navigation graph. Starts at Home; the 5 bottom tabs are
 * top-level, Safety/Help are nested under Settings.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    factory: KcViewModelFactory,
    onLanguageChange: (String) -> Unit,
    onAppearanceChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    startDestination: String = Destinations.HOME,
    onLogout: () -> Unit = {},
    onDemo: () -> Unit = {},
    onDemoRole: (AccountRole) -> Unit = {},
    requestedDemoMode: Boolean = false,
    demoRole: AccountRole? = null,
    role: AccountRole = AccountRole.COLLECTOR,
    sessionAuthenticated: Boolean = false,
    onAuthFinished: (OnboardingViewModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Demo is a debug/testing surface only. Enforce the boundary here as well
    // as at the onboarding entry so a release deep link or caller cannot
    // activate fixture routes by passing demoMode=true.
    // The authenticated app owns feature state, not each bottom-tab back stack.
    // Related screens must observe the same confirmed records and pending actions.
    val featureAccount = factory.currentAccount?.profileId.orEmpty()
    val featureOwner: AccountFeatureScopeViewModel = viewModel(viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current))
    featureOwner.useAccount("$featureAccount:${role.name}")
    val demoMode = DemoModePolicy.enabled(BuildConfig.DEBUG, requestedDemoMode)
    val kabadiwalaDemo = demoMode && role == AccountRole.COLLECTOR && demoRole == AccountRole.COLLECTOR
    val pickupChatScope = androidx.compose.runtime.rememberCoroutineScope()
    val pickupChatContext = LocalContext.current
    val openPickupChat: (String) -> Unit = { pickupRequestId ->
        if (!demoMode) pickupChatScope.launch {
            runCatching {
                factory.apiService.createPickupConversation(
                    com.irinteractivestudios.kabadiwalaconnect.data.remote.CreatePickupConversationRequestDto(pickupRequestId)
                ).requireData()
            }.onSuccess { conversation ->
                navController.navigate(Destinations.chatDetail(conversation.id)) { launchSingleTop = true }
            }.onFailure {
                Toast.makeText(pickupChatContext, "Chat could not be opened. Check your connection and pickup status.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val openBulkChat: (String, String, String) -> Unit = { bulkLotId, collectorId, recyclerId ->
        if (!demoMode) pickupChatScope.launch {
            runCatching {
                factory.apiService.createConversation(
                    com.irinteractivestudios.kabadiwalaconnect.data.remote.CreateConversationRequestDto(
                        collectorId = collectorId,
                        recyclerId = recyclerId,
                        bulkLotId = bulkLotId
                    )
                ).requireData()
            }.onSuccess { conversation ->
                navController.navigate(Destinations.chatDetail(conversation.id)) { launchSingleTop = true }
            }.onFailure {
                Toast.makeText(pickupChatContext, "Chat could not be opened. Check your connection and accepted offer.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val appLifecycleOwner = LocalLifecycleOwner.current
    // Both sides of a live journey must see confirmed remote actions even when
    // FCM is unavailable. Read the bounded change feed, not the whole dashboard;
    // existing account-scoped events reconcile content only when it changed.
    val liveJourneyVisible = currentRoute in setOf(
        Destinations.HOME, Destinations.KABADIWALA_PICKUPS,
        Destinations.KABADIWALA_INVENTORY, Destinations.KABADIWALA_LOTS,
        Destinations.KABADIWALA_TOOLS, Destinations.RECYCLER_MARKETPLACE,
        Destinations.RECYCLER_ORDERS, Destinations.RECYCLER_SCAN
    )
    LaunchedEffect(role, factory.currentAccount?.profileId, demoMode, sessionAuthenticated, liveJourneyVisible, appLifecycleOwner) {
        val activityFeedRole = role == AccountRole.COLLECTOR || role == AccountRole.HOUSEHOLD || role == AccountRole.RECYCLER
        if (sessionAuthenticated && !demoMode && activityFeedRole && factory.currentAccount?.profileId?.isNotBlank() == true) {
            appLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    try {
                        factory.refreshActivity()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Preserve usable content; push, resume and the next
                        // bounded check remain independent recovery paths.
                    }
                    delay(if (liveJourneyVisible) 2_000L else 120_000L)
                }
            }
        }
    }
    LaunchedEffect(factory.currentAccount?.profileId, sessionAuthenticated, demoMode) {
        val owner = factory.currentAccount?.profileId ?: return@LaunchedEffect
        if (demoMode || !sessionAuthenticated) return@LaunchedEffect
        PushRefreshEvents.events.collect { event ->
            if (event.accountId == owner && factory.currentAccount?.profileId == owner) {
                runCatching { factory.refreshActivity() }
            }
        }
    }
    LaunchedEffect(currentRoute, role, demoRole, demoMode, factory.currentAccount?.verificationStatus) {
        // NavHost publishes its start destination after the first composition.
        // A null route during that initialization is not a role violation;
        // treating it as one sends signed-out users to Home before MainActivity
        // can keep them on the auth start destination.
        val route = currentRoute ?: return@LaunchedEffect
        val verificationStatus = factory.currentAccount?.verificationStatus
        val verifiedRecyclerLanding = if (!demoMode && role == AccountRole.RECYCLER) {
            Destinations.verifiedRecyclerLanding(route, verificationStatus)
        } else null
        if (verifiedRecyclerLanding != null) {
            navController.navigate(verifiedRecyclerLanding) {
                popUpTo(Destinations.RECYCLER_VERIFY) { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }
        if (!Destinations.isAllowedForSession(role, route, demoMode, demoRole, verificationStatus)) {
            val safeDestination = when (role) {
                AccountRole.RECYCLER -> if (demoMode || verificationStatus == RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_MARKETPLACE else Destinations.RECYCLER_VERIFY
                AccountRole.ADMIN -> Destinations.ADMIN_DASHBOARD
                AccountRole.COLLECTOR, AccountRole.HOUSEHOLD -> Destinations.HOME
            }
            navController.navigate(safeDestination) { popUpTo(0) }
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Keep navigation instantaneous. Screen-level state changes can still
        // animate, but changing destinations should not slide or fade the
        // entire app surface.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Destinations.AUTH) {
            val vm: OnboardingViewModel = viewModel(factory = factory)
            OnboardingRoute(
                viewModel = vm,
                onDemo = onDemo,
                onDemoRole = { onDemoRole(it) },
                onFinished = onAuthFinished
            )
        }
        composable(Destinations.ADMIN_DASHBOARD) {
            if (role == AccountRole.ADMIN) {
                val vm: AdminConsoleViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refresh)
                AdminConsoleScreen(
                    state = state,
                    onSection = vm::selectSection,
                    onRefresh = vm::refresh,
                    onSelect = vm::select,
                    onClearSelection = vm::clearSelection,
                    onAuthorizeRecycler = vm::authorizeRecycler,
                    onResolveDispute = vm::resolveDispute,
                    onVerifyPayment = vm::verifyPayment,
                    onDisputePickupPayment = vm::disputePickupPayment,
                    onReversePayment = vm::reversePayment,
                    onResolveAnomaly = vm::resolveAnomaly,
                    onImportPrice = vm::importPrice,
                    onUpdatePrice = vm::updatePrice,
                    onExportDataset = vm::exportDataset,
                    onLogout = onLogout
                )
            }
        }
        composable(Destinations.CREATE_LOT) {
            val vm: LotManagementViewModel = viewModel(factory = factory)
            LotRoute(
                vm,
                onSafety = { navController.navigate(Destinations.SAFETY) },
                onViewSaved = { vm.state.value.savedLotId?.let { navController.navigate(Destinations.lotDetail(it)) } },
                onHome = {
                    if (!navController.popBackStack(Destinations.HOME, false)) {
                        navController.navigate(Destinations.HOME) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                },
                demoMode = demoMode
            )
        }
        composable(Destinations.CREATE_HOUSEHOLD_LISTING) {
            val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            HouseholdListingCreateScreen(
                state = state,
                initialArea = factory.currentAccount?.areaName.orEmpty(),
                initialPickupAddress = factory.currentAccount?.address.orEmpty().ifBlank { factory.currentAccount?.areaName.orEmpty() },
                savedAccountLatitude = factory.currentAccount?.latitude,
                savedAccountLongitude = factory.currentAccount?.longitude,
                busy = state.busy,
                onBack = { navController.popBackStack() },
                onSuggestMaterial = vm::suggestHouseholdMaterial,
                onClearMaterialSuggestion = vm::clearHouseholdMaterialSuggestion,
                onCreateListing = { input, paths, draftId -> vm.createListing(input, paths, draftId) },
                onEstimateHouseholdPrice = vm::estimateHouseholdPrice,
                onClearHouseholdPriceEstimate = vm::clearHouseholdPriceEstimate
            )
        }
        composable(Destinations.MY_LOTS) {
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            LotsScreen(lots, onOpen = { navController.navigate(Destinations.lotDetail(it)) })
        }
        composable(Destinations.LOT_DETAIL, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("lotId").orEmpty()
            val lot by factory.lotWriter.observeLot(id).collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            var repeating by remember(id) { mutableStateOf(false) }
            var repeatError by remember(id) { mutableStateOf(false) }
            lot?.let { item -> LotDetailScreen(item, onCancel = { scope.launch { factory.lotWriter.cancel(id, System.currentTimeMillis()) } }, onEdit = { navController.navigate(Destinations.lotEdit(id)) }, onRequestQuotes = { navController.navigate(Destinations.quoteRequest(id, "none")) }, repeating = repeating, repeatError = repeatError, onTimeline = { navController.navigate(Destinations.transactionTimeline(id)) }, onRepeat = {
                if (!repeating) {
                    repeating = true
                    repeatError = false
                    scope.launch {
                        runCatching { factory.apiService.repeatLot(id, java.util.UUID.randomUUID().toString()).requireData() }
                            .onSuccess { runCatching { factory.refreshCatalogs() }; navController.popBackStack() }
                            .onFailure { repeatError = true }
                        repeating = false
                    }
                }
            }) }
        }
        composable(Destinations.LOT_EDIT, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("lotId").orEmpty()
            val lot by factory.lotWriter.observeLot(id).collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            var saving by remember(id) { mutableStateOf(false) }
            var errorMessage by remember(id) { mutableStateOf<String?>(null) }
            val lockedMessage = stringResource(R.string.lot_edit_locked_error)
            lot?.let { item ->
                if (item.status == LotStatus.SAVED) {
                    LotEditScreen(
                        lot = item,
                        saving = saving,
                        errorMessage = errorMessage,
                        onCancel = { navController.popBackStack() },
                        onSave = { weightKg, condition, notes ->
                            if (!saving) {
                                saving = true
                                errorMessage = null
                                scope.launch {
                                    val updated = runCatching {
                                        factory.lotWriter.update(item.copy(weightKg = weightKg, condition = condition, notes = notes))
                                    }.getOrDefault(false)
                                    saving = false
                                    if (updated) navController.popBackStack()
                                    else errorMessage = lockedMessage
                                }
                            }
                        }
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Text(stringResource(R.string.lot_edit_locked), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        androidx.compose.material3.Text(stringResource(R.string.lot_edit_locked_detail), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        composable(Destinations.TRANSACTION_TIMELINE, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("lotId").orEmpty()
            val vm: TransactionTimelineViewModel = viewModel(factory = factory)
            TransactionTimelineScreen(id, vm, onBack = { navController.popBackStack() })
        }
        composable(Destinations.HOME) {
            if (role == AccountRole.HOUSEHOLD && !demoMode) {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = { vm.refreshHousehold() }, onResume = vm::refreshIfStale, dataPaths = setOf("/household/", "/kabadiwala/", "/payments", "/sync"))
                // The NavHost destination can compose before its back-stack
                // entry is published. Start the first fetch from the screen's
                // own lifecycle, independent of that navigation timing.
                HouseholdSupplyScreen(
                    state = state,
                    onRefresh = vm::refreshHousehold,
                    onCreateListing = { navController.navigate(Destinations.CREATE_HOUSEHOLD_LISTING) },
                    onIncreaseRadius = vm::increaseHouseholdRadius,
                    onRetryPhoto = vm::retryListingPhoto,
                    onLoadListingPhotos = vm::loadHouseholdListingPhotos,
                    onRequestPickup = vm::requestPickup,
                    onCancelListing = vm::cancelListing,
                    onCancelPickup = vm::cancelPickup,
                    onReschedulePickup = vm::reschedulePickup,
                    onDecideSettlement = { pickupId, decision, reason, notes -> vm.decideHouseholdSettlement(pickupId, decision, reason, notes) },
                    onConfirmPaymentReceived = vm::confirmHouseholdPaymentReceived,
                    pickupQr = state.householdPickupQr,
                    pickupQrLoadingId = state.householdPickupQrLoadingId,
                    pickupQrError = state.householdPickupQrError,
                    onLoadPickupQr = vm::loadHouseholdPickupQr,
                    onClearPickupQr = vm::clearHouseholdPickupQr,
                    onCheckPickupQrScanned = vm::checkHouseholdPickupQrScanned,
                    onOpenPickupChat = openPickupChat,
                    onLoadMoreHistory = vm::loadMoreHouseholdHistory,
                    initialArea = factory.currentAccount?.areaName.orEmpty(),
                    busy = state.busy
                )
            } else if (role == AccountRole.HOUSEHOLD && demoMode) DemoHouseholdHomeScreen(
                onFindKabadiwala = { navController.navigate(Destinations.RECYCLERS) },
                onOpenDeal = {
                    val selectedId = DemoSessionStore.state.value.selectedKabadiwalaId
                        ?: DemoSessionStore.PRIMARY_KABADIWALA_ID
                    navController.navigate(Destinations.householdDeal(selectedId))
                }
            ) else if (role == AccountRole.HOUSEHOLD) HouseholdHomeScreen(
                area = factory.currentAccount?.areaName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_area_not_set),
                onCreateLot = { navController.navigate(Destinations.HOME) },
                onFindKabadiwala = { navController.navigate(Destinations.RECYCLERS) },
                onOpenDeal = { navController.navigate(Destinations.HOME) }
            ) else if (kabadiwalaDemo) {
                DemoKabadiwalaHomeScreen(
                    onInventory = { navController.navigate(Destinations.KABADIWALA_INVENTORY) },
                    onPickups = { navController.navigate(Destinations.KABADIWALA_PICKUPS) },
                    onLots = { navController.navigate(Destinations.KABADIWALA_LOTS) }
                )
            } else if (!demoMode) {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                 KabadiwalaSupplyScreen(state, KabadiwalaSection.HOME, vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup, vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, currentArea = factory.currentAccount?.areaName.orEmpty(), currentCollectorId = factory.currentAccount?.profileId.orEmpty(), listingPhotos = state.listingPhotos, listingPhotoErrors = state.listingPhotoErrors, onLoadListingPhotos = vm::loadKabadiwalaListingPhotos, onRouteEstimate = vm::loadRouteAdvantage, onCreatePool = vm::createPool, onJoinPool = vm::joinPool, onLeavePool = vm::leavePool, onLockPool = vm::lockPool, onPreparePoolHandover = vm::preparePoolHandover, onPrepareBulkHandover = vm::prepareBulkHandover, onConfirmCollectorHandover = vm::confirmCollectorHandover, onAcknowledgeSafety = vm::acknowledgeSafety, onCreateCapturedLot = { navController.navigate(Destinations.CREATE_LOT) }, onOpenTools = { navController.navigate(Destinations.KABADIWALA_TOOLS) }, onOpenPickups = { navController.navigate(Destinations.KABADIWALA_PICKUPS) }, onOpenInventory = { navController.navigate(Destinations.KABADIWALA_INVENTORY) }, onRejectPickup = vm::rejectPickup, onCancelPickup = vm::cancelKabadiwalaPickup, onReassignPickup = vm::reassignPickup, onRejectOffer = vm::rejectOffer, onCounterOffer = vm::counterOffer, onLoadSafetyRouting = vm::loadSafetyRouting, onLoadMaterialPassport = vm::loadMaterialPassport, onLoadAnomalies = vm::loadAnomalies, onDecideSupplySettlement = vm::decideSupplySettlement, onRecordPickupPayment = vm::recordPickupSettlementPayment, onVerifyHouseholdPickupQr = vm::verifyHouseholdPickupQr, onOpenPickupChat = openPickupChat, onOpenBulkChat = { lotId, recyclerId -> openBulkChat(lotId, factory.currentAccount?.profileId.orEmpty(), recyclerId) }, onResumeRefresh = vm::refreshIfStale, onConfirmSupplyPayment = vm::confirmSupplyPayment, onCheckHandoverReceipt = vm::checkCollectorHandoverReceipt, onLoadTradeDetails = vm::loadBulkTradeDetails, onPrepareTradeHandover = vm::prepareBulkHandoverAt, onHandoverCompleted = { vm.refreshKabadiwala(); navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = false }; launchSingleTop = true } })
            } else {
                val vm: HomeViewModel = viewModel(factory = factory)
                val state by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                state = state,
                demoMode = demoMode,
                household = role == AccountRole.HOUSEHOLD,
                onSeePrices = { navController.navigate(Destinations.PRICES) },
                onFindRecyclers = { navController.navigate(Destinations.RECYCLERS) },
                onRetry = null,
                onCreateLot = { navController.navigate(Destinations.CREATE_LOT) },
                onMyLots = { navController.navigate(Destinations.MY_LOTS) },
                onNextAction = { action, lotId ->
                    when (action) {
                        HomeNextAction.CREATE_LOT -> navController.navigate(Destinations.CREATE_LOT)
                        HomeNextAction.FIND_RECYCLERS -> navController.navigate(lotId?.let(Destinations::recyclersForLot) ?: Destinations.RECYCLERS)
                        HomeNextAction.REVIEW_QUOTES -> lotId?.let { navController.navigate(Destinations.quoteCompare(it)) }
                        HomeNextAction.PREPARE_HANDOVER -> lotId?.let { navController.navigate(Destinations.lotDetail(it)) }
                        HomeNextAction.RECORD_PAYMENT -> navController.navigate(Destinations.PAYMENT_CREATE)
                        HomeNextAction.REVIEW_DISPUTE -> navController.navigate(Destinations.DISPUTE_ANALYTICS)
                    }
                }
                )
            }
        }
        composable(Destinations.KABADIWALA_INVENTORY) {
            if (kabadiwalaDemo) DemoKabadiwalaInventoryScreen()
            else {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory); val state by vm.state.collectAsStateWithLifecycle()
                KabadiwalaSupplyScreen(state, KabadiwalaSection.INVENTORY, vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup, vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, currentArea = factory.currentAccount?.areaName.orEmpty(), currentCollectorId = featureAccount, onRejectPickup = vm::rejectPickup, onCancelPickup = vm::cancelKabadiwalaPickup, onReassignPickup = vm::reassignPickup, onRejectOffer = vm::rejectOffer, onCounterOffer = vm::counterOffer, onLoadSafetyRouting = vm::loadSafetyRouting, onLoadMaterialPassport = vm::loadMaterialPassport, onLoadAnomalies = vm::loadAnomalies, onDecideSupplySettlement = vm::decideSupplySettlement, onOpenBulkChat = { lotId, recyclerId -> openBulkChat(lotId, factory.currentAccount?.profileId.orEmpty(), recyclerId) }, onResumeRefresh = vm::refreshIfStale, onConfirmSupplyPayment = vm::confirmSupplyPayment, onCheckHandoverReceipt = vm::checkCollectorHandoverReceipt, onLoadTradeDetails = vm::loadBulkTradeDetails, onPrepareTradeHandover = vm::prepareBulkHandoverAt, onHandoverCompleted = { vm.refreshKabadiwala(); navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = false }; launchSingleTop = true } })
            }
        }
        composable(Destinations.KABADIWALA_PICKUPS) {
            if (kabadiwalaDemo) DemoKabadiwalaPickupsScreen()
            else {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory); val state by vm.state.collectAsStateWithLifecycle()
                KabadiwalaSupplyScreen(state, KabadiwalaSection.PICKUPS, vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup, vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, currentArea = factory.currentAccount?.areaName.orEmpty(), currentCollectorId = featureAccount, listingPhotos = state.listingPhotos, listingPhotoErrors = state.listingPhotoErrors, onLoadListingPhotos = vm::loadKabadiwalaListingPhotos, onRejectPickup = vm::rejectPickup, onCancelPickup = vm::cancelKabadiwalaPickup, onReassignPickup = vm::reassignPickup, onRejectOffer = vm::rejectOffer, onCounterOffer = vm::counterOffer, onLoadSafetyRouting = vm::loadSafetyRouting, onLoadMaterialPassport = vm::loadMaterialPassport, onLoadAnomalies = vm::loadAnomalies, onDecideSupplySettlement = vm::decideSupplySettlement, onRecordPickupPayment = vm::recordPickupSettlementPayment, onVerifyHouseholdPickupQr = vm::verifyHouseholdPickupQr, onOpenPickupChat = openPickupChat, onOpenBulkChat = { lotId, recyclerId -> openBulkChat(lotId, factory.currentAccount?.profileId.orEmpty(), recyclerId) }, onLoadMorePickups = vm::loadMoreCollectorPickups, onResumeRefresh = vm::refreshIfStale, onConfirmSupplyPayment = vm::confirmSupplyPayment, onCheckHandoverReceipt = vm::checkCollectorHandoverReceipt, onLoadTradeDetails = vm::loadBulkTradeDetails, onPrepareTradeHandover = vm::prepareBulkHandoverAt, onHandoverCompleted = { vm.refreshKabadiwala(); navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = false }; launchSingleTop = true } })
            }
        }
        composable(Destinations.KABADIWALA_LOTS) {
            if (kabadiwalaDemo) DemoKabadiwalaLotsScreen()
            else {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory); val state by vm.state.collectAsStateWithLifecycle()
                val capturedLots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
                KabadiwalaSupplyScreen(state, KabadiwalaSection.LOTS, vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup, vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, capturedLots, currentArea = factory.currentAccount?.areaName.orEmpty(), currentCollectorId = factory.currentAccount?.profileId.orEmpty(), onCreateCapturedLot = { navController.navigate(Destinations.CREATE_LOT) }, onOpenInventory = { navController.navigate(Destinations.KABADIWALA_INVENTORY) }, onPrepareBulkHandover = vm::prepareBulkHandover, onConfirmCollectorHandover = vm::confirmCollectorHandover, onRejectOffer = vm::rejectOffer, onCounterOffer = vm::counterOffer, onLoadMaterialPassport = vm::loadMaterialPassport, onLoadAnomalies = vm::loadAnomalies, onDecideSupplySettlement = vm::decideSupplySettlement, onOpenBulkChat = { lotId, recyclerId -> openBulkChat(lotId, factory.currentAccount?.profileId.orEmpty(), recyclerId) }, onLoadMoreLots = vm::loadMoreCollectorLots, onLoadMoreOffers = vm::loadMoreCollectorOffers, onResumeRefresh = vm::refreshIfStale, onConfirmSupplyPayment = vm::confirmSupplyPayment, onCheckHandoverReceipt = vm::checkCollectorHandoverReceipt, onLoadTradeDetails = vm::loadBulkTradeDetails, onPrepareTradeHandover = vm::prepareBulkHandoverAt, onHandoverCompleted = { vm.refreshKabadiwala(); navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = false }; launchSingleTop = true } })
            }
        }
        composable(Destinations.KABADIWALA_TOOLS) {
            val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            KabadiwalaSupplyScreen(state, KabadiwalaSection.TOOLS, vm::refreshKabadiwala, vm::acceptListing, vm::schedulePickup, vm::pickupStatus, vm::completePickup, vm::createBulkLot, vm::cancelBulkLot, vm::acceptOffer, currentArea = factory.currentAccount?.areaName.orEmpty(), currentCollectorId = factory.currentAccount?.profileId.orEmpty(), onRouteEstimate = vm::loadRouteAdvantage, onCreatePool = vm::createPool, onJoinPool = vm::joinPool, onLeavePool = vm::leavePool, onLockPool = vm::lockPool, onPreparePoolHandover = vm::preparePoolHandover, onPrepareBulkHandover = vm::prepareBulkHandover, onConfirmCollectorHandover = vm::confirmCollectorHandover, onAcknowledgeSafety = vm::acknowledgeSafety, onLoadSafetyRouting = vm::loadSafetyRouting, onLoadMaterialPassport = vm::loadMaterialPassport, onLoadAnomalies = vm::loadAnomalies, onDecideSupplySettlement = vm::decideSupplySettlement, onOpenBulkChat = { lotId, recyclerId -> openBulkChat(lotId, factory.currentAccount?.profileId.orEmpty(), recyclerId) }, onResumeRefresh = vm::refreshIfStale, onConfirmSupplyPayment = vm::confirmSupplyPayment, onCheckHandoverReceipt = vm::checkCollectorHandoverReceipt, onLoadTradeDetails = vm::loadBulkTradeDetails, onPrepareTradeHandover = vm::prepareBulkHandoverAt, onHandoverCompleted = { vm.refreshKabadiwala(); navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = false }; launchSingleTop = true } })
        }
        composable(Destinations.PRICES) {
            val vm: PricesViewModel = viewModel(factory = factory)
            vm.setCatalogRefresher { location -> factory.refreshCatalogs(location = location, force = true) }
            if (!demoMode) RefreshOnResume(featureAccount, onRefresh = vm::refresh)
            val state by vm.uiState.collectAsStateWithLifecycle()
            PricesScreen(state = if (demoMode) UiState.Success(DemoDataProvider.prices(LocalContext.current)) else state, vm = vm, speaker = factory.priceSpeaker, demoMode = demoMode)
        }
        composable(Destinations.RECYCLERS) {
            val vm: RecyclersViewModel = viewModel(factory = factory)
            vm.setCatalogRefresher { current -> factory.refreshCatalogs(current = current) }
            val state by vm.uiState.collectAsStateWithLifecycle()
            if (role == AccountRole.HOUSEHOLD && demoMode) DemoHouseholdKabadiwalasScreen(
                onInvite = { navController.navigate(Destinations.householdDeal(it)) }
            ) else if (role == AccountRole.HOUSEHOLD) {
                val supplyVm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val supplyState by supplyVm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = { supplyVm.refreshHousehold() }, onResume = supplyVm::refreshIfStale, dataPaths = setOf("/household/", "/kabadiwala/", "/sync"))
                HouseholdKabadiwalasScreen(
                    state = supplyState,
                    onRefresh = supplyVm::refreshHousehold,
                    onSearch = { area, radius -> supplyVm.searchHouseholdKabadiwalas(area, radius) },
                    onUseLocation = { location, radius -> supplyVm.searchHouseholdKabadiwalas(location.areaName.orEmpty(), radius, location) },
                    onLoadMore = supplyVm::loadMoreHouseholdKabadiwalas,
                    onOpenProfile = {
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set("kabadiwalaProfileLat", supplyState.kabadiwalaLatitude)
                            set("kabadiwalaProfileLon", supplyState.kabadiwalaLongitude)
                        }
                        navController.navigate(Destinations.kabadiwalaPublicProfile(it))
                    }
                )
            } else RecyclersScreen(state = state, vm = vm, onOpen = { navController.navigate(Destinations.recyclerDetail(it)) }, demoMode = demoMode)
        }
        composable(Destinations.KABADIWALA_PUBLIC_PROFILE, arguments = listOf(navArgument("kabadiwalaId") { type = NavType.StringType })) { entry ->
            val kabadiwalaId = entry.arguments?.getString("kabadiwalaId").orEmpty()
            val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val searchLatitude = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("kabadiwalaProfileLat")
            val searchLongitude = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("kabadiwalaProfileLon")
            LaunchedEffect(kabadiwalaId, searchLatitude, searchLongitude) {
                vm.openKabadiwalaProfile(
                    kabadiwalaId,
                    searchLatitude ?: factory.currentAccount?.latitude,
                    searchLongitude ?: factory.currentAccount?.longitude
                )
            }
            KabadiwalaPublicProfileScreen(
                profile = state.kabadiwalaProfile,
                loading = state.kabadiwalaProfileLoading,
                pickups = state.pickups.filter { it.kabadiwalaId == kabadiwalaId && it.status == "COMPLETED" && it.householdReviewRating == null },
                error = state.error,
                onRatePickup = vm::rateHouseholdPickup,
                onDismiss = { navController.popBackStack() }
            )
        }
        composable(Destinations.KABADIWALA_PICKUP_PRICING) {
            var pricing by remember { mutableStateOf<com.irinteractivestudios.kabadiwalaconnect.data.remote.PickupPricingDto?>(null) }
            var saving by remember { mutableStateOf(false) }
            var saved by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                runCatching { factory.apiService.getKabadiwalaPickupPricing().requireData() }
                    .onSuccess { pricing = it }
                    .onFailure { error = "Could not load pickup charges" }
            }
            KabadiwalaPickupPricingScreen(pricing, saving, error, saved, onSave = { input ->
                scope.launch {
                    saving = true; error = null; saved = false
                    runCatching { factory.apiService.updateKabadiwalaPickupPricing(input).requireData() }
                        .onSuccess { pricing = it; saved = true }
                        .onFailure { error = "Could not save pickup charges" }
                    saving = false
                }
            })
        }
        composable(Destinations.RECYCLERS_FOR_LOT, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val lotId = entry.arguments?.getString("lotId")
            val vm: RecyclersViewModel = viewModel(factory = factory)
            vm.setCatalogRefresher { current -> factory.refreshCatalogs(current = current) }
            val state by vm.uiState.collectAsStateWithLifecycle()
            RecyclersScreen(state = state, vm = vm, onOpen = { recyclerId ->
                navController.currentBackStackEntry?.savedStateHandle?.set("matchLotId", lotId)
                navController.navigate(Destinations.recyclerDetail(recyclerId))
            }, demoMode = demoMode, lotId = lotId)
        }
        composable(Destinations.HOUSEHOLD_DEAL, arguments = listOf(navArgument("kabadiwalaId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("kabadiwalaId").orEmpty()
            if (demoMode && role == AccountRole.HOUSEHOLD) DemoHouseholdDealScreen(
                kabadiwalaId = id,
                onFindAnother = {
                    if (!navController.popBackStack(Destinations.RECYCLERS, false)) navController.navigate(Destinations.RECYCLERS)
                }
            ) else LaunchedEffect(Unit) {
                navController.navigate(Destinations.HOME) { popUpTo(0) }
            }
        }
        composable(Destinations.RECYCLER_DETAIL, arguments = listOf(navArgument("recyclerId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("recyclerId").orEmpty()
            val matchedLotId = navController.previousBackStackEntry?.savedStateHandle?.get<String>("matchLotId")
            val recycler by factory.recyclerCatalog!!.observeRecycler(id).collectAsStateWithLifecycle(initialValue = null)
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            recycler?.let { item -> RecyclerDetailScreen(item, onCall = { /* Contact access is enabled only after an accepted quote or booking. */ }, onRequestQuote = {
                val lot = lots.firstOrNull { it.id == matchedLotId } ?: lots.firstOrNull { it.status == LotStatus.SAVED }
                if (lot != null) navController.navigate(Destinations.quoteRequest(lot.id, item.id)) else navController.navigate(Destinations.CREATE_LOT)
            }) }
        }
        composable(Destinations.QUOTE_REQUEST, arguments = listOf(navArgument("lotId") { type = NavType.StringType }, navArgument("recyclerId") { type = NavType.StringType })) { entry ->
            val lotId = entry.arguments?.getString("lotId").orEmpty()
            val recyclerId = entry.arguments?.getString("recyclerId").orEmpty()
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            val recyclers by factory.recyclers.observeRecyclers().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            LaunchedEffect(lotId) { factory.refreshCatalogs(force = true) }
            QuoteRequestScreen(lots, recyclers, lotId, recyclerId, factory.quoteRepository, onSubmitted = { id -> scope.launch { factory.lotWriter.lock(id, System.currentTimeMillis()); navController.navigate(Destinations.quoteCompare(id)) } }, onRefreshCatalogs = { scope.launch { factory.refreshCatalogs(force = true) } })
        }
        composable(Destinations.QUOTE_COMPARE, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val lotId = entry.arguments?.getString("lotId").orEmpty()
            val quotes by factory.quoteRepository.observeForLot(lotId).collectAsStateWithLifecycle(initialValue = emptyList())
            val lot by factory.lotWriter.observeLot(lotId).collectAsStateWithLifecycle(initialValue = null)
            val prices by factory.priceCatalog.observePrices().collectAsStateWithLifecycle(initialValue = emptyList())
            val recyclers by factory.recyclerCatalog!!.observeRecyclers().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            var refreshing by remember(lotId) { mutableStateOf(false) }
            var refreshError by remember(lotId) { mutableStateOf(false) }
            var processingQuoteId by remember(lotId) { mutableStateOf<String?>(null) }
            var actionError by remember(lotId) { mutableStateOf(false) }
            fun refreshQuotes() {
                val selectedLot = lot ?: return
                if (refreshing) return
                scope.launch {
                    refreshing = true
                    refreshError = false
                    runCatching { factory.quoteRepository.refresh(selectedLot, recyclers.firstOrNull()) }
                        .onFailure { refreshError = true }
                    refreshing = false
                }
            }
            RefreshOnResume(featureAccount, onRefresh = ::refreshQuotes)
            QuoteComparisonScreen(quotes, lot, prices.firstOrNull { it.materialLabel == lot?.materialLabel }, factory.quoteRepository, onRejected = {
                scope.launch {
                    lot?.let { factory.lotWriter.reopenQuote(it.id, System.currentTimeMillis()) }
                    refreshQuotes()
                }
            }, onRefresh = ::refreshQuotes, refreshing = refreshing, refreshError = refreshError, processingQuoteId = processingQuoteId, actionError = actionError, onAccepted = { quoteId, id ->
                if (processingQuoteId == null) {
                    actionError = false
                    processingQuoteId = quoteId
                    scope.launch {
                        try {
                            if (factory.quoteRepository.accept(quoteId)) {
                                factory.lotWriter.confirm(id, System.currentTimeMillis())
                                val quote = quotes.firstOrNull { it.id == quoteId }
                                val conversation = runCatching { factory.apiService.createConversation(com.irinteractivestudios.kabadiwalaconnect.data.remote.CreateConversationRequestDto(id, quoteId, recyclerId = quote?.recyclerId)).requireData() }.getOrNull()
                                // A conversation cannot be created offline because the
                                // server owns its id. Continue to the offline-capable
                                // handover flow instead of opening an empty, unusable chat.
                                navController.navigate(conversation?.id?.let(Destinations::chatDetail) ?: Destinations.handoverCreate(id, quoteId))
                            }
                        } catch (_: Exception) {
                            actionError = true
                        } finally {
                            processingQuoteId = null
                        }
                    }
                }
            })
        }
        composable(Destinations.HANDOVER_CREATE, arguments = listOf(navArgument("lotId") { type = NavType.StringType }, navArgument("quoteId") { type = NavType.StringType })) { entry ->
            val lotId = entry.arguments?.getString("lotId").orEmpty(); val quoteId = entry.arguments?.getString("quoteId").orEmpty()
            val lot by factory.lotWriter.observeLot(lotId).collectAsStateWithLifecycle(initialValue = null)
            val quotes by factory.quoteRepository.observeForLot(lotId).collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            var saving by remember(lotId, quoteId) { mutableStateOf(false) }
            var saveError by remember(lotId, quoteId) { mutableStateOf(false) }
            lot?.let { item -> quotes.firstOrNull { it.id == quoteId }?.let { quote ->
                HandoverCreateScreen(item, quote, item.collectorId.ifBlank { "local-collector" }, saving = saving, saveError = saveError) { type, location, time ->
                    if (!saving) scope.launch {
                        saving = true
                        saveError = false
                        runCatching { factory.handoverRepository.create(item, quote, item.collectorId.ifBlank { "local-collector" }, type, location, time) }
                            .onSuccess { navController.navigate(Destinations.handoverDocument(it.id)) }
                            .onFailure { saveError = true }
                        saving = false
                    }
                }
            } }
        }
        composable(Destinations.HANDOVER_DOCUMENT, arguments = listOf(navArgument("handoverId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("handoverId").orEmpty()
            val handover by factory.handoverRepository.observe(id).collectAsStateWithLifecycle(initialValue = null)
            val lot by factory.lotWriter.observeLot(handover?.lotId.orEmpty()).collectAsStateWithLifecycle(initialValue = null)
            val prices by factory.priceCatalog.observePrices().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            var pendingScalePhoto by rememberSaveable(id) { mutableStateOf<String?>(null) }
            var evidenceError by rememberSaveable(id) { mutableStateOf(false) }
            val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                val path = pendingScalePhoto
                pendingScalePhoto = null
                if (ok && path != null) {
                    handover?.let { item ->
                        scope.launch {
                            val normalized = withContext(Dispatchers.IO) {
                                runCatching {
                                    ImagePipeline.prepareForUpload(
                                        File(path),
                                        File(context.filesDir, "handover_photos")
                                    )
                                }.getOrNull()
                            }
                            File(path).delete()
                            if (normalized == null) {
                                evidenceError = true
                            } else {
                                runCatching {
                                    factory.handoverRepository.updateEvidence(
                                        id,
                                        item.actualWeightKg ?: item.weightKg,
                                        item.materialConfirmed,
                                        item.collectorConfirmed,
                                        normalized.absolutePath
                                    )
                                }.onSuccess {
                                    evidenceError = false
                                }.onFailure {
                                    evidenceError = true
                                }
                            }
                        }
                    } ?: File(path).delete()
                } else {
                    path?.let { File(it).delete() }
                }
            }
            val launchScaleCamera: () -> Unit = {
                val directory = File(context.filesDir, "handover_photos").apply { mkdirs() }
                val file = File(directory, "scale_${System.currentTimeMillis()}.jpg")
                runCatching {
                    pendingScalePhoto = file.absolutePath
                    camera.launch(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    )
                }.onFailure {
                    pendingScalePhoto = null
                    file.delete()
                    evidenceError = true
                }
                Unit
            }
            val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) launchScaleCamera() else evidenceError = true
            }
            var marking by remember(id) { mutableStateOf(false) }
            var markError by remember(id) { mutableStateOf(false) }
            handover?.let { item ->
                HandoverDocumentScreen(
                    handover = item,
                    photoPath = lot?.localPhotoPath,
                    referencePrice = prices.firstOrNull { it.materialLabel == item.materialLabel },
                    onUpdateEvidence = { weight, materialMatch, photo ->
                        scope.launch {
                            runCatching {
                                factory.handoverRepository.updateEvidence(id, weight, materialMatch, true, photo)
                            }.onSuccess {
                                evidenceError = false
                            }.onFailure {
                                evidenceError = true
                            }
                        }
                    },
                    onCaptureScalePhoto = {
                        evidenceError = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchScaleCamera()
                        } else {
                            runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }
                                .onFailure { evidenceError = true }
                        }
                    },
                    onOpenDispute = { navController.navigate(Destinations.handoverDispute(id)) },
                    onRate = { navController.navigate(Destinations.rateHandover(id)) },
                    actionInFlight = marking,
                    actionError = markError,
                    evidenceError = evidenceError,
                    onMark = {
                        if (!marking) scope.launch {
                            marking = true
                            markError = false
                            runCatching { factory.handoverRepository.markHandedOver(id) }
                                .onFailure { markError = true }
                            marking = false
                        }
                    }
                )
            }
        }
        composable(Destinations.RATE_HANDOVER, arguments = listOf(navArgument("handoverId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("handoverId").orEmpty()
            val handover by factory.handoverRepository.observe(id).collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            var submitting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            handover?.let { item ->
                VerifiedRatingScreen(item.recyclerName, submitting, error, onSubmit = { rating, pickup, payment, comment ->
                    scope.launch {
                        submitting = true
                        error = null
                        runCatching { factory.apiService.submitRecyclerReview(SubmitReviewRequestDto(id, rating, pickup, payment, comment.ifBlank { null })).requireData() }
                            .onSuccess { navController.popBackStack() }
                            .onFailure { error = "This review could not be submitted. Check your connection and try again." }
                        submitting = false
                    }
                })
            }
        }
        composable(Destinations.HANDOVER_DISPUTE, arguments = listOf(navArgument("handoverId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("handoverId").orEmpty()
            val handover by factory.handoverRepository.observe(id).collectAsStateWithLifecycle(initialValue = null)
            val disputes by factory.disputeRepository.observeForHandover(id).collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            var disputePending by remember(id, featureAccount) { mutableStateOf(false) }
            var disputeError by remember(id, featureAccount) { mutableStateOf<String?>(null) }
            val disputeClientId = rememberSaveable(id, featureAccount) { "DSP-${java.util.UUID.randomUUID()}" }
            handover?.let { item ->
                DisputeCenterScreen(item, disputes, pending = disputePending, error = disputeError) { type, description ->
                    if (disputePending) return@DisputeCenterScreen
                    val owner = factory.currentAccount?.profileId ?: return@DisputeCenterScreen
                    disputePending = true
                    disputeError = null
                    scope.launch {
                        try {
                        if (factory.currentAccount?.profileId != owner) return@launch
                        val localId = disputeClientId
                        val local = Dispute(localId, item.id, item.lotId, item.collectorId, item.recyclerId, type, description, item.weightKg, item.actualWeightKg, DisputeStatus.SAVED_LOCALLY, System.currentTimeMillis(), false, null)
                        try {
                            val body = JsonObject().apply {
                                addProperty("clientDisputeId", localId)
                                addProperty("type", type.name)
                                addProperty("description", description)
                                addProperty("claimedWeight", item.weightKg)
                                item.actualWeightKg?.let { addProperty("actualValue", it) }
                            }
                            val remoteId = factory.apiService.disputeHandover(id, body).requireData().id
                            if (factory.currentAccount?.profileId != owner) return@launch
                            factory.disputeRepository.save(local)
                            factory.disputeRepository.markSynced(localId, remoteId)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            if (factory.currentAccount?.profileId != owner) return@launch
                            if (!failure.isRetryableTransportFailure()) throw failure
                            val payload = JsonObject().apply {
                                addProperty("id", localId)
                                addProperty("handoverId", id)
                                addProperty("type", type.name)
                                addProperty("description", description)
                                addProperty("claimedWeight", item.weightKg)
                                item.actualWeightKg?.let { addProperty("actualValue", it) }
                            }
                            factory.syncQueue.enqueueOnce(com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity(operation = "CREATE_DISPUTE", payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = owner, idempotencyKey = localId))
                            if (factory.currentAccount?.profileId != owner) return@launch
                            factory.disputeRepository.save(local)
                            factory.requestSync()
                        }
                        if (factory.currentAccount?.profileId == owner) navController.popBackStack()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            if (factory.currentAccount?.profileId == owner) disputeError = userFacingError(failure, "The dispute could not be submitted. Try again.")
                        } finally {
                            if (factory.currentAccount?.profileId == owner) disputePending = false
                        }
                    }
                }
            }
        }
        composable(Destinations.EARNINGS) {
            val vm: EarningsViewModel = viewModel(factory = factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val payments by factory.paymentRepository.observePayments().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            var refreshing by remember { mutableStateOf(false) }
            var refreshError by remember { mutableStateOf(false) }
            EarningsScreen(state = state, payments = payments, onRetry = null, refreshing = refreshing, refreshError = refreshError, onRefresh = {
                if (!refreshing) scope.launch {
                    refreshing = true
                    refreshError = false
                    runCatching { factory.refreshEarnings() }.onFailure { refreshError = true }
                    refreshing = false
                }
            }, onRecordPayment = { navController.navigate(Destinations.PAYMENT_CREATE) })
        }
        composable(Destinations.PAYMENT_CREATE) {
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            PaymentRecordScreen(lots.filter { it.status == LotStatus.COLLECTOR_CONFIRMED || it.status == LotStatus.HANDED_OVER }, factory.paymentRepository) { lotId, amount -> scope.launch { if (factory.lotWriter.markPaid(lotId, amount, System.currentTimeMillis())) navController.popBackStack() } }
        }
        composable(Destinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val language by vm.language.collectAsStateWithLifecycle()
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            val exportShareTitle = stringResource(R.string.settings_export_share_title)
            val accountId = factory.currentAccount?.profileId
            var smsNotificationsEnabled by remember(accountId) { mutableStateOf(true) }
            var pushNotificationsEnabled by remember(accountId) { mutableStateOf(true) }
            LaunchedEffect(accountId, demoMode) {
                if (!demoMode && accountId != null && !BuildConfig.API_BASE_URL.contains(".invalid")) {
                    runCatching { factory.apiService.getPreferences().requireData() }
                        .onSuccess { preferences ->
                            if (factory.currentAccount?.profileId == accountId) {
                                smsNotificationsEnabled = preferences.smsNotificationsEnabled
                                pushNotificationsEnabled = preferences.pushNotificationsEnabled
                            }
                        }
                }
            }
            val syncItems by factory.syncQueue.observeForAccount(factory.currentAccount?.profileId.orEmpty()).collectAsStateWithLifecycle(initialValue = emptyList())
            SettingsScreen(
                language = language,
                appearance = appearance,
                smsNotificationsEnabled = smsNotificationsEnabled,
                pushNotificationsEnabled = pushNotificationsEnabled,
                appVersion = vm.appVersion,
                onLanguageChange = { tag ->
                    if (vm.setLanguage(tag)) {
                        factory.updateStoredAccountLanguage(tag)
                        onLanguageChange(tag)
                    } else {
                        Toast.makeText(pickupChatContext, "Could not save language. Try again.", Toast.LENGTH_LONG).show()
                    }
                },
                onAppearanceChange = { mode -> vm.setAppearance(mode); onAppearanceChange(mode) },
                onSmsNotificationsChange = { enabled ->
                    val previous = smsNotificationsEnabled
                    smsNotificationsEnabled = enabled
                    if (!demoMode) scope.launch {
                        runCatching {
                            factory.apiService.updatePreferences(PreferencesUpdateDto(smsNotificationsEnabled = enabled)).requireData()
                        }.onFailure {
                            if (factory.currentAccount?.profileId == accountId) {
                                smsNotificationsEnabled = previous
                                Toast.makeText(context, R.string.settings_notification_save_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onPushNotificationsChange = { enabled ->
                    val previous = pushNotificationsEnabled
                    pushNotificationsEnabled = enabled
                    if (!demoMode) scope.launch {
                        runCatching {
                            factory.apiService.updatePreferences(PreferencesUpdateDto(pushNotificationsEnabled = enabled)).requireData()
                        }.onFailure {
                            if (factory.currentAccount?.profileId == accountId) {
                                pushNotificationsEnabled = previous
                                Toast.makeText(context, R.string.settings_notification_save_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                 onCheckForUpdates = { if (!demoMode) onCheckForUpdates() },
                onExportAccount = {
                    if (!demoMode) scope.launch {
                        runCatching { factory.exportAccount() }
                            .onSuccess { export ->
                                if (export == null) {
                                    Toast.makeText(context, R.string.settings_export_unavailable, Toast.LENGTH_LONG).show()
                                } else {
                                    runCatching {
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_TEXT, export.toString())
                                        }, exportShareTitle))
                                    }.onFailure {
                                        Toast.makeText(context, R.string.external_action_unavailable, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .onFailure { Toast.makeText(context, R.string.settings_export_failed, Toast.LENGTH_LONG).show() }
                    }
                },
                onDeleteAccount = {
                    if (!demoMode) scope.launch {
                        runCatching { factory.deleteAccount() }
                            .onSuccess { deleted ->
                                if (deleted) {
                                    factory.clearAccount()
                                    onLogout()
                                    navController.navigate(Destinations.AUTH) { popUpTo(0) }
                                } else {
                                    Toast.makeText(context, R.string.settings_delete_account_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                            .onFailure { Toast.makeText(context, R.string.settings_delete_account_failed, Toast.LENGTH_LONG).show() }
                    }
                },
                onOpenProfile = { navController.navigate(if (role == AccountRole.RECYCLER) Destinations.RECYCLER_PROFILE else Destinations.PROFILE) },
                onOpenPickupPricing = { navController.navigate(Destinations.KABADIWALA_PICKUP_PRICING) },
                showPickupPricing = role == AccountRole.COLLECTOR && !demoMode,
                onOpenSafety = { navController.navigate(Destinations.SAFETY) },
                onOpenHelp = { navController.navigate(Destinations.HELP) },
                onOpenRewards = { navController.navigate(Destinations.REWARDS) },
                onOpenSchemes = { navController.navigate(Destinations.SCHEMES) },
                onOpenActivities = { navController.navigate(Destinations.ACTIVITIES) },
                onOpenChat = { navController.navigate(Destinations.CHAT) },
                onOpenNotifications = { navController.navigate(Destinations.NOTIFICATIONS) },
                onOpenDisputes = { navController.navigate(Destinations.DISPUTE_ANALYTICS) },
                // The legacy rewards/chat/dispute tools describe the old
                // collector↔recycler marketplace. Keep them in the offline
                // demo only; live roles use the supply-chain workspace.
                 showRoleTools = demoMode && demoRole == null,
                 showMessaging = !demoMode && role in setOf(AccountRole.HOUSEHOLD, AccountRole.COLLECTOR, AccountRole.RECYCLER),
                syncItems = syncItems,
                syncPendingCount = syncItems.size,
                 onRetrySync = { if (!demoMode) factory.requestSync() },
                 onRetrySyncItem = { uid ->
                     if (!demoMode) scope.launch {
                         factory.resetSyncItem(uid)
                         factory.requestSync()
                     }
                 },
                onLogout = {
                    onLogout()
                    navController.navigate(Destinations.AUTH) {
                        popUpTo(0)
                    }
                }
            )
        }
        composable(Destinations.PROFILE) {
            if (demoMode) DemoProfileScreen(role = role, onExitDemo = onLogout, onResetDemo = DemoSessionStore::reset)
            else {
                val vm: AccountProfileViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    profile = state.profile,
                    saving = state.saving,
                    saveError = state.saveError,
                    onSave = vm::save
                )
            }
        }
        composable(Destinations.NOTIFICATIONS) {
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_notifications_title), stringResource(R.string.demo_info_notifications_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refreshNotifications, dataPaths = setOf("/notifications"))
                NotificationsScreen(
                    notifications = state.notifications,
                    unreadCount = state.unreadNotifications,
                    onRefresh = vm::refreshNotifications,
                    hasMore = state.notificationsNextCursor != null,
                    loadingMore = state.notificationsLoadingMore,
                    onLoadMore = vm::loadMoreNotifications,
                    onOpen = { notification ->
                        vm.markNotificationRead(notification.id)
                        Destinations.notificationDestination(
                            notification.route,
                            role,
                            demoMode,
                            demoRole,
                            factory.currentAccount?.verificationStatus
                        )?.let { target ->
                            if (target in Destinations.topLevelFor(role, newNavigation = !demoMode || kabadiwalaDemo)) {
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                navController.navigate(target) { launchSingleTop = true }
                            }
                        }
                    },
                    onMarkAllRead = vm::markAllNotificationsRead
                )
            }
        }
        composable(Destinations.SAFETY) { SafetyScreen() }
        composable(Destinations.HELP) { HelpScreen() }
        composable(Destinations.REWARDS) {
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_rewards_title), stringResource(R.string.demo_info_rewards_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refreshRewards)
                RewardsScreen(state, vm::refreshRewards)
            }
        }
        composable(Destinations.SCHEMES) {
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_schemes_title), stringResource(R.string.demo_info_schemes_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refreshSchemes)
                SchemesScreen(state, vm::refreshSchemes)
            }
        }
        composable(Destinations.ACTIVITIES) {
            if (demoMode) DiyActivitiesScreen(FutureFeatureViewModel.offlineActivities)
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refreshActivities)
                DiyActivitiesScreen(state.activities)
            }
        }
        composable(Destinations.CHAT) {
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_messages_title), stringResource(R.string.demo_info_messages_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                val owner = factory.currentAccount?.profileId
                LaunchedEffect(Unit) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            vm.loadConversations()
                            delay(20_000)
                        }
                    }
                }
                LaunchedEffect(owner) {
                    PushRefreshEvents.events.collect { event ->
                        if (event.accountId == owner && event.type == "CHAT_MESSAGE") vm.loadConversations()
                    }
                }
                ChatListScreen(state.conversations, { navController.navigate(Destinations.chatDetail(it)) }, vm::loadConversations,
                    currentAccountId = factory.currentAccount?.profileId.orEmpty(),
                    lastSyncedAt = state.conversationsLastSyncedAt, refreshError = state.conversationsRefreshError)
            }
        }
        composable(Destinations.CHAT_DETAIL, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("conversationId").orEmpty()
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_conversation_title), stringResource(R.string.demo_info_conversation_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                val owner = factory.currentAccount?.profileId
                val conversation = state.conversations.firstOrNull { it.id == id }
                LaunchedEffect(id, lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        vm.loadConversations()
                        vm.startPolling(id)
                        try { awaitCancellation() } finally { vm.stopPolling() }
                    }
                }
                LaunchedEffect(id, owner) {
                    PushRefreshEvents.events.collect { event ->
                        if (event.accountId == owner && ((event.type == "CHAT_MESSAGE" && event.route == "messages/$id") || event.type.orEmpty().startsWith("SUPPLY_HANDOVER"))) {
                            vm.loadMessages(id)
                            vm.loadConversations()
                        }
                    }
                }
                conversation?.let {
                ChatDetailScreen(
                    conversation = it,
                    messages = state.messages[id].orEmpty(),
                    refreshError = state.messagesRefreshErrors[id] ?: state.error,
                    loadingMessages = id in state.messagesLoading,
                    hasOlderMessages = state.messagesNextCursor[id] != null,
                    loadingOlderMessages = id in state.loadingOlderMessages,
                    onLoadOlder = { vm.loadOlderMessages(id) },
                    currentAccountId = factory.currentAccount?.profileId.orEmpty(),
                        sending = state.sending,
                        onSend = { vm.sendMessage(id, it) },
                        onRetryMessage = { vm.retryMessage(id, it) },
                        draftSuggestion = state.drafts[id],
                        drafting = state.draftingConversationId == id,
                        onDraftReply = { vm.draftReply(id) },
                        onDraftCleared = { vm.clearDraft(id) },
                        onProceedToHandover = conversation.quoteId?.let { quoteId ->
                            { navController.navigate(Destinations.handoverCreate(conversation.lotId, quoteId)) }
                        }
                    )
                } ?: Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    androidx.compose.material3.Text(stringResource(R.string.future_no_messages_title))
                    if (state.conversationsLoading) androidx.compose.material3.CircularProgressIndicator()
                    else {
                        androidx.compose.material3.Text(state.conversationsRefreshError?.let { localizedUserFacingError(it) } ?: stringResource(R.string.future_no_messages_detail))
                        androidx.compose.material3.Button(onClick = vm::loadConversations) { androidx.compose.material3.Text(stringResource(R.string.future_retry)) }
                    }
                }
            }
        }
        composable(Destinations.DISPUTE_ANALYTICS) {
            if (demoMode) DemoInfoScreen(stringResource(R.string.demo_info_fairness_title), stringResource(R.string.demo_info_fairness_detail))
            else {
                val vm: FutureFeatureViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refreshAnalytics)
                DisputeAnalyticsScreen(state.analytics)
            }
        }
        composable(Destinations.RECYCLER_VERIFY) {
            if (demoMode) {
                RecyclerVerificationScreen(
                    profile = DemoDataProvider.profile(AccountRole.RECYCLER),
                    onOpenMarketplace = { navController.navigate(Destinations.RECYCLER_MARKETPLACE) }
                )
            } else {
                val vm: RecyclerProfileViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                val owner = factory.currentAccount?.profileId
                LaunchedEffect(Unit) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            vm.refresh()
                            delay(120_000)
                        }
                    }
                }
                LaunchedEffect(owner) {
                    PushRefreshEvents.events.collect { event ->
                        if (event.accountId == owner) vm.refresh()
                    }
                }
                LaunchedEffect(state.profile?.id, state.profile?.authorizationStatus) {
                    val profile = state.profile ?: return@LaunchedEffect
                    val account = factory.reconcileRecyclerAuthorization(profile) ?: return@LaunchedEffect
                    if (account.verificationStatus == RecyclerVerificationStatus.VERIFIED &&
                        navController.currentDestination?.route == Destinations.RECYCLER_VERIFY
                    ) {
                        navController.navigate(Destinations.RECYCLER_MARKETPLACE) {
                            popUpTo(Destinations.RECYCLER_VERIFY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                RecyclerVerificationScreen(
                    profile = factory.currentAccount,
                    recyclerProfile = state.profile,
                    loading = state.loading,
                    saving = state.saving,
                    error = state.error,
                    saved = state.saved,
                    onRefresh = vm::refresh,
                    onSubmit = vm::submitVerification,
                    onOpenMarketplace = {
                        navController.navigate(Destinations.RECYCLER_MARKETPLACE) {
                            popUpTo(Destinations.RECYCLER_VERIFY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(Destinations.RECYCLER_MARKETPLACE) {
            if (demoMode) DemoRecyclerMarketplaceScreen(onOpenScan = { navController.navigate(Destinations.RECYCLER_SCAN) })
            else {
                val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerSupplyScreen(state, vm::refreshRecycler, vm::makeOffer, vm::receiveLot, onOpenDemand = { navController.navigate(Destinations.RECYCLER_CREATE_DEMAND) }, onWithdrawOffer = vm::withdrawOffer, onUpdateRequirement = vm::updateRequirement, onOpenHandoverScanner = { navController.navigate(Destinations.RECYCLER_SCAN) }, onOpenBulkChat = { lotId, collectorId -> openBulkChat(lotId, collectorId, factory.currentAccount?.profileId.orEmpty()) }, onLoadMoreLots = vm::loadMoreRecyclerLots, onLoadMoreOffers = vm::loadMoreRecyclerOffers, onLoadMoreDemand = vm::loadMoreRecyclerRequirements, currentAccountId = factory.currentAccount?.profileId.orEmpty(), onResumeRefresh = vm::refreshIfStale, onLoadTradeDetails = vm::loadBulkTradeDetails, onRecordSupplyPayment = vm::recordSupplyPayment, onOpenOrders = { navController.navigate(Destinations.RECYCLER_ORDERS) })
            }
        }
        composable(Destinations.RECYCLER_CREATE_DEMAND) {
            val vm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            var supportedMaterials by remember { mutableStateOf<List<String>>(emptyList()) }
            var materialsError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                runCatching { factory.apiService.materialCategories().requireData() }
                    .onSuccess { supportedMaterials = it }
                    .onFailure { materialsError = "Could not load supported materials" }
            }
            LaunchedEffect(state.notice) { if (state.notice == "Requirement published to Kabadiwalas.") navController.popBackStack() }
            RecyclerDemandCreateScreen(supportedMaterials = supportedMaterials, isSubmitting = "create-demand" in state.busy, error = state.error ?: materialsError, onBack = { navController.popBackStack() }, onSubmit = vm::createRequirement)
        }
        composable(Destinations.RECYCLER_ORDERS) {
            if (demoMode) DemoRecyclerOrdersScreen(onOpenScan = { navController.navigate(Destinations.RECYCLER_SCAN) })
            else {
                val vm: RecyclerOrdersViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val supplyVm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val tradeState by supplyVm.state.collectAsStateWithLifecycle()
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refresh)
                RecyclerOrdersScreen(liveHandovers = state.handovers, liveLoading = state.loading, liveError = state.error,
                    onRefresh = vm::refresh, onScan = { navController.navigate(Destinations.RECYCLER_SCAN) },
                    paymentBusy = state.handovers.filter { "supply-payment-${it.id}" in tradeState.busy }.map { it.id }.toSet(),
                    paymentError = tradeState.error ?: state.paymentError,
                    onRecordPayment = { id, input -> state.handovers.firstOrNull { it.id == id }?.let { supplyVm.recordSupplyPaymentForReceipt(it, input) } },
                    tradeDetails = tradeState.tradeDetails, tradeErrors = tradeState.tradeDetailErrors, tradeBusy = tradeState.busy,
                    onLoadTradeDetails = supplyVm::loadBulkTradeDetails,
                    onOpenBulkChat = { lotId, collectorId -> openBulkChat(lotId, collectorId, factory.currentAccount?.profileId.orEmpty()) })
            }
        }
        composable(Destinations.RECYCLER_PICKUPS) {
            if (demoMode) RecyclerPickupsScreen(demoMode = true)
            else {
                val vm: RecyclerProfileViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refresh)
                RecyclerPickupsScreen(availability = state.profile?.pickupAvailability, profile = state.profile, loading = state.loading, saving = state.saving, savingSection = state.savingSection, savedSection = state.savedSection, error = state.error, onRefresh = vm::refresh, onSave = vm::saveAvailability, onSavePricing = vm::savePickupPricing)
            }
        }
        composable(Destinations.RECYCLER_RATES) {
            if (demoMode) RecyclerRatesScreen(rates = listOf(com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto("PCB", 340.0), com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto("COPPER", 535.0)), demoMode = true)
            else {
                val vm: RecyclerProfileViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RefreshOnResume(featureAccount, onRefresh = vm::refresh)
                RecyclerRatesScreen(acceptedMaterials = state.profile?.materialsAccepted?.map { it.category }.orEmpty(), supportedMaterials = state.materialCategories, materialsError = state.materialCategoriesError, rates = state.profile?.rates.orEmpty(), loading = state.loading, saving = state.saving, saved = state.savedSection == "rates", error = state.error, onRefresh = vm::refresh, onSave = vm::saveRates)
            }
        }
        composable(Destinations.RECYCLER_PROFILE) {
            if (demoMode) DemoProfileScreen(AccountRole.RECYCLER, onExitDemo = onLogout, onResetDemo = DemoSessionStore::reset)
            else {
                val vm: AccountProfileViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerProfileScreen(
                    profile = state.profile,
                    saving = state.saving,
                    saveError = state.saveError,
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                    onSave = vm::save
                )
            }
        }
        composable(Destinations.RECYCLER_SCAN) {
            if (demoMode) DemoRecyclerScanScreen()
            else {
                val vm: com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val supplyVm: SupplyChainViewModel = accountFeatureViewModel(owner = featureOwner, accountId = featureAccount, factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerScanScreen(state = state, onVerify = vm::verify, onConfirm = vm::confirm, onReset = vm::reset, onReceiptRecorded = {
                    supplyVm.refreshRecycler()
                    vm.reset()
                    navController.navigate(Destinations.RECYCLER_MARKETPLACE) {
                        popUpTo(Destinations.RECYCLER_SCAN) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }
        }
    }
}
