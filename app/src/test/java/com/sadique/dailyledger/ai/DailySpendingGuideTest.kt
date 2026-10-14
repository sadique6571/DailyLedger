package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailySpendingGuideTest {
    private fun salary(date: String, category: String = "Salary") = TransactionEntity("t", "owner", "INCOME", 100000, category, "", date, 0, 0)
    private fun guide(rows: List<TransactionEntity>, date: String, summary: SalarySummary = SalarySummary(100000, 70000, 0)) =
        DailySpendingGuide.calculate(rows, summary, LocalDate.parse(date))

    @Test fun countsDaysAndDividesActualRemainingBalanceIncludingCarryAndOtherIncome() {
        val result = guide(listOf(salary("2026-10-05")), "2026-10-14", SalarySummary(100000, 70000, 20000, 5000, 10000, 9000))
        assertEquals(LocalDate.parse("2026-11-05"), result.expectedSalaryDate)
        assertEquals(22L, result.daysUntilSalary)
        assertEquals(2000L, result.dailyAllowanceMinor)
        assertFalse(result.lowBalance)
    }
    @Test fun monthEndAndLeapYearAreClamped() {
        assertEquals(LocalDate.parse("2026-02-28"), guide(listOf(salary("2026-01-31")), "2026-02-01").expectedSalaryDate)
        assertEquals(LocalDate.parse("2028-02-29"), guide(listOf(salary("2028-01-31")), "2028-02-01").expectedSalaryDate)
    }
    @Test fun overdueOrDueTodayDoesNotInventANewPaydayOrAllowance() {
        val today = guide(listOf(salary("2026-09-05")), "2026-10-05")
        assertEquals(0L, today.daysUntilSalary); assertNull(today.dailyAllowanceMinor)
        val late = guide(listOf(salary("2026-09-05")), "2026-10-14")
        assertEquals(-9L, late.daysUntilSalary); assertNull(late.dailyAllowanceMinor)
    }
    @Test fun ignoresFutureSalaryInvalidDatesAndOtherIncome() {
        val result = guide(listOf(salary("2026-09-30"), salary("2026-10-03"), salary("2026-11-01"), salary("invalid"), salary("2026-10-14", "Bonus")), "2026-10-14")
        assertEquals(LocalDate.parse("2026-11-03"), result.expectedSalaryDate)
        assertNull(guide(emptyList(), "2026-10-14").expectedSalaryDate)
    }
    @Test fun lowNoticeUsesAllIncomeAndDoesNotWarnForAnEmptyLedger() {
        assertFalse(guide(emptyList(), "2026-10-14", SalarySummary(0, 0, 0)).lowBalance)
        assertTrue(guide(emptyList(), "2026-10-14", SalarySummary(10000, 9000, 0)).lowBalance)
        assertFalse(guide(emptyList(), "2026-10-14", SalarySummary(10000, 9000, 10000)).lowBalance)
        val overdrawn = guide(listOf(salary("2026-10-05")), "2026-10-14", SalarySummary(10000, 11000, 0))
        assertTrue(overdrawn.lowBalance); assertEquals(0L, overdrawn.dailyAllowanceMinor)
    }
}
