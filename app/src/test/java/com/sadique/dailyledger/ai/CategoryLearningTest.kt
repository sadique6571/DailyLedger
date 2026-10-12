package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.TransactionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryLearningTest {
    @Test fun rememberedCorrectionOverridesTheAiSuggestionForMatchingItem() {
        val rules = listOf(LearnedCategoryRule("EXPENSE", "chai", "Tea & coffee"))

        val corrected = CategoryLearning.apply(
            TransactionDraft("EXPENSE", 15_000, "Other", "Chai 150", "2026-10-10"),
            rules,
        )

        assertEquals("Tea & coffee", corrected.category)
    }

    @Test fun mostSpecificCorrectionWinsAndDoesNotCrossTransactionTypes() {
        val rules = listOf(
            LearnedCategoryRule("EXPENSE", "chai", "Tea & coffee"),
            LearnedCategoryRule("EXPENSE", "office chai", "Work meals"),
            LearnedCategoryRule("INCOME", "chai", "Bonus"),
        )

        assertEquals("Work meals", CategoryLearning.match("EXPENSE", "Office chai 200", rules))
        assertEquals("Bonus", CategoryLearning.match("INCOME", "Chai 200", rules))
        assertNull(CategoryLearning.match("EXPENSE", "coffee 200", rules))
    }
}
