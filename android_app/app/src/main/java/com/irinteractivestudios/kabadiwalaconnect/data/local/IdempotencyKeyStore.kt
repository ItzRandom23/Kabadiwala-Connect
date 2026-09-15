package com.irinteractivestudios.kabadiwalaconnect.data.local

import android.content.Context
import java.util.UUID

/** Process-death safe keys for mutations that support backend replay. */
class IdempotencyKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("idempotency_keys", Context.MODE_PRIVATE)

    fun getOrCreate(operation: String): String = prefs.getString(key(operation), null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(key(operation), it).apply() }

    fun clear(operation: String) = prefs.edit().remove(key(operation)).apply()

    private fun key(operation: String) = "operation:${operation.take(120)}"
}
