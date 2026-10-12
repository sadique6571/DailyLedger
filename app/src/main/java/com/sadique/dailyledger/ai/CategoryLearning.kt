package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.data.TransactionDraft
import com.sadique.dailyledger.security.SecureSecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LearnedCategoryRule(val type: String, val phrase: String, val category: String)

object CategoryLearning {
    fun normalize(text: String): String = text.lowercase(Locale.ROOT)
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
            .filter { (_, phrase) -> phrase.isNotBlank() && " $normalized ".contains(" $phrase ") }
            .maxByOrNull { (_, phrase) -> phrase.length }
            ?.first?.category
    }

    fun apply(draft: TransactionDraft, rules: List<LearnedCategoryRule>): TransactionDraft {
        val learned = match(draft.type, draft.note, rules) ?: return draft
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
        val json = JSONObject().put("version", 1).put(
            "rules",
            JSONArray().apply {
                updated.take(MAX_RULES).forEach { rule ->
                    put(JSONObject().put("type", rule.type).put("phrase", rule.phrase).put("category", rule.category))
                }
            },
        ).toString().toByteArray(Charsets.UTF_8)
        try {
            storage.saveBytes(json)
        } finally {
            json.fill(0)
        }
    }

    fun apply(draft: TransactionDraft): TransactionDraft {
        return CategoryLearning.apply(draft, rules())
    }

    private companion object {
        const val MAX_RULES = 200
    }
}
