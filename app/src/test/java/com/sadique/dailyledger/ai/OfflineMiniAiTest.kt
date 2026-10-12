package com.sadique.dailyledger.ai

import org.junit.Assert.*
import org.junit.Test
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.data.monthlySalarySummary

class OfflineMiniAiTest {
    @Test fun parsesRomanUrduMultipleTransactions() {
        val result = OfflineMiniAi.drafts("doodh 300 aur petrol 2000, salary 60000 mili")!!
        assertEquals(3, result.entries.size)
        assertEquals("Milk", result.entries[0].category)
        assertEquals("Fuel", result.entries[1].category)
        assertEquals("INCOME", result.entries[2].type)
        assertEquals("Salary", result.entries[2].category)
    }

    @Test fun salaryAndOtherIncomeDraftsFeedDashboardMonthlyTotals() {
        val drafts = OfflineMiniAi.drafts("meri salary 50000 ayi aur bonus 5000 mila")!!.entries
        val transactions = drafts.mapIndexed { index, draft ->
            TransactionEntity("$index", "owner", draft.type, draft.amountMinor, draft.category, draft.note, draft.date, 1, 1)
        }
        val summary = monthlySalarySummary(transactions, drafts.first().date.substring(0, 7))
        assertEquals(5_000_000L, summary.salary)
        assertEquals(500_000L, summary.otherIncome)
    }

    @Test fun parsesHazarAndKAmounts() {
        val result = OfflineMiniAi.drafts("petrol 2k aur salary 70 hazar mili")!!
        assertEquals(200000L, result.entries[0].amountMinor)
        assertEquals(7000000L, result.entries[1].amountMinor)
    }

    @Test fun refusesDedicatedLoanAndKametiRecords() {
        assertNull(OfflineMiniAi.drafts("Ali ko loan 5000 diya"))
        assertNull(OfflineMiniAi.drafts("kameti 10000 di"))
    }

    @Test fun unknownTextDoesNotInventMoney() {
        assertNull(OfflineMiniAi.drafts("kal bazar jana hai"))
    }

    @Test fun compactInputWithoutSeparators() {
        val result = OfflineMiniAi.drafts("doodh 300 petrol 2k salary 35 hazar")!!
        assertEquals(3, result.entries.size)
        assertEquals(listOf("Milk", "Fuel", "Salary"), result.entries.map { it.category })
        assertEquals(listOf(30000L, 200000L, 3500000L), result.entries.map { it.amountMinor })
    }
    @Test fun groupedMoneyAndUrduDigitsAreExact() {
        assertEquals(listOf(150000L, 6000000L), OfflineMiniAi.drafts("petrol 1,500, salary 60,000")!!.entries.map { it.amountMinor })
        assertEquals(15000L, OfflineMiniAi.drafts("دودھ ۱۵۰")!!.entries.single().amountMinor)
    }
    @Test fun neverDropsPartsOfAnAmbiguousBatch() {
        assertNull(OfflineMiniAi.drafts("doodh 150 aur sabzi"))
        assertNull(OfflineMiniAi.drafts("doodh 150 aur loan 5000"))
        assertNull(OfflineMiniAi.drafts((1..11).joinToString(", ") { "petrol 100" }))
        assertNull(OfflineMiniAi.drafts("doodh -150"))
        assertNull(OfflineMiniAi.drafts("doodh 1,50"))
        assertTrue(runCatching { OfflineMiniAi.drafts("kal doodh 150") }.exceptionOrNull() is AiException)
        assertNull(OfflineMiniAi.drafts("doodh 150 sabzi 100 total 250"))
    }
    @Test fun wordsDoNotMatchInsideNamesAndCategoryOrderIsSpecific() {
        assertEquals("EXPENSE", OfflineMiniAi.drafts("Milad shopping 500")!!.entries.single().type)
        assertEquals("Rent", OfflineMiniAi.drafts("kiraya makan 5000")!!.entries.single().category)
        assertEquals(listOf("Milk", "Snacks", "Vegetables"), OfflineMiniAi.drafts("aj dodh 150 papar 100 sabzi 100")!!.entries.map { it.category })
    }
}
