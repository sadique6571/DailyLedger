package com.sadique.dailyledger.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Local, UI-only preferences. Does not touch ledger data, backup, or database. */
class DisplayPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("display_preferences_v1", Context.MODE_PRIVATE)
    var language by mutableStateOf(store.getString("language", "en").let { if (it == "ur") "ur" else "en" })
        private set
    fun updateLanguage(value: String) { language = if (value == "ur") "ur" else "en"; store.edit().putString("language", language).apply() }
    var dashboardOrder by mutableStateOf(normalizeOrder(store.getString("dashboard_order", "")?.split(",").orEmpty()))
        private set
    fun updateDashboardOrder(order: List<String>) { dashboardOrder = normalizeOrder(order); store.edit().putString("dashboard_order", dashboardOrder.joinToString(",")).apply() }
    fun resetDashboardOrder() = updateDashboardOrder(DEFAULT_ORDER)
    companion object {
        val DEFAULT_ORDER = listOf("greeting", "balance", "monthly-totals", "salary-cycle-controls", "budget-notice", "spending", "frequent-items", "recent", "insights-shortcut", "separate-totals", "udhar-shortcut", "insights")
        fun normalizeOrder(order: List<String>) = (order.filter { it in DEFAULT_ORDER } + DEFAULT_ORDER).distinct()
    }
    // One compact style for everyone; the phone's own accessibility font scale still applies.
    val fontSize = 1
    val iconSize = 1
    val cardSize = 0
    val compact = true
    var secureScreens by mutableStateOf(store.getBoolean("secure_screens", false))
        private set
    var privateAmounts by mutableStateOf(store.getBoolean("private_amounts", false))
        private set
    var showSpending by mutableStateOf(store.getBoolean("spending", true))
        private set
    var showFrequent by mutableStateOf(store.getBoolean("frequent", true))
        private set
    var showRecent by mutableStateOf(store.getBoolean("recent", true))
        private set
    var showSpaces by mutableStateOf(store.getBoolean("spaces", true))
        private set
    var showInsights by mutableStateOf(store.getBoolean("insights", true))
        private set
    var savingsFirst by mutableStateOf(store.getBoolean("savings_first", true))
        private set

    fun updateSecureScreens(value: Boolean) { secureScreens = value; store.edit().putBoolean("secure_screens", value).apply() }
    fun updatePrivateAmounts(value: Boolean) { privateAmounts = value; store.edit().putBoolean("private_amounts", value).apply() }
    fun updateShowSpending(value: Boolean) { showSpending = value; store.edit().putBoolean("spending", value).apply() }
    fun updateShowFrequent(value: Boolean) { showFrequent = value; store.edit().putBoolean("frequent", value).apply() }
    fun updateShowRecent(value: Boolean) { showRecent = value; store.edit().putBoolean("recent", value).apply() }
    fun updateShowSpaces(value: Boolean) { showSpaces = value; store.edit().putBoolean("spaces", value).apply() }
    fun updateShowInsights(value: Boolean) { showInsights = value; store.edit().putBoolean("insights", value).apply() }
    fun updateSavingsFirst(value: Boolean) { savingsFirst = value; store.edit().putBoolean("savings_first", value).apply() }
    fun reset() {
        updateSecureScreens(false); updatePrivateAmounts(false); updateShowSpending(true); updateShowFrequent(true); updateShowRecent(true)
        updateShowSpaces(true); updateShowInsights(true); updateSavingsFirst(true); resetDashboardOrder()
    }
    val fontMultiplier: Float get() = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)[fontSize]
    val iconMultiplier: Float get() = floatArrayOf(0.85f, 1f, 1.2f)[iconSize]
    val cardPadding: Int get() = if (compact) 10 else intArrayOf(10, 16, 22)[cardSize]
}
val LocalDisplayPreferences = staticCompositionLocalOf<DisplayPreferences?> { null }
