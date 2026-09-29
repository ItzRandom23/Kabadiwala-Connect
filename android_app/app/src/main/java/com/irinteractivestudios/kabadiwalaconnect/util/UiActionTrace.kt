package com.irinteractivestudios.kabadiwalaconnect.util

import android.os.SystemClock
import android.util.Log
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/** Debug-only, bounded tap → state → frame → server timing without account data. */
object UiActionTrace {
    private data class Pending(val startedNs: Long, var frameLogged: Boolean = false)
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun operation(key: String) = key.substringBefore('-').take(40)
    private fun elapsedMs(startedNs: Long) = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000

    fun begin(key: String) {
        if (!BuildConfig.DEBUG) return
        pending[key] = Pending(SystemClock.elapsedRealtimeNanos())
    }

    fun statePublished(key: String) {
        if (!BuildConfig.DEBUG) return
        pending[key]?.let { Log.d("KcUiTiming", "operation=${operation(key)} stage=state durationMs=${elapsedMs(it.startedNs)}") }
    }

    fun requestLinked(key: String, requestId: String) {
        if (!BuildConfig.DEBUG) return
        pending[key]?.let { Log.d("KcUiTiming", "operation=${operation(key)} stage=request requestId=$requestId durationMs=${elapsedMs(it.startedNs)}") }
    }

    fun frameDrawn(key: String) {
        if (!BuildConfig.DEBUG) return
        pending[key]?.let {
            if (!it.frameLogged) {
                it.frameLogged = true
                Log.d("KcUiTiming", "operation=${operation(key)} stage=frame durationMs=${elapsedMs(it.startedNs)}")
            }
        }
    }

    fun finish(key: String, succeeded: Boolean) {
        if (!BuildConfig.DEBUG) return
        pending.remove(key)?.let { Log.d("KcUiTiming", "operation=${operation(key)} stage=response durationMs=${elapsedMs(it.startedNs)} success=$succeeded") }
    }
}
