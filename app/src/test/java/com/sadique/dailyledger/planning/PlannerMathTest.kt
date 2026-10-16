package com.sadique.dailyledger.planning

import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ai.SpendingInsights
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlannerMathTest {
    private fun tx(id: String, owner: String, type: String, amount: Long, date: String) = TransactionEntity(id, owner, type, amount, "Milk", "", date, 1, 1)
    private val bill = RecurringBill("bca77004-54fe-4812-8059-2df6d98baf88", "Rent", "EXPENSE", "Rent", 10000, 31, "2026-01")
    @Test fun monthlyBudgetsExcludeOtherOwnersIncomeAndAdjacentMonths() {
        val rows = listOf(tx("1", "me", "EXPENSE", 799, "2026-10-01"), tx("2", "other", "EXPENSE", 10000, "2026-10-01"), tx("3", "me", "INCOME", 10000, "2026-10-01"), tx("4", "me", "EXPENSE", 10000, "2026-09-30"))
        val progress = PlannerMath.budgets("me", listOf(CategoryBudget("Milk", 1000)), rows, YearMonth.of(2026, 10)).single()
        assertEquals(799, progress.spent.toInt()); assertFalse(progress.nearLimit)
        assertTrue(progress.copy(spent = 800).nearLimit); assertFalse(progress.copy(spent = 800).exceeded)
        assertTrue(progress.copy(spent = 1200).exceeded); assertEquals(1f, progress.copy(spent = 1200).fraction)
    }
    @Test fun recurringDueDateClampsShortAndLeapMonthsAndRetainsMissedMonths() {
        assertEquals(LocalDate.parse("2026-01-31"), bill.nextDue())
        assertEquals(LocalDate.parse("2026-02-28"), bill.copy(handledThrough = "2026-01").nextDue())
        assertEquals(LocalDate.parse("2028-02-29"), bill.copy(startMonth = "2028-02").nextDue())
        assertEquals(1, PlannerMath.due(listOf(bill), LocalDate.parse("2026-08-01")).size)
        assertTrue(PlannerMath.due(listOf(bill.copy(enabled = false)), LocalDate.parse("2026-08-01")).isEmpty())
        assertTrue(PlannerMath.due(listOf(bill), LocalDate.parse("2026-01-30")).isEmpty())
    }
    @Test fun retriesUseSameIdentityButNextMonthIsANewOccurrence() {
        assertEquals(bill.batchId(), bill.copy(title = "Rent corrected", amountMinor = 15000).batchId())
        assertNotEquals(bill.batchId(), bill.copy(handledThrough = "2026-01").batchId())
    }
    @Test fun calendarShowsHistoricalInstallmentAndTwoPartialReceiptsWithoutDoubleCounting() {
        val c = CommitteeEntity("c", "me", "Office", 1000, 12, "2026-09", 2, false, true, "", 1, 2)
        val payment = CommitteePaymentEntity("p", "me", "c", 1, "2026-09", 2000, System.currentTimeMillis())
        val receipt = CommitteeReceiptEntity("r1", "me", "c", 5000, "2026-10-04", "first", 1)
        val all = listOf(receipt, receipt.copy(id = "r2", amountMinor = 7000, date = "2026-10-05"), receipt.copy(id = "other", ownerId = "other"), receipt.copy(id = "undated", date = null))
        val september = ReportQueries.calendar("me", YearMonth.of(2026, 9), listOf(c), listOf(payment), all, emptyList())
        assertEquals(2000L, september.installments.single().paid); assertTrue(september.receipts.isEmpty())
        val october = ReportQueries.calendar("me", YearMonth.of(2026, 10), listOf(c), listOf(payment), all, emptyList())
        assertEquals(0L, october.installments.single().paid); assertEquals(2000L, october.installments.single().expected)
        assertTrue(october.installments.single().personalTurn); assertEquals(12000L, october.receipts.sumOf { it.amountMinor })
    }
    @Test fun comparisonKeepsCalendarMonthsSeparateFromSalaryCycleAndOtherAccounts() {
        val rows = listOf(tx("a", "me", "INCOME", 1000, "2026-10-01"), tx("b", "me", "EXPENSE", 300, "2026-10-02"), tx("c", "me", "EXPENSE", 400, "2026-09-02"), tx("d", "other", "EXPENSE", 9999, "2026-10-02"))
        val a = ReportQueries.month("me", YearMonth.of(2026, 10), rows, emptyList())
        assertEquals(1000L, a.income); assertEquals(300L, a.expenses); assertEquals(mapOf("Milk" to 300L), a.categories)
        assertEquals(400L, ReportQueries.month("me", YearMonth.of(2026, 9), rows, emptyList()).expenses)
    }
    @Test fun insightsUseUdharRepaymentsOnceAndExcludeOtherOwnersAndFutureDates() {
        val rows = listOf(tx("a", "me", "INCOME", 10000, "2026-10-01").copy(category = "Salary"), tx("b", "me", "EXPENSE", 2000, "2026-10-02"))
        val paid = CreditPaymentEntity("c", "me", "credit", 1500, "2026-10-03", "", 2)
        val snapshot = SpendingInsights.calculate("me", rows, LocalDate.parse("2026-10-06"), creditPayments = listOf(paid, paid.copy(id = "other", ownerId = "other"), paid.copy(id = "future", date = "2026-10-30")))
        assertEquals(6500L, snapshot.summary.remaining)
        assertEquals(2000L, snapshot.summary.expenses)
        assertEquals(1500L, snapshot.summary.udharPayments)
        assertEquals(monthlySalarySummary(rows, "2026-10", creditPayments = listOf(paid)), snapshot.summary)
    }
}
