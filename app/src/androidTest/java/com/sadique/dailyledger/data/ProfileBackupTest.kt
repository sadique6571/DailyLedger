package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sadique.dailyledger.planning.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProfileBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun plan() = ProfileBackupData(PlannerData(listOf(CategoryBudget("Pizza", 500000)), listOf(
        RecurringBill(UUID.randomUUID().toString(), "Rent", "EXPENSE", "Rent", 2000000, 31, "2026-09", "2026-09")
    )), reference = "2026-10-05", expected = "2026-11-14", lastRecorded = "2026-10-05")
    @Test fun plansCalendarAndHandledRemindersRoundTripWithLedgerAndOwnerIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val source = LedgerRepository(db, "source")
            val settings = plan()
            source.updateProfile { settings }
            source.saveTransaction("EXPENSE", 10000, "Milk", "Keep", "2026-10-18")
            val json = source.exportJson()
            assertEquals(11, JSONObject(json).getInt("version"))
            val restored = LedgerRepository(db, "restored")
            restored.importJson(json)
            assertEquals(settings, restored.profileSnapshot().copy(workspace=FinanceWorkspace()))
            assertEquals("2026-10-31", restored.profileSnapshot().planner.bills.single().nextDue().toString())
            assertEquals(1, db.ledgerDao().transactionsNow("restored").size)
            assertFalse(LedgerRepository(db, "other").profileSnapshot().hasContent)
            assertTrue(LedgerRepository(db, "plans-only").apply { updateProfile { settings } }.hasRecords())
        } finally { db.close() }
    }
    @Test fun invalidNewBackupRollsBackPlansAndLedgerAndOldBackupsPreserveCurrentPlans() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            val data = plan(); repo.updateProfile { data }
            repo.saveTransaction("EXPENSE", 10000, "Milk", "Original", "2026-10-18")
            val json = repo.exportJson()
            val old = JSONObject(json).put("version", 8).apply { remove("profileSettings") }
            repo.importJson(old.toString()); assertEquals(data, repo.profileSnapshot().copy(workspace=FinanceWorkspace()))
            val bad = JSONObject(json)
            bad.getJSONObject("profileSettings").getJSONObject("planner").getJSONArray("budgets").getJSONObject(0).put("limit", -1)
            assertTrue(runCatching { repo.importJson(bad.toString()) }.isFailure)
            assertEquals(data, repo.profileSnapshot().copy(workspace=FinanceWorkspace()))
            val invalidLedger = JSONObject(json).put("profileSettings", ProfileBackupData().json())
            invalidLedger.getJSONArray("transactions").getJSONObject(0).put("amountMinor", -1)
            assertTrue(runCatching { repo.importJson(invalidLedger.toString()) }.isFailure)
            assertEquals(data, repo.profileSnapshot().copy(workspace=FinanceWorkspace()))
            assertEquals("Original", db.ledgerDao().transactionsNow("owner").single().note)
            repo.clearAllForOwner(); assertFalse(repo.profileSnapshot().hasContent)
        } finally { db.close() }
    }
    @Test fun databaseEightUpgradesWithoutResetAndVerifiedProfileMigrationKeepsCurrentRules() = runBlocking {
        val name = "profile-upgrade-${UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        LedgerRepository(first, "old").saveTransaction("EXPENSE", 100, "Milk", "Keep", "2026-10-18"); first.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("DROP TABLE profile_backup"); it.version = 8
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11).build()
        try {
            val old = LedgerRepository(db, "old"); val current = LedgerRepository(db, "new")
            old.updateProfile { plan() }
            current.updateProfile { it.copy(planner = PlannerData(listOf(CategoryBudget("Pizza", 990000)))) }
            current.migrateOwner("old")
            assertEquals(990000L, current.profileSnapshot().planner.budgets.single().limitMinor)
            assertEquals(1, current.profileSnapshot().planner.bills.size)
            assertFalse(old.profileSnapshot().hasContent)
            current.updateProfile { it.copy(planner = PlannerData()) }; current.migrateOwner("old")
            assertTrue(current.profileSnapshot().planner.bills.isEmpty())
            assertEquals("Keep", db.ledgerDao().transactionsNow("new").single().note)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
