package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizerKametiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Unsafe Kameti change was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun exactHalfSharesPairedDrawsAmountsAndPayoutsNeverChangeSalary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val owner = LedgerRepository(db, "owner")
            val another = LedgerRepository(db, "another")
            owner.organizer.addCircle("Twenty shares", 500000L, 20, "2026-10")
            val circle = owner.organizer.circles.first().single()
            owner.organizer.addMember(circle.id, "Ali", "03001111111", "03001111111", 1, 6)
            owner.organizer.addMember(circle.id, "Ahmed", "03002222222", "03002222222", 1, 6)
            val roster = owner.organizer.members.first()
            assertEquals(2, filledTurnUnits(roster, 6))
            assertTrue(availableDraws(roster, roster[0].copy(id = "new-member", turnNumber = 0), 20).none { it == 6 })
            rejects { owner.organizer.addMember(circle.id, "Extra", "", "", 1, 6) }
            assertTrue(another.organizer.circles.first().isEmpty())
            val ali = roster.first { it.name == "Ali" }
            val ahmed = roster.first { it.name == "Ahmed" }
            assertEquals(250000L, halfShareMoney(circle.fullMonthlyMinor, ali.halfUnits))
            owner.organizer.collect(ali.id, "2026-10", 250000L, "2026-10-06", "Cash")
            owner.organizer.collect(ahmed.id, "2026-10", 250000L, "2026-10-06", "Bank")
            rejects { owner.organizer.collect(ali.id, "2026-10", 1, "2026-10-07", "Cash") }
            owner.organizer.payOut(ali.id, 5000000L, "2026-10-06", "Cash")
            owner.organizer.payOut(ahmed.id, 5000000L, "2026-10-06", "Cash")
            rejects { owner.organizer.payOut(ali.id, 1, "2026-10-07", "Cash") }
            assertTrue(owner.transactions.first().isEmpty())
            assertTrue(owner.committeePayments.first().isEmpty())
            assertEquals(2, owner.organizer.payouts.first().size)
            rejects { owner.organizer.deleteMember(ali.id) }
            rejects { owner.organizer.deleteCircle(circle.id) }

            val json = owner.exportJson()
            assertEquals(7, JSONObject(json).getInt("version"))
            another.importJson(json)
            assertEquals(2, another.organizer.members.first().size)
            assertEquals(2, another.organizer.payouts.first().size)
            assertEquals(setOf("Ali", "Ahmed"), another.organizer.members.first().map { it.name }.toSet())
            val corrupt = JSONObject(json)
            corrupt.getJSONArray("organizerMembers").getJSONObject(0).put("halfUnits", 3)
            rejects { another.importJson(corrupt.toString()) }
            assertEquals(2, another.organizer.members.first().size)
        } finally { db.close() }
    }

    @Test fun unassignedRandomTurnAndFullShareCapacityRules() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.organizer.addCircle("Test", 500000, 4, "2026-10")
            val id = repo.organizer.circles.first().single().id
            repo.organizer.addMember(id, "Full", "", "", 2, 2)
            repo.organizer.addMember(id, "Half1", "", "", 1, 1)
            repo.organizer.addMember(id, "Half2", "", "", 1, 0)
            val unassigned = repo.organizer.members.first().single { it.name == "Half2" }
            val choices = availableDraws(repo.organizer.members.first(), unassigned, 4)
            assertFalse(2 in choices)
            assertTrue(1 in choices)
            repo.organizer.assignTurn(unassigned.id, 1)
            assertEquals(2, filledTurnUnits(repo.organizer.members.first(), 1))
            rejects { repo.organizer.assignTurn(unassigned.id, 3) }
            rejects { repo.organizer.addMember(id, "Extra", "", "", 1, 1) }
        } finally { db.close() }
    }

    @Test fun personalHalfAndOnePointFiveStayExactWithoutAlteringOldFullShares() = runBlocking {
        assertEquals(1, parseHalfShareUnits("0.5"))
        assertEquals(3, parseHalfShareUnits("1.5"))
        assertEquals(4, parseHalfShareUnits("2"))
        assertNull(parseHalfShareUnits("1.3"))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.saveCommittee("My", 500000L, 20, "2026-10", null, "", 2, sharesHalfUnits = 3)
            val circle = repo.committees.first().single()
            assertEquals(750000L, circle.monthlyContribution())
            repo.saveCommitteeMember(circle.id, "Me", "", "", 3, "2026-12", true, "", 2)
            repo.saveCommitteeMember(circle.id, "Me", "", "", 8, "2027-05", true, "", 1)
            assertEquals(3, repo.committeeMembers.first().sumOf { it.shareHalfUnits })
            rejects { repo.saveCommitteeMember(circle.id, "Me", "", "", 9, "2027-06", true, "", 1) }
            val json = repo.exportJson()
            repo.importJson(json)
            assertEquals(3, repo.committees.first().single().sharesHalfUnits)
            assertEquals(3, repo.committeeMembers.first().sumOf { it.shareHalfUnits })
        } finally { db.close() }
    }
}
