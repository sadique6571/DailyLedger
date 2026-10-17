package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class OrganizerReminderTest {
    private val circle = OrganizerCircleEntity("c","owner","Friends",500000,12,"2027-01",0)
    private val member = OrganizerMemberEntity("m","owner","c","Ali","03000000000","",1,0,0)
    private val other = OrganizerMemberEntity("x","other","c","Hidden",createdAt=0)
    @Test fun partiallyPaidHalfShareProducesPrivateReminder() {
        val receipts = listOf(OrganizerCollectionEntity("p","owner","c","m","2027-01",100000,"2027-01-09","Cash",0))
        val result = organizerReminderCandidates(circle,listOf(member,other),receipts,YearMonth.of(2027,1),LocalDate.of(2027,1,12))
        assertEquals(1,result.size)
        assertEquals(150000L,result.single().outstandingMinor)
        assertTrue(result.single().overdue)
        assertTrue(organizerReminderMessage(circle.name,YearMonth.of(2027,1),result.single()).contains("Rs 1500"))
    }
    @Test fun fullyPaidMembersDoNotGetReminders() {
        val receipts = listOf(OrganizerCollectionEntity("p","owner","c","m","2027-01",250000,"2027-01-09","Cash",0))
        assertTrue(organizerReminderCandidates(circle,listOf(member),receipts,YearMonth.of(2027,1),LocalDate.of(2027,1,12)).isEmpty())
    }
    @Test(expected=IllegalArgumentException::class)
    fun overpaymentRejected() {
        val receipts = listOf(OrganizerCollectionEntity("p","owner","c","m","2027-01",250001,"2027-01-09","Cash",0))
        organizerReminderCandidates(circle,listOf(member),receipts,YearMonth.of(2027,1),LocalDate.of(2027,1,12))
    }
}
