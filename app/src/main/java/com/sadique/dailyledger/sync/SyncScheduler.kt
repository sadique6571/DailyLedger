package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.*
import com.sadique.dailyledger.auth.FirebaseRuntime
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val OWNER = "backup-owner"
    internal fun workName(owner: String) = "drive-sync-now:${driveBackupFilename(owner)}"
    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun currentOwner(context: Context): String? = if (FirebaseRuntime.configured(context)) FirebaseRuntime.auth(context).currentUser?.uid else null
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("account-backup")
        manager.cancelUniqueWork("account-backup-now")
        manager.cancelUniqueWork("drive-sync-now") // Replace old unbound queues with the current account's queue.
        manager.enqueueUniquePeriodicWork("drive-sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveSyncWorker>(1, TimeUnit.HOURS).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun syncNow(context: Context, ownerId: String? = currentOwner(context)) {
        val owner = ownerId?.takeIf { it.isNotBlank() } ?: return
        // Appending avoids cancelling an in-flight upload; unchanged snapshots are verified without re-upload.
        WorkManager.getInstance(context).enqueueUniqueWork(workName(owner), ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInputData(workDataOf(OWNER to owner)).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun cancelImmediate(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("drive-sync-now")
        currentOwner(context)?.let { manager.cancelUniqueWork(workName(it)) }
    }
}
