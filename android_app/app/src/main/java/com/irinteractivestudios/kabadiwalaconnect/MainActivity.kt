package com.irinteractivestudios.kabadiwalaconnect

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * Single-activity host (Phase 1).
 *
 * - Launches directly into Home via the nav graph.
 * - Shows the 5-tab bottom bar on top-level destinations only.
 * - Shows the offline banner on every screen when disconnected.
 * - Applies the saved locale before inflation; recreates on language change.
 * - Requests NO permissions on launch (or anywhere in Phase 1).
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
        // The collector id can be created by the remote OTP response during
        // this activity session, so ViewModels read it when they are created.
        val factory = KcViewModelFactory(app, app.container)
        setContent {
            var appearanceMode by remember { mutableStateOf(AppearanceManager.load(this@MainActivity)) }
            var activeRole by remember { mutableStateOf(app.container.currentAccount()?.role ?: AccountRole.COLLECTOR) }
            KabadiwalaConnectTheme(
                darkTheme = AppearanceManager.isDark(appearanceMode),
                role = activeRole
            ) {
                val navController = rememberNavController()
                val uiScope = rememberCoroutineScope()
                var demoMode by remember { mutableStateOf(false) }
                var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
                var updateBusy by remember { mutableStateOf(false) }
                var updateError by remember { mutableStateOf<String?>(null) }
                val cachedAccount = app.container.currentAccount()
                val initialRoute = if (!app.container.hasValidSession() || cachedAccount == null) Destinations.AUTH else if (cachedAccount.role == AccountRole.RECYCLER && cachedAccount.verificationStatus != RecyclerVerificationStatus.VERIFIED) Destinations.RECYCLER_VERIFY else if (cachedAccount.role == AccountRole.RECYCLER) Destinations.RECYCLER_MARKETPLACE else Destinations.HOME
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val isTopLevel = route in Destinations.topLevelFor(activeRole)
                val languageSelected = LocaleManager.hasPersistedTag(this@MainActivity)

                val connection by app.container.connectivityObserver.state
                    .collectAsStateWithLifecycle(initialValue = ConnectionState.ONLINE)
                LaunchedEffect(connection) {
                    if (connection == ConnectionState.ONLINE && app.container.hasValidSession()) {
                        app.container.refreshAccount()
                        app.container.refreshCatalogs()
                    }
                }
                LaunchedEffect(languageSelected) {
                    if (languageSelected) {
                        availableUpdate = AppUpdateManager.check(this@MainActivity)
                    }
                }

                val title = when (route) {
                    Destinations.PRICES -> stringResource(R.string.prices_title)
                    Destinations.RECYCLERS -> stringResource(R.string.recyclers_title)
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
                            KcTopBar(
                                title = title,
                                showBack = route != Destinations.AUTH && !isTopLevel,
                                onBack = { navController.popBackStack() }
                            )
                        },
                        bottomBar = {
                            if (isTopLevel) {
                                KcBottomBar(currentRoute = route, role = activeRole, onNavigate = { target ->
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
                            AppNavHost(
                                navController = navController,
                                factory = factory,
                                onLanguageChange = { recreate() },
                                onAppearanceChange = { appearanceMode = AppearanceManager.normalize(it) },
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
