package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Observes connectivity and exposes it as [StateFlow] for Compose screens.
 */
interface ConnectivityObserver {
    val state: StateFlow<ConnectionState>

    /** Called by the future network layer when an API call fails/succeeds. */
    fun markApiReachable(reachable: Boolean)

    fun start()
    fun stop()
}

/**
 * Real implementation based on [ConnectivityManager.NetworkCallback].
 * Uses the application context; safe to keep for the app lifetime.
 */
class SystemConnectivityObserver(context: Context) : ConnectivityObserver {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val hasNetwork = MutableStateFlow(currentHasNetwork())
    private val apiReachable = MutableStateFlow<Boolean?>(null)

    override val state: StateFlow<ConnectionState> =
        combine(hasNetwork, apiReachable, ::resolveConnectionState)
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, resolveConnectionState(hasNetwork.value, null))

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            hasNetwork.value = true
        }

        override fun onLost(network: Network) {
            hasNetwork.value = currentHasNetwork()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            hasNetwork.value = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
        }
    }

    private var registered = false

    override fun markApiReachable(reachable: Boolean) {
        apiReachable.value = reachable
    }

    override fun start() {
        if (registered) return
        registered = true
        hasNetwork.value = currentHasNetwork()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback
            )
        }
    }

    override fun stop() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        scope.cancel()
    }

    private fun currentHasNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/** In-memory fake for previews and unit tests. */
class FakeConnectivityObserver(initial: ConnectionState = ConnectionState.ONLINE) :
    ConnectivityObserver {
    private val backing = MutableStateFlow(initial)
    override val state: StateFlow<ConnectionState> = backing.asStateFlow()
    override fun markApiReachable(reachable: Boolean) {
        backing.value = if (reachable) ConnectionState.ONLINE else ConnectionState.LIMITED
    }

    fun emit(state: ConnectionState) {
        backing.value = state
    }

    override fun start() = Unit
    override fun stop() = Unit
}
