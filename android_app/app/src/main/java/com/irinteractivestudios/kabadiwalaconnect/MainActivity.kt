package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.AppNavHost
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.auth.InitialLanguageScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.util.ConnectionState
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.AppearanceManager
import com.irinteractivestudios.kabadiwalaconnect.util.AppUpdateManager
import com.irinteractivestudios.kabadiwalaconnect.util.AvailableAppUpdate
import com.irinteractivestudios.kabadiwalaconnect.util.InstallUpdateResult
import kotlinx.coroutines.launch

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
        val householdPreviewMode = householdPreview || householdLivePreview
        // The collector id can be created by the remote OTP response during
        // this activity session, so ViewModels read it when they are created.
        val factory = KcViewModelFactory(app, app.container)
        setContent {
            var appearanceMode by remember { mutableStateOf(AppearanceManager.load(this@MainActivity)) }
            var activeRole by remember { mutableStateOf(if (householdPreviewMode) AccountRole.HOUSEHOLD else app.container.currentAccount()?.role ?: AccountRole.COLLECTOR) }
            KabadiwalaConnectTheme(
                darkTheme = AppearanceManager.isDark(appearanceMode),
                role = activeRole
            ) {
                val navController = rememberNavController()
                val uiScope = rememberCoroutineScope()
                // Demo mode has no persisted account/session. Keep only this
                // short-lived flag across activity recreation so a language
                // change does not send the demo user back to AUTH.
                var demoMode by rememberSaveable { mutableStateOf(householdPreview) }
                var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
                var updateBusy by remember { mutableStateOf(false) }
                var updateError by remember { mutableStateOf<String?>(null) }
                val cachedAccount = app.container.currentAccount()
                val initialRoute = if (householdLivePreview || demoMode) Destinations.HOME else if (!app.container.hasRestorableSession() || cachedAccount == null) Destinations.AUTH else if (cachedAccount.role == AccountRole.RECYCLER && cachedAccount.verificationStatus != RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_VERIFY else if (cachedAccount.role == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val isTopLevel = route in Destinations.topLevelFor(activeRole, newNavigation = !demoMode)
                val languageSelected = LocaleManager.hasPersistedTag(this@MainActivity)
                var navGuardReady by remember { mutableStateOf(false) }

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
                LaunchedEffect(languageSelected, route, demoMode, app.container.hasRestorableSession()) {
                    if (languageSelected && !demoMode && !app.container.hasRestorableSession() && route != Destinations.AUTH && route != null) {
                        navController.navigate(Destinations.AUTH) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                }

                val connection by app.container.connectivityObserver.state
                    .collectAsStateWithLifecycle(initialValue = ConnectionState.ONLINE)
                val unreadNotifications by app.container.unreadNotificationCount(app.container.currentAccount()?.profileId.orEmpty())
                    .collectAsStateWithLifecycle(initialValue = 0)
                LaunchedEffect(connection) {
                    if (!householdLivePreview && connection == ConnectionState.ONLINE && app.container.hasRestorableSession()) {
                        val hadValidSession = app.container.hasValidSession()
                        val refreshedAccount = app.container.refreshAccount()
                        if (app.container.hasValidSession()) {
                            // Pull server deltas after auth refresh so a
                            // reconnect repairs stale local state before the
                            // broader catalogue refresh runs.
                            runCatching { app.container.reconcileChanges() }
                            app.container.refreshCatalogs()
                        } else if (!hadValidSession && refreshedAccount == null) {
                            // A refresh token can be present after an expired
                            // or revoked session. Do not strand the user on a
                            // collector screen with a permanent “session
                            // expired” banner: clear the account boundary and
                            // return to the real sign-in route.
                            app.container.clearAccount()
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
                LaunchedEffect(languageSelected, connection) {
                    // Retry after connectivity comes online. The first check can
                    // otherwise race the network observer during cold start.
                    if (languageSelected && connection == ConnectionState.ONLINE) {
                        AppUpdateManager.check(this@MainActivity)?.let { update ->
                            availableUpdate = update
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
                    Destinations.KABADIWALA_INVENTORY -> stringResource(R.string.nav_kabadiwala_inventory)
                    Destinations.KABADIWALA_PICKUPS -> stringResource(R.string.nav_kabadiwala_pickups)
                    Destinations.KABADIWALA_LOTS -> stringResource(R.string.nav_kabadiwala_lots)
                    Destinations.HOUSEHOLD_DEAL -> stringResource(R.string.future_transaction_chat)
                    Destinations.REWARDS -> stringResource(R.string.settings_rewards)
                    Destinations.SCHEMES -> stringResource(R.string.settings_schemes)
                    Destinations.ACTIVITIES -> stringResource(R.string.settings_diy)
                    Destinations.CHAT, Destinations.CHAT_DETAIL -> stringResource(R.string.settings_messages)
                    Destinations.DISPUTE_ANALYTICS -> stringResource(R.string.settings_disputes)
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
                                KcBottomBar(currentRoute = route, role = activeRole, unreadNotifications = unreadNotifications, demoMode = demoMode, onNavigate = { target ->
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
                                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.testing_mode_banner),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
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
                                        app.container.authenticationRepository.logout()
                                        app.container.clearAccount()
                                        activeRole = AccountRole.COLLECTOR
                                        navController.navigate(Destinations.AUTH) { popUpTo(0) }
                                    }
                                },
                                onDemo = {
                                    demoMode = true
                                    activeRole = AccountRole.COLLECTOR
                                    navController.navigate(Destinations.HOME) {
                                        popUpTo(Destinations.AUTH) { inclusive = true }
                                    }
                                },
                                demoMode = demoMode,
                                role = activeRole,
                                onAuthFinished = {
                                    activeRole = app.container.currentAccount()?.role ?: AccountRole.COLLECTOR
                                    val account = app.container.currentAccount()
                                    val target = if (activeRole == AccountRole.RECYCLER && account?.verificationStatus != RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_VERIFY else if (activeRole == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
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
}
