package com.sadique.dailyledger.ai

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DatedAiTest {
    private val today = LocalDate.parse("2026-10-08")
    @Test fun explicitDatesAreNotAmountsAndRemainOnTheirOwnEntries() {
        for (text in listOf("2026-10-01 doodh 150 aur 02/10/2026 petrol 1,500", "1st October 2026 doodh 150, Oct 2 2026 petrol 1,500")) {
            val rows = OfflineMiniAi.drafts(text, today)!!.entries
            assertEquals(listOf("2026-10-01", "2026-10-02"), rows.map { it.date })
            assertEquals(listOf(15000L, 150000L), rows.map { it.amountMinor })
        }
        val compact = OfflineMiniAi.drafts("1 Oct doodh 150 2 Oct petrol 200", today)!!.entries
        assertEquals(listOf("2026-10-01", "2026-10-02"), compact.map { it.date })
    }
    @Test fun oneDateAppliesToTheListAndMissingDatesUseToday() {
        for (text in listOf("1 Oct, doodh 150, sabzi 100", "doodh 150 sabzi 100 on 1 Oct")) {
            assertTrue(OfflineMiniAi.drafts(text, today)!!.entries.all { it.date == "2026-10-01" })
        }
        assertEquals("2026-10-08", OfflineMiniAi.drafts("doodh 150", today)!!.entries.single().date)
        assertEquals("2026-10-07", OfflineMiniAi.drafts("yesterday doodh 150", today)!!.entries.single().date)
        assertEquals("2026-10-09", OfflineMiniAi.drafts("tomorrow doodh 150", today)!!.entries.single().date)
        val future = OfflineMiniAi.drafts("15 Oct school fees 2000", today)!!
        assertEquals("2026-10-15", future.entries.single().date)
        assertTrue(future.message.contains("Year omitted: 2026"))
    }
    @Test fun invalidOrAmbiguousDatesNeverBecomeTodayOrMoney() {
        for (text in listOf("31/02/2026 doodh 150", "2026-02-29 petrol 500", "31 April doodh 150", "kal doodh 150", "doodh 150 on 01/10")) {
            assertTrue(text, runCatching { OfflineMiniAi.drafts(text, today) }.exceptionOrNull() is AiException)
        }
        assertEquals("2024-02-29", OfflineMiniAi.drafts("29/02/2024 doodh 150", today)!!.entries.single().date)
        assertEquals("2026-10-01", OfflineMiniAi.drafts("۰۱/۱۰/۲۰۲۶ دودھ ۱۵۰", today)!!.entries.single().date)
    }
    @Test fun repeatedPurchasesStayRepeatedAndBatchesAreNotTruncated() {
        assertEquals(2, OfflineMiniAi.drafts("1 Oct doodh 150, doodh 150", today)!!.entries.size)
        assertNull(OfflineMiniAi.drafts("1 Oct doodh 150, sabzi", today))
        assertNull(OfflineMiniAi.drafts((1..11).joinToString(", ") { "doodh 150" }, today))
    }
    @Test fun creditDatesAmountsAndPaymentDueRemainSeparate() {
        val row = OfflineCreditAi.drafts("1 Oct rashan 5,000 Aslam Store se udhar, due 15 Oct", today = today)!!.entries.single()
        assertEquals("Aslam Store", row.creditor)
        assertEquals(500000L, row.amountMinor)
        assertEquals("Groceries", row.category)
        assertEquals("2026-10-01", row.purchaseDate)
        assertEquals("2026-10-15", row.dueDate)
        val rows = OfflineCreditAi.drafts("1 Oct doodh 150, 2 Oct doodh 150", "Aslam", today)!!.entries
        assertEquals(listOf("2026-10-01", "2026-10-02"), rows.map { it.purchaseDate })
        assertEquals(2, OfflineCreditAi.drafts("doodh 150, doodh 150", "Aslam", today)!!.entries.size)
        assertNull(OfflineCreditAi.drafts("rashan 5000, sabzi", today = today))
        assertNull(OfflineCreditAi.drafts((1..11).joinToString(", ") { "rashan 5000" }, today = today))
        assertNull(OfflineCreditAi.drafts("rashan 1,50", today = today))
    }
    @Test fun specificCategoriesBeatBroadAliases() {
        val cases = mapOf(
            "formula milk" to "Baby food & formula",
            "school shoes" to "School uniform",
            "hair oil" to "Shampoo & hair care",
            "bartan soap" to "Dishwashing",
            "oil change" to "Vehicle service",
            "admission fee" to "Admission & exam fees",
            "wife kharcha" to "Spouse personal expenses",
            "ghar ka daily kharcha" to "Household cash",
            "phone cover" to "Phone accessories",
            "gas cylinder" to "Cooking gas cylinder",
            "house insurance" to "Home insurance",
            "generator petrol" to "Generator fuel",
            "office lunch" to "Work meals",
            "masjid ka kharcha" to "Mosque & madrasa",
            "masjid donation" to "Mosque & madrasa",
            "property tax" to "Property tax",
            "laptop repair" to "Device repair",
        )
        cases.forEach { (text, category) -> assertEquals(text, category, CategoryRules.infer("EXPENSE", text)) }
        assertEquals("Commission income", CategoryRules.infer("INCOME", "commission"))
        assertEquals("EXPENSE", OfflineMiniAi.drafts("Milad shopping 500", today)!!.entries.single().type)
    }
}
