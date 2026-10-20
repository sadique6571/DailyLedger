package com.sadique.dailyledger.planning

import com.sadique.dailyledger.data.*
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class CalendarInstallment(val committee: CommitteeEntity, val number: Int, val paid: Long, val expected: Long, val personalTurn: Boolean)
data class CalendarMonth(val installments: List<CalendarInstallment>, val receipts: List<CommitteeReceiptEntity>)
data class MonthReport(val income: Long, val expenses: Long, val savings: Long, val categories: Map<String, Long>)
object ReportQueries {
    fun calendar(owner: String, month: YearMonth, committees: List<CommitteeEntity>, payments: List<CommitteePaymentEntity>, receipts: List<CommitteeReceiptEntity>, members: List<CommitteeMemberEntity>): CalendarMonth {
        val own = committees.filter { it.ownerId == owner }
        return CalendarMonth(own.mapNotNull { c ->
            val number = runCatching { ChronoUnit.MONTHS.between(YearMonth.parse(c.startMonth), month).toInt() + 1 }.getOrNull() ?: return@mapNotNull null
            if (number !in 1..c.totalInstallments) return@mapNotNull null
            val turns = members.filter { it.ownerId == owner && it.committeeId == c.id && it.isMe }
            CalendarInstallment(c, number, payments.filter { it.ownerId == owner && it.committeeId == c.id && it.installmentNumber == number }.sumOf { it.amountMinor }, c.monthlyContribution(),
                if (turns.isNotEmpty()) turns.any { it.turnMonth == month.toString() } else c.payoutInstallment == number)
        }, receipts.filter { it.ownerId == owner && it.committeeId in own.map { c -> c.id } && it.date?.startsWith("$month-") == true })
    }
    fun month(owner: String, month: YearMonth, transactions: List<TransactionEntity>, savings: List<SavingEntity>): MonthReport {
        val rows = transactions.filter { it.ownerId == owner && it.date.startsWith("$month-") }
        return MonthReport(rows.filter { it.type == "INCOME" }.sumOf { it.amountMinor }, rows.filter { it.type == "EXPENSE" }.sumOf { it.expenseEffect() },
            savings.filter { it.ownerId == owner && it.date.startsWith("$month-") }.sumOf { it.amountMinor },
            rows.filter { it.type == "EXPENSE" }.groupBy { it.category }.mapValues { (_, values) -> values.sumOf { it.expenseEffect() } })
    }
}
