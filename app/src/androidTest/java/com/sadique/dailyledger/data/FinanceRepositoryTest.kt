package com.sadique.dailyledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class FinanceRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun refundConservationCapsAndOwnerIsolation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o")
            val id=r.finance.saveEntry("EXPENSE",10000,"Milk","Milk","2026-10-01",quantity="2",unit="litre",rate=5000)
            r.finance.refund(id,2500,"2026-10-02","Returned","Bank")
            assertTrue(runCatching{r.finance.refund(id,8000,"2026-10-03","Too much","Bank")}.isFailure)
            val tx=db.ledgerDao().transactionsNow("o");assertEquals(2,tx.size);assertEquals(7500L,monthlySalarySummary(tx,"2026-10").expenses)
            assertEquals(0,db.ledgerDao().transactionsNow("other").size)
            assertTrue(runCatching{LedgerRepository(db,"other").finance.refund(id,100,"2026-10-03","Other owner","Cash")}.isFailure)
            assertTrue(runCatching{r.deleteTransaction(tx.first{it.id==id})}.isFailure)
            val w=FinanceWorkspace(items=listOf(FinanceItem(kind=FinanceKind.TRANSFER,title="Deposit",amount=5000,date="2026-10-02",reference="Bank",note="Deposit")))
            assertEquals(-7500L,w.accounts.sumOf{FinanceMath.accountBalance(it,w,tx)})
        }finally{db.close()}
    }
    @Test fun splitAndShoppingAreAtomicAndCannotChargeTwice()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o");val batch=UUID.randomUUID().toString()
            val rows=listOf(TransactionDraft("EXPENSE",1000,"Milk","M","2026-10-01"),TransactionDraft("EXPENSE",2000,"Bread","B","2026-10-01"))
            assertTrue(runCatching{r.finance.splitBill(rows,4000,"Cash",batch)}.isFailure);assertTrue(db.ledgerDao().transactionsNow("o").isEmpty())
            r.finance.splitBill(rows,3000,"Bank",batch);assertTrue(runCatching{r.finance.splitBill(rows,3000,"Bank",batch)}.isFailure)
            val item=FinanceItem(kind=FinanceKind.SHOPPING,title="Milk",amount=15000,date="2026-10-01",category="Milk",quantity="1.5",unit="litre",rate=10000)
            r.finance.saveItem(item);r.finance.payItems(setOf(item.id),"2026-10-02")
            assertTrue(runCatching{r.finance.payItems(setOf(item.id),"2026-10-02")}.isFailure)
            assertEquals(3,db.ledgerDao().transactionsNow("o").size)
            val paid=r.profileSnapshot().workspace.items.single();assertTrue(paid.done)
            r.deleteTransaction(db.ledgerDao().transactionsNow("o").first{it.id==paid.transactionId})
            assertFalse(r.profileSnapshot().workspace.items.single().done)
        }finally{db.close()}
    }
    @Test fun claimsAndInstallmentsCountOnlyActualPaymentsAndUndoClaimReceipt()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o")
            r.finance.createReimbursement("Office",10000,"Other","2026-10-01","Cash")
            val claim=r.profileSnapshot().workspace.items.single();r.finance.settleReimbursement(claim.id,3000,"2026-10-02","Bank")
            assertEquals(3000L,r.profileSnapshot().workspace.items.first{it.id==claim.id}.settled)
            assertTrue(runCatching{r.finance.settleReimbursement(claim.id,8000,"2026-10-03","Bank")}.isFailure)
            val received=db.ledgerDao().transactionsNow("o").first{it.type=="INCOME"};r.deleteTransaction(received)
            assertEquals(0L,r.profileSnapshot().workspace.items.first{it.id==claim.id}.settled)
            r.finance.createInstallments("Phone",10001,2000,3,LocalDate.parse("2026-11-01"),"Cash","Other")
            val parts=r.profileSnapshot().workspace.items.filter{it.kind==FinanceKind.INSTALLMENT}
            assertEquals(8001L,parts.sumOf{it.amount});assertEquals(12000L,db.ledgerDao().transactionsNow("o").sumOf{it.expenseEffect()})
            r.finance.payItems(setOf(parts.first().id),"2026-11-01")
            assertEquals(12000L+parts.first().amount,db.ledgerDao().transactionsNow("o").sumOf{it.expenseEffect()})
        }finally{db.close()}
    }
    @Test fun goalsTrackActualSavingDeletionWithoutDoubleDeduction()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o");val goal=FinanceItem(kind=FinanceKind.GOAL,title="Laptop",amount=100000,date=LocalDate.now().plusMonths(3).toString(),settled=10000,opening=10000)
            r.finance.saveItem(goal);r.finance.contributeGoal(goal.id,20000,LocalDate.now().toString())
            assertEquals(30000L,r.profileSnapshot().workspace.items.first{it.id==goal.id}.settled)
            val saved=db.ledgerDao().savingsNow("o").single();assertEquals(20000L,saved.amountMinor)
            r.deleteSaving(saved);assertEquals(10000L,r.profileSnapshot().workspace.items.single().settled)
        }finally{db.close()}
    }
    @Test fun workspacePrivateFieldsAuditAndRefundLinksRoundTripAndMalformedRestoreRollsBack()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val source=LedgerRepository(db,"source")
            source.finance.saveItem(FinanceItem(kind=FinanceKind.ACCOUNT,title="Wallet",amount=100000,date="2026-10-01"))
            val id=source.finance.saveEntry("EXPENSE",20000,"Milk","Private milk","2026-10-01","Wallet","2","litre",10000,true)
            source.finance.refund(id,5000,"2026-10-02","Private refund","Wallet")
            val json=source.exportJson();val target=LedgerRepository(db,"target");target.importJson(json)
            val tx=db.ledgerDao().transactionsNow("target");assertEquals(2,tx.size);assertTrue(tx.all{it.privateEntry && it.account=="Wallet"})
            val parent=tx.first{it.refundOf.isBlank()};assertEquals(parent.id,tx.first{it.refundOf.isNotBlank()}.refundOf)
            assertEquals("2",parent.quantity);assertEquals(2,target.profileSnapshot().workspace.revisions.size)
            val broken=JSONObject(json);broken.getJSONArray("transactions").getJSONObject(0).put("amountMinor",-10)
            assertTrue(runCatching{target.importJson(broken.toString())}.isFailure);assertEquals(2,db.ledgerDao().transactionsNow("target").size)
            val legacy=JSONObject(json).put("version",9)
            assertTrue(runCatching{target.importJson(legacy.toString())}.isFailure)
        }finally{db.close()}
    }
    @Test fun editsRecordBeforeAndAfterAndRejectStaleWrites()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o");r.saveTransaction("EXPENSE",10000,"Milk","Old","2026-10-01")
            val before=db.ledgerDao().transactionsNow("o").single()
            r.saveTransaction("EXPENSE",15000,"Milk","New","2026-10-02",before)
            val revision=r.profileSnapshot().workspace.revisions.last();assertEquals("Old",JSONObject(revision.before).getString("note"));assertEquals("New",JSONObject(revision.after).getString("note"))
            if(db.ledgerDao().transactionsNow("o").single().updatedAt!=before.updatedAt)assertTrue(runCatching{r.saveTransaction("EXPENSE",12000,"Milk","Stale","2026-10-01",before)}.isFailure)
        }finally{db.close()}
    }
    @Test fun pastedSmsDropsIdentifiersAndReceiptSkipsGrandTotals() {
        val sms=FinanceIntake.sms("PKR 1,500.00 debited from account 123456789 on 2026-10-12. Balance PKR 99,000.00","2026-10-19","EXPENSE")
        assertEquals(150000L,sms.amountMinor);assertEquals("2026-10-12",sms.date);assertFalse(sms.note.contains("123456789"))
        val rows=FinanceIntake.receipt("Milk 150.00\nBread 100.00\nSubtotal 250.00\nGrand Total 250.00","2026-10-19")
        assertEquals(2,rows.size);assertEquals(25000L,rows.sumOf{it.amountMinor})
    }
    @Test fun claimsGoalsAndInstallmentsRestoreAcrossOwnersWithEveryLinkIntact()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val source=LedgerRepository(db,"source")
            source.finance.createReimbursement("Office",10000,"Other","2026-10-01","Cash")
            val claim=source.profileSnapshot().workspace.items.single();source.finance.settleReimbursement(claim.id,3000,"2026-10-02","Bank")
            val goal=FinanceItem(kind=FinanceKind.GOAL,title="Laptop",amount=100000,date=LocalDate.now().plusMonths(3).toString(),settled=10000,opening=10000)
            source.finance.saveItem(goal);source.finance.contributeGoal(goal.id,5000,"2026-10-03","Bank")
            source.finance.createInstallments("Phone",100000,20000,4,LocalDate.parse("2026-11-01"),"Cash","Other")
            val part=source.profileSnapshot().workspace.items.first{it.kind==FinanceKind.INSTALLMENT}
            source.finance.payItems(setOf(part.id),"2026-10-19")
            val backup=source.exportJson();val target=LedgerRepository(db,"target");target.importJson(backup)
            val w=target.profileSnapshot().workspace
            assertEquals(3000L,w.items.first{it.kind==FinanceKind.REIMBURSEMENT}.settled)
            assertEquals(15000L,w.items.first{it.kind==FinanceKind.GOAL}.settled)
            assertEquals(40000L,w.items.first{it.kind==FinanceKind.INSTALLMENT_PURCHASE}.settled)
            assertEquals(1,db.ledgerDao().savingsNow("target").size)
            val tx=db.ledgerDao().transactionsNow("target");assertTrue(w.items.first{it.kind==FinanceKind.REIMBURSEMENT}.reference in tx.map{it.id})
            assertTrue(w.revisions.all{it.transactionId.startsWith("target:")})
            val broken=JSONObject(backup);val items=broken.getJSONObject("profileSettings").getJSONObject("workspace").getJSONArray("items")
            for(n in 0 until items.length())if(items.getJSONObject(n).getString("kind")=="REIMBURSEMENT")items.getJSONObject(n).put("settled",4000)
            assertTrue(runCatching{target.importJson(broken.toString())}.isFailure)
            assertEquals(3000L,target.profileSnapshot().workspace.items.first{it.kind==FinanceKind.REIMBURSEMENT}.settled)
        }finally{db.close()}
    }
    @Test fun deletingAndReRecordingAdvanceReconcilesPurchaseAndUnpaidPlansCanBeCancelled()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val repo=LedgerRepository(db,"o")
            repo.finance.createInstallments("Phone",100000,20000,4,LocalDate.parse("2026-11-01"),"Cash","Other")
            val root=repo.profileSnapshot().workspace.items.first{it.kind==FinanceKind.INSTALLMENT_PURCHASE}
            repo.deleteTransaction(db.ledgerDao().transactionsNow("o").single())
            assertEquals(0L,repo.profileSnapshot().workspace.items.first{it.id==root.id}.settled)
            repo.finance.payAdvance(root.id,"2026-10-19","Bank")
            assertEquals(20000L,repo.profileSnapshot().workspace.items.first{it.id==root.id}.settled)
            assertTrue(runCatching{repo.finance.payAdvance(root.id,"2026-10-19","Bank")}.isFailure)
            repo.deleteTransaction(db.ledgerDao().transactionsNow("o").single())
            repo.finance.cancelInstallments(root.id)
            assertTrue(repo.profileSnapshot().workspace.items.isEmpty())
        }finally{db.close()}
    }

}
