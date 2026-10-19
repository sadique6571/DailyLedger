package com.sadique.dailyledger.sync

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupRetentionTest {
    @Test fun onlyTheLatestHeadIsKeptAndMissingHeadNeverDeletesAnything() {
        val revisions = (1..30).map { DriveRevision("$it", "2026-10-01T00:00:00Z", "hash", it != 2) }
        val deleted = revisionsToRemove(revisions, "30").map { it.id }.toSet()
        assertEquals(29, deleted.size)
        assertFalse("30" in deleted)
        assertTrue("2" in deleted)
        assertTrue(revisionsToRemove(revisions, "").isEmpty())
    }
    @Test fun threeHourGateIncludesBoundaryAndClockRecovery() {
        val now = 12_000_000L
        assertFalse(backupAttemptDue(now - 10_799_999L, now))
        assertTrue(backupAttemptDue(now - 10_800_000L, now))
        assertTrue(backupAttemptDue(0, now))
        assertTrue(backupAttemptDue(now + 1, now))
        assertEquals(3L, SyncScheduler.INTERVAL_HOURS)
    }
    @Test fun queuedAccountCannotTurnIntoAnotherAccountsBackup() {
        assertTrue(backupOwnerMatches("alice", "alice", "alice"))
        assertTrue(backupOwnerMatches(null, "alice", "alice"))
        assertFalse(backupOwnerMatches("alice", "bob", "bob"))
        assertFalse(backupOwnerMatches("alice", "alice", null))
        assertNotEquals(SyncScheduler.workName("alice"), SyncScheduler.workName("bob"))
    }
}
