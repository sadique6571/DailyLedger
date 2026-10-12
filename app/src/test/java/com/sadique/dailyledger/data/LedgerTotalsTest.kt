package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test

class LedgerTotalsTest {
    private fun tx(type: String, amount: Long, category: String, date: String = "2026-10-06") =
        TransactionEntity("t", "owner", type, amount, category, "", date, 1, 1)
    private fun committee(shares: Int = 2) = CommitteeEntity("c", "owner", "Office", 500000, 12, "2026-10", null, false, true, "", 1, shares)
    private fun receipt(id: String, amount: Long, owner: String = "owner", committee: String = "c") =
        CommitteeReceiptEntity(id, owner, committee, amount, "2026-10-06", "", 1)

    @Test fun salarySubtractsOnlyCurrentMonthExpenses() {
        val summary = monthlySalarySummary(listOf(
            tx("INCOME", 10000000, "Salary"), tx("EXPENSE", 2500000, "Rent"),
            tx("EXPENSE", 500000, "Food"), tx("INCOME", 3000000, "Gift"),
            tx("INCOME", 10000000, "Salary", "2026-09-30"),
            tx("EXPENSE", 800000, "Food", "2026-11-01")
        ), "2026-10")
        assertEquals(10000000L, summary.salary)
        assertEquals(3000000L, summary.expenses)
        assertEquals(10000000L, summary.remaining)
        assertEquals(3000000L, summary.otherIncome)
    }
    @Test fun salaryLabelsAreRecognizedWithoutTreatingAllIncomeAsSalary() {
        assertTrue(tx("INCOME", 1, " salary ").isSalary())
        assertTrue(tx("INCOME", 1, "Monthly salary").isSalary())
        assertTrue(tx("INCOME", 1, "تنخواہ").isSalary())
        assertFalse(tx("INCOME", 1, "Other").isSalary())
        assertFalse(tx("EXPENSE", 1, "Salary").isSalary())
    }
    @Test fun overspendingRemainsNegative() {
        val summary = monthlySalarySummary(listOf(tx("INCOME", 10000, "Salary"), tx("EXPENSE", 12500, "Food")), "2026-10")
        assertEquals(-2500L, summary.remaining)
        assertEquals(0L, monthlySalarySummary(emptyList(), "2026-10").remaining)
    }
    @Test fun currentMonthKametiAndSavingsAreDeductedFromAvailableSalary() {
        val contribution = CommitteePaymentEntity("cp", "owner", "c", 1, "2026-10", 1500, 1)
        val previousContribution = contribution.copy(id = "cp-old", month = "2026-09")
        val saving = SavingEntity("s", "owner", "DIRECT", 500, "2026-10-06", "", 1)
        val previousSaving = saving.copy(id = "s-old", date = "2026-09-30")
        val summary = monthlySalarySummary(
            listOf(tx("INCOME", 10000, "Salary"), tx("EXPENSE", 2500, "Food")),
            "2026-10",
            listOf(contribution, previousContribution),
            listOf(saving, previousSaving),
        )
        assertEquals(1500L, summary.committeeContributions)
        assertEquals(500L, summary.savingsContributions)
        assertEquals(4500L, summary.totalOutflow)
        assertEquals(5500L, summary.remaining)
    }
    @Test fun manualSalaryCycleKeepsOpeningBalanceAndOnlyCountsNewEntries() {
        val cycle = LedgerCycleEntity("owner", 100, 700)
        val transactions = listOf(
            tx("INCOME", 9000, "Salary").copy(createdAt = 99),
            tx("INCOME", 10000, "Salary").copy(createdAt = 100),
            tx("INCOME", 2000, "Bonus").copy(createdAt = 120),
            tx("EXPENSE", 3000, "Food").copy(createdAt = 130),
        )
        val payments = listOf(
            CommitteePaymentEntity("old", "owner", "c", 1, "2026-10", 400, 99),
            CommitteePaymentEntity("new", "owner", "c", 2, "2026-10", 500, 135),
        )
        val savings = listOf(
            SavingEntity("old", "owner", "DIRECT", 200, "2026-10-01", "", 99),
            SavingEntity("new", "owner", "DIRECT", 600, "2026-10-02", "", 140),
        )
        val summary = ledgerCycleSummary(transactions, payments, savings, cycle, now = 150)
        assertEquals(10000L, summary.salary)
        assertEquals(2000L, summary.otherIncome)
        assertEquals(3000L, summary.expenses)
        assertEquals(500L, summary.committeeContributions)
        assertEquals(600L, summary.savingsContributions)
        assertEquals(8600L, summary.remaining)
    }
    @Test fun twoKametisReceiveOneAtATime() {
        val c = committee()
        assertEquals(1000000L, c.monthlyContribution())
        assertEquals(12000000L, c.expectedPayout())
        val first = receipt("first", 6000000)
        val partial = committeeBalance(c, listOf(first))
        assertEquals("Partially received", partial.status)
        assertEquals(6000000L, partial.remaining)
        assertFalse(partial.complete)
        val full = committeeBalance(c, listOf(first, receipt("second", 6000000)))
        assertEquals("Fully received", full.status)
        assertEquals(0L, full.remaining)
        assertTrue(full.complete)
    }
    @Test fun arbitraryPartialAmountsAreSummedAndOtherAccountsIgnored() {
        val b = committeeBalance(committee(), listOf(receipt("1", 100000), receipt("2", 500000), receipt("3", 900000, owner = "other"), receipt("4", 900000, committee = "different")))
        assertEquals(600000L, b.received)
        assertEquals(11400000L, b.remaining)
    }
    @Test fun originalSingleKametiKeepsExpectedPayout() {
        val c = committee(1)
        assertEquals(6000000L, c.expectedPayout())
        assertEquals("Not received", committeeBalance(c, emptyList()).status)
    }
    @Test(expected = ArithmeticException::class) fun payoutOverflowIsRejected() {
        committee().copy(monthlyAmountMinor = Long.MAX_VALUE).expectedPayout()
    }
}
