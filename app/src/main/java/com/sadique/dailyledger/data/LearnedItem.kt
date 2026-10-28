package com.sadique.dailyledger.data

import java.security.MessageDigest

/**
 * An item the user actually spends on, learned from a saved Online AI entry.
 * Stored inside [com.sadique.dailyledger.ai.LearningSnapshot] (owner-scoped,
 * Room-backed), so it is part of the account backup and survives restore.
 *
 * [iconUrl] is the source URL of the item's downloaded original icon. It is
 * only a reference: the icon file itself lives in filesDir (permanent app
 * storage, never bundled in the APK) and is re-downloaded from [iconUrl] —
 * or by name when no URL was recorded — after a restore on a new device.
 */
data class LearnedItem(
    val name: String,
    val type: String,
    val category: String,
    val iconUrl: String? = null,
)

/** Pure helpers for learned items: dedupe, catalog rows, icon references. */
object LearnedItems {
    const val MAX_ITEMS = 300

    /** Case/space/punctuation-insensitive form used for dedupe and filenames. */
    fun normalizeName(name: String): String = name.lowercase(java.util.Locale.ROOT).trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    /**
     * True when [name] is already an item: it matches a built-in catalog label
     * or alias (variant spellings are one item — e.g. sonf/saunf and
     * laal march/laal mirch/surkh mirch all resolve to the same row), or an
     * already-learned item of the same type.
     */
    fun isKnown(
        name: String,
        type: String,
        catalog: List<LedgerCategory>,
        learned: List<LearnedItem>,
    ): Boolean {
        val wanted = normalizeName(name)
        if (wanted.isBlank()) return true
        if (learned.any { it.type == type && normalizeName(it.name) == wanted }) return true
        // A label match of any type counts: one item, one row.
        if (catalog.any { normalizeName(it.label) == wanted }) return true
        return catalog.any { row ->
            row.type == type && row.aliases.any { normalizeName(it) == wanted }
        }
    }

    /** Append [item] unless an equal (normalized name + type) item is present. */
    fun add(existing: List<LearnedItem>, item: LearnedItem): List<LearnedItem> {
        val wanted = normalizeName(item.name)
        if (existing.any { it.type == item.type && normalizeName(it.name) == wanted }) return existing
        return (listOf(item) + existing).take(MAX_ITEMS)
    }

    /** Rows for the picker and the AI prompt list. Grouped under "My items". */
    fun toLedgerCategories(items: List<LearnedItem>): List<LedgerCategory> =
        items.map { LedgerCategory("My items", it.name, it.type, listOf(normalizeName(it.name))) }

    /** Deterministic on-disk name, so a restore re-downloads to the same path. */
    fun iconFileName(label: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalizeName(label).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "item_${hash.take(16)}.webp"
    }

    /** Strict URL gate for icon sources: https only, real host, no credentials. */
    fun isValidIconUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > 500) return false
        return try {
            val uri = java.net.URI(url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        } catch (_: Exception) {
            false
        }
    }
}
