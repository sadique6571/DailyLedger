package com.sadique.dailyledger.ai

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class OfflineEvolutionTest {
    private val today = LocalDate.parse("2026-10-14")
    @Test fun separatesQuantitiesFromTheActualTotalAndRetainsTheNote() {
        val rows = OfflineMiniAi.drafts("2 litre dodh 300, 1.5 kg aalo 180, moong dal 1 kg 400", today)!!.entries
        assertEquals(listOf(30000L, 18000L, 40000L), rows.map { it.amountMinor })
        assertEquals(listOf("Milk", "Potatoes", "Moong dal"), rows.map { it.category })
        assertTrue(rows.first().note.contains("2 litre"))
        assertNull(OfflineMiniAi.drafts("doodh 2 litre", today))
        assertNull(OfflineMiniAi.drafts("2 kg aalo @ 100", today))
    }
    @Test fun individualItemsAndUrduSpellingAliasesAreRecognized() {
        val cases = mapOf("aaloo" to "Potatoes", "piyaz" to "Onions", "tamater" to "Tomatoes", "masur daal" to "Masoor dal", "مرغی" to "Chicken", "دودھ" to "Milk")
        cases.forEach { (label, category) -> assertEquals(label, category, CategoryRules.infer("EXPENSE", label)) }
    }
    @Test fun explicitRelativeDaysAndWeekdaysAreAssignedPerEntry() {
        val rows = OfflineMiniAi.drafts("2 din pehle doodh 150, last Friday petrol 500, next Monday sabzi 300", today)!!.entries
        assertEquals(listOf("2026-10-12", "2026-10-09", "2026-10-19"), rows.map { it.date })
        assertEquals(listOf(15000L, 50000L, 30000L), rows.map { it.amountMinor })
        assertEquals("2026-10-13", OfflineMiniAi.drafts("guzra kal doodh 150", today)!!.entries.single().date)
        assertTrue(runCatching { OfflineMiniAi.drafts("parson petrol 500", today) }.exceptionOrNull() is AiException)
    }
    @Test fun receivingGoodsIsNotIncomeAndAmbiguousDirectionNeedsReview() {
        assertEquals("EXPENSE", OfflineMiniAi.drafts("doodh 150 ka mila", today)!!.entries.single().type)
        assertEquals("INCOME", OfflineMiniAi.drafts("paise 500 received", today)!!.entries.single().type)
        assertNull(OfflineMiniAi.drafts("salary 50000 paid", today))
        assertNull(OfflineMiniAi.drafts("Ali 500 received", today))
    }
}
