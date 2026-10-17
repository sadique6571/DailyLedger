package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class OrganizerInstallmentCalendarTest {
    private val circle = OrganizerCircleEntity("c", "owner", "Family", 500000, 3, "2027-01", 0)
    private val members = listOf(
        OrganizerMemberEntity("a", "owner", "c", "Ali", halfUnits = 2, createdAt = 0),
        OrganizerMemberEntity("b", "owner", "c", "Bilal", halfUnits = 1, createdAt = 0),
        OrganizerMemberEntity("x", "other", "c", "Hidden", halfUnits = 2, createdAt = 0))
    private fun pay(id: String, member: String, amount: Long, month: String = "2027-01", owner: String = "owner") =
        OrganizerCollectionEntity(id, owner, "c", member, month, amount, "2027-01-03", "Cash", 0)

    @Test fun calendarHandlesFullHalfAndUnpaidWithoutCrossOwnerLeak() {
        val months = organizerInstallmentCalendar(circle, members,
            listOf(pay("1", "a", 500000), pay("2", "b", 100000), pay("3", "x", 500000, owner = "other")))
        assertEquals(3, months.size)
        assertEquals(YearMonth.of(2027, 1), months[0].month)
        assertEquals(750000L, months[0].expectedAssignedMinor)
        assertEquals(600000L, months[0].collectedMinor)
        assertEquals(1, months[0].paidMembers)
        assertEquals(1, months[0].partialMembers)
        assertEquals(0, months[0].unpaidMembers)
        assertEquals(2, months[1].unpaidMembers)
        assertEquals(750000L, months[1].unpaidMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessInstallmentRejected() {
        organizerInstallmentCalendar(circle, members, listOf(pay("1", "b", 250001)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownOwnerMemberRejected() {
        organizerInstallmentCalendar(circle, members, listOf(pay("1", "x", 100)))
    }
}
