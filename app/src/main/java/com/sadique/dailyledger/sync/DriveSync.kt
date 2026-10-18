package com.sadique.dailyledger.sync

import android.content.Context
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.data.*
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class DriveSync internal constructor(
    private val context: Context, private val ownerId: String,
    private val repo: LedgerRepository,
    private val settings: SettingsStore,
    private val apiFactory: (String) -> DriveStorage,
    private val signedInOwner: () -> String?,
) {
    constructor(context: Context, ownerId: String) : this(context, ownerId,
        LedgerRepository(AppDatabase.get(context), ownerId) { com.sadique.dailyledger.ai.CategoryLearningStore(context, ownerId).snapshot() }, SettingsStore(context),
        { DriveApi(it, ownerId) }, { FirebaseRuntime.auth(context).currentUser?.uid })

    private fun checkAccount() {
        check(signedInOwner() == ownerId) { "Please sign in again." }
    }
    suspend fun backupInfo(token: String): DriveBackupInfo? = withContext(Dispatchers.IO) {
        checkAccount(); apiFactory(token).findBackup()
    }
    suspend fun history(token: String): List<DriveRevision> = withContext(Dispatchers.IO) {
        checkAccount()
        val api = apiFactory(token)
        val info = api.findBackup() ?: return@withContext emptyList()
        api.revisions(info).filter { it.checksum.isNotBlank() }
    }
    private suspend fun verified(info: DriveBackupInfo, hash: String, status: String, enable: Boolean) {
        checkAccount()
        val time = runCatching { java.time.Instant.parse(info.modifiedTime).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
        settings.completeDriveBackup(ownerId, DriveCheckpoint(info.version, info.fileId, hash, info.checksum), time, status, enable)
    }
    suspend fun upload(token: String, allowReplace: Boolean = false) = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            if (!allowReplace && settings.all.first().let { !it.driveEnabled || it.user?.id != ownerId }) return@withContext
            val api = apiFactory(token)
            val existing = api.findBackup()
            var known = settings.driveCheckpoint(ownerId)
            val attempt = settings.driveUploadAttempt(ownerId)
            val json = repo.exportJson()
            require(JSONObject(json).optString("ownerId") == ownerId) { "Local records do not belong to the signed-in account. Nothing was uploaded." }
            val hash = driveContentHash(json)
            if (existing != null && existing.checksum.isNotBlank()) {
                val unchanged = known.fileId == existing.fileId && known.version == existing.version &&
                    known.checksum == existing.checksum && known.contentHash == hash
                // Reconcile an upload whose response was lost, or a crash before the local checkpoint committed.
                val remoteHash = if (!unchanged && (known.version != existing.version || known.fileId != existing.fileId))
                    driveContentHash(String(api.download(existing).data, Charsets.UTF_8)) else null
                if (unchanged || remoteHash == hash) {
                    verified(existing, hash, "Google Drive backup verified. All ledger changes are backed up.", allowReplace)
                    cleanHistory(api, existing)
                    return@withContext
                }
                if (attempt.contentHash.isNotBlank() && remoteHash == attempt.contentHash &&
                    (attempt.fileId.isBlank() || attempt.fileId == existing.fileId)) {
                    // Edits may have arrived since the lost acknowledgement. Accept only our attempted
                    // content as the new baseline, keep the UI pending, then upload the latest local snapshot.
                    checkAccount()
                    known = DriveCheckpoint(existing.version, existing.fileId, attempt.contentHash, existing.checksum)
                    val time = runCatching { java.time.Instant.parse(existing.modifiedTime).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                    settings.completeDriveBackup(ownerId, known, time,
                        "Changes are saved on this phone. Waiting for Google Drive backup.", state = "pending")
                }
            }
            if (!allowReplace && (driveBackupHasConflict(known.version, existing?.version, repo.hasRecords()) ||
                    (known.fileId.isNotBlank() && existing?.fileId != known.fileId))) throw DriveBackupConflictException()
            checkAccount()
            settings.setDriveStatus(ownerId, "Backing up ledger changes to Google Drive...", "pending")
            settings.beginDriveUpload(ownerId, hash, existing?.fileId.orEmpty())
            val updated = api.upload(json.toByteArray(Charsets.UTF_8), existing)
            verified(updated, hash, "Google Drive backup verified. All ledger changes are backed up.", allowReplace)
            cleanHistory(api, updated)
        }
    }
    private suspend fun cleanHistory(api: DriveStorage, info: DriveBackupInfo) {
        try { api.trimHistory(info) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { settings.setDriveStatus(ownerId, "Backup verified. Older copies were kept because history cleanup could not finish; cleanup will retry on the next check.", "verified") }
    }
    suspend fun restore(token: String, revision: DriveRevision? = null) = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            val api = apiFactory(token)
            val info = api.findBackup() ?: error("No Daily Ledger backup was found in this Google Drive account.")
            val backup = if (revision == null) api.download(info) else api.downloadRevision(info, revision)
            val json = String(backup.data, Charsets.UTF_8)
            require(JSONObject(json).optString("ownerId") == ownerId) {
                "This Drive backup belongs to a different Daily Ledger account. Your local records were not changed."
            }
            checkAccount()
            // Pause first: process death after the DB commit must not upload a historical copy automatically.
            settings.pauseDriveBackup(ownerId, "Restore in progress. Automatic backup is paused.")
            repo.importJson(json) // Room transaction validates and rolls back an invalid restore.
            if (revision == null || revision.id == info.headRevision) {
                val remoteHash = driveContentHash(json)
                val localHash = driveContentHash(repo.exportJson())
                verified(info, remoteHash, "Google Drive backup restored to this phone.", true)
                if (localHash != remoteHash) {
                    settings.markDrivePending(ownerId)
                    runCatching { SyncScheduler.syncNow(context, ownerId) }
                }
            } else {
                settings.setDriveStatus(ownerId, "Older backup restored. Automatic backup is paused. Review your records, then use Back up now to make this the latest copy.", "paused")
            }
        }
    }
}
