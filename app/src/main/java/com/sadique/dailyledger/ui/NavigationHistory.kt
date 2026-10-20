package com.sadique.dailyledger.ui

/** Real screen history, including nested search/Settings/AI paths. Bottom tabs start a new path. */
data class NavigationHistory(val entries: List<Int> = listOf(HOME)) {
    init { require(entries.isNotEmpty() && entries.first() == HOME && entries.all { it in HOME..FINANCE_TOOLS }) }
    val current get() = entries.last()
    val parent get() = entries.dropLast(1).lastOrNull()
    val canGoBack get() = entries.size > 1
    fun open(destination: Int) = if (current == destination) this else copy(entries = entries + destination)
    fun selectTab(destination: Int) = NavigationHistory(if (destination == HOME) listOf(HOME) else listOf(HOME, destination))
    fun back() = if (canGoBack) copy(entries = entries.dropLast(1)) else this
    companion object {
        const val HOME = 0
        const val TRANSACTIONS = 1
        const val LOANS = 2
        const val KAMETI = 3
        const val SAVINGS = 4
        const val INSIGHTS = 5
        const val MORE = 6
        const val UDHAR = 7
        const val SETTINGS = 8
        const val AUTO_FILL = 9
        const val BUDGETS = 10
        const val BILLS = 11
        const val CALENDAR = 12
        const val COMPARISON = 13
        const val SEARCH = 14
        const val FINANCE_TOOLS = 16
        const val LEARNING = 15
        fun label(route: Int?) = when (route) {
            TRANSACTIONS -> "Transactions"; LOANS -> "Loans"; KAMETI -> "Kameti"; SAVINGS -> "Savings"
            INSIGHTS -> "Insights"; MORE -> "More"; UDHAR -> "Udhar"; SETTINGS -> "Settings"
            AUTO_FILL -> "AI Auto Fill"; BUDGETS -> "Category budgets"; BILLS -> "Bills & reminders"
            CALENDAR -> "Kameti calendar"; COMPARISON -> "Monthly comparison"; SEARCH -> "Search all ledgers"
            FINANCE_TOOLS -> "Money tools"; LEARNING -> "AI learning"; else -> "Home"
        }
    }
}
