package com.sadique.dailyledger.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("account-backup")
        manager.cancelUniqueWork("account-backup-now")
        manager.enqueueUniquePeriodicWork("drive-sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveSyncWorker>(6, TimeUnit.HOURS).setConstraints(connected()).build())
    }
    fun syncNow(context: Context) {
        val manager = WorkManager.getInstance(context)
        // Keep later edits queued; replacing unique work can cancel an upload mid-backup.
        manager.enqueueUniqueWork("drive-sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DriveSyncWorker>().setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun cancelImmediate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("drive-sync-now")
    }
}
