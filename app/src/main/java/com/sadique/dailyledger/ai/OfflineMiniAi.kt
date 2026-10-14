package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.TransactionDraft
import java.time.LocalDate

/** Offline date/category extraction. Ambiguous batches are never partially saved. */
object OfflineMiniAi {
    private val incomeWords = listOf("salary", "tankhwa", "تنخواہ", "income", "bonus", "profit", "aamdani", "آمدنی", "kamaya", "kamai", "earned", "refund", "pension", "overtime", "commission", "freelance", "rental income", "gift received")
    private val expenseWords = listOf("kharcha", "spent", "paid", "diya", "kharida", "kharidi", "bought", "purchase", "خرید", "خرچ")
    private val receivedWords = listOf("mili", "mila", "received", "aaye", "aye", "ayi")

    private fun entryType(label: String): String? {
        val words = OfflineVocabulary.normalize(label)
        val income = incomeWords.any { EntryText.has(words, it) }
        val expense = expenseWords.any { EntryText.has(words, it) }
        if (income && expense) return null
        if (expense) return "EXPENSE"
        if (income) return "INCOME"
        // Receiving goods ("doodh 150 ka mila") is still a purchase.
        if (CategoryRules.infer("EXPENSE", words) != "Other") return "EXPENSE"
        if (receivedWords.any { EntryText.has(words, it) }) {
            return if (listOf("paise", "paisay", "raqam", "money", "cash", "رقم", "پیسے").any { EntryText.has(words, it) }) "INCOME" else null
        }
        return "EXPENSE"
    }

    fun usesDedicatedLedger(input: String) = CategoryRules.isDedicatedLedgerText(input)

    fun drafts(input: String, today: LocalDate = LocalDate.now()): AiDraftResult? {
        if (input.isBlank() || input.length > AiProtocol.MAX_INPUT || usesDedicatedLedger(input)) return null
        val clean = EntryText.normalize(input)
        if (clean.none(Char::isDigit)) return null
        val dates = EntryDateParser.extract(clean, today)
        val masked = EntryDateParser.mask(clean, dates.map { it.range })
        if (listOf("total", "baqi", "balance").any { EntryText.has(masked, it) }) return null
        val pieces = EntryText.parts(masked) ?: return null
        val rows = pieces.map { part ->
            val type = entryType(part.label) ?: return null
            TransactionDraft(type, part.amountMinor, CategoryRules.infer(type, part.label), part.label.take(500),
                EntryDateParser.forPart(part.range, dates, today).toString()).validated()
        }
        val reviewHint = if (rows.any { it.category == "Other" || it.category == "Other income" }) " Unrecognized item: choose its category before saving." else ""
        val yearHint = if (dates.any { it.yearOmitted }) " Year omitted: ${today.year} used." else ""
        return AiDraftResult("Offline Mini AI • ${rows.size} entries. Amount, category aur har entry ki date review karein.$yearHint$reviewHint", rows)
    }
}
