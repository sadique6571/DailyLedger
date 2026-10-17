package com.sadique.dailyledger.sync

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupRetentionTest {
    @Test fun rapidEditsKeepTenRecentCopiesAndSevenDailyRecoveryPoints() {
        val days = (1..12).map { n -> DriveRevision("d$n", "${LocalDate.of(2026, 10, n)}T23:00:00Z", "hash", true) }
        val rapid = (0..20).map { n -> DriveRevision("rapid$n", "2026-10-13T12:${n.toString().padStart(2, '0')}:00Z", "hash", true) }
        val deleted = revisionsToRemove(days + rapid, "rapid20").map { it.id }.toSet()
        (11..20).forEach { assertFalse(deleted.contains("rapid$it")) }
        (7..12).forEach { assertFalse(deleted.contains("d$it")) }
        assertTrue(deleted.contains("d1"))
        assertTrue(deleted.contains("rapid0"))
    }
    @Test fun headAndUnmanagedUnpinnedCopiesAreNeverDeleted() {
        val revisions = (1..30).map { DriveRevision("$it", "2026-10-${it.toString().padStart(2, '0')}T00:00:00Z", "hash", it != 2) }
        val deleted = revisionsToRemove(revisions, "1").map { it.id }
        assertFalse("1" in deleted)
        assertFalse("2" in deleted)
    }
    @Test fun queuedAccountCannotTurnIntoAnotherAccountsBackup() {
        assertTrue(backupOwnerMatches("alice", "alice", "alice"))
        assertTrue(backupOwnerMatches(null, "alice", "alice"))
        assertFalse(backupOwnerMatches("alice", "bob", "bob"))
        assertFalse(backupOwnerMatches("alice", "alice", null))
        assertNotEquals(SyncScheduler.workName("alice"), SyncScheduler.workName("bob"))
    }
}
