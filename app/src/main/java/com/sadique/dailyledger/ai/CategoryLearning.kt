package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.data.TransactionDraft
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.security.SecureSecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LearnedCategoryRule(val type: String, val phrase: String, val category: String)

object CategoryLearning {
    fun normalize(text: String): String = OfflineVocabulary.normalize(EntryText.withoutQuantities(text))
        .replace(Regex("\\b(?:aaj|aj|kal|parson|parso|today|yesterday|tomorrow|subah|raat|آج|کل|پرسوں|خریدا|خریدی|لیا|لیے|bought|buy|liya|lia|liye|kharida|kharidi|kharcha|spent|paid|diya|ka|ki|ke|ko|se|hai|mila|mili|rs|pkr|rupees|rupay|rupaye)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\b\\d+\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun match(type: String, text: String, rules: List<LearnedCategoryRule>): String? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        return rules.asSequence()
            .filter { it.type == type }
            .map { it to normalize(it.phrase) }
            .filter { (_, phrase) -> phrase.isNotBlank() && phrase.split(" ").all { " $normalized ".contains(" $it ") } }
            .maxByOrNull { (_, phrase) -> phrase.length }
            ?.first?.category
    }

    /** Learn only repeated, consistently categorized saved items. Explicit corrections win. */
    fun fromHistory(transactions: List<TransactionEntity>, ownerId: String): List<LearnedCategoryRule> =
        transactions.asSequence().filter {
            it.ownerId == ownerId && it.note.isNotBlank() && it.type in listOf("INCOME", "EXPENSE") &&
                it.category.isNotBlank() && it.category !in listOf("Other", "Other income")
        }.sortedByDescending { it.createdAt }.take(3000)
            .groupBy { it.type to normalize(it.note) }
            .mapNotNull { (key, rows) ->
                val (type, phrase) = key
                if (phrase.isBlank() || rows.size < 2 || rows.map { it.category }.distinct().size != 1) null
                else LearnedCategoryRule(type, phrase, rows.first().category)
            }.take(200)

    fun apply(draft: TransactionDraft, rules: List<LearnedCategoryRule>, history: List<LearnedCategoryRule> = emptyList()): TransactionDraft {
        val learned = match(draft.type, draft.note, rules) ?: match(draft.type, draft.note, history) ?: return draft
        return draft.copy(category = learned).validated()
    }
}

class CategoryLearningStore(context: Context, ownerId: String) {
    private val storage = SecureSecretStore(context.applicationContext, "category-learning:$ownerId")

    fun rules(): List<LearnedCategoryRule> {
        val bytes = storage.loadBytes() ?: return emptyList()
        return try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            require(root.getInt("version") == 1) { "Saved category learning has an unsupported version." }
            val rows = root.getJSONArray("rules")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                LearnedCategoryRule(
                    row.getString("type"),
                    row.getString("phrase"),
                    row.getString("category"),
                ).also {
                    require(it.type == "INCOME" || it.type == "EXPENSE")
                    require(it.phrase.isNotBlank() && it.category.isNotBlank())
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun remember(type: String, phrase: String, category: String) {
        require(type == "INCOME" || type == "EXPENSE") { "Choose income or expense before teaching a category." }
        val normalizedPhrase = CategoryLearning.normalize(phrase).take(120)
        require(normalizedPhrase.isNotBlank()) { "Add a short item name or note so this category can be remembered." }
        val cleanCategory = category.trim().take(60)
        require(cleanCategory.isNotBlank()) { "Choose a category to remember." }

        val updated = listOf(LearnedCategoryRule(type, normalizedPhrase, cleanCategory)) +
            rules().filterNot { it.type == type && CategoryLearning.normalize(it.phrase) == normalizedPhrase }
        writeRules(updated.take(MAX_RULES))
    }

    fun forget(type: String, phrase: String) {
        writeRules(rules().filterNot { it.type == type && CategoryLearning.normalize(it.phrase) == CategoryLearning.normalize(phrase) })
        val hidden = suppressed().toMutableSet(); hidden += "$type:${CategoryLearning.normalize(phrase)}"
        writeSuppressed(hidden)
    }
    private val suppressedStorage = SecureSecretStore(context.applicationContext, "category-learning-hidden:$ownerId")
    fun suppressed(): Set<String> {
        val bytes = suppressedStorage.loadBytes() ?: return emptySet()
        try { val a = JSONArray(String(bytes, Charsets.UTF_8)); return (0 until a.length()).map { a.getString(it) }.toSet() }
        finally { bytes.fill(0) }
    }
    private fun writeSuppressed(values: Set<String>) {
        val bytes = JSONArray(values.toList()).toString().toByteArray(Charsets.UTF_8)
        try { suppressedStorage.saveBytes(bytes) } finally { bytes.fill(0) }
    }
    private fun writeRules(updated: List<LearnedCategoryRule>) {
        val json = JSONObject().put("version", 1).put("rules", JSONArray().apply {
            updated.forEach { put(JSONObject().put("type", it.type).put("phrase", it.phrase).put("category", it.category)) }
        }).toString().toByteArray(Charsets.UTF_8)
        try { storage.saveBytes(json) } finally { json.fill(0) }
    }

    fun apply(draft: TransactionDraft): TransactionDraft {
        return CategoryLearning.apply(draft, rules())
    }

    private companion object {
        const val MAX_RULES = 200
    }
}
