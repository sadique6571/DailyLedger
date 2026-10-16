package com.sadique.dailyledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope

/** Keeps item keys stable while rendering the user's saved dashboard order. */
class DashboardSections {
    private data class Section(val key: Any, val index: Int, val content: @Composable LazyItemScope.() -> Unit)
    private val sections = mutableListOf<Section>()
    fun enteringItem(key: Any? = null, index: Int = 0, content: @Composable LazyItemScope.() -> Unit) {
        sections += Section(key ?: "section-${sections.size}", index, content)
    }
    fun render(scope: LazyListScope, order: List<String>) {
        val ranking = order.withIndex().associate { it.value to it.index }
        sections.sortedBy { ranking[it.key] ?: Int.MAX_VALUE }.forEach { section ->
            scope.enteringItem(section.key, section.index, section.content)
        }
    }
}
fun dashboardSectionLabel(key: String) = when (key) {
    "greeting" -> "Greeting & weather"; "balance" -> "Remaining balance"; "monthly-totals" -> "Salary & expenses"
    "salary-cycle-controls" -> "Salary cycle options"; "budget-notice" -> "Budgets & reminders"
    "spending" -> "Spending chart"; "frequent-items" -> "Frequent purchases"; "recent" -> "Recent transactions"
    "insights-shortcut" -> "Insights shortcut"; "separate-totals" -> "Your money spaces"; "udhar-shortcut" -> "Udhar shortcut"
    "insights" -> "Insights"; else -> key
}
