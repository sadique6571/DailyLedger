package com.sadique.dailyledger.data
import android.content.Context
import org.json.JSONArray

data class LedgerCategory(val group:String,val label:String,val type:String,val aliases:List<String>)
object CategoryCatalog {
    @Volatile private var cached:List<LedgerCategory>?=null
    @Volatile private var groups: Map<String, String> = emptyMap()
    fun visualGroup(label: String) = groups[label.lowercase(java.util.Locale.ROOT)]
    fun load(context:Context):List<LedgerCategory> = cached ?: synchronized(this) {
        cached ?: context.assets.open("categories.json").bufferedReader().use { reader ->
            val a=JSONArray(reader.readText())
            (0 until a.length()).map { i -> val r=a.getJSONObject(i);val words=r.getJSONArray("aliases")
                LedgerCategory(r.getString("group"),r.getString("label"),r.getString("type"),(0 until words.length()).map{words.getString(it)}) }
        }.let { base -> (ItemCategories.entries + base).distinctBy { it.type to it.label } }.also{ groups = it.associate { row -> row.label.lowercase(java.util.Locale.ROOT) to row.group }; cached=it }
    }
}
