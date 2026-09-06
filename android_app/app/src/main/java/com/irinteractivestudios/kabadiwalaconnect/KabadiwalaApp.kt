package com.irinteractivestudios.kabadiwalaconnect

import android.app.Application
import android.content.Context
import com.irinteractivestudios.kabadiwalaconnect.di.AppContainer
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager

/**
 * Application entry point.
 *
 * Owns the [AppContainer] service locator and starts/stops connectivity
 * observation for the app lifetime. Requests NO permissions here.
 */
class KabadiwalaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivityObserver.start()
    }

    override fun onTerminate() {
        container.connectivityObserver.stop()
        super.onTerminate()
    }
}
