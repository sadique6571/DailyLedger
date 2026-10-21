package com.sadique.dailyledger.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

fun money(minor: Long): String = displayMoney(minor)

/** Presentation-only masking for shared planning/report screens. */
@androidx.compose.runtime.Composable
fun visibleAmount(minor: Long): String = if (LocalDisplayPreferences.current?.privateAmounts == true) "Rs •••••" else money(minor)

/** Parses user text such as "1,250.50" into integer paisa. Returns null for invalid input. */
fun parseMinor(text: String): Long? = runCatching {
    text.replace(",", "").trim().toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}.getOrNull()

/**
 * Like [parseMinor] but also accepts a simple +/- chain of amounts, e.g.
 * "1200+350-100" — handy when an entry is made of several parts. A single
 * plain amount behaves exactly like [parseMinor]; invalid input returns null.
 */
fun parseAmountExpression(raw: String): Long? {
    val text = raw.replace(",", "").trim()
    if (text.isEmpty()) return null
    if (text.none { it == '+' || it == '-' }) return parseMinor(text)
    var total = 0L
    var sign = 1L
    var index = 0
    if (text[0] == '-') { sign = -1L; index = 1 } else if (text[0] == '+') { index = 1 }
    val current = StringBuilder()
    fun flush(): Boolean {
        val part = current.toString()
        if (part.isEmpty()) return false
        val value = parseMinor(part) ?: return false
        total += sign * value
        current.clear()
        return true
    }
    while (index < text.length) {
        val c = text[index]
        if (c == '+' || c == '-') {
            if (!flush()) return null
            sign = if (c == '-') -1L else 1L
        } else current.append(c)
        index++
    }
    if (!flush()) return null
    return total
}

/** Paisa -> plain decimal string (never scientific notation), e.g. 1250050 -> "12500.50". */
fun plainAmount(minor: Long): String = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2).toPlainString()

fun isValidDate(text: String): Boolean = runCatching { LocalDate.parse(text.trim()) }.isSuccess
fun isValidMonth(text: String): Boolean = runCatching { YearMonth.parse(text.trim()) }.isSuccess
