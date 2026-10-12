package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = SettingsStore(applicationContext).all.first()
        val user = state.user ?: return Result.success()
        val email = user.googleEmail ?: user.email
        if (email.isBlank()) return Result.success()
        if (!state.driveEnabled || !FirebaseRuntime.configured(applicationContext)) return Result.success()
        if (FirebaseRuntime.auth(applicationContext).currentUser?.uid != user.id) return Result.success()
        val store = SettingsStore(applicationContext)
        val token = DriveAuth.silentToken(applicationContext, email) ?: run {
            store.setDriveStatus(user.id, "Google Drive access needs to be approved again in Settings.")
            return Result.failure()
        }
        return try {
            DriveSync(applicationContext, user.id).upload(token)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: DriveBackupConflictException) {
            store.setDriveStatus(user.id, e.message.orEmpty())
            Result.failure()
        }
        catch (e: Exception) {
            store.setDriveStatus(user.id, e.message ?: "Google Drive backup failed. Check the account and internet connection.")
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}
