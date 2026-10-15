package com.sadique.dailyledger.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Local, UI-only preferences. Does not touch ledger data, backup, or database. */
class DisplayPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("display_preferences_v1", Context.MODE_PRIVATE)
    var fontSize by mutableStateOf(store.getInt("font", 1).coerceIn(0, 3))
        private set
    var iconSize by mutableStateOf(store.getInt("icons", 1).coerceIn(0, 2))
        private set
    var cardSize by mutableStateOf(store.getInt("cards", 1).coerceIn(0, 2))
        private set
    var compact by mutableStateOf(store.getBoolean("compact", false))
        private set
    var privateAmounts by mutableStateOf(store.getBoolean("private_amounts", false))
        private set
    var showSpending by mutableStateOf(store.getBoolean("spending", true))
        private set
    var showRecent by mutableStateOf(store.getBoolean("recent", true))
        private set
    var showSpaces by mutableStateOf(store.getBoolean("spaces", true))
        private set
    var showInsights by mutableStateOf(store.getBoolean("insights", true))
        private set
    var savingsFirst by mutableStateOf(store.getBoolean("savings_first", true))
        private set

    fun setFontSize(value: Int) { fontSize = value.coerceIn(0, 3); store.edit().putInt("font", fontSize).apply() }
    fun setIconSize(value: Int) { iconSize = value.coerceIn(0, 2); store.edit().putInt("icons", iconSize).apply() }
    fun setCardSize(value: Int) { cardSize = value.coerceIn(0, 2); store.edit().putInt("cards", cardSize).apply() }
    fun setCompact(value: Boolean) { compact = value; store.edit().putBoolean("compact", value).apply() }
    fun setPrivateAmounts(value: Boolean) { privateAmounts = value; store.edit().putBoolean("private_amounts", value).apply() }
    fun setShowSpending(value: Boolean) { showSpending = value; store.edit().putBoolean("spending", value).apply() }
    fun setShowRecent(value: Boolean) { showRecent = value; store.edit().putBoolean("recent", value).apply() }
    fun setShowSpaces(value: Boolean) { showSpaces = value; store.edit().putBoolean("spaces", value).apply() }
    fun setShowInsights(value: Boolean) { showInsights = value; store.edit().putBoolean("insights", value).apply() }
    fun setSavingsFirst(value: Boolean) { savingsFirst = value; store.edit().putBoolean("savings_first", value).apply() }
    fun reset() {
        setFontSize(1); setIconSize(1); setCardSize(1); setCompact(false)
        setPrivateAmounts(false); setShowSpending(true); setShowRecent(true)
        setShowSpaces(true); setShowInsights(true); setSavingsFirst(true)
    }
    val fontMultiplier: Float get() = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)[fontSize]
    val iconMultiplier: Float get() = floatArrayOf(0.85f, 1f, 1.2f)[iconSize]
    val cardPadding: Int get() = if (compact) 10 else intArrayOf(10, 16, 22)[cardSize]
}
val LocalDisplayPreferences = staticCompositionLocalOf<DisplayPreferences?> { null }
