package com.sadique.dailyledger.ai
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
@RunWith(AndroidJUnit4::class) class AiDataTest {
 private val context:Context=ApplicationProvider.getApplicationContext()
 private val valid="""{"message":"Check","transactions":[{"type":"EXPENSE","amount_pkr":"1500.25","category":"Fuel","note":"Petrol","date":"2026-10-06"}]}"""
 @Test fun parseExactMoneyRejectMalformed(){assertEquals(150025L,AiProtocol.decodeDrafts(valid).entries.single().amountMinor);for(bad in listOf(valid.replace("1500.25","-1"),valid.replace("1500.25","1.001"),valid.replace("2026-10-06","2026-02-30"),valid.replace("EXPENSE","DELETE"),valid.replace("\"type\":","\"ownerId\":\"other\",\"type\":"),"not json"))assertTrue(runCatching{AiProtocol.decodeDrafts(bad)}.exceptionOrNull() is AiException)}
 @Test fun atomicIdempotentAccountScopedBatch()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try{val repo=LedgerRepository(db,"owner");LedgerRepository(db,"other").saveTransaction("EXPENSE",777,"Other","","2026-10-06");val first=AiProtocol.decodeDrafts(valid).entries.single();val batch="c98951b9-5f90-48a4-b1a3-433b80a47c1c";val drafts=listOf(first,first.copy(category="Milk",amountMinor=30000));repo.saveDraftBatch(drafts,batch);repo.saveDraftBatch(drafts,batch);assertEquals(2,db.ledgerDao().transactionsNow("owner").size);assertEquals(777L,db.ledgerDao().transactionsNow("other").single().amountMinor);assertTrue(runCatching{repo.saveDraftBatch(listOf(first.copy(amountMinor=999)),batch)}.isFailure)
   db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_bad_draft BEFORE INSERT ON transactions WHEN NEW.category='FAIL' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
   assertTrue(runCatching{repo.saveDraftBatch(listOf(first,first.copy(category="FAIL")),"ae6cbb44-abba-45cd-9c54-c4c0e1eb38d2")}.isFailure);assertEquals(2,db.ledgerDao().transactionsNow("owner").size)
  }finally{db.close()}
 }
 @Test fun privateSummaryAndConsentIsolation(){val row=TransactionEntity("secret-id","owner","EXPENSE",150000,"Fuel","private-note","2026-10-06",1,1);val json=AiProtocol.context(SpendingInsights.calculate("owner",listOf(row),LocalDate.parse("2026-10-06"))).toString();assertFalse(json.contains("secret-id"));assertFalse(json.contains("owner"));assertFalse(json.contains("private-note"));val a=AiPreferences(context,"test-a");val b=AiPreferences(context,"test-b");a.enabled=false;b.enabled=false;a.enabled=true;assertFalse(b.enabled);a.save("hash",listOf(SpendingTip("title","detail")));assertNotNull(a.cached("hash"));assertNull(b.cached("hash"));a.enabled=false;assertNull(a.cached("hash"))}
 @Test fun catalogAndGeminiSummaryPrivacy(){
  val c=CategoryCatalog.load(context);assertEquals(160,c.size);assertEquals(160,c.map{it.label}.distinct().size);assertTrue(c.any{it.label=="Salary"&&it.type=="INCOME"})
  assertTrue(c.any{it.label=="Household cash"&&it.type=="EXPENSE"})
  assertTrue(c.any{it.label=="Spouse personal expenses"&&it.type=="EXPENSE"})
  assertEquals("Spouse personal expenses",CategoryRules.infer("EXPENSE","wife kharcha"))
  assertEquals("Household cash",CategoryRules.infer("EXPENSE","ghar ka daily kharcha"))
  assertEquals("Overtime",CategoryRules.infer("INCOME","overtime"))
  val rows=listOf(TransactionEntity("private-id","me","EXPENSE",10000,"Private person's bill","private note","2026-10-06",1,1),TransactionEntity("id2","me","EXPENSE",20000,"Another private name","","2026-10-06",1,1))
  val input=AiPrompts.insights(SpendingInsights.calculate("me",rows,LocalDate.parse("2026-10-06")),c).input
  assertFalse(input.contains("private",ignoreCase=true));assertFalse(input.contains("Another"));assertTrue(input.contains("Other expenses"));assertTrue(input.contains("300.00"))
 }
 @Test fun categoryCorrectionsPersistEncryptedAndStayAccountScoped(){
  val owner="category-learning-"+java.util.UUID.randomUUID()
  val learned=CategoryLearningStore(context,owner)
  learned.remember("EXPENSE","Chai 150","Tea & coffee")
  val reopened=CategoryLearningStore(context,owner)
  val draft=TransactionDraft("EXPENSE",20000,"Other","chai 200","2026-10-10")
  assertEquals("Tea & coffee",reopened.apply(draft).category)
  assertTrue(CategoryLearningStore(context,"other-"+owner).rules().isEmpty())
 }
 @Test fun aiBalanceContextIncludesKametiAndSavingsDeductions(){
  val day=LocalDate.parse("2026-10-06")
  val tx=listOf(TransactionEntity("salary","me","INCOME",1000000,"Salary","","2026-10-01",1,1),TransactionEntity("expense","me","EXPENSE",200000,"Food","","2026-10-02",1,1))
  val savings=listOf(SavingEntity("saving","me","DIRECT",50000,"2026-10-05","",1))
  val payments=listOf(CommitteePaymentEntity("payment","me","committee",1,"2026-10",150000,1))
  val snapshot=SpendingInsights.calculate("me",tx,day,savings=savings,committeePayments=payments)
  val context=AiProtocol.context(snapshot)
  assertEquals("0.00",context.getString("opening_balance"))
  assertEquals("1500.00",context.getString("kameti_contributions"))
  assertEquals("500.00",context.getString("savings_contributions"))
  assertEquals("6000.00",context.getString("remaining"))
 }
 @Test fun carriedBalanceIsIncludedInAiContext(){
  val day=LocalDate.parse("2026-10-06")
  val salary=TransactionEntity("salary","me","INCOME",100000,"Salary","","2026-10-01",2,2)
  val snapshot=SpendingInsights.calculate("me",listOf(salary),day,ledgerCycle=LedgerCycleEntity("me",1,25000))
  val context=AiProtocol.context(snapshot)
  assertEquals("250.00",context.getString("opening_balance"))
  assertEquals("1250.00",context.getString("remaining"))
 }
 @Test fun oldProviderConsentDoesNotEnableGeminiAndLimitsAreAccountScoped(){
  val owner="gemini-migration-test";val raw=context.getSharedPreferences("ai_preferences",Context.MODE_PRIVATE)
  val old=AiPreferences.digest(owner)+":";val fresh="gemini-v1:"+old
  raw.edit().putBoolean(old+"enabled",true).remove(fresh+"enabled").remove(fresh+"quota_day").commit()
  val prefs=AiPreferences(context,owner);assertFalse(prefs.enabled);prefs.enabled=true;assertTrue(prefs.enabled)
  val now=java.time.Instant.parse("2026-10-07T01:00:00Z").toEpochMilli()
  repeat(10){assertTrue(prefs.reserve(AiTask.AUTOFILL,now))};assertFalse(prefs.reserve(AiTask.AUTOFILL,now))
  repeat(2){assertTrue(prefs.reserve(AiTask.INSIGHTS,now))};assertFalse(prefs.reserve(AiTask.INSIGHTS,now))
  val other=AiPreferences(context,"another-"+java.util.UUID.randomUUID());assertTrue(other.reserve(AiTask.AUTOFILL,now))
  assertTrue(prefs.reserve(AiTask.AUTOFILL,now+86400000));prefs.enabled=false
 }
}
