package com.sadique.dailyledger.data
import android.content.Context
import org.json.JSONArray

data class LedgerCategory(val group:String,val label:String,val type:String,val aliases:List<String>)
object CategoryCatalog {
    @Volatile private var cached:List<LedgerCategory>?=null
    @Volatile private var groups: Map<String, String> = emptyMap()
    /** Learned-item rows, refreshed by the ViewModel from the account's
     * learning snapshot (startup and after every Online AI save). Kept apart
     * from the cached base list so the overlay can change without touching
     * the asset catalog. Built-in rows always win a label clash. */
    @Volatile private var learnedOverlay: List<LedgerCategory> = emptyList()
    /** Bumped on every overlay refresh; dropdowns key their options on it. */
    @Volatile var overlayVersion: Int = 0
        private set
    fun visualGroup(label: String) = groups[label.lowercase(java.util.Locale.ROOT)]
    fun setLearnedOverlay(items: List<LearnedItem>) = synchronized(this) {
        learnedOverlay = LearnedItems.toLedgerCategories(items)
        cached = null
        overlayVersion++
    }
    fun load(context:Context):List<LedgerCategory> = cached ?: synchronized(this) {
        cached ?: context.assets.open("categories.json").bufferedReader().use { reader ->
            val a=JSONArray(reader.readText())
            (0 until a.length()).map { i -> val r=a.getJSONObject(i);val words=r.getJSONArray("aliases")
                LedgerCategory(r.getString("group"),r.getString("label"),r.getString("type"),(0 until words.length()).map{words.getString(it)}) }
        }.let { base -> (ItemCategories.entries + base + learnedOverlay).distinctBy { it.type to it.label } }.also{ groups = it.associate { row -> row.label.lowercase(java.util.Locale.ROOT) to row.group }; cached=it }
    }
}
