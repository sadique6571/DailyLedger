package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sadique.dailyledger.ai.*
import com.sadique.dailyledger.sync.driveContentHash
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiLearningBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun fullRoundTripIncludesCorrectionsIgnoredRulesAndFeedbackWithOwnerIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val source = LedgerRepository(db, "source")
            source.updateLearning { it.remember("EXPENSE", "dodh 100", "Milk").remember("EXPENSE", "chai", "Tea & coffee") }
            source.updateLearning { it.forget("EXPENSE", "chai").dismiss(SpendingTip("Food", "Try reducing optional meals by 100")) }
            val snapshot = source.learningSnapshot()
            val before = source.exportJson()
            assertEquals(10, JSONObject(before).getInt("version"))
            val restored = LedgerRepository(db, "restored")
            restored.importJson(before)
            assertEquals(snapshot, restored.learningSnapshot())
            assertTrue(restored.hasRecords()) // Learning-only backups must also be protected from stale overwrites.
            assertFalse(LedgerRepository(db, "unrelated").learningSnapshot().hasContent)
            assertEquals("Milk", CategoryLearning.apply(TransactionDraft("EXPENSE", 5000, "Other", "milk", "2026-10-18"), restored.learningSnapshot().rules).category)
            source.updateLearning { it.remember("EXPENSE", "bus", "Transport") }
            assertNotEquals(driveContentHash(before), driveContentHash(source.exportJson()))
        } finally { db.close() }
    }

    @Test fun oldBackupsPreserveLearningAndInvalidNewLearningRollsBackTheEntireRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.saveTransaction("EXPENSE", 10000, "Milk", "Original", "2026-10-18")
            repo.updateLearning { it.remember("EXPENSE", "chai", "Tea & coffee") }
            val learning = repo.learningSnapshot()
            val original = repo.exportJson()
            val old = JSONObject(original).put("version", 7).apply { remove("aiLearning") }
            repo.importJson(old.toString())
            assertEquals(learning, repo.learningSnapshot())
            val bad = JSONObject(original)
            bad.getJSONArray("transactions").getJSONObject(0).put("note", "Must not replace original")
            bad.getJSONObject("aiLearning").getJSONArray("rules").getJSONObject(0).put("type", "DELETE")
            assertTrue(runCatching { repo.importJson(bad.toString()) }.isFailure)
            assertEquals(learning, repo.learningSnapshot())
            assertEquals("Original", db.ledgerDao().transactionsNow("owner").single().note)
            val invalidMoney = JSONObject(original)
            invalidMoney.put("aiLearning", LearningSnapshot().remember("EXPENSE", "petrol", "Fuel").json())
            invalidMoney.getJSONArray("transactions").getJSONObject(0).put("amountMinor", -1)
            assertTrue(runCatching { repo.importJson(invalidMoney.toString()) }.isFailure)
            assertEquals(learning, repo.learningSnapshot())
            assertEquals("Original", db.ledgerDao().transactionsNow("owner").single().note)
        } finally { db.close() }
    }

    @Test fun encryptedLegacyLearningMigratesOnceAndCannotResurrectAfterRestoreOrReset() = runBlocking {
        val owner = "legacy-${UUID.randomUUID()}"
        val legacy = CategoryLearningStore(context, owner)
        legacy.remember("EXPENSE", "dodh 100", "Milk")
        legacy.remember("EXPENSE", "chai", "Tea & coffee")
        legacy.forget("EXPENSE", "chai")
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, owner, legacy::snapshot)
            assertEquals(legacy.snapshot(), repo.learningSnapshot())
            val json = JSONObject(repo.exportJson()).put("aiLearning", LearningSnapshot().json())
            repo.importJson(json.toString())
            assertFalse(repo.learningSnapshot().hasContent)
            assertFalse(LedgerRepository(db, owner, legacy::snapshot).learningSnapshot().hasContent)
            repo.updateLearning { it.remember("EXPENSE", "bus", "Transport") }
            repo.clearAllForOwner()
            assertFalse(LedgerRepository(db, owner, legacy::snapshot).learningSnapshot().hasContent)
        } finally { db.close() }
    }

    @Test fun profileMigrationKeepsCurrentCorrectionsAndCannotResurrectForgottenLegacyRules() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val old = LedgerRepository(db, "offline-old")
            val current = LedgerRepository(db, "firebase-owner")
            old.updateLearning { it.remember("EXPENSE", "chai", "Food").remember("EXPENSE", "bus", "Transport") }
            current.updateLearning { it.remember("EXPENSE", "chai", "Tea & coffee") }
            current.migrateOwner("offline-old")
            assertEquals("Tea & coffee", current.learningSnapshot().rules.single { it.phrase == "chai" }.category)
            assertEquals(2, current.learningSnapshot().rules.size)
            current.updateLearning { it.forget("EXPENSE", "bus") }
            current.migrateOwner("offline-old")
            assertEquals(1, current.learningSnapshot().rules.size)
            assertFalse(old.learningSnapshot().hasContent)
        } finally { db.close() }
    }

    @Test fun versionSevenDatabaseMigratesWithoutResettingLedger() = runBlocking {
        val name = "ai-upgrade-${UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        LedgerRepository(first, "owner").saveTransaction("EXPENSE", 25000, "Milk", "Keep", "2026-10-18")
        first.close()
        val file = context.getDatabasePath(name)
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("DROP TABLE ai_learning")
            old.execSQL("DROP INDEX index_transactions_owner_date_created")
            old.version = 7
        }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build()
        try {
            assertEquals("Keep", upgraded.ledgerDao().transactionsNow("owner").single().note)
            assertFalse(LedgerRepository(upgraded, "owner").learningSnapshot().hasContent)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun ownerOrderedTransactionQueryUsesTheCompositeIndex() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            LedgerRepository(db, "owner").saveTransaction("EXPENSE", 100, "Milk", "", "2026-10-18")
            db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM transactions WHERE ownerId='owner' ORDER BY date DESC, createdAt DESC").use { cursor ->
                val plans = mutableListOf<String>()
                while (cursor.moveToNext()) plans += cursor.getString(3)
                assertTrue(plans.joinToString().contains("index_transactions_owner_date_created"))
                assertFalse(plans.joinToString().contains("TEMP B-TREE"))
            }
        } finally { db.close() }
    }
}
