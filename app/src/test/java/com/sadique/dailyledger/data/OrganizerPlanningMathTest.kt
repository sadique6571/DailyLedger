package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class OrganizerPlanningMathTest {
    private val circle = OrganizerCircleEntity("c", "owner", "Friends", 500000L, 20, "2027-01", 0L)
    private fun member(id: String, half: Int) =
        OrganizerMemberEntity(id, "owner", "c", id, halfUnits = half, createdAt = 0L)
    private fun payment(id: String, amount: Long) =
        OrganizerCollectionEntity("pay-$id", "owner", "c", id, "2027-01", amount, "2027-01-03", "Cash", 0L)

    @Test fun countsPartialAndHalfSharesAndPreservesPoolTarget() {
        val result = organizerMonthlySummary(circle, listOf(member("ali", 2), member("half", 1), member("unpaid", 2)),
            listOf(payment("ali", 500000), payment("half", 100000)), YearMonth.of(2027, 1))
        assertEquals(10000000L, result.expectedMinor)
        assertEquals(600000L, result.collectedMinor)
        assertEquals(9400000L, result.outstandingMinor)
        assertEquals(1, result.fullyPaidShares)
        assertEquals(1, result.partialShares)
        assertEquals(1, result.unpaidShares)
    }
    @Test fun advanceCanSpanThreeMonthsButNeverExceedSchedule() {
        val m = YearMonth.of(2027, 1)
        val allocated = allocateAdvanceInstallment(500000, m, 4, mapOf(m to 100000L), 1400000L)
        assertEquals(400000L, allocated[m])
        assertEquals(500000L, allocated[m.plusMonths(1)])
        assertEquals(500000L, allocated[m.plusMonths(2)])
        assertEquals(3, allocated.size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun advanceOverflowRejectsRatherThanSilentlyDiscardingMoney() {
        allocateAdvanceInstallment(500000, YearMonth.of(2027, 1), 2, emptyMap(), 1100000)
    }
    @Test fun lateOnlyAfterExplicitDueDate() {
        val due = LocalDate.of(2027, 1, 10)
        assertEquals(OrganizerPaymentState.PARTIAL, organizerPaymentState(100, 500, due, due))
        assertEquals(OrganizerPaymentState.OVERDUE, organizerPaymentState(100, 500, due, due.plusDays(1)))
        assertEquals(OrganizerPaymentState.PAID, organizerPaymentState(500, 500, due, due.plusDays(1)))
    }
    @Test fun payoutShortfallNeverChangesLedger() {
        assertEquals(250000L, organizerPayoutShortfall(1000000, 750000))
        assertEquals(0L, organizerPayoutShortfall(750000, 1000000))
    }
}
