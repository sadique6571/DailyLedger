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
    @Test fun manualDayOverridesRecordedDayAndWorksBeforeFirstSalary() {
        val date = LocalDate.parse("2026-10-17")
        val summary = SalarySummary(100000, 70000, 0)
        val rows = listOf(salary("2026-10-05"))
        val guide = DailySpendingGuide.calculate(rows, summary, date, 25)
        assertEquals(LocalDate.parse("2026-10-25"), guide.expectedSalaryDate)
        assertEquals(8L, guide.daysUntilSalary)
        assertEquals(3750L, guide.dailyAllowanceMinor)
        assertEquals(LocalDate.parse("2026-11-01"), DailySpendingGuide.calculate(rows, summary, date, 1).expectedSalaryDate)
        assertEquals(LocalDate.parse("2026-10-25"), DailySpendingGuide.calculate(emptyList(), summary, date, 25).expectedSalaryDate)
        assertEquals(LocalDate.parse("2026-11-05"), DailySpendingGuide.calculate(emptyList(), summary, date, 5).expectedSalaryDate)
    }
    @Test fun manualMonthEndLeapYearAndLateSalaryStayAccurate() {
        val summary = SalarySummary(100000, 70000, 0)
        assertEquals(LocalDate.parse("2026-02-28"), DailySpendingGuide.calculate(listOf(salary("2026-01-31")), summary, LocalDate.parse("2026-02-01"), 31).expectedSalaryDate)
        assertEquals(LocalDate.parse("2028-02-29"), DailySpendingGuide.calculate(listOf(salary("2028-01-31")), summary, LocalDate.parse("2028-02-01"), 31).expectedSalaryDate)
        val late = DailySpendingGuide.calculate(listOf(salary("2026-09-05")), summary, LocalDate.parse("2026-10-17"), 10)
        assertEquals(LocalDate.parse("2026-10-10"), late.expectedSalaryDate)
        assertTrue(late.daysUntilSalary!! < 0)
        assertNull(late.dailyAllowanceMinor)
    }
    @Test(expected = IllegalArgumentException::class) fun manualDayRejectsZero() {
        DailySpendingGuide.calculate(emptyList(), SalarySummary(0, 0, 0), LocalDate.parse("2026-10-17"), 0)
    }
    @Test(expected = IllegalArgumentException::class) fun manualDayRejectsAbove31() {
        DailySpendingGuide.calculate(emptyList(), SalarySummary(0, 0, 0), LocalDate.parse("2026-10-17"), 32)
    }
    @Test fun editableCalendarSupportsTwentyAndFortyDaySalaryGaps() {
        val last = LocalDate.parse("2026-10-05")
        val summary = SalarySummary(100000, 70000, 0)
        val rows = listOf(salary(last.toString()))
        for (gap in listOf(20L, 40L)) {
            val expected = last.plusDays(gap)
            val guide = DailySpendingGuide.calculate(rows, summary, last.plusDays(10),
                plannedSalaryDate = expected, salaryRecordedWhenPlanned = last)
            assertEquals(expected, guide.expectedSalaryDate)
            assertEquals(gap - 10, guide.daysUntilSalary)
        }
        val overdue = DailySpendingGuide.calculate(rows, summary, last.plusDays(41),
            plannedSalaryDate = last.plusDays(40), salaryRecordedWhenPlanned = last)
        assertEquals(-1L, overdue.daysUntilSalary)
        assertNull(overdue.dailyAllowanceMinor)
    }
    @Test fun newRecordedSalarySuggestsSameDateNextMonthWithoutLosingManualOverrides() {
        val summary = SalarySummary(100000, 70000, 0)
        val first = LocalDate.parse("2026-10-05")
        val next = LocalDate.parse("2026-10-25")
        val rows = listOf(salary(first.toString()), salary(next.toString()))
        assertEquals(next.plusMonths(1), DailySpendingGuide.calculate(rows, summary, next,
            plannedSalaryDate = first.plusMonths(1), salaryRecordedWhenPlanned = first).expectedSalaryDate)
        val manual = LocalDate.parse("2026-12-04")
        assertEquals(manual, DailySpendingGuide.calculate(rows, summary, next,
            plannedSalaryDate = manual, salaryRecordedWhenPlanned = next).expectedSalaryDate)
    }

}
