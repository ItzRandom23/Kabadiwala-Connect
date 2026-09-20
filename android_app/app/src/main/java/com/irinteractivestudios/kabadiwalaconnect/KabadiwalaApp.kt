package com.irinteractivestudios.kabadiwalaconnect

import android.app.Application
import android.content.Context
import com.irinteractivestudios.kabadiwalaconnect.di.AppContainer
import com.irinteractivestudios.kabadiwalaconnect.ui.demo.DemoSessionStore
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
        // Demo state is never initialized in a production process. This keeps
        // the local fixture store outside the production session lifecycle.
        if (BuildConfig.DEBUG) DemoSessionStore.initialize(this)
        container = AppContainer(this)
        container.connectivityObserver.start()
        // Do not arm authenticated WorkManager or push-registration work from
        // Application.onCreate. Session restoration is owned by MainActivity;
        // background work is re-armed only after that coordinator has proven a
        // valid account and access token.
    }

    override fun onTerminate() {
        container.connectivityObserver.stop()
        super.onTerminate()
    }
}
