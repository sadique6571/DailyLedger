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

class LedgerWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings=SettingsStore(context).all.first()
                var text="Balance hidden"
                if(settings.user!=null && !settings.biometric && !com.sadique.dailyledger.ui.DisplayPreferences(context).privateAmounts) {
                    val owner=settings.user.id
                    if(com.sadique.dailyledger.auth.FirebaseRuntime.auth(context).currentUser?.uid==owner) {
                        val repo=LedgerRepository(AppDatabase.get(context),owner)
                        val rows=repo.transactions.first();val w=repo.profileSnapshot().workspace
                        if(w.widgetBalance && rows.none { it.privateEntry }) {
                            val cycle=repo.ledgerCycle.first()
                            val cp=repo.committeePayments.first();val savings=repo.savings.first();val credit=repo.creditPayments.first()
                            val summary=cycle?.let{ledgerCycleSummary(rows,cp,savings,it,creditPayments=credit)} ?: monthlySalarySummary(rows,java.time.YearMonth.now().toString(),cp,savings,credit)
                            text=com.sadique.dailyledger.ui.money(summary.remaining)
                        }
                    }
                }
                val latest=SettingsStore(context).all.first()
                if(latest.user?.id!=settings.user?.id || latest.biometric || com.sadique.dailyledger.ui.DisplayPreferences(context).privateAmounts)text="Balance hidden"
                if(latest.user!=null) {
                    val repo=LedgerRepository(AppDatabase.get(context),latest.user.id)
                    if(!repo.profileSnapshot().workspace.widgetBalance || repo.transactions.first().any{it.privateEntry})text="Balance hidden"
                }
                ids.forEach { id ->
                    val views=RemoteViews(context.packageName,R.layout.ledger_widget)
                    views.setTextViewText(R.id.widget_balance,text)
                    views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,id*2,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    views.setOnClickPendingIntent(R.id.widget_add,PendingIntent.getActivity(context,id*2+1,Intent(context,MainActivity::class.java).putExtra("ledger_action","expense").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    manager.updateAppWidget(id,views)
                }
            } catch(_:Exception) {
                ids.forEach { id -> val views=RemoteViews(context.packageName,R.layout.ledger_widget);views.setTextViewText(R.id.widget_balance,"Open DailyLedger");manager.updateAppWidget(id,views) }
            } finally { pending.finish() }
        }
    }
    companion object {
        fun refresh(context:Context) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,LedgerWidget::class.java))
            if(ids.isNotEmpty())context.sendBroadcast(Intent(context,LedgerWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))
        }
    }
}
