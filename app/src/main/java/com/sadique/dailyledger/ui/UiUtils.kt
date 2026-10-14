package com.sadique.dailyledger.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

fun money(minor: Long): String = displayMoney(minor)

/** Parses user text such as "1,250.50" into integer paisa. Returns null for invalid input. */
fun parseMinor(text: String): Long? = runCatching {
    text.replace(",", "").trim().toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}.getOrNull()

/** Paisa -> plain decimal string (never scientific notation), e.g. 1250050 -> "12500.50". */
fun plainAmount(minor: Long): String = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2).toPlainString()

fun isValidDate(text: String): Boolean = runCatching { LocalDate.parse(text.trim()) }.isSuccess
fun isValidMonth(text: String): Boolean = runCatching { YearMonth.parse(text.trim()) }.isSuccess
