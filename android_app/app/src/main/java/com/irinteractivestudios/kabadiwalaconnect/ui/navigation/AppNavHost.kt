package com.irinteractivestudios.kabadiwalaconnect.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.composable
import com.irinteractivestudios.kabadiwalaconnect.di.KcViewModelFactory
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.earnings.EarningsViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.home.HomeViewModel
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
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingRoute
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.OnboardingViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotManagementViewModel
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotRoute
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.lots.LotDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.RewardsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.SchemesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.DiyActivitiesScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.ChatListScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.ChatDetailScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.DisputeAnalyticsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.VerifiedRatingScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.NotificationsScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions.TransactionTimelineScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.transactions.TransactionTimelineViewModel
import com.irinteractivestudios.kabadiwalaconnect.data.remote.SubmitReviewRequestDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Dispute
import com.irinteractivestudios.kabadiwalaconnect.domain.model.DisputeStatus
import com.irinteractivestudios.kabadiwalaconnect.domain.model.LotStatus
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.remote.requireData
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
    startDestination: String = Destinations.HOME,
    onLogout: () -> Unit = {},
    onDemo: () -> Unit = {},
    demoMode: Boolean = false,
    role: AccountRole = AccountRole.COLLECTOR,
    onAuthFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val recyclerRoutes = setOf(Destinations.RECYCLER_VERIFY, Destinations.RECYCLER_MARKETPLACE, Destinations.RECYCLER_ORDERS, Destinations.RECYCLER_PICKUPS, Destinations.RECYCLER_RATES, Destinations.RECYCLER_PROFILE, Destinations.RECYCLER_SCAN)
    val collectorRoutes = setOf(Destinations.HOME, Destinations.PRICES, Destinations.RECYCLERS, Destinations.EARNINGS, Destinations.SETTINGS, Destinations.PROFILE, Destinations.SAFETY, Destinations.HELP, Destinations.REWARDS, Destinations.SCHEMES, Destinations.ACTIVITIES, Destinations.CHAT, Destinations.NOTIFICATIONS, Destinations.DISPUTE_ANALYTICS, Destinations.CREATE_LOT, Destinations.MY_LOTS, Destinations.RECYCLER_DETAIL, Destinations.QUOTE_REQUEST, Destinations.QUOTE_COMPARE, Destinations.HANDOVER_CREATE, Destinations.HANDOVER_DOCUMENT, Destinations.HANDOVER_DISPUTE, Destinations.RATE_HANDOVER, Destinations.PAYMENT_CREATE, Destinations.HOUSEHOLD_DEAL, Destinations.TRANSACTION_TIMELINE)
    LaunchedEffect(currentRoute, role) {
        val collectorRoute = currentRoute in collectorRoutes || currentRoute?.startsWith("lots/") == true || currentRoute?.startsWith("quotes/") == true || currentRoute?.startsWith("handovers/") == true
        val recyclerRoute = currentRoute in recyclerRoutes
        if (role == AccountRole.RECYCLER && collectorRoute) {
            navController.navigate(if (factory.currentAccount?.verificationStatus?.name == "VERIFIED") Destinations.RECYCLER_MARKETPLACE else Destinations.RECYCLER_VERIFY) { popUpTo(0) }
        } else if ((role == AccountRole.COLLECTOR || role == AccountRole.HOUSEHOLD) && recyclerRoute) {
            navController.navigate(Destinations.HOME) { popUpTo(0) }
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
                onFinished = { onAuthFinished() }
            )
        }
        composable(Destinations.CREATE_LOT) {
            val vm: LotManagementViewModel = viewModel(factory = factory)
            LotRoute(
                vm,
                onSafety = { navController.navigate(Destinations.SAFETY) },
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
            lot?.let { item -> LotDetailScreen(item, onCancel = { scope.launch { factory.lotWriter.cancel(id, System.currentTimeMillis()) } }, repeating = repeating, repeatError = repeatError, onTimeline = { navController.navigate(Destinations.transactionTimeline(id)) }, onRepeat = {
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
        composable(Destinations.TRANSACTION_TIMELINE, arguments = listOf(navArgument("lotId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("lotId").orEmpty()
            val vm: TransactionTimelineViewModel = viewModel(factory = factory)
            TransactionTimelineScreen(id, vm, onBack = { navController.popBackStack() })
        }
        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            if (role == AccountRole.HOUSEHOLD) HouseholdHomeScreen(
                area = factory.currentAccount?.areaName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_area_not_set),
                onCreateLot = { navController.navigate(Destinations.CREATE_LOT) },
                onFindKabadiwala = { navController.navigate(Destinations.RECYCLERS) },
                onOpenDeal = { navController.navigate(if (demoMode) Destinations.householdDeal("kb-01") else Destinations.CHAT) }
            ) else HomeScreen(
                state = state,
                demoMode = demoMode,
                household = role == AccountRole.HOUSEHOLD,
                onSeePrices = { navController.navigate(Destinations.PRICES) },
                onFindRecyclers = { navController.navigate(Destinations.RECYCLERS) },
                onRetry = null,
                onCreateLot = { navController.navigate(Destinations.CREATE_LOT) },
                onMyLots = { navController.navigate(Destinations.MY_LOTS) },
                onOpenRewards = { navController.navigate(Destinations.REWARDS) },
                onOpenSchemes = { navController.navigate(Destinations.SCHEMES) },
                onOpenActivities = { navController.navigate(Destinations.ACTIVITIES) },
                onOpenChat = { navController.navigate(Destinations.CHAT) },
                onOpenDisputes = { navController.navigate(Destinations.DISPUTE_ANALYTICS) }
            )
        }
        composable(Destinations.PRICES) {
            val vm: PricesViewModel = viewModel(factory = factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            PricesScreen(state = state, vm = vm, speaker = factory.priceSpeaker, demoMode = demoMode)
        }
        composable(Destinations.RECYCLERS) {
            val vm: RecyclersViewModel = viewModel(factory = factory)
            vm.setCatalogRefresher { current -> factory.refreshCatalogs(current = current) }
            val state by vm.uiState.collectAsStateWithLifecycle()
            if (role == AccountRole.HOUSEHOLD && demoMode) NearbyKabadiwalasScreen(
                area = factory.currentAccount?.areaName ?: "Kothrud, Pune",
                onInvite = { navController.navigate(Destinations.householdDeal(it)) }
            ) else RecyclersScreen(state = state, vm = vm, onOpen = { navController.navigate(Destinations.recyclerDetail(it)) }, demoMode = demoMode)
        }
        composable(Destinations.HOUSEHOLD_DEAL, arguments = listOf(navArgument("kabadiwalaId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("kabadiwalaId").orEmpty()
            if (demoMode) HouseholdDealScreen(
                kabadiwalaId = id,
                onFindAnother = {
                    if (!navController.popBackStack(Destinations.RECYCLERS, false)) navController.navigate(Destinations.RECYCLERS)
                }
            ) else LaunchedEffect(Unit) {
                navController.navigate(Destinations.CHAT) { popUpTo(Destinations.HOUSEHOLD_DEAL) { inclusive = true } }
            }
        }
        composable(Destinations.RECYCLER_DETAIL, arguments = listOf(navArgument("recyclerId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("recyclerId").orEmpty()
            val recycler by factory.recyclerCatalog!!.observeRecycler(id).collectAsStateWithLifecycle(initialValue = null)
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            recycler?.let { item -> RecyclerDetailScreen(item, onCall = { /* Contact access is enabled only after an accepted quote or booking. */ }, onRequestQuote = {
                val lot = lots.firstOrNull { it.status == LotStatus.SAVED }
                if (lot != null) navController.navigate(Destinations.quoteRequest(lot.id, item.id)) else navController.navigate(Destinations.CREATE_LOT)
            }) }
        }
        composable(Destinations.QUOTE_REQUEST, arguments = listOf(navArgument("lotId") { type = NavType.StringType }, navArgument("recyclerId") { type = NavType.StringType })) { entry ->
            val lotId = entry.arguments?.getString("lotId").orEmpty()
            val recyclerId = entry.arguments?.getString("recyclerId").orEmpty()
            val lots by factory.lots.observeLots().collectAsStateWithLifecycle(initialValue = emptyList())
            val recyclers by factory.recyclers.observeRecyclers().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            QuoteRequestScreen(lots, recyclers, lotId, recyclerId, factory.quoteRepository, onSubmitted = { id -> scope.launch { factory.lotWriter.lock(id, System.currentTimeMillis()); navController.navigate(Destinations.quoteCompare(id)) } })
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
            LaunchedEffect(lotId, lot != null) { if (lot != null) refreshQuotes() }
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
            val id = entry.arguments?.getString("handoverId").orEmpty(); val handover by factory.handoverRepository.observe(id).collectAsStateWithLifecycle(initialValue = null); val lot by factory.lotWriter.observeLot(handover?.lotId.orEmpty()).collectAsStateWithLifecycle(initialValue = null); val prices by factory.priceCatalog.observePrices().collectAsStateWithLifecycle(initialValue = emptyList()); val scope = rememberCoroutineScope(); val context = LocalContext.current; var pendingScalePhoto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val path = pendingScalePhoto; if (ok && path != null) { handover?.let { item -> scope.launch { factory.handoverRepository.updateEvidence(id, item.actualWeightKg ?: item.weightKg, item.materialConfirmed, item.collectorConfirmed, path) } } } }
            var marking by remember(id) { mutableStateOf(false) }
            var markError by remember(id) { mutableStateOf(false) }
            handover?.let { item ->
                HandoverDocumentScreen(
                    handover = item,
                    photoPath = lot?.localPhotoPath,
                    referencePrice = prices.firstOrNull { it.materialLabel == item.materialLabel },
                    onUpdateEvidence = { weight, materialMatch, photo -> scope.launch { factory.handoverRepository.updateEvidence(id, weight, materialMatch, true, photo) } },
                    onCaptureScalePhoto = { val dir = File(context.filesDir, "handover_photos").apply { mkdirs() }; val file = File(dir, "scale_${System.currentTimeMillis()}.jpg"); pendingScalePhoto = file.absolutePath; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) },
                    onOpenDispute = { navController.navigate(Destinations.handoverDispute(id)) },
                    onRate = { navController.navigate(Destinations.rateHandover(id)) },
                    actionInFlight = marking,
                    actionError = markError,
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
            handover?.let { item ->
                DisputeCenterScreen(item, disputes) { type, description ->
                    scope.launch {
                        val localId = "DSP-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"
                        val local = Dispute(localId, item.id, item.lotId, item.collectorId, item.recyclerId, type, description, item.weightKg, item.actualWeightKg, DisputeStatus.SAVED_LOCALLY, System.currentTimeMillis(), false, null)
                        factory.disputeRepository.save(local)
                        runCatching {
                            val body = JsonObject().apply {
                                addProperty("clientDisputeId", localId)
                                addProperty("type", type.name)
                                addProperty("description", description)
                                addProperty("claimedWeight", item.weightKg)
                                item.actualWeightKg?.let { addProperty("actualValue", it) }
                            }
                            factory.apiService.disputeHandover(id, body).requireData().id.let { remoteId -> factory.disputeRepository.markSynced(localId, remoteId) }
                        }.onFailure {
                            val payload = JsonObject().apply {
                                addProperty("id", localId)
                                addProperty("handoverId", id)
                                addProperty("type", type.name)
                                addProperty("description", description)
                                addProperty("claimedWeight", item.weightKg)
                                item.actualWeightKg?.let { addProperty("actualValue", it) }
                            }
                            factory.syncQueue.enqueue(com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity(operation = "CREATE_DISPUTE", payloadJson = payload.toString(), createdAtEpochMs = System.currentTimeMillis(), accountId = factory.currentAccount?.profileId))
                            factory.requestSync()
                        }
                        navController.popBackStack()
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
            val language by vm.language.collectAsStateWithLifecycle()
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            val syncItems by factory.syncQueue.observeForAccount(factory.currentAccount?.profileId.orEmpty()).collectAsStateWithLifecycle(initialValue = emptyList())
            SettingsScreen(
                language = language,
                appearance = appearance,
                appVersion = vm.appVersion,
                onLanguageChange = { tag ->
                    vm.setLanguage(tag)
                    onLanguageChange(tag)
                },
                onAppearanceChange = { mode -> vm.setAppearance(mode); onAppearanceChange(mode) },
                onOpenProfile = { navController.navigate(Destinations.PROFILE) },
                onOpenSafety = { navController.navigate(Destinations.SAFETY) },
                onOpenHelp = { navController.navigate(Destinations.HELP) },
                onOpenRewards = { navController.navigate(Destinations.REWARDS) },
                onOpenSchemes = { navController.navigate(Destinations.SCHEMES) },
                onOpenActivities = { navController.navigate(Destinations.ACTIVITIES) },
                onOpenChat = { navController.navigate(Destinations.CHAT) },
                onOpenNotifications = { navController.navigate(Destinations.NOTIFICATIONS) },
                onOpenDisputes = { navController.navigate(Destinations.DISPUTE_ANALYTICS) },
                syncItems = syncItems,
                syncPendingCount = syncItems.size,
                onRetrySync = factory::requestSync,
                onLogout = {
                    onLogout()
                    navController.navigate(Destinations.AUTH) {
                        popUpTo(0)
                    }
                }
            )
        }
        composable(Destinations.PROFILE) { ProfileScreen(factory.currentAccount) }
        composable(Destinations.NOTIFICATIONS) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            NotificationsScreen(
                notifications = state.notifications,
                unreadCount = state.unreadNotifications,
                onRefresh = vm::refresh,
                onOpen = { notification ->
                    vm.markNotificationRead(notification.id)
                    notification.route?.takeIf(::isSafeNotificationRoute)
                        ?.let { route -> navController.navigate(route) }
                },
                onMarkAllRead = vm::markAllNotificationsRead
            )
        }
        composable(Destinations.SAFETY) { SafetyScreen() }
        composable(Destinations.HELP) { HelpScreen() }
        composable(Destinations.REWARDS) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            RewardsScreen(state, vm::refresh)
        }
        composable(Destinations.SCHEMES) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            SchemesScreen(state, vm::refresh)
        }
        composable(Destinations.ACTIVITIES) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            DiyActivitiesScreen(state.activities)
        }
        composable(Destinations.CHAT) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            ChatListScreen(state.conversations, { navController.navigate(Destinations.chatDetail(it)) }, vm::refresh)
        }
        composable(Destinations.CHAT_DETAIL, arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("conversationId").orEmpty()
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val conversation = state.conversations.firstOrNull { it.id == id }
            LaunchedEffect(id) { vm.refresh(); vm.startPolling(id) }
            conversation?.let {
                ChatDetailScreen(
                    conversation = it,
                    messages = state.messages[id].orEmpty(),
                    sending = state.sending,
                    onSend = { vm.sendMessage(id, it) },
                    onRetryMessage = { vm.retryMessage(id, it) },
                    draftSuggestion = state.drafts[id],
                    drafting = state.draftingConversationId == id,
                    onDraftReply = { vm.draftReply(id) },
                    onProceedToHandover = conversation.quoteId?.let { quoteId ->
                        { navController.navigate(Destinations.handoverCreate(conversation.lotId, quoteId)) }
                    }
                )
            }
        }
        composable(Destinations.DISPUTE_ANALYTICS) {
            val vm: FutureFeatureViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refresh() }
            DisputeAnalyticsScreen(state.analytics)
        }
        composable(Destinations.RECYCLER_VERIFY) {
            var profile by remember { mutableStateOf(factory.currentAccount) }
            val scope = rememberCoroutineScope()
            RecyclerVerificationScreen(profile) {
                scope.launch {
                    factory.refreshAccount()
                    profile = factory.currentAccount
                }
            }
        }
        composable(Destinations.RECYCLER_MARKETPLACE) {
            if (demoMode) RecyclerMarketplaceScreen(demoMode = true)
            else {
                val vm: RecyclerMarketplaceViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerMarketplaceScreen(liveLots = state.lots, liveLoading = state.loading, liveError = state.error, liveSubmittedIds = state.submittedIds, liveSubmittingIds = state.submittingIds, onRefresh = vm::refresh, onLiveOfferSent = vm::submitOffer)
            }
        }
        composable(Destinations.RECYCLER_ORDERS) {
            if (demoMode) RecyclerOrdersScreen(demoMode = true, onScan = { navController.navigate(Destinations.RECYCLER_SCAN) })
            else {
                val vm: RecyclerOrdersViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerOrdersScreen(liveHandovers = state.handovers, liveLoading = state.loading, liveError = state.error, onRefresh = vm::refresh, onScan = { navController.navigate(Destinations.RECYCLER_SCAN) })
            }
        }
        composable(Destinations.RECYCLER_PICKUPS) {
            if (demoMode) RecyclerPickupsScreen(demoMode = true)
            else {
                val vm: RecyclerProfileViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerPickupsScreen(availability = state.profile?.pickupAvailability, loading = state.loading, saving = state.saving, error = state.error, onRefresh = vm::refresh, onSave = vm::saveAvailability)
            }
        }
        composable(Destinations.RECYCLER_RATES) {
            if (demoMode) RecyclerRatesScreen(rates = listOf(com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto("PCB", 340.0), com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerRateDto("COPPER", 535.0)))
            else {
                val vm: RecyclerProfileViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsStateWithLifecycle()
                RecyclerRatesScreen(acceptedMaterials = state.profile?.materialsAccepted?.map { it.category }.orEmpty(), rates = state.profile?.rates.orEmpty(), loading = state.loading, saving = state.saving, saved = state.saved, error = state.error, onRefresh = vm::refresh, onSave = vm::saveRates)
            }
        }
        composable(Destinations.RECYCLER_PROFILE) { RecyclerProfileScreen(factory.currentAccount, onLogout = onLogout) }
        composable(Destinations.RECYCLER_SCAN) {
            val vm: com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerScanViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            RecyclerScanScreen(state = state, onVerify = vm::verify, onConfirm = vm::confirm, onReset = vm::reset)
        }
    }
}

private fun isSafeNotificationRoute(route: String): Boolean {
    if (route.length > 120 || !route.matches(Regex("^[A-Za-z0-9_/-]+$"))) return false
    return route == Destinations.EARNINGS ||
        route == Destinations.RECYCLER_MARKETPLACE ||
        route == Destinations.RECYCLER_ORDERS ||
        route == Destinations.DISPUTE_ANALYTICS ||
        route.startsWith("quotes/compare/") ||
        route.startsWith("handovers/create/") ||
        route.startsWith("handovers/document/") ||
        route.startsWith("handovers/dispute/")
}
