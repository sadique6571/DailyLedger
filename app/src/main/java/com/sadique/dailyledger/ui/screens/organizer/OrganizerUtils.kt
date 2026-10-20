package com.sadique.dailyledger.ui.screens

import androidx.compose.runtime.Composable
import com.sadique.dailyledger.ui.visibleAmount

internal fun shareLabel(units: Int) = if (units == 1) "0.5" else "1"

internal fun organizerWhatsApp(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}

@Composable
internal fun rowMoney(amount: Long) = visibleAmount(amount)
