package com.sadique.dailyledger.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DriveApiTest {
    @Test
    fun authorizationUsesTheSuppliedBearerToken() {
        assertEquals("Bearer drive-token", driveAuthorizationHeader("drive-token"))
    }

    @Test
    fun authorizationRejectsAnEmptyToken() {
        assertThrows(IllegalArgumentException::class.java) {
            driveAuthorizationHeader(" ")
        }
    }

    @Test
    fun backupFileIsScopedToTheDailyLedgerAccountWithoutExposingItsUid() {
        val alice = driveBackupFilename("firebase-user-alice")
        assertEquals(alice, driveBackupFilename("firebase-user-alice"))
        assertNotEquals(alice, driveBackupFilename("firebase-user-bob"))
        org.junit.Assert.assertFalse(alice.contains("firebase-user-alice"))
    }

    @Test
    fun automaticBackupOnlyProceedsWhenDriveVersionMatches() {
        org.junit.Assert.assertFalse(driveBackupHasConflict("", null))
        org.junit.Assert.assertTrue(driveBackupHasConflict("", "1"))
        org.junit.Assert.assertFalse(driveBackupHasConflict("1", "1"))
        org.junit.Assert.assertTrue(driveBackupHasConflict("1", "2"))
        org.junit.Assert.assertTrue(driveBackupHasConflict("1", null))
        org.junit.Assert.assertTrue(driveBackupHasConflict("1", "1", hasLocalRecords = false))
        org.junit.Assert.assertFalse(driveBackupHasConflict("", null, hasLocalRecords = false))
    }
}
