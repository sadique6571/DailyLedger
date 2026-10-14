package com.sadique.dailyledger.ai

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Recognize dates before amounts, so day/year digits can never become money. */
internal object EntryDateParser {
    data class Token(val range: IntRange, val date: LocalDate, val yearOmitted: Boolean = false)
    private val months = listOf("jan(?:uary)?", "feb(?:ruary)?", "mar(?:ch)?", "apr(?:il)?", "may", "jun(?:e)?", "jul(?:y)?", "aug(?:ust)?", "sep(?:t(?:ember)?)?", "oct(?:ober)?", "nov(?:ember)?", "dec(?:ember)?")
    private val month = months.joinToString("|")
    private val numeric = Regex("(?<![\\p{L}\\d])(?:\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})(?!\\d)")
    private val dayFirst = Regex("(?<![\\p{L}\\d])(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($month)(?![\\p{L}])(?:[ ,]+((?:19|20)\\d{2})(?!\\d))?", RegexOption.IGNORE_CASE)
    private val monthFirst = Regex("(?<![\\p{L}])($month)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)(?:[ ,]+((?:19|20)\\d{2})(?!\\d))?", RegexOption.IGNORE_CASE)
    private val relative = Regex("(?<![\\p{L}\\d])(today|aaj|aj|آج|yesterday|tomorrow)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)

    private val weekdays = listOf("monday|mon|peer|pir|پیر", "tuesday|tue|mangal|منگل", "wednesday|wed|budh|بدھ", "thursday|thu|jumerat|jumeraat|جمعرات", "friday|fri|jumma|juma|جمعہ", "saturday|sat|hafta|ہفتہ", "sunday|sun|itwar|اتوار")
    private val namedDay = Regex("(?<![\\p{L}])(?:last|previous|pichlay|pichle|pichli|guzishta|next|aglay|agle|agli)\\s+(" + weekdays.joinToString("|") + ")(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val daysAgo = Regex("(?<![\\p{L}\\d])(\\d{1,3}|aik|ek|one|do|two|teen|three|char|four|panch|five|saat|seven)\\s+(din|days?|haftay|hafte|weeks?)\\s+(pehle|pahle|ago|baad|later)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val clearYesterday = Regex("(?<![\\p{L}])(beeta kal|guzra kal|pichla din|guzishta din)(?![\\p{L}])", RegexOption.IGNORE_CASE)

    fun extract(text: String, today: LocalDate): List<Token> {
        val tokens = mutableListOf<Token>()
        fun add(match: MatchResult, omitted: Boolean = false, build: () -> LocalDate) {
            if (tokens.any { it.range.first <= match.range.last && match.range.first <= it.range.last }) return
            val date = runCatching(build).getOrElse { throw AiException("Date '${match.value}' is invalid. Use YYYY-MM-DD or DD/MM/YYYY. Nothing was saved.") }
            tokens += Token(match.range, date, omitted)
        }
        numeric.findAll(text).forEach { m -> add(m) {
            val parts = m.value.split('/', '.', '-').map(String::toInt)
            if (m.value.substringBefore('-').length == 4 && '-' in m.value) LocalDate.of(parts[0], parts[1], parts[2])
            else LocalDate.of(if (parts[2] < 100) parts[2] + 2000 else parts[2], parts[1], parts[0])
        } }
        fun monthNumber(word: String) = months.indexOfFirst { Regex(it, RegexOption.IGNORE_CASE).matches(word) } + 1
        dayFirst.findAll(text).forEach { m -> add(m, m.groupValues[3].isBlank()) {
            LocalDate.of(m.groupValues[3].toIntOrNull() ?: today.year, monthNumber(m.groupValues[2]), m.groupValues[1].toInt())
        } }
        monthFirst.findAll(text).forEach { m -> add(m, m.groupValues[3].isBlank()) {
            LocalDate.of(m.groupValues[3].toIntOrNull() ?: today.year, monthNumber(m.groupValues[1]), m.groupValues[2].toInt())
        } }
        namedDay.findAll(text).forEach { m -> add(m) {
            val day = DayOfWeek.of(weekdays.indexOfFirst { Regex(it, RegexOption.IGNORE_CASE).matches(m.groupValues[1]) } + 1)
            val next = Regex("^(?:next|aglay|agle|agli)", RegexOption.IGNORE_CASE).containsMatchIn(m.value)
            today.with(if (next) TemporalAdjusters.next(day) else TemporalAdjusters.previous(day))
        } }
        daysAgo.findAll(text).forEach { m -> add(m) {
            val count = m.groupValues[1].toLongOrNull() ?: when (m.groupValues[1].lowercase(Locale.ROOT)) {
                "aik", "ek", "one" -> 1L; "do", "two" -> 2L; "teen", "three" -> 3L; "char", "four" -> 4L; "panch", "five" -> 5L; else -> 7L
            }
            val days = count * if (m.groupValues[2].lowercase(Locale.ROOT).startsWith("haft") || m.groupValues[2].lowercase(Locale.ROOT).startsWith("week")) 7 else 1
            if (m.groupValues[3].lowercase(Locale.ROOT) in listOf("baad", "later")) today.plusDays(days) else today.minusDays(days)
        } }
        clearYesterday.findAll(text).forEach { m -> add(m) { today.minusDays(1) } }
        relative.findAll(text).forEach { m -> add(m) {
            when (m.value.lowercase(Locale.ROOT)) { "yesterday" -> today.minusDays(1); "tomorrow" -> today.plusDays(1); else -> today }
        } }
        val remaining = mask(text, tokens.map { it.range })
        if (EntryText.has(remaining, "kal") || EntryText.has(remaining, "کل") || EntryText.has(remaining, "parson") || EntryText.has(remaining, "پرسوں")) {
            throw AiException("'Kal/parson' is ambiguous. Write an exact date, yesterday/tomorrow, or '2 din pehle', then review again.")
        }
        if (Regex("\\d[./-]\\d|\\d{1,2}(?:st|nd|rd|th)\\b", RegexOption.IGNORE_CASE).containsMatchIn(remaining.replace(Regex("\\d+\\.\\d{1,2}(?!\\d)"), ""))) {
            throw AiException("A date is incomplete or unclear. Write YYYY-MM-DD or DD/MM/YYYY. Nothing was saved.")
        }
        return tokens.sortedBy { it.range.first }
    }

    fun mask(text: String, ranges: List<IntRange>): String = text.toCharArray().also { chars ->
        ranges.forEach { range -> range.forEach { if (it in chars.indices) chars[it] = ' ' } }
    }.concatToString()

    fun forPart(range: IntRange, dates: List<Token>, today: LocalDate): LocalDate {
        val inside = dates.filter { it.range.first in range }.map { it.date }.distinct()
        if (inside.size > 1) throw AiException("More than one date matches an entry. Separate the entries and review each date.")
        return inside.singleOrNull() ?: dates.lastOrNull { it.range.last < range.first }?.date
            ?: dates.singleOrNull()?.date ?: today
    }
}
