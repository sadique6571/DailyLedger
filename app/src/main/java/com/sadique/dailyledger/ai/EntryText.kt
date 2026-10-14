package com.sadique.dailyledger.ai

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal object EntryText {
    private val amount = Regex("(?<![\\p{L}\\d.\\-])(\\d{1,11}(?:\\.\\d{1,2})?)(?:\\s*(k|thousand|hazar|hazaar|ہزار|lakh|lac|لاکھ))?(?![\\p{L}\\d.])", RegexOption.IGNORE_CASE)
    private val grouped = Regex("(?<![\\d.,])\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?(?![\\d.]|,\\d)")
    private val separators = Regex("[,;،\\n]+|\\s+(?:aur|and|phir|plus|اور)\\s+", RegexOption.IGNORE_CASE)
    private val quantity = Regex("(?<![\\p{L}\\d.])(\\d+(?:\\.\\d+)?)\\s*(?:kilograms?|kilos?|kgs?|grams?|g|litres?|liters?|ltr|l|ml|dozens?|darjan|packets?|packs?|pcs|pieces?|bottles?|کلو|گرام|لیٹر|درجن|پیکٹ)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    fun withoutQuantities(text: String): String = quantity.replace(text) { " ".repeat(it.value.length) }
    data class Part(val range: IntRange, val label: String, val amountMinor: Long)
    fun has(text: String, word: String) = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
    fun normalize(input: String): String {
        val digits = input.trim().map { c -> Character.digit(c, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: c }.joinToString("")
        return grouped.replace(digits) { it.value.replace(",", "") }
    }
    fun parts(masked: String): List<Part>? {
        if (Regex("\\d,\\d|[-−]\\s*\\d").containsMatchIn(masked)) return null
        if (Regex("(?:@|×|\\b(?:per|each|fi|فی)\\b)", RegexOption.IGNORE_CASE).containsMatchIn(masked)) return null
        val out = mutableListOf<Part>()
        val ranges = mutableListOf<IntRange>()
        var start = 0
        separators.findAll(masked).forEach { split -> ranges += start until split.range.first; start = split.range.last + 1 }
        ranges += start until masked.length
        for (range in ranges) {
            if (range.isEmpty()) continue
            val raw = masked.substring(range)
            val matches = amount.findAll(withoutQuantities(raw)).toList()
            if (matches.isEmpty()) {
                // Date-only separators are permitted; unknown/missing-amount entries are not discarded.
                if (raw.replace(Regex("\\b(?:on|ko|date)\\b", RegexOption.IGNORE_CASE), "").isBlank()) continue
                return null
            }
            var offset = 0
            matches.forEachIndexed { index, m ->
                val end = if (index == matches.lastIndex) raw.length else m.range.last + 1
                val label = (raw.substring(offset, m.range.first) + " " + raw.substring(m.range.last + 1, end))
                    .replace(Regex("\\b(?:rs|pkr|rupees|rupay|rupaye|on|ko|date)\\b", RegexOption.IGNORE_CASE), " ").trim(' ', ':', '-')
                if (label.isBlank() || label.none(Char::isLetter)) return null
                val factor = when (m.groupValues[2].lowercase(Locale.ROOT)) {
                    "k", "thousand", "hazar", "hazaar", "ہزار" -> 1_000L
                    "lakh", "lac", "لاکھ" -> 100_000L
                    else -> 1L
                }
                val minor = runCatching { BigDecimal(m.groupValues[1]).multiply(BigDecimal.valueOf(factor)).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                    ?.takeIf { it in 1..1_000_000_000_000L } ?: return null
                out += Part((range.first + offset) until (range.first + end), label.replace(Regex("\\s+"), " "), minor)
                offset = m.range.last + 1
            }
        }
        return out.takeIf { it.size in 1..10 }
    }
}
