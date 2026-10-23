package com.sadique.dailyledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sadique.dailyledger.R
import com.sadique.dailyledger.MainActivity
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LedgerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context).all.first()
                val displayPrefs = com.sadique.dailyledger.ui.DisplayPreferences(context)
                val strings = com.sadique.dailyledger.i18n.LedgerStrings(displayPrefs.language == "ur", com.sadique.dailyledger.i18n.LedgerStrings.load(context))
                var text = "Balance hidden"
                var duesText = ""
                // Visibility rule (modernized): the user controls this with the
                // "Widget par balance dikhao" toggle in Settings. Privacy mode and
                // private entries still always hide the balance. (The old rule also
                // required a workspace flag that defaulted off and had no UI switch
                // anywhere, so the widget could never show the balance.)
                val owner = settings.user?.id
                if (owner != null && displayPrefs.widgetShowBalance && !displayPrefs.privateAmounts &&
                    com.sadique.dailyledger.auth.FirebaseRuntime.auth(context).currentUser?.uid == owner
                ) {
                    val repo = LedgerRepository(AppDatabase.get(context), owner)
                    val rows = repo.transactions.first()
                    if (rows.none { it.privateEntry }) {
                        val cycle = repo.ledgerCycle.first()
                        val cp = repo.committeePayments.first(); val savings = repo.savings.first(); val credit = repo.creditPayments.first()
                        val summary = cycle?.let { ledgerCycleSummary(rows, cp, savings, it, creditPayments = credit) }
                            ?: monthlySalarySummary(rows, java.time.YearMonth.now().toString(), cp, savings, credit)
                        text = com.sadique.dailyledger.ui.money(summary.remaining)
                        val credits = repo.creditPurchases.first()
                        val udharDue = credits.filter { !it.closed }.sumOf { c -> (c.amountMinor - credit.filter { p -> p.creditId == c.id }.sumOf { it.amountMinor }).coerceAtLeast(0L) }
                        val committees = repo.committees.first()
                        val kametiDue = committees.filter { it.active }.sumOf { c -> (c.expectedPayout() - cp.filter { p -> p.committeeId == c.id }.sumOf { it.amountMinor }).coerceAtLeast(0L) }
                        duesText = when {
                            udharDue > 0 && kametiDue > 0 -> "Udhar ${com.sadique.dailyledger.ui.money(udharDue)} • Kameti ${com.sadique.dailyledger.ui.money(kametiDue)}"
                            udharDue > 0 -> "Udhar ${com.sadique.dailyledger.ui.money(udharDue)}"
                            kametiDue > 0 -> "Kameti ${com.sadique.dailyledger.ui.money(kametiDue)}"
                            else -> ""
                        }
                    }
                }
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.ledger_widget)
                    views.setTextViewText(R.id.widget_balance, strings.text(text))
                    views.setTextViewText(R.id.widget_dues, if (text == "Balance hidden") "" else duesText)
                    views.setTextViewText(R.id.widget_add, "+ " + strings.text("Expense"))
                    views.setTextViewText(R.id.widget_udhar, "+ " + strings.text("Udhar"))
                    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id * 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, id * 3 + 1, Intent(context, MainActivity::class.java).putExtra("ledger_action", "expense").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    views.setOnClickPendingIntent(R.id.widget_udhar, PendingIntent.getActivity(context, id * 3 + 2, Intent(context, MainActivity::class.java).putExtra("ledger_action", "credit").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    manager.updateAppWidget(id, views)
                }
            } catch (_: Exception) {
                ids.forEach { id -> val views = RemoteViews(context.packageName, R.layout.ledger_widget); views.setTextViewText(R.id.widget_balance, "Open DailyLedger"); manager.updateAppWidget(id, views) }
            } finally { pending.finish() }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LedgerWidget::class.java))
            if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, LedgerWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}
