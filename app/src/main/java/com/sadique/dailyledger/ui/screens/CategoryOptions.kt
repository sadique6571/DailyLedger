package com.sadique.dailyledger.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import com.sadique.dailyledger.ai.OfflineVocabulary
import com.sadique.dailyledger.data.LedgerCategory
import com.sadique.dailyledger.data.TransactionEntity

data class CategoryOptions(val indexed: List<Pair<LedgerCategory, String>>, val frequent: List<String>) {
    companion object {
        fun build(catalog: List<LedgerCategory>, history: List<TransactionEntity>, translate: (String) -> String, customs: List<String> = emptyList()): Map<String, CategoryOptions> =
            listOf("INCOME", "EXPENSE").associateWith { type ->
                val rows = history.filter { it.type == type }
                val customRows = customs.mapNotNull { entry ->
                    val parts = entry.split("|", limit = 2)
                    if (parts.size == 2 && parts[0] == type && parts[1].isNotBlank()) LedgerCategory("Your categories", parts[1], type, emptyList()) else null
                }
                val categories = (catalog.filter { it.type == type } + customRows + rows.map {
                    LedgerCategory("Your categories", it.category, type, emptyList())
                }).filter { it.label.isNotBlank() }.distinctBy { it.label.lowercase(java.util.Locale.ROOT) }
                CategoryOptions(categories.map { category -> category to OfflineVocabulary.normalize(
                    (listOf(category.label, category.group, translate(category.label), translate(category.group)) + category.aliases).joinToString(" ")) },
                    rows.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key })
            }
    }
}
val LocalCategoryOptions = staticCompositionLocalOf<Map<String, CategoryOptions>?> { null }
