package com.sadique.dailyledger.ai
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class EverydayFoodTest {
 @Test fun familiarFoodsAndSpellingAliasesHaveSpecificCategories(){
  mapOf("subah bahir se salan" to "Salan","saalan" to "Salan","chicken salan" to "Salan","chicken curry" to "Salan","سالن" to "Salan","milk pack" to "Packaged milk","milkpak" to "Packaged milk","olpers" to "Packaged milk","ملک پیک" to "Packaged milk","choclate" to "Chocolate","tofi" to "Toffee & candy","ٹافی" to "Toffee & candy","papad" to "Papar","پاپڑ" to "Papar","biskut" to "Biscuits","gachak" to "Peanut brittle").forEach{(item,expected)->assertEquals(item,expected,CategoryRules.infer("EXPENSE",item))}
  assertEquals("Milk",CategoryRules.infer("EXPENSE","doodh"))
 }
 @Test fun mixedBatchKeepsAmountsAndYesterdayDates(){
  val rows=OfflineMiniAi.drafts("kal salan 100 milk pack 150 chocolate 50 toffee 20 papar 30",LocalDate.parse("2026-10-19"))!!.entries
  assertEquals(listOf("Salan","Packaged milk","Chocolate","Toffee & candy","Papar"),rows.map{it.category})
  assertEquals(listOf(10000L,15000L,5000L,2000L,3000L),rows.map{it.amountMinor})
  assertTrue(rows.all{it.date=="2026-10-18"})
 }
 @Test fun cloudBroadLabelsAreCorrectedButExplicitPersonalCategoriesRemain(){
  assertEquals("Packaged milk",CategoryRules.normalize("EXPENSE","Milk","milk pack"))
  assertEquals("Papar",CategoryRules.normalize("EXPENSE","Snacks","papar"))
  assertEquals("Salan",CategoryRules.normalize("EXPENSE","Food","salan"))
  assertEquals("Milk",CategoryRules.normalize("EXPENSE","Milk","doodh"))
  assertEquals("Salary",CategoryRules.normalize("INCOME","Salary","salary"))
 }
}

