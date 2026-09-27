package com.irinteractivestudios.kabadiwalaconnect.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.irinteractivestudios.kabadiwalaconnect.KabadiwalaApp
import com.irinteractivestudios.kabadiwalaconnect.MainActivity
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations

/** Receives token rotation and foreground messages without owning account state. */
class KcFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? KabadiwalaApp)?.container?.queuePushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (title, body) = pushCopy(message.data["type"])
        // Bump the channel id so existing installs receive the higher
        // importance setting; Android does not allow an app to upgrade an
        // already-created channel in place.
        val channelId = "kabadiwala_urgent_updates_v2"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Backend push messages intentionally carry only a notification
            // id. Open the role-scoped inbox and let it fetch authorized data.
            putExtra(MainActivity.EXTRA_NOTIFICATION_ROUTE, Destinations.NOTIFICATIONS)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.data["notificationId"]?.hashCode() ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_kc_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(message.data["notificationId"]?.hashCode() ?: body.hashCode(), notification)
    }

    private fun pushCopy(type: String?): Pair<String, String> = when (type) {
        "PICKUP_REQUESTED", "PICKUP_REASSIGNED_TO_COLLECTOR" ->
            "New pickup request" to "A household selected you for a pickup. Tap to review it."
        "PICKUP_WAITING_FOR_PICKUP" ->
            "Pickup needed nearby" to "A household is waiting for a Kabadiwala. Tap to view pickups."
        else -> getString(R.string.app_name) to getString(R.string.notification_generic_body)
    }
}
