package com.sadique.dailyledger.data

import com.sadique.dailyledger.security.DatabaseEncryption
import com.sadique.dailyledger.security.EncryptedOpenHelperFactory
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun versionOneDatabaseUpgradesWithoutLosingData() = runBlocking {
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, date TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE loans (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, direction TEXT NOT NULL, person TEXT NOT NULL, principalMinor INTEGER NOT NULL, dueDate TEXT, note TEXT NOT NULL, createdAt INTEGER NOT NULL, closed INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE loan_payments (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, loanId TEXT NOT NULL, amountMinor INTEGER NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE committees (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, monthlyAmountMinor INTEGER NOT NULL, totalInstallments INTEGER NOT NULL, startMonth TEXT NOT NULL, payoutInstallment INTEGER, received INTEGER NOT NULL, active INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE committee_payments (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, committeeId TEXT NOT NULL, installmentNumber INTEGER NOT NULL, month TEXT NOT NULL, amountMinor INTEGER NOT NULL, paidAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE savings (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, kind TEXT NOT NULL, amountMinor INTEGER NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            listOf("transactions", "loans", "loan_payments", "committees", "committee_payments", "savings").forEach { table -> old.execSQL("CREATE INDEX index_${table}_ownerId ON $table(ownerId)") }
            old.execSQL("CREATE INDEX index_transactions_date ON transactions(date)")
            old.execSQL("CREATE INDEX index_savings_date ON savings(date)")
            old.execSQL("CREATE INDEX index_loan_payments_loanId ON loan_payments(loanId)")
            old.execSQL("CREATE INDEX index_committee_payments_committeeId ON committee_payments(committeeId)")
            old.execSQL("INSERT INTO committees VALUES ('owner:paid','owner','Office',500000,12,'2026-01',1,1,1,'Existing note',1)")
            old.execSQL("INSERT INTO committees VALUES ('owner:pending','owner','Home',250000,12,'2026-01',NULL,0,1,'',2)")
            old.execSQL("INSERT INTO transactions VALUES ('owner:salary','owner','INCOME',10000000,'Salary','','2026-10-01',1,1)")
            old.execSQL("INSERT INTO savings VALUES ('owner:saving','owner','DIRECT',200000,'2026-10-01','Saved',1)")
            old.execSQL("INSERT INTO loans VALUES ('owner:loan','owner','LENT','Friend',1000000,NULL,'',1,0)")
            old.execSQL("INSERT INTO loan_payments VALUES ('owner:lp','owner','owner:loan',100000,'2026-10-01','',1)")
            old.execSQL("INSERT INTO committee_payments VALUES ('owner:cp','owner','owner:paid',1,'2026-01',500000,1)")
            old.execSQL("INSERT INTO savings VALUES ('other:s','other','DIRECT',70000,'2026-10-01','',1)")
            old.version = 1
        }
        val password = "migration-test-key-32-bytes-long!!".toByteArray()
        DatabaseEncryption.migrate(file, password)
        assertFalse(DatabaseEncryption.isPlaintext(file))
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(EncryptedOpenHelperFactory(password)).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).build()
        try {
            val dao = db.ledgerDao()
            val committees = dao.committeesNow("owner") // Opening validates all migrated tables against Room's schema.
            assertEquals(2, committees.size)
            val repo = LedgerRepository(db, "owner")
            repo.saveCreditDrafts(listOf(CreditDraft("Shop", "Doodh", 15000, "Milk", "2026-10-01")), java.util.UUID.randomUUID().toString())
            assertEquals("2026-10-01", dao.creditPurchasesNow("owner").single().purchaseDate)
            assertTrue(dao.creditPurchasesNow("other").isEmpty())
            assertFalse(DatabaseEncryption.isPlaintext(file))
            assertTrue(committees.all { it.shares == 1 })
            assertEquals("Existing note", committees.first { it.id == "owner:paid" }.note)
            val oldReceipt = dao.committeeReceiptsNow("owner").single()
            assertEquals("owner:paid", oldReceipt.committeeId)
            assertEquals(6000000L, oldReceipt.amountMinor)
            assertNull(oldReceipt.date)
            assertEquals(10000000L, dao.transactionsNow("owner").single().amountMinor)
            assertEquals(200000L, dao.savingsNow("owner").single().amountMinor)
            assertEquals(1000000L, dao.loansNow("owner").single().principalMinor)
            assertEquals(100000L, dao.loanPaymentsNow("owner").single().amountMinor)
            assertEquals(500000L, dao.committeePaymentsNow("owner").single().amountMinor)
            assertEquals(70000L, dao.savingsNow("other").single().amountMinor)
            assertFalse(committeeBalance(committees.first { it.id == "owner:pending" }, listOf(oldReceipt)).complete)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun partialReceivingAndBackupsRoundTripWithAccountIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.ledgerDao()
            val repo = LedgerRepository(db, "owner")
            repo.saveCommittee("Office", 500000, 12, "2026-10", null, "Two at one place", 2)
            val c = repo.committees.first().single()
            repo.saveCommitteeMember(c.id, "Me", "03001234567", "", 2, "2026-11", true, "My turn")
            repo.saveCommitteeMember(c.id, "Ali", "03007654321", "", 3, "2026-12", false, "Friend")
            repo.markCommitteePaid(c, 1, "2026-10")
            assertEquals(1000000L, repo.committeePayments.first().single().amountMinor)
            expectRejected { repo.markCommitteePaid(c, 1, "2026-10") }
            repo.addCommitteeReceipt(c.id, 6000000, "2026-10-06", "First kameti")
            assertFalse(repo.committees.first().single().received)
            assertEquals(6000000L, committeeBalance(c, repo.committeeReceipts.first()).remaining)
            expectRejected { repo.addCommitteeReceipt(c.id, 6000001, "2026-10-07", "Too much") }
            expectRejected { repo.addCommitteeReceipt(c.id, 0, "2026-10-07", "Zero") }
            repo.addCommitteeReceipt(c.id, 6000000, "2026-11-06", "Second kameti")
            assertTrue(repo.committees.first().single().received)
            val json = repo.exportJson()
            assertEquals(8, JSONObject(json).getInt("version"))
            val restored = LedgerRepository(db, "restored")
            restored.importJson(json)
            val restoredCommittee = restored.committees.first().single()
            val restoredReceipts = restored.committeeReceipts.first()
            val restoredMembers = restored.committeeMembers.first()
            assertEquals(2, restoredCommittee.shares)
            assertEquals(2, restoredMembers.size)
            assertTrue(restoredMembers.any { it.isMe && it.turnMonth == "2026-11" })
            assertEquals(2, restoredReceipts.size)
            assertTrue(restoredReceipts.all { it.ownerId == "restored" && it.committeeId == restoredCommittee.id })
            assertEquals(12000000L, committeeBalance(restoredCommittee, restoredReceipts).received)
            assertEquals(2, repo.committeeReceipts.first().size)
            restored.deleteCommitteeReceipt(restoredReceipts.first())
            assertFalse(restored.committees.first().single().received)
            assertEquals(6000000L, committeeBalance(restoredCommittee, restored.committeeReceipts.first()).remaining)
            val malformed = JSONObject(json)
            malformed.getJSONArray("committeeReceipts").getJSONObject(0).put("amountMinor", 12000001)
            expectRejected { restored.importJson(malformed.toString()) }
            assertEquals(1, restored.committeeReceipts.first().size) // Failed import rolled back.
            assertEquals(6000000L, restored.committeeReceipts.first().single().amountMinor)
            val incomplete = JSONObject(json).apply { remove("committeeReceipts") }
            expectRejected { restored.importJson(incomplete.toString()) }
            assertEquals(1, restored.committeeReceipts.first().size)
            restored.deleteCommittee(restoredCommittee)
            assertTrue(restored.committeeReceipts.first().isEmpty())
            assertTrue(restored.committeePayments.first().isEmpty())
            assertEquals(2, dao.committeeReceiptsNow("owner").size)
        } finally { db.close() }
    }

    @Test fun everyIncomeAndExpenseTransactionIsRestoredFromBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val original = LedgerRepository(db, "owner")
            original.saveTransaction("INCOME", 25000000, "Salary", "Monthly salary", "2026-10-01")
            original.saveTransaction("EXPENSE", 50000, "Vegetables", "Sabzi", "2026-10-02")
            original.saveTransaction("EXPENSE", 20000, "Household", "Soap and shampoo", "2026-10-03")
            val cycleStart = System.currentTimeMillis() - 10_000
            db.ledgerDao().upsertLedgerCycle(LedgerCycleEntity("owner", cycleStart, 250000))
            db.ledgerDao().insertLedgerArchive(
                LedgerArchiveEntity("owner:archive:calendar:2026-09", "owner", "calendar:2026-09", "2026-09",
                    1, 2, 1000, 200, 300, 100, 50, 0, 550, 2),
            )

            val restored = LedgerRepository(db, "restored")
            val backupJson = original.exportJson()
            assertEquals(8, JSONObject(backupJson).getInt("version"))
            restored.importJson(backupJson)

            val transactions = restored.transactions.first().sortedBy { it.date }
            assertEquals(3, transactions.size)
            assertEquals(listOf("INCOME", "EXPENSE", "EXPENSE"), transactions.map { it.type })
            assertEquals(listOf(25000000L, 50000L, 20000L), transactions.map { it.amountMinor })
            assertEquals(listOf("Salary", "Vegetables", "Household"), transactions.map { it.category })
            assertEquals(listOf("restored", "restored", "restored"), transactions.map { it.ownerId })
            assertEquals(cycleStart, restored.ledgerCycle.first()?.startAt)
            assertEquals(250000L, restored.ledgerCycle.first()?.openingBalanceMinor)
            assertEquals(550L, restored.ledgerArchives.first().single().closingBalanceMinor)
        } finally { db.close() }
    }

    @Test fun duplicateTransactionIdsCannotSilentlyDropEntriesDuringRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val source = LedgerRepository(db, "source")
            source.saveTransaction("EXPENSE", 50000, "Vegetables", "Sabzi", "2026-10-01")
            source.saveTransaction("EXPENSE", 20000, "Vegetables", "Tomatoes", "2026-10-02")
            val malformed = JSONObject(source.exportJson())
            val rows = malformed.getJSONArray("transactions")
            rows.getJSONObject(1).put("id", rows.getJSONObject(0).getString("id"))

            val destination = LedgerRepository(db, "destination")
            destination.saveTransaction("INCOME", 100000, "Salary", "Existing local entry", "2026-10-03")
            expectRejected { destination.importJson(malformed.toString()) }
            val remaining = destination.transactions.first()
            assertEquals(1, remaining.size)
            assertEquals("Existing local entry", remaining.single().note)
        } finally { db.close() }
    }

    @Test fun startingSalaryCycleArchivesAndCarriesRemainingBalance() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            val repo = LedgerRepository(db, "owner")
            val dao = db.ledgerDao()
            dao.upsertLedgerCycle(LedgerCycleEntity("owner", now - 20_000, 5000))
            dao.insertTransactions(
                listOf(
                    TransactionEntity("salary", "owner", "INCOME", 10000, "Salary", "", "2026-10-01", now - 10_000, now - 10_000),
                    TransactionEntity("food", "owner", "EXPENSE", 3000, "Food", "", "2026-10-02", now - 5_000, now - 5_000),
                ),
            )
            repo.startNewLedgerCycle(now)
            val cycle = repo.ledgerCycle.first()!!
            assertEquals(now, cycle.startAt)
            assertEquals(12000L, cycle.openingBalanceMinor)
            assertEquals(12000L, repo.ledgerArchives.first().single().closingBalanceMinor)
            assertEquals(12000L, ledgerCycleSummary(repo.transactions.first(), emptyList(), emptyList(), cycle, now + 1).remaining)
        } finally { db.close() }
    }

    @Test fun initialCycleUsesLastRecordedSalaryAndDoesNotRollAtCalendarChange() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            val dao = db.ledgerDao()
            dao.insertTransactions(
                listOf(
                    TransactionEntity("old-salary", "owner", "INCOME", 10000, "Salary", "", "2026-08-01", now - 60L * 86_400_000, now - 60L * 86_400_000),
                    TransactionEntity("last-salary", "owner", "INCOME", 12000, "Salary", "", "2026-09-01", now - 20L * 86_400_000, now - 20L * 86_400_000),
                ),
            )
            val repo = LedgerRepository(db, "owner")
            repo.ensureLedgerCycle(now)
            val salaryBoundary = repo.ledgerCycle.first()!!.startAt
            assertEquals(now - 20L * 86_400_000, salaryBoundary)
            repo.ensureLedgerCycle(now + 60L * 86_400_000)
            assertEquals(salaryBoundary, repo.ledgerCycle.first()!!.startAt)
        } finally { db.close() }
    }

    @Test fun legacyBackupAndOfflineMigrationPreserveReceiving() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "offline-test")
            repo.saveCommittee("Old", 500000, 12, "2026-01", 1, "")
            val legacy = JSONObject(repo.exportJson()).put("version", 1).apply { remove("committeeReceipts") }
            legacy.getJSONArray("committees").getJSONObject(0).apply { put("received", true); remove("shares") }
            repo.importJson(legacy.toString())
            val receipt = repo.committeeReceipts.first().single()
            assertEquals(6000000L, receipt.amountMinor)
            assertNull(receipt.date)
            assertTrue(repo.committees.first().single().received)
            val signedIn = LedgerRepository(db, "signed-in")
            signedIn.migrateOwner("offline-test")
            assertTrue(repo.committeeReceipts.first().isEmpty())
            assertEquals(6000000L, signedIn.committeeReceipts.first().single().amountMinor)
            val backup = signedIn.exportJson()
            signedIn.importJson(backup)
            assertTrue(signedIn.committeeReceipts.first().single().id.startsWith("signed-in:"))
            assertTrue(committeeBalance(signedIn.committees.first().single(), signedIn.committeeReceipts.first()).complete)
        } finally { db.close() }
    }

    @Test fun loansMembersAndNewBackupFieldsStayConsistent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, "owner")
            repo.saveLoan("LENT", "Ali", 100000, "2026-11-01", "", "03001234567", "923001234567")
            val loan = repo.loans.first().single()
            repo.addLoanPayment(loan.id, 40000, "2026-10-07", "First", "Bank")
            expectRejected { repo.addLoanPayment(loan.id, 60001, "2026-10-08", "Too much") }
            assertEquals(1, repo.loanPayments.first().size)
            repo.addLoanPayment(loan.id, 60000, "2026-10-08", "Last", "JazzCash")
            assertTrue(repo.loans.first().single().closed)
            expectRejected { repo.addLoanPayment(loan.id, 1, "2026-10-08", "Already settled") }
            repo.saveCommittee("Office", 10000, 3, "2026-10", null, "", 1, "03007654321")
            val committee = repo.committees.first().single()
            repo.saveCommitteeMember(committee.id, "Me", "", "", 1, "2026-10", true, "")
            expectRejected { repo.saveCommitteeMember(committee.id, "Duplicate", "", "", 1, "2026-10", false, "") }
            expectRejected { repo.saveCommitteeMember(committee.id, "Too many", "", "", 2, "2026-11", true, "") }
            repo.markCommitteeMemberReceived(repo.committeeMembers.first().single(), "2026-10-07")
            repo.markCommitteePaid(committee, 1, "2026-10", "Easypaisa")
            val json = repo.exportJson()
            val restored = LedgerRepository(db, "restored")
            restored.importJson(json)
            assertEquals("03001234567", restored.loans.first().single().phone)
            assertEquals("923001234567", restored.loans.first().single().whatsapp)
            assertTrue(restored.loans.first().single().closed)
            assertEquals(setOf("Bank", "JazzCash"), restored.loanPayments.first().map { it.method }.toSet())
            assertEquals("Easypaisa", restored.committeePayments.first().single().method)
            assertEquals("2026-10-07", restored.committeeMembers.first().single().receivedDate)
            assertTrue(restored.committeeMembers.first().all { it.ownerId == "restored" })
            val damaged = JSONObject(json)
            val members = damaged.getJSONArray("committeeMembers")
            members.put(JSONObject(members.getJSONObject(0).toString()).put("turnNumber", 2).put("turnMonth", "2026-11"))
            expectRejected { restored.importJson(damaged.toString()) }
            assertEquals(1, restored.committeeMembers.first().size)
            restored.deleteCommittee(restored.committees.first().single())
            assertTrue(restored.committeeMembers.first().isEmpty())
            assertEquals(1, repo.committeeMembers.first().size)
            restored.deleteLoan(restored.loans.first().single())
            assertTrue(restored.loanPayments.first().isEmpty())
            assertEquals(2, repo.loanPayments.first().size)
        } finally { db.close() }
    }

    private suspend fun expectRejected(block: suspend () -> Unit) {
        try { block(); fail("Invalid operation was accepted") } catch (_: IllegalArgumentException) { }
    }
}
