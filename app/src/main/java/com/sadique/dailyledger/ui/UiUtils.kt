package com.sadique.dailyledger.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

fun money(minor: Long): String = displayMoney(minor)

private val WORDS_ONES = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
private val WORDS_TENS = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

private fun wordsBelowHundred(n: Int): String = when {
    n < 20 -> WORDS_ONES[n]
    else -> listOf(WORDS_TENS[n / 10], WORDS_ONES[n % 10]).filter { it.isNotBlank() }.joinToString(" ")
}

private fun wordsBelowThousand(n: Int): String {
    val h = n / 100; val rest = n % 100
    return listOf(if (h > 0) "${WORDS_ONES[h]} Hundred" else "", wordsBelowHundred(rest)).filter { it.isNotBlank() }.joinToString(" ")
}

/** Cheque-style amount in words with Pakistani numbering (Thousand, Lakh, Crore). Input is minor units (paisa). */
fun amountInWords(minor: Long): String {
    if (minor <= 0) return ""
    val rupees = minor / 100
    val paisa = (minor % 100).toInt()
    if (rupees == 0L) return "$paisa Paisa Only"
    val crore = (rupees / 10_000_000).toInt()
    val lakh = ((rupees / 100_000) % 100).toInt()
    val thousand = ((rupees / 1_000) % 100).toInt()
    val rest = (rupees % 1_000).toInt()
    val parts = mutableListOf<String>()
    if (crore > 0) parts += "${wordsBelowHundred(crore)} Crore"
    if (lakh > 0) parts += "${wordsBelowHundred(lakh)} Lakh"
    if (thousand > 0) parts += "${wordsBelowHundred(thousand)} Thousand"
    if (rest > 0) parts += wordsBelowThousand(rest)
    val base = parts.joinToString(" ") + if (rupees == 1L) " Rupee" else " Rupees"
    return if (paisa > 0) "$base and $paisa Paisa Only" else "$base Only"
}

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
