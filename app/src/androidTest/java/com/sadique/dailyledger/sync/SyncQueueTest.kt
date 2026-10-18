package com.sadique.dailyledger.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncQueueTest {
    @Test fun repeatedEditsCoalesceIntoOneDelayedAccountBoundBackup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val owner = "queue-${UUID.randomUUID()}"
        val manager = WorkManager.getInstance(context)
        val before = System.currentTimeMillis()
        try {
            repeat(50) { SyncScheduler.syncNow(context, owner) }
            val rows = manager.getWorkInfosForUniqueWork(SyncScheduler.workName(owner)).get(20, TimeUnit.SECONDS)
            assertEquals(1, rows.size)
            assertTrue(rows.single().nextScheduleTimeMillis >= before + TimeUnit.HOURS.toMillis(3) - 1000)
            assertTrue(manager.getWorkInfosForUniqueWork(SyncScheduler.workName("other-$owner")).get().isEmpty())
        } finally { manager.cancelUniqueWork(SyncScheduler.workName(owner)).result.get() }
    }
}
