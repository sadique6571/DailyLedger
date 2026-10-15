package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.TransactionDraft
import com.sadique.dailyledger.data.TransactionEntity
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class RelativeLearningTest {
    private val today = LocalDate.parse("2026-10-15")
    @Test fun kalUsesYesterdayAndMixedDatesStayOnTheirOwnRows() {
        val r = OfflineMiniAi.drafts("kal dodh 100, aaj sabzi 200, parson petrol 500", today)!!
        assertEquals(listOf("2026-10-14", "2026-10-15", "2026-10-13"), r.entries.map { it.date })
        assertEquals(listOf("Milk", "Vegetables", "Fuel"), r.entries.map { it.category })
        assertEquals(listOf(10000L, 20000L, 50000L), r.entries.map { it.amountMinor })
        assertTrue(r.message.contains("Kal = yesterday"))
    }
    @Test fun timePhrasesAndUrduResolveWithoutBecomingAmounts() {
        val cases = mapOf("kal raat" to -1L, "last night" to -1L, "aaj subah" to 0L,
            "aane wala kal" to 1L, "day after tomorrow" to 2L, "day before yesterday" to -2L,
            "کل" to -1L, "پرسوں" to -2L, "۲ دن پہلے" to -2L, "ایک دن بعد" to 1L)
        cases.forEach { (phrase, offset) ->
            val row = OfflineMiniAi.drafts("$phrase دودھ ۱۰۰", today)!!.entries.single()
            assertEquals(phrase, today.plusDays(offset).toString(), row.date); assertEquals(10000L, row.amountMinor)
        }
    }
    @Test fun yesterdayCrossesYearsAndLeapDaysWithoutMatchingInsideWords() {
        assertEquals("2025-12-31", OfflineMiniAi.drafts("kal doodh 100", LocalDate.parse("2026-01-01"))!!.entries.single().date)
        assertEquals("2024-02-29", OfflineMiniAi.drafts("kal doodh 100", LocalDate.parse("2024-03-01"))!!.entries.single().date)
        assertEquals(today.toString(), OfflineMiniAi.drafts("kaleji 500", today)!!.entries.single().date)
    }
    @Test fun learningHandlesWordOrderSpellingAndQuantitiesWithoutChangingMoneyOrDate() {
        val rules = listOf(LearnedCategoryRule("EXPENSE", "office doodh", "Work meals"))
        val draft = TransactionDraft("EXPENSE", 10000L, "Milk", "kal 2 litre dodh office liya", "2026-10-14")
        assertEquals(draft.copy(category = "Work meals"), CategoryLearning.apply(draft, rules))
        assertNull(CategoryLearning.match("INCOME", draft.note, rules))
        assertNull(CategoryLearning.match("EXPENSE", "office doodhwala", rules))
    }
    @Test fun consistentSavedItemsTeachOnlyThisAccountAndExplicitCorrectionsWin() {
        fun record(id: String, owner: String, category: String) =
            TransactionEntity(id, owner, "EXPENSE", 10000, category, "Office chai", today.toString(), 1, 1)
        val history = CategoryLearning.fromHistory(listOf(record("1", "me", "Work meals"), record("2", "me", "Work meals"), record("3", "other", "Tea & coffee")), "me")
        val draft = TransactionDraft("EXPENSE", 25000, "Other", "chai office", "2026-10-14")
        assertEquals("Work meals", CategoryLearning.apply(draft, emptyList(), history).category)
        assertEquals("Tea & coffee", CategoryLearning.apply(draft, listOf(LearnedCategoryRule("EXPENSE", "office chai", "Tea & coffee")), history).category)
        assertTrue(CategoryLearning.fromHistory(listOf(record("1", "me", "Work meals")), "me").isEmpty())
        assertTrue(CategoryLearning.fromHistory(listOf(record("1", "me", "Work meals"), record("2", "me", "Tea & coffee")), "me").isEmpty())
    }
}
