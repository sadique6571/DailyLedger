package com.sadique.dailyledger.notifications
import com.sadique.dailyledger.data.expenseEffect


import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sadique.dailyledger.R
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val user = SettingsStore(applicationContext).user.first() ?: return Result.success()
        val dao = AppDatabase.get(applicationContext).ledgerDao()
        val loans = dao.loansNow(user.id)
        val committees = dao.committeesNow(user.id)
        val committeePayments = dao.committeePaymentsNow(user.id)
        val committeeMembers = dao.committeeMembersNow(user.id)
        val creditPurchases = dao.creditPurchasesNow(user.id)
        val transactions = dao.transactionsNow(user.id)
        val today = LocalDate.now()

        val dueLoans = loans.count {
            !it.closed && it.dueDate?.let { date ->
                runCatching { LocalDate.parse(date) }.getOrNull()?.let { d ->
                    !d.isBefore(today) && !d.isAfter(today.plusDays(3))
                }
            } == true
        }

        val dueCredits = creditPurchases.count { !it.closed && it.dueDate?.let { date ->
            runCatching { LocalDate.parse(date) }.getOrNull()?.let { !it.isAfter(today.plusDays(3)) }
        } == true }
        val currentMonth = YearMonth.now()
        val committeeDue = committees.count { committee ->
            if (!committee.active) return@count false
            val currentInstallment = runCatching {
                val start = YearMonth.parse(committee.startMonth)
                val offset = java.time.temporal.ChronoUnit.MONTHS.between(start, currentMonth).toInt() + 1
                offset.takeIf { it in 1..committee.totalInstallments }
            }.getOrNull() ?: return@count false
            committeePayments.none { it.committeeId == committee.id && it.installmentNumber == currentInstallment }
        }

        val myTurnsThisMonth = committeeMembers.count { it.ownerId == user.id && it.isMe && !it.received && it.turnMonth == currentMonth.toString() }
        val myTurnsNextMonth = committeeMembers.count { it.ownerId == user.id && it.isMe && !it.received && it.turnMonth == currentMonth.plusMonths(1).toString() }

        val messages = mutableListOf<String>()
        if (dueCredits > 0) messages += "$dueCredits udhar payment due"
        if (dueLoans > 0) messages += "$dueLoans loan due soon"
        if (committeeDue > 0) messages += "$committeeDue kameti payment due"
        if (myTurnsThisMonth > 0) messages += "Your kameti turn is this month"
        if (today.dayOfMonth >= 25 && myTurnsNextMonth > 0) messages += "Your kameti turn is next month"
        if (today.dayOfMonth == 1) {
            val previous = currentMonth.minusMonths(1).toString()
            val income = transactions.filter { it.type == "INCOME" && it.date.startsWith(previous) }.sumOf { it.amountMinor }
            val expense = transactions.filter { it.type == "EXPENSE" && it.date.startsWith(previous) }.sumOf { it.expenseEffect() }
            messages += "Last month: income PKR ${income / 100}, expense PKR ${expense / 100}"
        }
        if (messages.isNotEmpty()) notify(messages.joinToString(" • "))
        return Result.success()
    }

    private fun notify(text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminders", "Ledger reminders", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(
            101,
            NotificationCompat.Builder(applicationContext, "reminders")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Daily Ledger")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        )
    }
}
