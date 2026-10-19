package com.sadique.dailyledger.ai

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Versioned learning data, stored with the account's encrypted Room ledger and Drive snapshot. */
data class LearningSnapshot(
    val rules: List<LearnedCategoryRule> = emptyList(),
    val suppressed: Set<String> = emptySet(),
    val dismissedTips: Set<String> = emptySet(),
) {
    val hasContent get() = rules.isNotEmpty() || suppressed.isNotEmpty() || dismissedTips.isNotEmpty()
    fun validated(): LearningSnapshot {
        require(rules.size <= 200 && suppressed.size <= 500 && dismissedTips.size <= 100) { "AI learning exceeds supported limits." }
        require(rules.all { it.type in setOf("INCOME", "EXPENSE") && it.phrase.isNotBlank() && it.phrase.length <= 120 &&
            it.category.isNotBlank() && it.category.length <= 60 && CategoryLearning.normalize(it.phrase).isNotBlank() }) { "Invalid AI learning rule." }
        require(rules.map { it.type to CategoryLearning.normalize(it.phrase) }.distinct().size == rules.size) { "Duplicate AI learning rules." }
        require(suppressed.all { key ->
            key.substringBefore(':') in setOf("INCOME", "EXPENSE") && key.substringAfter(':', "").let { it.isNotBlank() && it.length <= 500 && CategoryLearning.normalize(it).isNotBlank() }
        }) { "Invalid ignored AI learning rule." }
        require(dismissedTips.all { Regex("[a-f0-9]{64}").matches(it) }) { "Invalid suggestion feedback." }
        return this
    }
    fun json(): JSONObject = JSONObject().put("version", 1).put("rules", JSONArray().apply {
        rules.forEach { put(JSONObject().put("type", it.type).put("phrase", it.phrase).put("category", it.category)) }
    }).put("suppressed", JSONArray(suppressed.sorted())).put("dismissedTips", JSONArray(dismissedTips.sorted()))

    fun remember(type: String, phrase: String, category: String): LearningSnapshot {
        val normalized = CategoryLearning.normalize(phrase).take(120)
        require(normalized.isNotBlank()) { "Add a short item name or note so this category can be remembered." }
        val rule = LearnedCategoryRule(type, normalized, category.trim().take(60))
        return copy(rules = (listOf(rule) + rules.filterNot { it.type == type && CategoryLearning.normalize(it.phrase) == normalized }).take(200),
            suppressed = suppressed - "$type:$normalized").validated()
    }
    fun forget(type: String, phrase: String): LearningSnapshot {
        val normalized = CategoryLearning.normalize(phrase)
        return copy(rules = rules.filterNot { it.type == type && CategoryLearning.normalize(it.phrase) == normalized },
            suppressed = (suppressed + "$type:$normalized").toList().takeLast(500).toSet()).validated()
    }
    fun mergeOlder(older: LearningSnapshot): LearningSnapshot {
        val merged = (rules + older.rules.filterNot { "${it.type}:${CategoryLearning.normalize(it.phrase)}" in suppressed }).distinctBy { it.type to CategoryLearning.normalize(it.phrase) }
        val activeKeys = merged.map { "${it.type}:${CategoryLearning.normalize(it.phrase)}" }.toSet()
        return copy(rules = merged, suppressed = (suppressed + older.suppressed) - activeKeys,
            dismissedTips = dismissedTips + older.dismissedTips).validated()
    }
    fun dismiss(tip: SpendingTip) = copy(dismissedTips = (dismissedTips + feedbackKey(tip)).toList().takeLast(100).toSet()).validated()

    companion object {
        fun decode(root: JSONObject): LearningSnapshot {
            require(root.keys().asSequence().toSet() == setOf("version", "rules", "suppressed", "dismissedTips") && root.getInt("version") == 1) { "Unsupported AI learning backup." }
            fun text(row: JSONObject, key: String) = (row.get(key) as? String) ?: error("AI learning text expected.")
            fun strings(key: String): Set<String> {
                val array = root.getJSONArray(key)
                require(array.length() <= 500)
                val values = (0 until array.length()).map { (array.get(it) as? String) ?: error("Invalid AI learning text.") }
                require(values.distinct().size == values.size)
                return values.toSet()
            }
            val rows = root.getJSONArray("rules"); require(rows.length() <= 200)
            return LearningSnapshot((0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                require(row.keys().asSequence().toSet() == setOf("type", "phrase", "category"))
                LearnedCategoryRule(text(row, "type"), text(row, "phrase"), text(row, "category"))
            }, strings("suppressed"), strings("dismissedTips")).validated()
        }
        fun feedbackKey(tip: SpendingTip): String {
            val text = (tip.title + "\n" + tip.detail).lowercase(Locale.ROOT).replace(Regex("[0-9]+(?:[.,][0-9]+)*"), "#").replace(Regex("\\s+"), " ").trim()
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
