package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OrganizerCashControlsTest {
    private val circle = OrganizerCircleEntity("c", "owner", "Friends", 500000L, 20, "2027-01", 0L)
    private val day = LocalDate.of(2027, 1, 8)
    private fun collect(id: String, amount: Long, method: String = "Cash", owner: String = "owner") =
        OrganizerCollectionEntity(id, owner, "c", "m", "2027-01", amount, day.toString(), method, 0L)
    private fun payout(id: String, amount: Long, method: String = "Cash") =
        OrganizerPayoutEntity(id, "owner", "c", "m", 1, amount, day.toString(), method, 0L)

    @Test fun cashCloseIgnoresBankAndAnotherAccount() {
        val result = organizerCashClosingPreview(circle, day, 200000L, 360000L,
            listOf(collect("a", 200000), collect("bank", 900000, "Bank"),
                collect("other", 600000, owner = "another")),
            listOf(payout("p", 40000)))
        assertEquals(360000L, result.expectedClosingMinor)
        assertEquals(0L, result.discrepancyMinor)
        assertEquals(200000L, result.collectionsMinor)
    }
    @Test fun discrepancyIsSignedAndNeverHidden() {
        val result = organizerCashClosingPreview(circle, day, 100000L, 90000L, emptyList(), emptyList())
        assertEquals(-10000L, result.discrepancyMinor)
    }
    @Test fun payoutRequiresFilledTurnAndEnoughCash() {
        val member = OrganizerMemberEntity("m", "owner", "c", "Ali", halfUnits = 2, turnNumber = 1, createdAt = 0L)
        val blocked = organizerPayoutReadiness(circle, member, listOf(member), emptyList(), 500000L)
        assertFalse(blocked.canProceed)
        assertEquals(9500000L, blocked.shortfallMinor)
        val ready = organizerPayoutReadiness(circle, member, listOf(member), emptyList(), 10000000L)
        assertTrue(ready.canProceed)
    }
    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerPayoutPreviewRejected() {
        val member = OrganizerMemberEntity("m", "other", "c", "Ali", createdAt = 0L)
        organizerPayoutReadiness(circle, member, emptyList(), emptyList(), 0L)
    }
}
