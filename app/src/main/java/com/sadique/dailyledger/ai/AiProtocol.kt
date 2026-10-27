package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.TransactionDraft
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

class AiException(message:String):Exception(message)
data class AiDraftResult(val message:String,val entries:List<TransactionDraft>, val reviewIssues: List<List<String>> = emptyList())
object AiProtocol {
    const val MAX_INPUT=3000
    const val MAX_DRAFTS=30
    private fun JSONObject.only(expected:Set<String>){require(keys().asSequence().toSet()==expected)}
    private fun JSONObject.text(key:String):String=(get(key) as? String)?:error("Expected text")
    /**
     * Real model answers are rarely as tidy as the schema promises: JSON wrapped
     * in ``` fences or prose, amount_pkr as a bare number instead of a string,
     * lowercase types, or a missing date/note. All of those used to reject the
     * WHOLE answer with "AI returned an invalid entry" — so Online AI created
     * nothing for any input. Normalize those harmless shape differences here.
     * What stays strictly rejected (tested in AiDataTest): negative amounts,
     * more than two decimals, impossible dates, unknown types, extra keys
     * (e.g. a smuggled ownerId), and non-JSON output. Money and dates are
     * never invented: a missing date falls back to today (as the prompt says),
     * a missing amount or type still fails.
     */
    private fun extractJsonObject(content:String):String {
        var t = content.trim()
        if (t.startsWith("```")) {
            val firstNl = t.indexOf('\n')
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1)
                val end = t.lastIndexOf("```")
                if (end >= 0) t = t.substring(0, end)
                t = t.trim()
            }
        }
        val start = t.indexOf('{')
        if (start < 0) return t
        var depth = 0; var inStr = false; var esc = false; var i = start
        while (i < t.length) {
            val c = t[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false }
            else when (c) { '"' -> inStr = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) return t.substring(start, i + 1) } }
            i++
        }
        return t
    }
    private fun cleanAmount(raw:Any?):String? {
        val text = when (raw) {
            null, JSONObject.NULL -> return null
            is Number -> raw.toString()
            is String -> raw.trim()
                .replace(Regex("^(?i)(rs\\.?|pkr)\\s*"), "")
                .replace(",", "").replace(" ", "")
            else -> return null
        }
        return text.takeIf { Regex("[0-9]{1,11}(\\.[0-9]{1,2})?").matches(it) }
    }
    fun decodeDrafts(content:String):AiDraftResult=try {
        val root=JSONObject(extractJsonObject(content))
        require(root.keys().asSequence().toSet().all{it in setOf("message","transactions")} && root.has("transactions"))
        val message=root.optString("message").take(1000)
        val rows=when (val tx=root.opt("transactions")) {
            is JSONArray -> tx
            is JSONObject -> JSONArray().put(tx)
            else -> error("Expected transactions array")
        }
        require(rows.length()<=MAX_DRAFTS)
        val entries=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i)
            require(row.keys().asSequence().toSet().all{it in setOf("type","amount_pkr","category","note","date")})
            require(row.has("type") && row.has("amount_pkr"))
            val amount=cleanAmount(row.opt("amount_pkr")) ?: error("Bad amount")
            val type=row.optString("type").trim().uppercase()
            val category=row.optString("category").trim().ifBlank{"Other"}
            val note=row.optString("note").take(500)
            val date=row.optString("date").trim().ifBlank{LocalDate.now().toString()}
            TransactionDraft(type,BigDecimal(amount).movePointRight(2).longValueExact(),category,note,date).validated()
        };AiDraftResult(message.ifBlank{"Entries check karke Save karein."},entries)
    }catch(_:Exception){throw AiException("AI returned an invalid entry. Nothing was saved. Try a clearer description.")}
    fun decodeInsights(content:String):List<SpendingTip> = try {
        val root=JSONObject(content);root.only(setOf("suggestions"));val rows=root.getJSONArray("suggestions");require(rows.length() in 1..3)
        (0 until rows.length()).map {i->val row=rows.getJSONObject(i);row.only(setOf("title","detail"));SpendingTip(row.text("title").also{require(it.length in 1..80)},row.text("detail").also{require(it.length in 1..600)})}
    }catch(_:Exception){throw AiException("AI suggestions are unavailable. Local insights still work.")}
    fun context(s:SpendingSnapshot):JSONObject {
        fun money(v:Long)=BigDecimal.valueOf(v,2).toPlainString()
        return JSONObject().put("month",s.month).put("through",s.through).put("salary",money(s.summary.salary)).put("opening_balance",money(s.summary.openingBalanceMinor)).put("expenses",money(s.summary.expenses)).put("kameti_contributions",money(s.summary.committeeContributions)).put("savings_contributions",money(s.summary.savingsContributions)).put("udhar_payments",money(s.summary.udharPayments)).put("remaining",money(s.summary.remaining)).put("other_income",money(s.summary.otherIncome)).put("comparison_days",s.comparisonDays).put("previous_comparable",money(s.previousComparable)).put("current_comparable",money(s.currentComparable)).put("record_count",s.count).put("saved_this_month",money(s.savedThisMonth)).put("potential_saving",money(s.potentialSaving)).put("saving_rate_percent",s.savingRatePercent).put("health_score",s.healthScore)
            .put("categories",JSONArray().apply{s.categories.take(12).forEach{(name,value)->put(JSONObject().put("name",name.take(60)).put("amount",money(value)))}})
    }
}
