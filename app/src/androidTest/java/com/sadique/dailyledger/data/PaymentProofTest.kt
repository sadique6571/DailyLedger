package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PaymentProofTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun pdf(size:Int=128)=ByteArray(size){32}.apply{"%PDF-1.7".toByteArray().copyInto(this)}
    private suspend fun payment(r:LedgerRepository):String {
        r.saveLoan("LENT","Test",10000,null,"")
        val loan=r.database.ledgerDao().loansNow(r.ownerId).single()
        r.addLoanPayment(loan.id,100,"2026-10-19","")
        return r.database.ledgerDao().loanPaymentsNow(r.ownerId).single().id
    }
    @Test fun largeProofIsFullyBackedUpAndRestoredWithOwnerLinks()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val source=LedgerRepository(db,"source");val id=payment(source);val bytes=pdf(1_500_000)
            source.savePaymentProofData(id,"loan",bytes)
            val json=source.exportJson();assertEquals(11,JSONObject(json).getInt("version"))
            val dest=LedgerRepository(db,"destination");dest.importJson(json)
            val proof=dest.validProofs().single();assertTrue(proof.id.startsWith("destination:"));assertTrue(proof.paymentId.startsWith("destination:"))
            assertArrayEquals(bytes,proofBytes(proof));assertEquals(0,LedgerRepository(db,"other").validProofs().size)
            assertTrue(runCatching{LedgerRepository(db,"other").savePaymentProofData(id,"loan",bytes)}.isFailure)
            assertTrue(runCatching{source.savePaymentProofData(id,"organizer",bytes)}.isFailure)
        }finally{db.close()}
    }
    @Test fun invalidProofAndOldRestoreCannotDestroyCurrentLedger()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=LedgerRepository(db,"o");val id=payment(r);r.savePaymentProofData(id,"loan",pdf())
            val original=r.exportJson();val bad=JSONObject(original);bad.getJSONArray("paymentProofs").getJSONObject(0).put("data","corrupt")
            assertTrue(runCatching{r.importJson(bad.toString())}.isFailure)
            assertEquals(1,r.validProofs().size);assertEquals(1,db.ledgerDao().loansNow("o").size)
            assertTrue(runCatching{r.importJson(JSONObject(original).put("version",10).toString())}.isFailure)
            assertTrue(runCatching{r.savePaymentProofData(id,"loan",ByteArray(MAX_PROOF_BYTES+1))}.isFailure)
            r.deleteLoan(db.ledgerDao().loansNow("o").single());assertTrue(db.ledgerDao().paymentProofsNow("o").isEmpty())
        }finally{db.close()}
    }
    @Test fun totalLimitIsExplicitAndMigrationKeepsFinanceData()=runBlocking {
        val name="proof-upgrade-${UUID.randomUUID()}.db"
        val first=Room.databaseBuilder(context,AppDatabase::class.java,name).build();val r=LedgerRepository(first,"o");val id=payment(r)
        r.finance.saveEntry("EXPENSE",100,"Milk","Keep","2026-10-19",account="Bank",privateEntry=true)
        first.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,0).use{it.execSQL("DROP TABLE payment_proofs");it.execSQL("DROP TABLE payment_proof_chunks");it.version=10}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_10_11).build()
        try {
            val repo=LedgerRepository(db,"o");assertEquals("Bank",db.ledgerDao().transactionsNow("o").single().account);assertTrue(db.ledgerDao().transactionsNow("o").single().privateEntry)
            val bytes=pdf(MAX_PROOF_BYTES);repo.savePaymentProofData(id,"loan",bytes);repo.savePaymentProofData(id,"loan",bytes)
            assertTrue(runCatching{repo.savePaymentProofData(id,"loan",pdf())}.isFailure);assertEquals(2,repo.validProofs().size)
        }finally{db.close();context.deleteDatabase(name)}
    }
}
