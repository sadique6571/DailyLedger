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

internal fun backupAttemptDue(lastSync: Long, now: Long) = lastSync <= 0L || now < lastSync || now - lastSync >= java.util.concurrent.TimeUnit.HOURS.toMillis(SyncScheduler.INTERVAL_HOURS)

class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val state = store.all.first()
        val user = state.user ?: return Result.success()
        if (!state.driveEnabled || !FirebaseRuntime.configured(applicationContext)) return Result.success()
        if (!backupOwnerMatches(inputData.getString(SyncScheduler.OWNER), user.id,
                FirebaseRuntime.auth(applicationContext).currentUser?.uid)) return Result.success()
        if (!backupAttemptDue(store.lastDriveCheck(user.id), System.currentTimeMillis())) return Result.success()
        val outcome = runBackupAttempt(
            upload = { DriveSync(applicationContext, user.id).upload() },
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

/** The upload belongs to the durable retry boundary. No attempt-count cutoff. */
internal suspend fun runBackupAttempt(upload: suspend () -> Unit): BackupAttempt {
    fun retry() = BackupAttempt(BackupAttemptResult.RETRY,
        "Backup is pending. Your records are saved on this phone; the cloud backup will retry automatically when available.", "pending")
    return try {
        upload()
        BackupAttempt(BackupAttemptResult.SUCCESS)
    } catch (e: CancellationException) { throw e }
    catch (e: DriveBackupConflictException) {
        BackupAttempt(BackupAttemptResult.FAILURE, e.message.orEmpty(), "conflict")
    } catch (e: DriveHttpException) {
        if (e.retryable) retry() else {
            BackupAttempt(BackupAttemptResult.FAILURE,
                "Cloud backup needs attention. Open Settings and try Back up now. Local records are still on this phone.", "error")
        }
    } catch (_: Exception) { retry() }
}
