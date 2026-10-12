package com.sadique.dailyledger.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sadique.dailyledger.R
import java.io.File

object ExportNotifications {
    private const val CHANNEL_ID = "exports"
    private const val NOTIFICATION_ID = 102

    fun post(context: Context, format: String, location: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Export status", NotificationManager.IMPORTANCE_DEFAULT)
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }

        return try {
            notifications.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("$format export complete")
                    .setContentText("Saved: ${File(location).name}")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Saved to: $location"))
                    .setAutoCancel(true)
                    .build()
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
