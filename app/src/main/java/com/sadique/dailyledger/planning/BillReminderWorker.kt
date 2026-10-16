package com.sadique.dailyledger.planning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sadique.dailyledger.MainActivity
import com.sadique.dailyledger.R
import com.sadique.dailyledger.auth.FirebaseRuntime
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = FirebaseRuntime.auth(applicationContext).currentUser?.uid ?: return Result.success()
        return try {
            val due = PlannerMath.due(PlannerStore(applicationContext, owner).data.value.bills, LocalDate.now())
            val notifications = NotificationManagerCompat.from(applicationContext)
            if (due.isEmpty() || !notifications.areNotificationsEnabled()) return Result.success()
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
            val prefs = applicationContext.getSharedPreferences("bill-notifications", Context.MODE_PRIVATE)
            val key = "sent:$owner"; val today = LocalDate.now().toString()
            if (prefs.getString(key, "") == today) return Result.success()
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("bills", "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel("bills")?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
            val intent = PendingIntent.getActivity(applicationContext, 106, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // No amounts, item names or account details on the lock screen.
            val urdu = applicationContext.getSharedPreferences("display_preferences_v1", Context.MODE_PRIVATE).getString("language", "en") == "ur"
            notifications.notify(106, NotificationCompat.Builder(applicationContext, "bills").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (urdu) "بلوں کی یاد دہانی" else "Bills ready for review")
                .setContentText(if (urdu) "اندراجات کا جائزہ لینے کے لیے ڈیلی لیجر کھولیں۔" else "Open Daily Ledger → More → Bills & reminders.")
                .setContentIntent(intent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            prefs.edit().putString(key, today).apply()
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("bill-reminders", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS).build())
        }
    }
}
