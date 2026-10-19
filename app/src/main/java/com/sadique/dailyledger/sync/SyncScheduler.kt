package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.*
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val OWNER = "backup-owner"
    const val INTERVAL_HOURS = 3L
    internal fun workName(owner: String) = "drive-sync-now:${driveBackupFilename(owner)}"
    internal fun periodicName(owner: String) = "drive-sync-periodic:${driveBackupFilename(owner)}"
    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun currentOwner(context: Context): String? = if (FirebaseRuntime.configured(context)) FirebaseRuntime.auth(context).currentUser?.uid else null
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        listOf("account-backup", "account-backup-now", "drive-sync-now", "drive-sync").forEach(manager::cancelUniqueWork)
        val owner = currentOwner(context) ?: return
        manager.enqueueUniquePeriodicWork(periodicName(owner), ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setInputData(workDataOf(OWNER to owner)).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    /** One delayed obligation per account. New edits never append an unbounded job chain. */
    fun syncNow(context: Context, ownerId: String? = currentOwner(context)) = enqueue(context, ownerId, INTERVAL_HOURS * 60 * 60)
    private fun enqueue(context: Context, ownerId: String?, delaySeconds: Long) {
        val owner = ownerId?.takeIf { it.isNotBlank() } ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(workName(owner), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DriveSyncWorker>().setInputData(workDataOf(OWNER to owner))
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    suspend fun catchUp(context: Context) {
        schedule(context)
        val state = SettingsStore(context).all.first()
        if (!state.driveEnabled || state.user?.id != currentOwner(context)) return
        val checked = SettingsStore(context).lastDriveCheck(state.user!!.id)
        if (backupAttemptDue(checked, System.currentTimeMillis())) {
            // A delayed edit job may exist already; periodic work is also durable. Use a separate KEEP
            // name for foreground catch-up without cancelling an in-flight upload.
            val owner = state.user!!.id
            WorkManager.getInstance(context).enqueueUniqueWork("drive-catch-up:${driveBackupFilename(owner)}", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DriveSyncWorker>().setInputData(workDataOf(OWNER to owner)).setConstraints(connected()).build())
        }
    }
    fun cancelImmediate(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("drive-sync-now")
        currentOwner(context)?.let { manager.cancelUniqueWork(workName(it)); manager.cancelUniqueWork(periodicName(it)); manager.cancelUniqueWork("drive-catch-up:${driveBackupFilename(it)}") }
    }
}
