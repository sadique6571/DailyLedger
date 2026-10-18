package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.CreditDraft
import java.time.LocalDate

data class CreditAiResult(val message: String, val entries: List<CreditDraft>)

/** Separate offline goods-on-credit parser; it never creates cash expenses or loans. */
object OfflineCreditAi {
    private val merchants = listOf(
        Regex("((?:[\\p{L}][\\p{L}'&.-]*\\s+){0,3}(?:store|shop|mart|dukan|medical|pharmacy))\\s+se\\b", RegexOption.IGNORE_CASE),
        Regex("\\b([\\p{L}][\\p{L}'-]{1,25})\\s+se\\s+(?:udhar|credit)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfrom\\s+([\\p{L}][\\p{L}'&.-]*(?:\\s+(?:store|shop|mart))?)(?=\\s+(?:credit|udhar|due|on)|[,;]|$)", RegexOption.IGNORE_CASE),
    )
    private val filler = Regex("\\b(?:udhar|credit|liya|lia|liye|khareeda|kharida|saman|saaman|from|se|ko|ka|ki|ke|due|dena|deni|pay|payment|jama|ada|clear|wapas)\\b", RegexOption.IGNORE_CASE)
    private val duePrefix = Regex("\\b(?:due|pay|payment|dena|deni|ada|clear)\\s*$", RegexOption.IGNORE_CASE)
    private val dueSuffix = Regex("^\\s*(?:ko\\s+)?(?:dena|deni|ada|pay|due)\\b", RegexOption.IGNORE_CASE)

    fun drafts(input: String, creditorHint: String = "", today: LocalDate = LocalDate.now()): CreditAiResult? {
        if (input.isBlank() || input.length > AiProtocol.MAX_INPUT) return null
        val clean = EntryText.normalize(input)
        if (listOf("loan", "qarz", "kameti", "committee", "salary", "total", "baqi", "balance").any { EntryText.has(clean, it) }) return null
        val dates = EntryDateParser.extract(clean, today)
        val (dueDates, purchaseDates) = dates.partition { date ->
            duePrefix.containsMatchIn(clean.substring(0, date.range.first)) ||
                dueSuffix.containsMatchIn(clean.substring(date.range.last + 1))
        }
        val shops = merchants.flatMap { it.findAll(clean).toList() }.distinctBy { it.range.first }.sortedBy { it.range.first }
        var masked = EntryDateParser.mask(clean, dates.map { it.range } + shops.map { it.range })
        masked = filler.replace(masked) { " ".repeat(it.value.length) }
        val parts = EntryText.parts(masked)?.takeIf { it.size <= 10 } ?: return null
        val rows = parts.map { part ->
            val shop = shops.firstOrNull { it.range.first in part.range }
                ?: shops.singleOrNull() ?: shops.lastOrNull { it.range.last < part.range.first }
            val creditor = shop?.groupValues?.get(1)?.trim().orEmpty().ifBlank { creditorHint.trim() }.ifBlank { "Unknown shop/person" }
            val localDues = dueDates.filter { it.range.first in part.range }
            val due = if (localDues.isNotEmpty()) EntryDateParser.forPart(part.range, localDues, today)
                else dueDates.singleOrNull()?.date ?: dueDates.lastOrNull { it.range.last < part.range.first }?.date
            CreditDraft(creditor, part.label.take(120), part.amountMinor, CategoryRules.infer("EXPENSE", part.label),
                EntryDateParser.forPart(part.range, purchaseDates, today).toString(), due?.toString(), part.label.take(500)).validated()
        }
        val yearHint = if (dates.any { it.yearOmitted }) " Year omitted: ${today.year} used." else ""
        return CreditAiResult("Offline Udhar AI • ${rows.size} records. Shop, item, amount aur purchase/due dates review karein.$yearHint", rows)
    }
}
