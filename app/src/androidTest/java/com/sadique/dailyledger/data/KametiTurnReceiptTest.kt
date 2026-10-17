package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KametiTurnReceiptTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun separateHalfAndFullTurnReceiptsStayLinkedAndRestoreSafely() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.saveCommittee("Office", 500000, 20, "2026-10", null, "", shares = 2, sharesHalfUnits = 3)
            val circle = repo.committees.first().single()
            repo.saveCommitteeMember(circle.id, "Me", "", "", 3, "2026-12", true, "", 2)
            repo.saveCommitteeMember(circle.id, "Me", "", "", 8, "2027-05", true, "", 1)
            val myTurns = repo.committeeMembers.first()
            val halfTurn = myTurns.single { it.shareHalfUnits == 1 }
            val fullTurn = myTurns.single { it.shareHalfUnits == 2 }
            assertEquals(750000L, circle.monthlyContribution())
            assertEquals(15000000L, circle.expectedPayout())
            repo.addPersonalTurnReceipt(halfTurn.id, 5000000L, "2027-05-15", "Half turn", "Bank")
            val halfReceipt = repo.committeeReceipts.first().single()
            assertEquals(halfTurn.id, halfReceipt.memberId)
            assertTrue(repo.committeeMembers.first().single { it.id == halfTurn.id }.received)
            assertFalse(repo.committeeMembers.first().single { it.id == fullTurn.id }.received)
            assertFalse(repo.committees.first().single().received)
            try {
                repo.addPersonalTurnReceipt(halfTurn.id, 100L, "2027-05-16", "Overpaid", "Cash")
                fail("An overpaid half turn should have been rejected")
            } catch (_: IllegalArgumentException) { }
            try {
                repo.deleteCommitteeMember(halfTurn)
                fail("A received turn with financial history must not be deletable")
            } catch (_: IllegalArgumentException) { }
            val backup = repo.exportJson()
            val restored = LedgerRepository(db, "restored")
            restored.importJson(backup)
            assertEquals(2, restored.committeeMembers.first().size)
            val restoredReceipt = restored.committeeReceipts.first().single()
            assertEquals(restored.committeeMembers.first().single { it.shareHalfUnits == 1 }.id, restoredReceipt.memberId)
            repo.deleteCommitteeReceipt(halfReceipt)
            assertFalse(repo.committeeMembers.first().single { it.id == halfTurn.id }.received)
            assertEquals(0L, repo.committeeReceipts.first().sumOf { it.amountMinor })
        } finally {
            db.close()
        }
    }
}
