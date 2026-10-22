package com.sadique.dailyledger.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.auth.UserProfile
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AutoBackupTest {
    private class Remote : DriveStorage {
        var info: DriveBackupInfo? = null
        var bytes = byteArrayOf()
        var historical = byteArrayOf()
        var writes = 0
        var loseAcknowledgement = false
        var failBeforeUpload = false
        var failCleanup = false
        fun set(json: String) {
            bytes = json.toByteArray()
            val version = ((info?.version?.toIntOrNull() ?: 0) + 1).toString()
            info = DriveBackupInfo("2026-10-17T00:00:00Z", version, "file", driveDigest(bytes), "r$version")
        }
        override fun findBackup() = info
        override fun upload(data: ByteArray, existing: DriveBackupInfo?): DriveBackupInfo {
            if (failBeforeUpload) throw IOException("offline")
            writes++; set(String(data))
            if (loseAcknowledgement) { loseAcknowledgement = false; throw IOException("response lost") }
            return info!!
        }
        override fun download(info: DriveBackupInfo) = DriveBackupFile(info, bytes)
        override fun revisions(info: DriveBackupInfo) = listOf(DriveRevision("old", "2026-10-16T00:00:00Z", driveDigest(historical), true))
        override fun downloadRevision(info: DriveBackupInfo, revision: DriveRevision) = DriveBackupFile(info, historical)
        override fun trimHistory(info: DriveBackupInfo) { if (failCleanup) throw IOException("cleanup offline") }
    }
    private class Fixture(val context: Context, val db: AppDatabase) {
        val owner = "backup-test-${UUID.randomUUID()}"
        val repo = LedgerRepository(db, owner)
        val settings = SettingsStore(context)
        val remote = Remote()
        var signedIn: String? = owner
        val sync = DriveSync(context, owner, repo, settings, { remote }, { signedIn })
        suspend fun add(amount: Long = 10000) { settings.markDrivePending(owner); repo.saveTransaction("EXPENSE", amount, "Milk", "", "2026-10-17") }
        suspend fun state() = settings.all.first()
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val fixture = Fixture(context, db)
        try {
            fixture.settings.saveUser(UserProfile(fixture.owner, "backup-test@example.com", "Test"))
            fixture.settings.setDriveEnabled(fixture.owner, true)
            fixture.repo.ensureLedgerCycle()
            fixture.block()
        } finally {
            fixture.settings.setDriveEnabled(fixture.owner, false)
            fixture.settings.clearUser()
            db.close()
        }
    }
    @Test fun lostAcknowledgementRecoversWithoutConflictOrDuplicateRevision() = runBlocking { fixture {
        add(); remote.loseAcknowledgement = true
        assertTrue(runCatching { sync.upload() }.exceptionOrNull() is IOException)
        assertEquals(0L, state().lastSync)
        assertEquals("", settings.driveVersion(owner))
        // Simulate a restarted worker: it reads persistent settings and the already-written remote copy.
        DriveSync(context, owner, repo, SettingsStore(context), { remote }, { signedIn }).upload()
        assertEquals(1, remote.writes)
        assertEquals(remote.info!!.version, settings.driveVersion(owner))
        assertEquals("verified", state().driveState)
    } }
    @Test fun offlineEditsRemainPendingAndNextAttemptIncludesEverySavedEntry() = runBlocking { fixture {
        add(); remote.failBeforeUpload = true
        runCatching { sync.upload() }
        assertEquals("pending", state().driveState)
        assertEquals(0L, state().lastSync)
        add(20000); remote.failBeforeUpload = false
        sync.upload()
        assertEquals(2, JSONObject(String(remote.bytes)).getJSONArray("transactions").length())
        assertEquals("verified", state().driveState)
        sync.upload()
        assertEquals(1, remote.writes) // unchanged startup/periodic jobs do not upload again
    } }
    @Test fun lostResponseFollowedByMoreOfflineEditsContinuesAutomatically() = runBlocking { fixture {
        add(); remote.loseAcknowledgement = true
        assertTrue(runCatching { sync.upload() }.isFailure)
        add(30000)
        sync.upload()
        assertEquals(2, remote.writes)
        assertEquals(2, JSONObject(String(remote.bytes)).getJSONArray("transactions").length())
        assertEquals("verified", state().driveState)
        assertEquals("", settings.driveUploadAttempt(owner).contentHash)
    } }
    @Test fun newerRemoteCopyAndEmptyResetCannotBeOverwrittenAutomatically() = runBlocking { fixture {
        add(); sync.upload()
        remote.set(String(remote.bytes).replace("10000", "99999"))
        assertTrue(runCatching { sync.upload() }.exceptionOrNull() is DriveBackupConflictException)
        assertEquals(1, remote.writes)
        repo.clearAllForOwner()
        assertTrue(runCatching { sync.upload() }.exceptionOrNull() is DriveBackupConflictException)
        assertEquals(1, remote.writes)
    } }
    @Test fun switchedAccountAndDisabledAutoBackupCannotUpload() = runBlocking { fixture {
        add(); signedIn = "someone-else"
        assertTrue(runCatching { sync.upload() }.isFailure)
        assertEquals(0, remote.writes)
        signedIn = owner; settings.setDriveEnabled(owner, false)
        sync.upload()
        assertEquals(0, remote.writes)
    } }
    @Test fun restoreRejectsOtherOwnerAndInvalidDataWithoutDeletingLocalRecords() = runBlocking { fixture {
        add(); val original = repo.exportJson()
        remote.set(original.replace(owner, "another-owner"))
        assertTrue(runCatching { sync.restore() }.isFailure)
        assertEquals(1, repo.transactions.first().size)
        remote.set(JSONObject(original).apply { remove("savings") }.toString())
        assertTrue(runCatching { sync.restore() }.isFailure)
        assertEquals(1, repo.transactions.first().size)
        assertFalse(state().driveEnabled)
    } }
    @Test fun historicalRestorePausesAndCannotOverwriteLatestByOnlyEnablingTheSwitch() = runBlocking { fixture {
        add(); sync.upload(); remote.historical = remote.bytes.copyOf()
        add(20000); sync.upload()
        val latest = remote.bytes.copyOf()
        sync.restore(remote.revisions(remote.info!!).single())
        assertEquals(1, repo.transactions.first().size)
        assertArrayEquals(latest, remote.bytes)
        assertFalse(state().driveEnabled)
        assertEquals("paused", state().driveState)
        settings.setDriveEnabled(owner, true)
        assertTrue(runCatching { sync.upload() }.exceptionOrNull() is DriveBackupConflictException)
        assertArrayEquals(latest, remote.bytes)
        sync.upload(allowReplace = true)
        assertEquals(1, JSONObject(String(remote.bytes)).getJSONArray("transactions").length())
    } }
    @Test fun latestRestoreRecordsVerifiedCheckpointAndCleanupFailureKeepsSuccess() = runBlocking { fixture {
        add(); remote.failCleanup = true; sync.upload()
        assertEquals("verified", state().driveState)
        repo.clearAllForOwner()
        sync.restore()
        assertEquals(1, repo.transactions.first().size)
        assertTrue(state().driveEnabled)
        sync.upload()
        assertEquals(1, remote.writes)
    } }
    @Test fun learningChangesUploadAndOldBackupsDoNotFalselyVerifyMissingLearning() = runBlocking { fixture {
        add()
        repo.updateLearning { it.remember("EXPENSE", "dodh", "Milk") }
        sync.upload()
        assertEquals(1, JSONObject(String(remote.bytes)).getJSONObject("aiLearning").getJSONArray("rules").length())
        val old = JSONObject(String(remote.bytes)).put("version", 7).apply { remove("aiLearning") }
        remote.set(old.toString())
        sync.restore()
        assertEquals(1, repo.learningSnapshot().rules.size)
        assertEquals("pending", state().driveState)
        sync.upload()
        assertEquals("verified", state().driveState)
        assertEquals(11, JSONObject(String(remote.bytes)).getInt("version"))
        assertEquals(1, JSONObject(String(remote.bytes)).getJSONObject("aiLearning").getJSONArray("rules").length())
    } }
    @Test fun tokenFailuresRetryBeyondOldCutoffAndPermissionDenialNeedsAction() = runBlocking {
        repeat(8) {
            val failure = runBackupAttempt(token = { throw IOException("offline token service") }, upload = { fail("No token") })
            assertEquals(BackupAttemptResult.RETRY, failure.result)
        }
        assertEquals("auth", runBackupAttempt(token = { null }, upload = { fail("Permission denied") }).state)
        assertEquals(BackupAttemptResult.SUCCESS, runBackupAttempt(token = { "fresh" }, upload = { assertEquals("fresh", it) }).result)
        assertEquals(BackupAttemptResult.RETRY, runBackupAttempt(token = { "t" }, upload = { throw DriveHttpException(403, "rateLimitExceeded") }).result)
        assertEquals(BackupAttemptResult.FAILURE, runBackupAttempt(token = { "t" }, upload = { throw DriveHttpException(403, "storageQuotaExceeded") }).result)
        assertEquals("conflict", runBackupAttempt(token = { "t" }, upload = { throw DriveBackupConflictException() }).state)
        assertTrue(runCatching { runBackupAttempt(token = { throw CancellationException() }, upload = {}) }.exceptionOrNull() is CancellationException)
    }
}
