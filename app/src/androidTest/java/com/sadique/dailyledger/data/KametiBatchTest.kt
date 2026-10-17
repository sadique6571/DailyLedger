package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KametiBatchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun request() = UUID.randomUUID().toString()
    private fun organizer(name: String, turn: Int, units: Int = 2) = OrganizerMemberDraft(name, "", "", units, turn)
    private fun personal(turn: Int, own: Boolean = false, units: Int = 2) =
        PersonalTurnDraft("Member", "", "", turn, "2026-${9 + turn}", own, "", units)
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Invalid batch was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun organizerRejectsWholeListAndReplaysWithoutOverwritingLaterEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.organizer.addCircle("Friends", 10000, 3, "2026-10")
            val id = repo.organizer.circles.first().single().id
            rejects { repo.organizer.addMembers(id, listOf(organizer("A", 1), organizer("B", 1)), request()) }
            assertTrue(repo.organizer.members.first().isEmpty())
            val key = request()
            val rows = listOf(organizer("Same person", 1), organizer("Same person", 2, 1))
            repo.organizer.addMembers(id, rows, key)
            val first = repo.organizer.members.first().first()
            repo.organizer.updateContact(first.id, "Corrected", "03001234567", "")
            repo.organizer.addMembers(id, rows, key)
            assertEquals(2, repo.organizer.members.first().size)
            assertEquals("Corrected", repo.organizer.members.first().single { it.id == first.id }.name)
            rejects { repo.organizer.addMembers(id, rows.reversed(), key) }
            rejects { repo.organizer.addMembers(id, rows.take(1), key) }
            rejects { LedgerRepository(db, "other").organizer.addMembers(id, rows, request()) }
            // Stable batch IDs and rows also survive the real v7 backup/restore path.
            val backup = repo.exportJson()
            repo.importJson(backup)
            repo.organizer.addMembers(repo.organizer.circles.first().single().id, rows, key)
            assertEquals(2, repo.organizer.members.first().size)
        } finally { db.close() }
    }

    @Test fun personalChecksQueuedOwnSharesDatesAndWholeRequestReplay() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.saveCommittee("My", 10000, 3, "2026-10", null, "", 2, sharesHalfUnits = 3)
            val id = repo.committees.first().single().id
            rejects { repo.saveCommitteeMembers(id, listOf(personal(1, true), personal(2, true)), request()) }
            rejects { repo.saveCommitteeMembers(id, listOf(personal(1), personal(2).copy(month = "2026-12")), request()) }
            assertTrue(repo.committeeMembers.first().isEmpty())
            val key = request()
            val rows = listOf(personal(1, true), personal(2, true, 1))
            repo.saveCommitteeMembers(id, rows, key)
            val first = repo.committeeMembers.first().first()
            repo.markCommitteeMemberReceived(first, "2026-10-17")
            repo.saveCommitteeMembers(id, rows, key)
            assertEquals(2, repo.committeeMembers.first().size)
            assertTrue(repo.committeeMembers.first().single { it.id == first.id }.received)
            rejects { repo.saveCommitteeMembers(id, rows + personal(3), key) }
            rejects { LedgerRepository(db, "other").saveCommitteeMembers(id, rows, request()) }
        } finally { db.close() }
    }

    @Test fun databaseFailureOnSecondInsertRollsBackBothKinds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.organizer.addCircle("Organizer", 10000, 3, "2026-10")
            repo.saveCommittee("Personal", 10000, 3, "2026-10", null, "")
            for (table in listOf("organizer_members", "committee_members")) {
                db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_second_${table} BEFORE INSERT ON $table WHEN NEW.name = 'Fail' BEGIN SELECT RAISE(ABORT, 'Simulated storage failure'); END")
            }
            var failed = false
            try { repo.organizer.addMembers(repo.organizer.circles.first().single().id,
                listOf(organizer("Good", 1), organizer("Fail", 2)), request()) }
            catch (_: android.database.sqlite.SQLiteException) { failed = true }
            assertTrue(failed)
            assertTrue(repo.organizer.members.first().isEmpty())
            failed = false
            try { repo.saveCommitteeMembers(repo.committees.first().single().id,
                listOf(personal(1), personal(2).copy(name = "Fail")), request()) }
            catch (_: android.database.sqlite.SQLiteException) { failed = true }
            assertTrue(failed)
            assertTrue(repo.committeeMembers.first().isEmpty())
        } finally { db.close() }
    }

    @Test fun concurrentSavesCannotOverfillEitherTurn() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.organizer.addCircle("Organizer", 10000, 3, "2026-10")
            repo.saveCommittee("Personal", 10000, 3, "2026-10", null, "")
            val c = repo.organizer.circles.first().single().id
            val p = repo.committees.first().single().id
            val outcomes = (1..2).map { async(Dispatchers.Default) {
                runCatching { repo.organizer.addMembers(c, listOf(organizer("Member", 1)), request()) }.isSuccess
            } }.awaitAll()
            assertEquals(1, outcomes.count { it })
            val personalOutcomes = (1..2).map { async(Dispatchers.Default) {
                runCatching { repo.saveCommitteeMembers(p, listOf(personal(1)), request()) }.isSuccess
            } }.awaitAll()
            assertEquals(1, personalOutcomes.count { it })
            assertEquals(1, repo.organizer.members.first().size)
            assertEquals(1, repo.committeeMembers.first().size)
        } finally { db.close() }
    }

    @Test fun inputBoundsAndOversizedPoolAreRejectedBeforeWriting() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            rejects { repo.organizer.addCircle("Huge", Long.MAX_VALUE - 1, 120, "2026-10") }
            repo.organizer.addCircle("Small", 10000, 2, "2026-10")
            val id = repo.organizer.circles.first().single().id
            rejects { repo.organizer.addMembers(id, listOf(organizer("ا".repeat(101), 1)), request()) }
            rejects { repo.organizer.addMembers(id, emptyList(), request()) }
            rejects { repo.organizer.addMembers(id, listOf(organizer("A", 0, 1), organizer("B", 0, 2), organizer("C", 0, 2)), request()) }
            assertTrue(repo.organizer.members.first().isEmpty())
        } finally { db.close() }
    }
}
