package com.irinteractivestudios.kabadiwalaconnect

import android.app.Application
import android.content.Context
import com.irinteractivestudios.kabadiwalaconnect.di.AppContainer
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Owns the [AppContainer] service locator and starts/stops connectivity
 * observation for the app lifetime. Requests NO permissions here.
 */
class KabadiwalaApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivityObserver.start()
        // Re-arm durable offline work after a process death or device reboot.
        // WorkManager still waits for connectivity and exits immediately when
        // there is no authenticated session.
        applicationScope.launch {
            if (container.hasRestorableSession()) container.syncScheduler.requestSync()
        }
    }

    override fun onTerminate() {
        container.connectivityObserver.stop()
        super.onTerminate()
    }
}
