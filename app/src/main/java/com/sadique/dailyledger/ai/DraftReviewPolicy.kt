package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.TransactionDraft
import java.time.LocalDate

/** Reasons are observable ambiguities, never a made-up model confidence score. */
object DraftReviewPolicy {
    fun checkMissingDetails(input: String, today: LocalDate = LocalDate.now()) {
        val clean = EntryText.normalize(input)
        val dates = EntryDateParser.extract(clean, today)
        val masked = EntryDateParser.mask(clean, dates.map { it.range })
        val missing = EntryText.missingAmountSegments(masked)
        if (missing.isNotEmpty()) throw AiException("Amount missing for: ${missing.joinToString(", ").take(180)}. Add the amount; nothing was saved.")
        if (masked.none(Char::isLetter)) throw AiException("Item or category is missing. Describe what the amount was for. Nothing was saved.")
    }
    fun issues(input: String, draft: TransactionDraft, cloud: Boolean = false, today: LocalDate = LocalDate.now()): List<String> {
        val dates = EntryDateParser.extract(EntryText.normalize(input), today)
        return buildList {
            if (draft.category in setOf("Other", "Other income")) add("Category unclear — choose the correct category.")
            if (dates.any { it.yearOmitted }) add("Date was incomplete — confirm the assumed month/year.")
            if (Regex("(?<![\\p{L}])(?:kal|کل|parson|parso|پرسوں)(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(input))
                add("Relative date — confirm whether you meant a past or future day.")
            if (cloud) add("Cloud AI draft — check the amount, category and date.")
            if (LocalDate.parse(draft.date) > today) add("Future-dated entry — confirm this date.")
        }.distinct()
    }
    fun total(entries: List<TransactionDraft>, type: String): Long = entries.filter { it.type == type }
        .fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
    fun signature(draft: TransactionDraft): String = listOf(draft.type, draft.amountMinor.toString(), draft.category, draft.note, draft.date)
        .joinToString("|") { "${it.length}:$it" }
}
