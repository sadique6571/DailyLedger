package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.SalarySummary
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.data.isSalary
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DailySpendingGuide(
    val lastSalaryDate: LocalDate?,
    val expectedSalaryDate: LocalDate?,
    val daysUntilSalary: Long?,
    val dailyAllowanceMinor: Long?,
    val lowBalance: Boolean,
) {
    companion object {
        /** Uses this account's recorded balance only. Never advances or resets a salary cycle. */
        fun calculate(transactions: List<TransactionEntity>, summary: SalarySummary, today: LocalDate): DailySpendingGuide {
            val lastSalary = transactions.asSequence().filter { it.isSalary() && it.amountMinor > 0 }
                .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                .filter { it <= today }.maxOrNull()
            // LocalDate clamps month-end salaries (e.g. 31 January -> 28 February).
            val expected = lastSalary?.plusMonths(1)
            val days = expected?.let { ChronoUnit.DAYS.between(today, it) }
            val allowance = days?.takeIf { it > 0 }?.let { summary.remaining.coerceAtLeast(0L) / it }
            val available = summary.openingBalanceMinor + summary.salary + summary.otherIncome
            val low = summary.remaining < 0L ||
                (available > 0 && BigDecimal.valueOf(summary.remaining).multiply(BigDecimal.TEN) <= BigDecimal.valueOf(available)) ||
                (summary.remaining == 0L && summary.totalOutflow > 0)
            return DailySpendingGuide(lastSalary, expected, days, allowance, low)
        }
    }
}
