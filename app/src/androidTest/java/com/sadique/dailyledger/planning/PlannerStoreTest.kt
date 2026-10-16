package com.sadique.dailyledger.planning

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.ai.CategoryLearningStore
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.security.SecureSecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlannerStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedPlansReopenAndStayIsolatedByAccount() {
        val owner = "planner-test-${UUID.randomUUID()}"
        val store = PlannerStore(context, owner)
        val bill = RecurringBill(UUID.randomUUID().toString(), "Private planned rent", "EXPENSE", "Rent", 220000, 31, "2026-10")
        store.setBudget("Milk", 100000); store.saveBill(bill)
        val reopened = PlannerStore(context, owner)
        assertEquals(store.data.value, reopened.data.value)
        assertTrue(PlannerStore(context, "other-$owner").data.value.bills.isEmpty())
        assertFalse(context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE).all.values.joinToString().contains("Private planned rent"))
        reopened.markHandled(bill.id, LocalDate.parse("2026-10-31"))
        assertEquals(LocalDate.parse("2026-11-30"), PlannerStore(context, owner).data.value.bills.single().nextDue())
        reopened.markHandled(bill.id, LocalDate.parse("2026-10-31"))
        assertEquals(LocalDate.parse("2026-11-30"), reopened.data.value.bills.single().nextDue())
        SecureSecretStore(context, "planner-v1:$owner").clearBytes()
    }
    @Test fun damagedPlansAreKeptAndCannotBeSilentlyOverwritten() {
        val owner = "planner-damaged-${UUID.randomUUID()}"
        val secret = SecureSecretStore(context, "planner-v1:$owner")
        secret.saveBytes("invalid-format".toByteArray())
        val store = PlannerStore(context, owner)
        assertNotNull(store.error)
        assertTrue(runCatching { store.setBudget("Milk", 10000) }.isFailure)
        assertEquals("invalid-format", String(requireNotNull(secret.loadBytes())))
        secret.clearBytes()
    }
    @Test fun recurringReviewBatchIsIdempotentAndPlanningAloneNeverWritesLedger() = runBlocking {
        val owner = "bill-${UUID.randomUUID()}"
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = LedgerRepository(db, owner)
            val store = PlannerStore(context, owner)
            val bill = RecurringBill(UUID.randomUUID().toString(), "Rent", "EXPENSE", "Rent", 220000, 1, "2026-10")
            store.saveBill(bill)
            assertTrue(db.ledgerDao().transactionsNow(owner).isEmpty())
            val draft = TransactionDraft("EXPENSE", bill.amountMinor, bill.category, bill.title, bill.nextDue().toString())
            repo.saveDraftBatch(listOf(draft), bill.batchId()); repo.saveDraftBatch(listOf(draft), bill.batchId())
            store.markHandled(bill.id, bill.nextDue())
            assertEquals(1, db.ledgerDao().transactionsNow(owner).size)
            assertEquals("2026-10-01", db.ledgerDao().transactionsNow(owner).single().date)
            assertTrue(db.ledgerDao().transactionsNow("other").isEmpty())
            SecureSecretStore(context, "planner-v1:$owner").clearBytes()
        } finally { db.close() }
    }
    @Test fun forgettingPersistsAndExplicitRetrainingWorks() {
        val owner = "learning-forget-${UUID.randomUUID()}"
        val store = CategoryLearningStore(context, owner)
        store.remember("EXPENSE", "chai", "Tea & coffee"); store.forget("EXPENSE", "chai")
        val reopened = CategoryLearningStore(context, owner)
        assertTrue(reopened.rules().isEmpty()); assertTrue("EXPENSE:chai" in reopened.suppressed())
        assertTrue(CategoryLearningStore(context, "other-$owner").suppressed().isEmpty())
        reopened.remember("EXPENSE", "chai", "Food")
        assertEquals("Food", reopened.apply(TransactionDraft("EXPENSE", 10000, "Other", "chai 100", "2026-10-16")).category)
        SecureSecretStore(context, "category-learning:$owner").clearBytes()
        SecureSecretStore(context, "category-learning-hidden:$owner").clearBytes()
    }
}
