package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class OrganizerReceiptReportTest {
    private val circle = OrganizerCircleEntity("c", "owner", "Friends", 500000, 12, "2027-01", 1)
    private val member = OrganizerMemberEntity("m", "owner", "c", "Ali", createdAt = 1)
    private val payment = OrganizerCollectionEntity("payment-1", "owner", "c", "m", "2027-01", 200000, "2027-01-06", "Cash", 2)

    @Test fun receiptIsStableAndOwnerBound() {
        assertEquals("KMT-PAYMENT-1", organizerReceiptNumber(payment))
        assertTrue(organizerReceiptText(circle, member, payment).contains("PKR 2000.00"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun cannotShareAnotherOwnersReceipt() {
        organizerReceiptText(circle, member.copy(ownerId = "other"), payment)
    }
    @Test fun forecastAndHealthAreReadOnly() {
        assertEquals(0L, organizerTurnCountdown("2027-01", 1, LocalDate.of(2027,1,3)))
        assertEquals(29L, organizerTurnCountdown("2027-01", 2, LocalDate.of(2027,1,3)))
        val h = organizerCircleHealth(circle, listOf(member), listOf(payment), emptyList(), YearMonth.of(2027,1))
        assertEquals(200000L, h.pooledBalanceMinor)
        assertEquals(1, h.membersWithOutstanding)
    }
}
