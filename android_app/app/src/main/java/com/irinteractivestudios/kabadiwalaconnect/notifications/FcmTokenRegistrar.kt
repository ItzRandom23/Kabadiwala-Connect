package com.irinteractivestudios.kabadiwalaconnect.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Firebase is optional in local/demo builds. A missing google-services.json
 * therefore produces no token and no crash; configured staging/release builds
 * initialize the default app and return the provider token asynchronously.
 */
object FcmTokenRegistrar {
    fun fetchToken(context: Context, onToken: (String) -> Unit) {
        runCatching {
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) ?: return
            // initializeApp() above makes this app the default Firebase app;
            // the public SDK accessor intentionally uses that default.
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) task.result?.trim()?.takeIf { it.isNotBlank() }?.let(onToken)
            }
        }
    }
}
