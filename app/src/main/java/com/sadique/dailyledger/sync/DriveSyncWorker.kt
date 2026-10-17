package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

internal fun backupOwnerMatches(requested: String?, settingsOwner: String, signedInOwner: String?) =
    settingsOwner == signedInOwner && (requested == null || requested == settingsOwner)

class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val state = store.all.first()
        val user = state.user ?: return Result.success()
        if (!state.driveEnabled || !FirebaseRuntime.configured(applicationContext)) return Result.success()
        if (!backupOwnerMatches(inputData.getString(SyncScheduler.OWNER), user.id,
                FirebaseRuntime.auth(applicationContext).currentUser?.uid)) return Result.success()
        val email = user.googleEmail ?: user.email
        if (email.isBlank()) {
            store.setDriveStatus(user.id, "Connect your Google account in Settings to enable backup.", "auth")
            return Result.failure()
        }
        val outcome = runBackupAttempt(
            token = { DriveAuth.silentToken(applicationContext, email) },
            upload = { DriveSync(applicationContext, user.id).upload(it) },
        )
        if (outcome.message.isNotEmpty()) store.setDriveStatus(user.id, outcome.message, outcome.state)
        return when (outcome.result) {
            BackupAttemptResult.SUCCESS -> Result.success()
            BackupAttemptResult.RETRY -> Result.retry()
            BackupAttemptResult.FAILURE -> Result.failure()
        }
    }
}

internal enum class BackupAttemptResult { SUCCESS, RETRY, FAILURE }
internal data class BackupAttempt(val result: BackupAttemptResult, val message: String = "", val state: String = "")

/** Both Google authorization and upload belong to the durable retry boundary. No attempt-count cutoff. */
internal suspend fun runBackupAttempt(token: suspend () -> String?, upload: suspend (String) -> Unit): BackupAttempt {
    fun retry() = BackupAttempt(BackupAttemptResult.RETRY,
        "Backup is pending. Your records are saved on this phone; Google Drive will retry automatically when available.", "pending")
    return try {
        val access = token() ?: return BackupAttempt(BackupAttemptResult.FAILURE,
            "Google Drive access needs to be approved again in Settings.", "auth")
        upload(access)
        BackupAttempt(BackupAttemptResult.SUCCESS)
    } catch (e: CancellationException) { throw e }
    catch (e: DriveBackupConflictException) {
        BackupAttempt(BackupAttemptResult.FAILURE, e.message.orEmpty(), "conflict")
    } catch (e: DriveHttpException) {
        if (e.retryable) retry() else {
            val message = when {
                e.reason == "storageQuotaExceeded" -> "Google Drive storage is full. Free up space, then tap Back up now. Local records are still on this phone."
                e.code == 401 || e.code == 403 -> "Google Drive access needs attention. Open Settings and tap Back up now to reconnect."
                else -> "Google Drive backup needs attention. Open Settings and try Back up now. Local records are still on this phone."
            }
            BackupAttempt(BackupAttemptResult.FAILURE, message, "error")
        }
    } catch (_: Exception) { retry() }
}
