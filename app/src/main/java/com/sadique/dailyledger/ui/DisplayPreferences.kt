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
        val DEFAULT_ORDER = listOf("greeting", "balance", "backup-status", "monthly-totals", "salary-cycle-controls", "budget-notice", "spending", "frequent-items", "recent", "insights-shortcut", "separate-totals", "udhar-shortcut", "insights")
        fun normalizeOrder(order: List<String>) = (order.filter { it in DEFAULT_ORDER } + DEFAULT_ORDER).distinct()
    }
    // Text size is user-selectable in Settings (0=Small, 1=Normal, 2=Large, 3=Extra large);
    // the phone's own accessibility font scale still applies on top.
    var fontSize by mutableStateOf(store.getInt("font_size", 1).coerceIn(0, 3))
        private set
    fun updateFontSize(value: Int) { fontSize = value.coerceIn(0, 3); store.edit().putInt("font_size", fontSize).apply() }
    // Optional user accent color (ARGB int; 0 = use the theme's own colors).
    var accentColor by mutableStateOf(store.getInt("accent_color", 0))
        private set
    fun updateAccent(value: Int) { accentColor = value; store.edit().putInt("accent_color", value).apply() }
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
    // First-run intro: shown once per install until the user finishes or skips it.
    var onboardingSeen by mutableStateOf(store.getBoolean("onboarding_seen", false))
        private set
    fun updateOnboardingSeen(value: Boolean) { onboardingSeen = value; store.edit().putBoolean("onboarding_seen", value).apply() }
    // Savings goal (target amount + optional label), shown on the Savings screen. UI-only; not part of ledger data.
    var savingsGoalMinor by mutableStateOf(store.getLong("savings_goal_minor", 0L))
        private set
    var savingsGoalLabel by mutableStateOf(store.getString("savings_goal_label", "") ?: "")
        private set
    fun updateSavingsGoal(minor: Long, label: String) { savingsGoalMinor = minor.coerceAtLeast(0L); savingsGoalLabel = label; store.edit().putLong("savings_goal_minor", savingsGoalMinor).putString("savings_goal_label", label).apply() }

    fun updateSecureScreens(value: Boolean) { secureScreens = value; store.edit().putBoolean("secure_screens", value).apply() }
    fun updatePrivateAmounts(value: Boolean) { privateAmounts = value; store.edit().putBoolean("private_amounts", value).apply() }
    fun updateShowSpending(value: Boolean) { showSpending = value; store.edit().putBoolean("spending", value).apply() }
    fun updateShowFrequent(value: Boolean) { showFrequent = value; store.edit().putBoolean("frequent", value).apply() }
    fun updateShowRecent(value: Boolean) { showRecent = value; store.edit().putBoolean("recent", value).apply() }
    fun updateShowSpaces(value: Boolean) { showSpaces = value; store.edit().putBoolean("spaces", value).apply() }
    fun updateShowInsights(value: Boolean) { showInsights = value; store.edit().putBoolean("insights", value).apply() }
    fun updateSavingsFirst(value: Boolean) { savingsFirst = value; store.edit().putBoolean("savings_first", value).apply() }
    fun reset() {
        updateFontSize(1); updateAccent(0)
        updateSecureScreens(false); updatePrivateAmounts(false); updateShowSpending(true); updateShowFrequent(true); updateShowRecent(true)
        updateShowSpaces(true); updateShowInsights(true); updateSavingsFirst(true); resetDashboardOrder()
    }
    val fontMultiplier: Float get() = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)[fontSize]
    val iconMultiplier: Float get() = floatArrayOf(0.85f, 1f, 1.2f)[iconSize]
    val cardPadding: Int get() = if (compact) 10 else intArrayOf(10, 16, 22)[cardSize]
}
val LocalDisplayPreferences = staticCompositionLocalOf<DisplayPreferences?> { null }
