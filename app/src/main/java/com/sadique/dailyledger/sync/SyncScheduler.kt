package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork("account-backup", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudBackupWorker>(6, TimeUnit.HOURS).setConstraints(connected()).build())
        // Drive is now an explicit extra copy; Firebase handles automatic account backups.
        manager.cancelUniqueWork("drive-sync")
        manager.cancelUniqueWork("drive-sync-now")
    }
    fun syncNow(context: Context) {
        val manager = WorkManager.getInstance(context)
        // Keep later edits queued; replacing unique work can cancel an upload mid-backup.
        manager.enqueueUniqueWork("account-backup-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CloudBackupWorker>().setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun cancelImmediate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("account-backup-now")
        WorkManager.getInstance(context).cancelUniqueWork("drive-sync-now")
    }
}
