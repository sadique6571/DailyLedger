package com.sadique.dailyledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small, consistent haptic taps for key ledger actions: a light tick for
 * navigation and a firmer press when something is saved or removed.
 * Uses only long-standing haptic types so it works on every supported device.
 */
class LedgerHaptics(private val feedback: HapticFeedback) {
    /** Light tick: tab switches and small selections. */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Firm confirmation: an entry was saved or an item was removed. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberLedgerHaptics(): LedgerHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { LedgerHaptics(feedback) }
}
