package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sadique.dailyledger.security.DatabaseEncryption
import com.sadique.dailyledger.security.EncryptedOpenHelperFactory

@Database(
    entities = [
        TransactionEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        CommitteeEntity::class,
        CommitteePaymentEntity::class,
        CommitteeReceiptEntity::class,
        CommitteeMemberEntity::class,
        SavingEntity::class,
        CreditPurchaseEntity::class,
        CreditPaymentEntity::class,
        LedgerCycleEntity::class,
        LedgerArchiveEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE committees ADD COLUMN shares INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS committee_receipts (id TEXT NOT NULL, ownerId TEXT NOT NULL, committeeId TEXT NOT NULL, amountMinor INTEGER NOT NULL, date TEXT, note TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_committee_receipts_ownerId ON committee_receipts(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_committee_receipts_committeeId ON committee_receipts(committeeId)")
                db.execSQL("INSERT INTO committee_receipts (id, ownerId, committeeId, amountMinor, date, note, createdAt) SELECT id || '-payout', ownerId, id, monthlyAmountMinor * totalInstallments, NULL, 'Earlier received status', createdAt FROM committees WHERE received = 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loans ADD COLUMN whatsapp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN method TEXT NOT NULL DEFAULT 'Cash'")
                db.execSQL("ALTER TABLE committees ADD COLUMN organizerPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE committees ADD COLUMN memberSchedule TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE committee_payments ADD COLUMN method TEXT NOT NULL DEFAULT 'Cash'")
                db.execSQL("ALTER TABLE committee_receipts ADD COLUMN method TEXT NOT NULL DEFAULT 'Cash'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS committee_members (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        committeeId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        whatsapp TEXT NOT NULL DEFAULT '',
                        turnNumber INTEGER NOT NULL,
                        turnMonth TEXT NOT NULL,
                        isMe INTEGER NOT NULL DEFAULT 0,
                        received INTEGER NOT NULL DEFAULT 0,
                        receivedDate TEXT,
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_committee_members_ownerId ON committee_members(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_committee_members_committeeId ON committee_members(committeeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_committee_members_turnMonth ON committee_members(turnMonth)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_purchases (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        creditor TEXT NOT NULL,
                        item TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        purchaseDate TEXT NOT NULL,
                        dueDate TEXT,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        closed INTEGER NOT NULL DEFAULT 0,
                        phone TEXT NOT NULL DEFAULT '',
                        whatsapp TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_purchases_ownerId ON credit_purchases(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_purchases_purchaseDate ON credit_purchases(purchaseDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_purchases_dueDate ON credit_purchases(dueDate)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_payments (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        creditId TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        method TEXT NOT NULL DEFAULT 'Cash',
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_payments_ownerId ON credit_payments(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_payments_creditId ON credit_payments(creditId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_payments_date ON credit_payments(date)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ledger_cycles (ownerId TEXT NOT NULL, startAt INTEGER NOT NULL, openingBalanceMinor INTEGER NOT NULL, PRIMARY KEY(ownerId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS ledger_archives (id TEXT NOT NULL, ownerId TEXT NOT NULL, archiveKey TEXT NOT NULL, label TEXT NOT NULL, startAt INTEGER NOT NULL, endAt INTEGER NOT NULL, salaryMinor INTEGER NOT NULL, otherIncomeMinor INTEGER NOT NULL, expensesMinor INTEGER NOT NULL, committeeMinor INTEGER NOT NULL, savingsMinor INTEGER NOT NULL, openingBalanceMinor INTEGER NOT NULL, closingBalanceMinor INTEGER NOT NULL, archivedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_archives_ownerId_archiveKey ON ledger_archives(ownerId, archiveKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_archives_ownerId ON ledger_archives(ownerId)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val file = context.getDatabasePath("daily-ledger.db")
                val password = DatabaseEncryption.password(context.applicationContext, file)
                DatabaseEncryption.migrate(file, password)
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "daily-ledger.db")
                    .openHelperFactory(EncryptedOpenHelperFactory(password))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
