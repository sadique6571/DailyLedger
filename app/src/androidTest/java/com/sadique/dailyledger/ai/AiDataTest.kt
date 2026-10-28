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
 @Test fun tolerantOnlineShapesStillParse(){
  val today=LocalDate.now().toString()
  val fenced="```json\n{\"message\":\"Ok\",\"transactions\":[{\"type\":\"expense\",\"amount_pkr\":30,\"category\":\"Sweets\",\"note\":\"Gachak\",\"date\":\"\"}]}\n```"
  val f=AiProtocol.decodeDrafts(fenced).entries.single();assertEquals(3000L,f.amountMinor);assertEquals("EXPENSE",f.type);assertEquals(today,f.date)
  val prose="Here is your entry: {\"message\":\"Ok\",\"transactions\":[{\"type\":\"EXPENSE\",\"amount_pkr\":\"Rs 1,500\",\"category\":\"Other\",\"note\":\"entertainment\",\"date\":\"2026-10-06\"}]} done"
  assertEquals(150000L,AiProtocol.decodeDrafts(prose).entries.single().amountMinor)
  val single="""{"message":"Ok","transactions":{"type":"EXPENSE","amount_pkr":"30","category":"","note":"snaks","date":"2026-10-06"}}"""
  assertEquals("Other",AiProtocol.decodeDrafts(single).entries.single().category)
  for(bad in listOf(valid.replace("1500.25","-1.5"),valid.replace("\"amount_pkr\":\"1500.25\"","\"amount_pkr\":1.001")))assertTrue(runCatching{AiProtocol.decodeDrafts(bad)}.exceptionOrNull() is AiException)
 }
 @Test fun tolerantDraftShapesRound2(){
  val extra="""{"message":"Ok","currency":"PKR","transactions":[{"type":"EXPENSE","amount_pkr":"3500","category":"Other","note":"Parday","date":"2026-10-27","confidence":0.9}]}"""
  val e=AiProtocol.decodeDrafts(extra).entries.single();assertEquals(350000L,e.amountMinor);assertEquals("Other",e.category)
  assertEquals(350000L,AiProtocol.decodeDrafts(valid.replace("1500.25","3500 PKR")).entries.single().amountMinor)
  assertEquals(350000L,AiProtocol.decodeDrafts(valid.replace("1500.25","Rs. 3,500/-")).entries.single().amountMinor)
  assertEquals("2026-10-27",AiProtocol.decodeDrafts(valid.replace("2026-10-06","27/10/2026")).entries.single().date)
  assertEquals("2026-10-27",AiProtocol.decodeDrafts(valid.replace("2026-10-06","27 Oct 2026")).entries.single().date)
  assertEquals("EXPENSE",AiProtocol.decodeDrafts(valid.replace("EXPENSE","debit")).entries.single().type)
  for(bad in listOf(valid.replace("\"message\"","\"ownerId\":\"other\",\"message\""),valid.replace("\"type\":","\"accountId\":\"x\",\"type\":")))assertTrue(runCatching{AiProtocol.decodeDrafts(bad)}.exceptionOrNull() is AiException)
  assertTrue(AiProtocol.decodeDrafts("""{"message":"?","transactions":[]}""").entries.isEmpty())
  assertEquals(60,AiProtocol.decodeDrafts(valid.replace("Fuel","X".repeat(70))).entries.single().category.length)
  val many="""{"message":"Ok","transactions":["""+ (1..31).joinToString(",") { """{"type":"EXPENSE","amount_pkr":"10","category":"Other","note":"x","date":"2026-10-06"}""" }+"]}"
  assertEquals(30,AiProtocol.decodeDrafts(many).entries.size)
 }
 @Test fun tolerantInsightsShapes(){
  val fenced="```json\n{\"message\":\"hi\",\"suggestions\":[{\"title\":\"${"T".repeat(90)}\",\"detail\":\"${"D".repeat(700)}\",\"extra\":1},{\"title\":\"\",\"detail\":\"blank\"},{\"title\":\"Short\",\"detail\":\"Ok\"},{\"title\":\"Fourth\",\"detail\":\"cut\"}]}```"
  val tips=AiProtocol.decodeInsights(fenced);assertEquals(3,tips.size);assertEquals(80,tips[0].title.length);assertEquals(600,tips[0].detail.length);assertEquals("Short",tips[1].title);assertEquals("Fourth",tips[2].title)
  assertTrue(runCatching{AiProtocol.decodeInsights("""{"suggestions":[]}""")}.exceptionOrNull() is AiException)
 }
 @Test fun exactItemNamePreservedThroughSave(){
  // The review screen shows the draft note as its title, so the note IS the
  // item name: it must survive normalization and learning untouched.
  val draft=TransactionDraft("EXPENSE",350000,"Parday","Parday","2026-10-27")
  val normalized=CategoryRules.normalizeResult(AiDraftResult("Ok",listOf(draft)),setOf("Parday"))
  assertEquals("Parday",normalized.entries.single().category)
  assertEquals("Parday",normalized.entries.single().note)
  assertEquals("Parday",CategoryLearning.apply(draft,emptyList(),emptyList()).note)
  val finalized=AiDraftResult("Ok",listOf(draft)).finalizeNames("Parday 3500")
  assertEquals("Parday",finalized.entries.single().note)
  // A generic model note falls back to the input's own item wording.
  val generic=TransactionDraft("EXPENSE",3000,"Other","Other","2026-10-27")
  assertEquals("Gachak",AiDraftResult("Ok",listOf(generic)).finalizeNames("Gachak 30").entries.single().note)
  val blank=TransactionDraft("EXPENSE",3000,"Other","","2026-10-27")
  assertEquals("Parday",AiDraftResult("Ok",listOf(blank)).finalizeNames("Parday 3500").entries.single().note)
  // No usable wording at all: never invent a name.
  assertEquals("",AiDraftResult("Ok",listOf(blank)).finalizeNames("3500").entries.single().note)
 }
 @Test fun learnedItemsAutoAddOnceAndDedupe(){
  val catalog=CategoryCatalog.load(context)
  // Variant spellings are ONE item through the existing alias logic.
  assertTrue(LearnedItems.isKnown("sonf","EXPENSE",catalog,emptyList()))
  assertTrue(LearnedItems.isKnown("laal march","EXPENSE",catalog,emptyList()))
  assertTrue(LearnedItems.isKnown("surkh mirch","EXPENSE",catalog,emptyList()))
  assertFalse(LearnedItems.isKnown("Handmade wall hanging","EXPENSE",catalog,emptyList()))
  assertTrue(LearnedItems.isKnown("Parday","EXPENSE",catalog,emptyList()))
  val learned=LearnedItems.add(emptyList(),LearnedItem("Handmade wall hanging","EXPENSE","Handmade wall hanging"))
  assertEquals(1,learned.size)
  assertEquals(learned,LearnedItems.add(learned,LearnedItem("handmade wall hanging","EXPENSE","Handmade wall hanging")))
  assertTrue(LearnedItems.isKnown("handmade wall hanging","EXPENSE",catalog,learned))
  val rows=LearnedItems.toLedgerCategories(learned)
  assertEquals("My items",rows.single().group)
  assertEquals("Handmade wall hanging",rows.single().label)
  CategoryCatalog.setLearnedOverlay(learned)
  try {
    assertTrue(CategoryCatalog.load(context).any{it.label=="Handmade wall hanging" && it.group=="My items"})
  } finally {
    CategoryCatalog.setLearnedOverlay(emptyList())
  }
 }
 @Test fun learningSnapshotItemsRoundTripAndBackup(){
  val item=LearnedItem("Parday","EXPENSE","Parday","https://example.com/parday.png")
  val snapshot=LearningSnapshot(items=listOf(item))
  val json=snapshot.json()
  assertEquals("Parday",json.getJSONArray("items").getJSONObject(0).getString("name"))
  assertEquals("https://example.com/parday.png",json.getJSONArray("items").getJSONObject(0).getString("iconUrl"))
  // This items array is exactly what exportJson embeds as aiLearning, so a
  // restore that decodes the snapshot restores the learned items.
  assertEquals(snapshot,LearningSnapshot.decode(json))
  val v1=org.json.JSONObject().put("version",1).put("rules",org.json.JSONArray())
    .put("suppressed",org.json.JSONArray()).put("dismissedTips",org.json.JSONArray())
  assertTrue(LearningSnapshot.decode(v1).items.isEmpty())
  val merged=snapshot.mergeOlder(LearningSnapshot(items=listOf(LearnedItem("Gachak","EXPENSE","Snacks"))))
  assertEquals(2,merged.items.size)
  val withIcon=LearningSnapshot(items=listOf(LearnedItem("Parday","EXPENSE","Parday")))
    .withItemIcon("parday","EXPENSE","https://example.com/parday.png")
  assertEquals("https://example.com/parday.png",withIcon.items.single().iconUrl)
  assertTrue(runCatching{LearningSnapshot(items=listOf(LearnedItem("Parday","EXPENSE","Parday","http://insecure.example/x.png"))).validated()}.isFailure)
 }
 @Test fun iconFileNameAndUrlRules(){
  assertEquals(LearnedItems.iconFileName("Parday"),LearnedItems.iconFileName("parday"))
  assertTrue(LearnedItems.iconFileName("Parday").startsWith("item_"))
  assertTrue(LearnedItems.iconFileName("Parday").endsWith(".webp"))
  assertTrue(LearnedItems.isValidIconUrl("https://example.com/icon.png"))
  assertFalse(LearnedItems.isValidIconUrl("http://example.com/icon.png"))
  assertFalse(LearnedItems.isValidIconUrl(""))
  assertFalse(LearnedItems.isValidIconUrl("https://"))
  assertFalse(LearnedItems.isValidIconUrl("https://user:pass@example.com/icon.png"))
  assertFalse(LearnedItems.isValidIconUrl(null))
  assertEquals(128 to 64,ItemIconService.targetSize(400,200,128))
  assertEquals(100 to 50,ItemIconService.targetSize(100,50,128))
  assertEquals(64 to 128,ItemIconService.targetSize(200,400,128))
 }
 @Test fun learnedLabelsKeptAsCategories(){
  val draft=TransactionDraft("EXPENSE",350000,"Parday","Parday","2026-10-27")
  val kept=CategoryRules.normalizeResult(AiDraftResult("Ok",listOf(draft)),setOf("Parday"))
  assertEquals("Parday",kept.entries.single().category)
 }
 @Test fun expandedOnlinePromptIncludesConcreteItemsAndTheirAliases(){
  val catalog=CategoryCatalog.load(context)
  val request=AiPrompts.drafts("parday 3500, badam 400",LocalDate.parse("2026-10-28"),catalog)
  assertTrue(request.instruction.contains("EXPENSE:Curtains"))
  assertTrue(request.instruction.contains("EXPENSE:Almonds"))
  assertTrue(request.instruction.contains("Curtains = curtain / parda / پردہ"))
  assertTrue(request.instruction.contains("Almonds = almond / badam / بادام"))
  assertTrue(request.instruction.contains("most specific matching item"))
  assertEquals("curtains",ItemArtwork.key("پردے"))
 }
 @Test fun suggestionsPromptIsGrounded(){
  val day=LocalDate.parse("2026-10-06")
  val rows=listOf(TransactionEntity("s1","me","EXPENSE",240000,"Snacks","","2026-10-01",1,1))
  val snapshot=SpendingInsights.calculate("me",rows,day)
  val request=AiPrompts.insights(snapshot,CategoryCatalog.load(context))
  assertTrue(request.instruction.contains("GROUND EVERY TIP IN THE SUPPLIED NUMBERS"))
  assertTrue(request.instruction.contains("LARGEST OPTIONAL"))
  assertTrue(request.instruction.contains("nothing optional left to cut"))
  assertTrue(request.instruction.contains("actual amount"))
  assertTrue(request.input.contains("Snacks"))
  assertTrue(request.input.contains("2400.00"))
 }
 @Test fun atomicIdempotentAccountScopedBatch()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try{val repo=LedgerRepository(db,"owner");LedgerRepository(db,"other").saveTransaction("EXPENSE",777,"Other","","2026-10-06");val first=AiProtocol.decodeDrafts(valid).entries.single();val batch="c98951b9-5f90-48a4-b1a3-433b80a47c1c";val drafts=listOf(first,first.copy(category="Milk",amountMinor=30000));repo.saveDraftBatch(drafts,batch);repo.saveDraftBatch(drafts,batch);assertEquals(2,db.ledgerDao().transactionsNow("owner").size);assertEquals(777L,db.ledgerDao().transactionsNow("other").single().amountMinor);assertTrue(runCatching{repo.saveDraftBatch(listOf(first.copy(amountMinor=999)),batch)}.isFailure)
   db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_bad_draft BEFORE INSERT ON transactions WHEN NEW.category='FAIL' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
   assertTrue(runCatching{repo.saveDraftBatch(listOf(first,first.copy(category="FAIL")),"ae6cbb44-abba-45cd-9c54-c4c0e1eb38d2")}.isFailure);assertEquals(2,db.ledgerDao().transactionsNow("owner").size)
  }finally{db.close()}
 }
 @Test fun privateSummaryAndConsentIsolation(){val row=TransactionEntity("secret-id","owner","EXPENSE",150000,"Fuel","private-note","2026-10-06",1,1);val json=AiProtocol.context(SpendingInsights.calculate("owner",listOf(row),LocalDate.parse("2026-10-06"))).toString();assertFalse(json.contains("secret-id"));assertFalse(json.contains("owner"));assertFalse(json.contains("private-note"));val a=AiPreferences(context,"test-a");val b=AiPreferences(context,"test-b");a.enabled=false;b.enabled=false;a.enabled=true;assertFalse(b.enabled);a.save("hash",listOf(SpendingTip("title","detail")));assertNotNull(a.cached("hash"));assertNull(b.cached("hash"));a.enabled=false;assertNull(a.cached("hash"))}
 @Test fun catalogAndGeminiSummaryPrivacy(){
  val c=CategoryCatalog.load(context);assertTrue(c.size > 200);assertEquals(c.size,c.map{it.type to it.label}.distinct().size);assertTrue(c.any{it.label=="Moong dal"});assertTrue(c.any{it.label=="Salary"&&it.type=="INCOME"})
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

