package com.sadique.dailyledger.ai

import java.util.Locale

/**
 * The exact item name of an entry. The review screen shows the draft's note
 * as its title, so the note IS the item name — it must stay the real item,
 * never the category label, never the amount, never a sentence.
 */
internal object ItemNames {
    private val generic = setOf(
        "expense", "income", "item", "purchase", "payment", "entry",
        "kharcha", "other", "other income",
    )
    private val trailingAmount = Regex(
        "(?i)\\s+\\d[\\d,]*(?:\\.\\d{1,2})?\\s*(rs\\.?|pkr|/-)?\\s*$"
    )
    private val leadingCurrency = Regex("(?i)^(rs\\.?|pkr)\\s+")

    /** Strip a trailing amount/currency tail; keep everything else verbatim. */
    fun cleanNote(note: String): String = note.trim()
        .replace(trailingAmount, "")
        .replace(leadingCurrency, "")
        .replace(Regex("\\s+"), " ").trim().take(500)

    private fun isGeneric(name: String, category: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return n.isBlank() || n == category.lowercase(Locale.ROOT) || n in generic
    }

    /**
     * The item name to learn and display for a draft, or null when there is
     * no real item name (blank/generic note and no usable input wording).
     * For a single-entry result the input's own wording is the fallback when
     * the model note is generic — the model must never invent a name, and
     * neither do we: a null result simply means "do not learn an item".
     */
    fun extractItemName(
        note: String,
        input: String?,
        singleEntry: Boolean,
        category: String,
    ): String? {
        var candidate = cleanNote(note)
        if (candidate.isBlank() || isGeneric(candidate, category)) {
            candidate = ""
            if (singleEntry && !input.isNullOrBlank()) {
                candidate = EntryText.parts(input)?.singleOrNull()?.label?.let { cleanNote(it) } ?: ""
            }
        }
        if (candidate.isBlank() || isGeneric(candidate, category)) return null
        return candidate.take(60).takeIf { it.any(Char::isLetter) }
    }
}

/** Apply exact-item-name rules to every draft of a decoded result. */
internal fun AiDraftResult.finalizeNames(input: String): AiDraftResult {
    val single = entries.size == 1
    return copy(entries = entries.map { draft ->
        val name = ItemNames.extractItemName(draft.note, input, single, draft.category)
        if (name == null) draft else draft.copy(note = name).validated()
    })
}
