package com.sadique.dailyledger.sync

import android.content.Context
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.SettingsStore
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DriveSync(private val context: Context, private val ownerId: String) {
    private val repo = LedgerRepository(AppDatabase.get(context), ownerId)
    private val settings = SettingsStore(context)

    private fun checkAccount() {
        check(FirebaseRuntime.auth(context).currentUser?.uid == ownerId) { "Please sign in again." }
    }

    suspend fun backupInfo(token: String): DriveBackupInfo? = withContext(Dispatchers.IO) {
        checkAccount()
        DriveApi(token, ownerId).findBackup()
    }

    suspend fun upload(token: String, allowReplace: Boolean = false) = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            val api = DriveApi(token, ownerId)
            val existing = api.findBackup()
            val expectedVersion = settings.driveVersion(ownerId)
            val hasLocalRecords = repo.hasRecords()
            if (!allowReplace && driveBackupHasConflict(expectedVersion, existing?.version, hasLocalRecords)) {
                throw DriveBackupConflictException()
            }
            val json = repo.exportJson()
            require(JSONObject(json).optString("ownerId") == ownerId) {
                "Local records do not belong to the signed-in account. Nothing was uploaded."
            }
            api.upload(json.toByteArray(Charsets.UTF_8), existing)
            val updated = api.findBackup()
                ?: error("Google Drive accepted the upload but the backup could not be verified.")
            settings.setDriveVersion(ownerId, updated.version)
            settings.setDriveEnabled(ownerId, true)
            settings.setLastSync(ownerId, System.currentTimeMillis())
            settings.setDriveStatus(ownerId, "Google Drive backup updated.")
        }
    }

    suspend fun restore(token: String) = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            val backup = DriveApi(token, ownerId).download()
                ?: error("No Daily Ledger backup was found in this Google Drive account.")
            val json = String(backup.data, Charsets.UTF_8)
            require(JSONObject(json).optString("ownerId") == ownerId) {
                "This Drive backup belongs to a different Daily Ledger account. Your local records were not changed."
            }
            checkAccount()
            repo.importJson(json)
            settings.setDriveVersion(ownerId, backup.info.version)
            settings.setDriveEnabled(ownerId, true)
            settings.setLastSync(ownerId, System.currentTimeMillis())
            settings.setDriveStatus(ownerId, "Google Drive backup restored to this phone.")
        }
    }
}
