package com.sadique.dailyledger.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sadique.dailyledger.auth.UserProfile
import com.sadique.dailyledger.security.SecureSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("daily_ledger_settings")

data class DriveCheckpoint(val version: String, val fileId: String, val contentHash: String, val checksum: String)
data class DriveUploadAttempt(val contentHash: String, val fileId: String)

data class AppSettings(
    val user: UserProfile?,
    val theme: String,
    val biometric: Boolean,
    val driveEnabled: Boolean,
    val lastSync: Long,
    val driveStatus: String = "Back up your records to the cloud.",
    val driveVersion: String = "",
    val driveState: String = "unknown",
    val transactionSounds: Boolean = false,
    val pinHash: String = "",
)

class SettingsStore(private val context: Context) {
    private object K {
        val userId = stringPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val name = stringPreferencesKey("name")
        val photo = stringPreferencesKey("photo")
        val googleEmail = stringPreferencesKey("google_email")
        val theme = stringPreferencesKey("theme")
        val biometric = booleanPreferencesKey("biometric")
        val oldDrive = booleanPreferencesKey("drive_enabled")
        val oldSync = longPreferencesKey("last_sync")
        val transactionSounds = booleanPreferencesKey("transaction_sounds")
    }
    private fun drive(owner: String) = booleanPreferencesKey("drive:$owner")
    private fun driveCheckTime(owner: String) = longPreferencesKey("drive_check_time:$owner")
    suspend fun lastDriveCheck(owner: String): Long = context.dataStore.data.first()[driveCheckTime(owner)] ?: 0L
    private fun driveTime(owner: String) = longPreferencesKey("drive_time:$owner")
    private fun driveStatus(owner: String) = stringPreferencesKey("drive_status:$owner")
    private fun driveVersionKey(owner: String) = stringPreferencesKey("drive_version:$owner")
    private fun driveStateKey(owner: String) = stringPreferencesKey("drive_state:$owner")
    private fun driveHashKey(owner: String) = stringPreferencesKey("drive_hash:$owner")
    private fun driveChecksumKey(owner: String) = stringPreferencesKey("drive_checksum:$owner")
    private fun driveFileKey(owner: String) = stringPreferencesKey("drive_file:$owner")
    private fun driveAttemptHashKey(owner: String) = stringPreferencesKey("drive_attempt_hash:$owner")
    private fun driveAttemptFileKey(owner: String) = stringPreferencesKey("drive_attempt_file:$owner")
    private fun skippedDriveBackupKey(owner: String) = stringPreferencesKey("drive_restore_skipped:$owner")
    private fun pinHashKey(owner: String) = stringPreferencesKey("pin_hash:$owner")
    private fun profile(p: Preferences) = p[K.userId]?.let {
        UserProfile(it, p[K.email].orEmpty(), p[K.name].orEmpty(), p[K.photo], p[K.googleEmail])
    }
    val all: Flow<AppSettings> = context.dataStore.data.map { p ->
        val id = p[K.userId].orEmpty()
        AppSettings(
            user = profile(p),
            theme = p[K.theme] ?: "AQUA",
            biometric = p[K.biometric] ?: false,
            driveEnabled = p[drive(id)] ?: false,
            lastSync = p[driveTime(id)] ?: 0L,
            driveStatus = p[driveStatus(id)] ?: "Back up your records to the cloud.",
            driveVersion = p[driveVersionKey(id)].orEmpty(),
            driveState = p[driveStateKey(id)] ?: "unknown",
            transactionSounds = p[K.transactionSounds] ?: false,
            pinHash = p[pinHashKey(id)].orEmpty(),
        )
    }
    val user = all.map { it.user }
    val theme = all.map { it.theme }
    val biometric = all.map { it.biometric }
    val driveEnabled = all.map { it.driveEnabled }
    val lastSync = all.map { it.lastSync }
    suspend fun saveUser(u: UserProfile) {
        context.dataStore.edit { p ->
            p[K.userId] = u.id; p[K.email] = u.email; p[K.name] = u.name
            if (u.photoUrl != null) p[K.photo] = u.photoUrl else p.remove(K.photo)
            if (u.googleEmail != null) p[K.googleEmail] = u.googleEmail else p.remove(K.googleEmail)
        }
    }
    suspend fun clearUser() {
        context.dataStore.edit {
            it.remove(K.userId); it.remove(K.email); it.remove(K.name); it.remove(K.photo); it.remove(K.googleEmail)
        }
    }
    suspend fun setTheme(value: String) { context.dataStore.edit { it[K.theme] = value } }
    suspend fun setBiometric(value: Boolean) { context.dataStore.edit { it[K.biometric] = value } }
    /** Stores the app-lock PIN hash for the owner; a blank hash removes the PIN. */
    suspend fun setPinHash(owner: String, hash: String) {
        context.dataStore.edit { if (hash.isBlank()) it.remove(pinHashKey(owner)) else it[pinHashKey(owner)] = hash }
    }
    suspend fun setDriveEnabled(owner: String, value: Boolean) { context.dataStore.edit { it[drive(owner)] = value } }
    suspend fun setLastSync(owner: String, value: Long) { context.dataStore.edit { it[driveTime(owner)] = value } }
    suspend fun setDriveStatus(owner: String, value: String, state: String = "error") {
        context.dataStore.edit { it[driveStatus(owner)] = value; it[driveStateKey(owner)] = state }
    }
    suspend fun markDrivePending(owner: String) = setDriveStatus(owner,
        "Changes are saved on this phone. Waiting for cloud backup.", "pending")
    suspend fun pauseDriveBackup(owner: String, status: String) {
        context.dataStore.edit {
            it[drive(owner)] = false
            it[driveVersionKey(owner)] = ""
            it.remove(driveAttemptHashKey(owner)); it.remove(driveAttemptFileKey(owner))
            it[driveStateKey(owner)] = "paused"
            it[driveStatus(owner)] = status
        }
    }
    suspend fun driveCheckpoint(owner: String): DriveCheckpoint = context.dataStore.data.first().let {
        DriveCheckpoint(it[driveVersionKey(owner)].orEmpty(), it[driveFileKey(owner)].orEmpty(),
            it[driveHashKey(owner)].orEmpty(), it[driveChecksumKey(owner)].orEmpty())
    }
    suspend fun driveUploadAttempt(owner: String): DriveUploadAttempt = context.dataStore.data.first().let {
        DriveUploadAttempt(it[driveAttemptHashKey(owner)].orEmpty(), it[driveAttemptFileKey(owner)].orEmpty())
    }
    suspend fun beginDriveUpload(owner: String, hash: String, fileId: String) {
        context.dataStore.edit { it[driveAttemptHashKey(owner)] = hash; it[driveAttemptFileKey(owner)] = fileId }
    }
    /** Version, content identity and successful status are committed together, never before verification. */
    suspend fun completeDriveBackup(owner: String, checkpoint: DriveCheckpoint, time: Long, status: String, enable: Boolean = false, state: String = "verified") {
        context.dataStore.edit {
            it[driveVersionKey(owner)] = checkpoint.version
            it[driveFileKey(owner)] = checkpoint.fileId
            it[driveHashKey(owner)] = checkpoint.contentHash
            it[driveChecksumKey(owner)] = checkpoint.checksum
            it[driveTime(owner)] = time
            it[driveCheckTime(owner)] = System.currentTimeMillis()
            it[driveStatus(owner)] = status
            it[driveStateKey(owner)] = state
            it.remove(driveAttemptHashKey(owner)); it.remove(driveAttemptFileKey(owner))
            if (enable) it[drive(owner)] = true
        }
    }
    suspend fun driveVersion(owner: String): String =
        context.dataStore.data.first()[driveVersionKey(owner)].orEmpty()
    suspend fun setDriveVersion(owner: String, value: String) {
        context.dataStore.edit { it[driveVersionKey(owner)] = value }
    }
    suspend fun setTransactionSounds(value: Boolean) { context.dataStore.edit { it[K.transactionSounds] = value } }
    suspend fun skippedDriveBackup(owner: String): String {
        return context.dataStore.data.first()[skippedDriveBackupKey(owner)].orEmpty()
    }
    suspend fun setSkippedDriveBackup(owner: String, value: String) {
        context.dataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(skippedDriveBackupKey(owner))
            else prefs[skippedDriveBackupKey(owner)] = value
        }
    }
    suspend fun migrateLegacyDriveSettings() {
        val p = context.dataStore.data.first()
        val owner = p[K.userId] ?: return
        SecureSecretStore(context, owner).clearBackupPassphrase()
        context.dataStore.edit {
            if (it[drive(owner)] == null) it[drive(owner)] = it[K.oldDrive] ?: false
            if (it[driveTime(owner)] == null) it[driveTime(owner)] = it[K.oldSync] ?: 0L
            it.remove(K.oldDrive); it.remove(K.oldSync)
        }
    }
    suspend fun migrateDriveAccount(old: String, new: String) {
        context.dataStore.edit {
            if (it[drive(new)] == null && it[drive(old)] != null) it[drive(new)] = it[drive(old)]!!
            if (it[driveTime(new)] == null && it[driveTime(old)] != null) it[driveTime(new)] = it[driveTime(old)]!!
            if (it[driveStatus(new)] == null && it[driveStatus(old)] != null) it[driveStatus(new)] = it[driveStatus(old)]!!
            if (it[driveVersionKey(new)] == null && it[driveVersionKey(old)] != null) {
                it[driveVersionKey(new)] = it[driveVersionKey(old)]!!
            }
            if (it[skippedDriveBackupKey(new)] == null && it[skippedDriveBackupKey(old)] != null) {
                it[skippedDriveBackupKey(new)] = it[skippedDriveBackupKey(old)]!!
            }
        }
    }
}
