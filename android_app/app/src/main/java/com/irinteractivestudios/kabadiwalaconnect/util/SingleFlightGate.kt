package com.irinteractivestudios.kabadiwalaconnect.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small synchronous guard for UI actions that launch coroutines. The caller
 * must call [exit] from a finally block so a failed request can be retried.
 */
class SingleFlightGate {
    private val active = AtomicBoolean(false)

    fun tryEnter(): Boolean = active.compareAndSet(false, true)

    fun exit() {
        active.set(false)
    }
}
