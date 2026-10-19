package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.TransactionDraft
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DraftReviewPolicyTest {
    private val today = LocalDate.parse("2026-10-18")
    @Test fun missingAmountsAreRequestedRatherThanInventedOrDropped() {
        for (input in listOf("doodh liya", "doodh 100 aur sabzi", "2 litre doodh", "kal doodh")) {
            assertTrue(input, runCatching { DraftReviewPolicy.checkMissingDetails(input, today) }.exceptionOrNull()?.message?.contains("Amount missing") == true)
        }
        DraftReviewPolicy.checkMissingDetails("kal doodh 100, sabzi 250", today)
        DraftReviewPolicy.checkMissingDetails("5 tareekh ko doodh 100", today)
    }
    @Test fun calendarWordsNeverBecomeMoneyAndAmbiguityIsFlagged() {
        val result = OfflineMiniAi.drafts("5 tareekh ko doodh 100", today)!!
        assertEquals(10000L, result.entries.single().amountMinor)
        assertEquals("2026-10-05", result.entries.single().date)
        assertTrue(DraftReviewPolicy.issues("5 tareekh ko doodh 100", result.entries.single(), today = today).any { it.contains("Date") })
        assertEquals("2026-10-16", OfflineMiniAi.drafts("پچھلے جمعہ دودھ 100", today)!!.entries.single().date)
        val unknown = TransactionDraft("EXPENSE", 10000, "Other", "item", "2026-10-19")
        assertEquals(3, DraftReviewPolicy.issues("item 100", unknown, cloud = true, today = today).size)
    }
    @Test fun batchesSupportThirtyEntriesAndStopAtTheBound() {
        val result = OfflineMiniAi.drafts((1..30).joinToString(", ") { "doodh 100" }, today)!!
        assertEquals(30, result.entries.size)
        assertEquals(300000L, DraftReviewPolicy.total(result.entries, "EXPENSE"))
        assertEquals(0L, DraftReviewPolicy.total(result.entries, "INCOME"))
        assertNull(OfflineMiniAi.drafts((1..31).joinToString(", ") { "doodh 100" }, today))
    }
    @Test fun aliasesKeepCorrectionsStableAcrossLanguages() {
        val rule = LearnedCategoryRule("EXPENSE", "doodh", "Milk")
        for (word in listOf("dodh", "dudh", "milk", "دودھ")) {
            assertEquals("Milk", CategoryLearning.match("EXPENSE", "$word 100", listOf(rule)))
        }
    }
}
