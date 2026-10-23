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
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.planning.CategoryBudget
import com.sadique.dailyledger.planning.PlannerMath
import com.sadique.dailyledger.ui.money
import java.time.YearMonth

/**
 * Budget alerts (Round 4): posts a phone notification the first time a category
 * budget crosses 80% or 100% in a month. Each category+month+level fires once,
 * remembered in SharedPreferences so it never spams.
 */
object BudgetNotifier {
    private const val CHANNEL_ID = "budget_alerts"

    fun check(context: Context, owner: String, rules: List<CategoryBudget>, records: List<TransactionEntity>) {
        if (rules.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val month = YearMonth.now()
        val rows = PlannerMath.budgets(owner, rules, records, month)
        val prefs = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("sent", emptySet())!!.toMutableSet()
        var changed = false
        rows.filter { it.limit > 0 }.forEach { row ->
            val level = when {
                row.exceeded -> 100
                row.nearLimit -> 80
                else -> 0
            }
            if (level == 0) return@forEach
            val key = "${row.category}|$month|$level"
            if (key in sent) return@forEach
            sent += key
            changed = true
            val text = if (level == 100) {
                "Budget pura ho gaya: ${row.category} — ${money(row.spent)} / ${money(row.limit)} is mahine."
            } else {
                "Budget 80% ho gaya: ${row.category} — ${money(row.spent)} / ${money(row.limit)}. Kharcha sambhal kar."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Budget alert")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(("budget-${row.category}-$level").hashCode(), notification)
            } catch (_: SecurityException) {
                // Permission revoked between the check and posting — stay silent.
            }
        }
        if (changed) prefs.edit().putStringSet("sent", sent).apply()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When a category budget reaches 80% or 100%" },
            )
        }
    }
}
