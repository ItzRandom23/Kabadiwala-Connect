package com.irinteractivestudios.kabadiwalaconnect

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.irinteractivestudios.kabadiwalaconnect.di.KcViewModelFactory
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcBottomBar
import com.irinteractivestudios.kabadiwalaconnect.ui.components.KcTopBar
import com.irinteractivestudios.kabadiwalaconnect.ui.components.OfflineBanner
import com.irinteractivestudios.kabadiwalaconnect.ui.components.AppUpdatePrompt
import com.irinteractivestudios.kabadiwalaconnect.ui.components.TestingEnvironmentIndicator
import com.irinteractivestudios.kabadiwalaconnect.ui.components.LoadingContent
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.AppNavHost
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.InitialLanguageScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionSnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionState
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.AppearanceManager
import com.irinteractivestudios.kabadiwalaconnect.util.AppUpdateManager
import com.irinteractivestudios.kabadiwalaconnect.util.AvailableAppUpdate
import com.irinteractivestudios.kabadiwalaconnect.util.InstallUpdateResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single-activity Compose host.
 *
 * - Launches directly into Home via the nav graph.
 * - Shows the 5-tab bottom bar on top-level destinations only.
 * - Shows the offline banner on every screen when disconnected.
 * - Applies the saved locale before inflation; recreates on language change.
 * - Requests no permissions on launch; features request access on demand.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Set system-bar icon contrast before the first Compose frame. The
        // theme repeats this when the user changes appearance in Settings,
        // but doing it here avoids a dark launch frame with black status icons.
        val initialAppearance = AppearanceManager.load(this)
        val initialDark = when (initialAppearance) {
            AppearanceManager.DARK -> true
            AppearanceManager.LIGHT -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !initialDark
            isAppearanceLightNavigationBars = !initialDark
        }
        val app = application as KabadiwalaApp
        // Local-only QA entry points. Release builds ignore these extras. The
        // live variant opens the real seller screen without creating an account,
        // so UI/API error states can be inspected on a clean emulator.
        val householdPreview = BuildConfig.DEBUG && intent.getBooleanExtra("previewHousehold", false)
        val householdLivePreview = BuildConfig.DEBUG && intent.getBooleanExtra("previewHouseholdLive", false)
        val forcedDemoRole = if (BuildConfig.DEBUG) {
            when (intent.getStringExtra("demoRole")?.uppercase()) {
                "HOUSEHOLD" -> AccountRole.HOUSEHOLD
                "KABADIWALA", "COLLECTOR" -> AccountRole.COLLECTOR
                "RECYCLER" -> AccountRole.RECYCLER
                else -> if (householdPreview) AccountRole.HOUSEHOLD else null
            }
        } else {
            null
        }
        val demoPreviewMode = forcedDemoRole != null
        val previewMode = demoPreviewMode || householdLivePreview
        val languageWasSelected = LocaleManager.hasPersistedTag(this)
        // The collector id can be created by the remote OTP response during
        // this activity session, so ViewModels read it when they are created.
        val factory = KcViewModelFactory(app, app.container)
        setContent {
            var appearanceMode by remember { mutableStateOf(AppearanceManager.load(this@MainActivity)) }
            var activeRole by remember { mutableStateOf(forcedDemoRole ?: AccountRole.COLLECTOR) }
            KabadiwalaConnectTheme(
                darkTheme = AppearanceManager.isDark(appearanceMode),
                role = activeRole
            ) {
                val navController = rememberNavController()
                val uiScope = rememberCoroutineScope()
                // Demo mode is a deliberate, debug-only entry point. It must
                // never be restored from an old Activity/task snapshot after
                // force-stop, process death, or a data-clear test: doing so
                // could resurrect fixture content before a real session is
                // restored. Keep it process-local and intent-driven.
                var demoMode by remember { mutableStateOf(demoPreviewMode) }
                var demoRoleName by remember { mutableStateOf(forcedDemoRole?.name.orEmpty()) }
                var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
                var updateBusy by remember { mutableStateOf(false) }
                var updateError by remember { mutableStateOf<String?>(null) }
                val connection by app.container.connectivityObserver.state
                    .collectAsStateWithLifecycle(initialValue = ConnectionState.ONLINE)

                // Check before authentication/onboarding state can block the
                // rest of the screen. The update manifest is public and must
                // remain discoverable even when a saved API session is stale.
                LaunchedEffect(connection, previewMode) {
                    if (!previewMode && connection == ConnectionState.ONLINE) {
                        AppUpdateManager.check(this@MainActivity)?.let { update ->
                            availableUpdate = update
                        }
                    }
                }
                var sessionBootstrap by remember {
                    mutableStateOf<SessionSnapshot?>(
                         if (previewMode || !languageWasSelected) SessionSnapshot(SessionState.UNAUTHENTICATED, null) else null
                    )
                }
                LaunchedEffect(languageWasSelected, previewMode) {
                    if (sessionBootstrap == null) {
                        sessionBootstrap = withContext(Dispatchers.IO) {
                            app.container.restoreAuthenticatedSession()
                            app.container.sessionCoordinator.snapshot.value
                        }
                    }
                }
                val bootstrap = sessionBootstrap
                val liveSession by app.container.sessionCoordinator.snapshot.collectAsStateWithLifecycle()
                if (bootstrap == null) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        LoadingContent(Modifier.fillMaxSize())
                    }
                    return@KabadiwalaConnectTheme
                }
                LaunchedEffect(bootstrap.account?.role) {
                    bootstrap.account?.role?.let { activeRole = it }
                }
                val cachedAccount = bootstrap.account
                val initialRoute = if (householdLivePreview) Destinations.HOME else if (demoMode && activeRole == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else if (demoMode) Destinations.HOME else if (!bootstrap.restorable || cachedAccount == null) Destinations.AUTH else if (cachedAccount.role == AccountRole.ADMIN) Destinations.ADMIN_DASHBOARD else if (cachedAccount.role == AccountRole.RECYCLER && cachedAccount.verificationStatus != RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_VERIFY else if (cachedAccount.role == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val kabadiwalaDemo = demoMode && activeRole == AccountRole.COLLECTOR && demoRoleName == AccountRole.COLLECTOR.name
                val isTopLevel = route in Destinations.topLevelFor(activeRole, newNavigation = !demoMode || kabadiwalaDemo)
                val languageSelected = languageWasSelected
                var navGuardReady by remember { mutableStateOf(false) }

                // A failed refresh (including a rotated-token reuse response)
                // can invalidate a session after the initial bootstrap. Keep
                // the account boundary honest and move the user to sign-in
                // without requiring a restart or exposing a raw 401.
                LaunchedEffect(liveSession.state, bootstrap.restorable, demoMode) {
                    if (!demoMode && bootstrap.restorable && liveSession.state in setOf(SessionState.UNAUTHENTICATED, SessionState.EXPIRED)) {
                        sessionBootstrap = liveSession
                        activeRole = AccountRole.COLLECTOR
                        if (route != Destinations.AUTH) {
                            navController.navigate(Destinations.AUTH) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        }
                    }
                }

                // A restored NavHost back stack can outlive a session (for
                // example after process death or test/activity recreation).
                // Never leave a signed-out user on an account-owned screen,
                // and start a valid session from its role-appropriate home.
                LaunchedEffect(initialRoute, route) {
                    if (!navGuardReady && route != null) {
                        navGuardReady = true
                        val shouldRecoverRoute = if (demoMode) {
                            route == Destinations.AUTH
                        } else {
                            route != initialRoute
                        }
                        if (shouldRecoverRoute) {
                            navController.navigate(initialRoute) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        }
                    }
                }
                LaunchedEffect(languageSelected, route, demoMode, bootstrap.restorable) {
                    if (languageSelected && !demoMode && !bootstrap.restorable && route != Destinations.AUTH && route != null) {
                        navController.navigate(Destinations.AUTH) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                }

                val unreadNotifications by app.container.unreadNotificationCount(cachedAccount?.profileId.orEmpty())
                    .collectAsStateWithLifecycle(initialValue = 0)
                LaunchedEffect(connection, bootstrap.restorable) {
                    if (!householdLivePreview && connection == ConnectionState.ONLINE && bootstrap.restorable) {
                        val restoredAccount = withContext(Dispatchers.IO) {
                            // A locally expired access token is not an
                            // automatic logout. Try the rotating refresh token
                            // first; the previous implementation checked only
                            // local expiry here and cleared valid accounts as
                            // soon as connectivity returned.
                            val account = app.container.restoreAuthenticatedSession()
                            if (account != null) {
                                // Pull server deltas only after restoration has
                                // established a valid authenticated session.
                                runCatching { app.container.reconcileChanges() }
                                app.container.refreshCatalogs()
                                // Re-arm durable offline work only after the
                                // process-local session gate has opened.
                                app.container.syncScheduler.requestSync()
                                app.container.startPushTokenRegistration()
                            }
                            account
                        }
                        if (restoredAccount == null) {
                            // Restoration has already classified the session
                            // as unauthenticated/expired. Return to the real
                            // sign-in route without issuing protected calls.
                            sessionBootstrap = app.container.sessionCoordinator.snapshot.value
                            activeRole = AccountRole.COLLECTOR
                            if (route != Destinations.AUTH) {
                                navController.navigate(Destinations.AUTH) {
                                    popUpTo(0)
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }
                val title = when (route) {
                    Destinations.MY_LOTS -> stringResource(R.string.home_my_lots)
                    Destinations.LOT_DETAIL -> stringResource(R.string.lot_review_title)
                    Destinations.LOT_EDIT -> stringResource(R.string.lot_edit_title)
                    Destinations.PRICES -> stringResource(R.string.prices_title)
                    Destinations.RECYCLERS -> stringResource(R.string.recyclers_title)
                    Destinations.RECYCLER_DETAIL -> stringResource(R.string.recyclers_title)
                    Destinations.QUOTE_REQUEST -> stringResource(R.string.quote_request_title)
                    Destinations.QUOTE_COMPARE -> stringResource(R.string.quote_compare_title)
                    Destinations.HANDOVER_CREATE -> stringResource(R.string.handover_create_title)
                    Destinations.HANDOVER_DOCUMENT -> stringResource(R.string.handover_document_title)
                    Destinations.HANDOVER_DISPUTE -> stringResource(R.string.dispute_title)
                    Destinations.RATE_HANDOVER -> stringResource(R.string.handover_rate_recycler)
                    Destinations.PAYMENT_CREATE -> stringResource(R.string.payment_title)
                    Destinations.EARNINGS -> stringResource(R.string.earnings_title)
                    Destinations.SETTINGS -> stringResource(R.string.settings_title)
                    Destinations.PROFILE -> stringResource(R.string.profile_title)
                    Destinations.SAFETY -> stringResource(R.string.safety_title)
                    Destinations.HELP -> stringResource(R.string.help_title)
                    Destinations.RECYCLER_MARKETPLACE -> stringResource(R.string.recycler_marketplace_title)
                    Destinations.RECYCLER_ORDERS -> stringResource(R.string.recycler_orders_title)
                    Destinations.RECYCLER_PICKUPS -> stringResource(R.string.recycler_pickups_title)
                    Destinations.RECYCLER_RATES -> stringResource(R.string.recycler_rates_title)
                    Destinations.RECYCLER_PROFILE -> stringResource(R.string.recycler_profile_title)
                    Destinations.RECYCLER_VERIFY -> stringResource(R.string.recycler_verification_title)
                    Destinations.ADMIN_DASHBOARD -> "Operator console"
                    Destinations.KABADIWALA_INVENTORY -> stringResource(R.string.nav_kabadiwala_inventory)
                    Destinations.KABADIWALA_PICKUPS -> stringResource(R.string.nav_kabadiwala_pickups)
                    Destinations.KABADIWALA_LOTS -> stringResource(R.string.nav_kabadiwala_lots)
                    Destinations.HOUSEHOLD_DEAL -> stringResource(R.string.future_transaction_chat)
                    Destinations.REWARDS -> stringResource(R.string.settings_rewards)
                    Destinations.SCHEMES -> stringResource(R.string.settings_schemes)
                    Destinations.ACTIVITIES -> stringResource(R.string.settings_diy)
                    Destinations.CHAT, Destinations.CHAT_DETAIL -> stringResource(R.string.settings_messages)
                    Destinations.DISPUTE_ANALYTICS -> stringResource(R.string.settings_disputes)
                    Destinations.CREATE_HOUSEHOLD_LISTING -> "Sell scrap"
                    else -> stringResource(R.string.app_name)
                }

                if (!languageSelected) {
                    InitialLanguageScreen(
                        onLanguageSelected = { tag ->
                            LocaleManager.persistTag(this@MainActivity, tag)
                            recreate()
                        }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            // Top-level screens own their visual headers. Keeping the
                            // app bar for detail screens only removes duplicated titles
                            // and gives the primary content a clearer first focal point.
                            if (!isTopLevel && route != Destinations.AUTH) {
                                KcTopBar(
                                    title = title,
                                    showBack = route != Destinations.CREATE_LOT,
                                    onBack = {
                                        if (route == Destinations.CREATE_LOT) {
                                            if (!navController.popBackStack(Destinations.HOME, false)) {
                                                navController.navigate(Destinations.HOME) {
                                                    popUpTo(0)
                                                    launchSingleTop = true
                                                }
                                            }
                                        } else if (!navController.popBackStack()) {
                                            navController.navigate(Destinations.HOME) {
                                                popUpTo(0)
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            if (isTopLevel) {
                                KcBottomBar(currentRoute = route, role = activeRole, unreadNotifications = unreadNotifications, demoMode = demoMode, kabadiwalaDemo = kabadiwalaDemo, onNavigate = { target ->
                                    navController.navigate(target) {
                                        popUpTo(Destinations.START) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                })
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            OfflineBanner(state = connection)
                            if (BuildConfig.APP_ENVIRONMENT == "TESTING") {
                                TestingEnvironmentIndicator(Modifier.padding(vertical = 4.dp))
                            }
                            AppNavHost(
                                navController = navController,
                                factory = factory,
                                onLanguageChange = { recreate() },
                                onAppearanceChange = { appearanceMode = AppearanceManager.normalize(it) },
                                onCheckForUpdates = {
                                    uiScope.launch {
                                        AppUpdateManager.check(this@MainActivity)?.let { update ->
                                            availableUpdate = update
                                        }
                                    }
                                },
                                startDestination = initialRoute,
                                onLogout = {
                                    uiScope.launch {
                                        app.container.unregisterCurrentPushToken()
                                        app.container.authenticationRepository.logout()
                                        app.container.clearAccount()
                                        sessionBootstrap = app.container.sessionCoordinator.snapshot.value
                                        activeRole = AccountRole.COLLECTOR
                                        demoMode = false
                                        demoRoleName = ""
                                        navController.navigate(Destinations.AUTH) { popUpTo(0) }
                                    }
                                },
                                onDemo = {
                                    if (BuildConfig.DEBUG) {
                                        demoMode = true
                                        demoRoleName = "LEGACY"
                                        sessionBootstrap = app.container.sessionCoordinator.snapshot.value
                                        activeRole = AccountRole.COLLECTOR
                                        navController.navigate(Destinations.HOME) {
                                            popUpTo(Destinations.AUTH) { inclusive = true }
                                        }
                                    }
                                },
                                onDemoRole = { selectedRole ->
                                    if (BuildConfig.DEBUG) {
                                        demoMode = true
                                        demoRoleName = selectedRole.name
                                        sessionBootstrap = app.container.sessionCoordinator.snapshot.value
                                        activeRole = selectedRole
                                        val target = if (selectedRole == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
                                        navController.navigate(target) {
                                            popUpTo(Destinations.AUTH) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                requestedDemoMode = demoMode,
                                demoRole = activeRole.takeIf { demoMode && demoRoleName != "LEGACY" },
                                role = activeRole,
                                sessionAuthenticated = bootstrap.restorable || demoMode,
                                onAuthFinished = {
                                    val account = app.container.currentAccount()
                                    app.container.markAuthenticatedBackgroundWorkReady(account)
                                    app.container.syncScheduler.requestSync()
                                    app.container.startPushTokenRegistration()
                                    requestNotificationPermissionIfNeeded()
                                    sessionBootstrap = app.container.sessionCoordinator.snapshot.value
                                    activeRole = account?.role ?: AccountRole.COLLECTOR
                                    val target = if (activeRole == AccountRole.ADMIN) Destinations.ADMIN_DASHBOARD else if (activeRole == AccountRole.RECYCLER && account?.verificationStatus != RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_VERIFY else if (activeRole == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
                                    navController.navigate(target) { popUpTo(Destinations.AUTH) { inclusive = true } }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                availableUpdate?.let { update ->
                    AppUpdatePrompt(
                        update = update,
                        isBusy = updateBusy,
                        errorMessage = updateError,
                        onLater = { availableUpdate = null },
                        onInstall = {
                            uiScope.launch {
                                updateBusy = true
                                updateError = null
                                try {
                                    val apk = AppUpdateManager.download(this@MainActivity, update)
                                    if (AppUpdateManager.install(this@MainActivity, apk) == InstallUpdateResult.PERMISSION_REQUIRED) {
                                        updateError = getString(R.string.update_install_permission)
                                    }
                                } catch (_: Exception) {
                                    updateError = getString(R.string.update_install_error)
                                } finally {
                                    updateBusy = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 7001
    }
}
