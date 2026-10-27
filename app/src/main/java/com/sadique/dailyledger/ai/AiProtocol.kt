package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.TransactionDraft
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AiException(message:String):Exception(message)
data class AiDraftResult(val message:String,val entries:List<TransactionDraft>, val reviewIssues: List<List<String>> = emptyList())
object AiProtocol {
    const val MAX_INPUT=3000
    const val MAX_DRAFTS=30
    /**
     * Keys that must never appear in a model answer: they smuggle account
     * scoping into a draft (the ownerId test case). Every OTHER extra key is
     * ignored — models routinely add benign keys (currency, confidence,
     * summary) and the old all-or-nothing key check killed the whole answer
     * for those, which is why Online AI failed for most inputs while a lucky
     * clean answer (e.g. one "Parday 3500" run) succeeded.
     */
    private val DANGEROUS_KEYS=setOf("ownerid","owner","accountid","userid","account_id","user_id","uid","owner_id")
    private fun JSONObject.hasDangerousKey():Boolean=keys().asSequence().any{it.lowercase() in DANGEROUS_KEYS}
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
                // Currency may sit before OR after the number ("3500 PKR",
                // "Rs. 3,500/-") — strip every currency token, not just a prefix.
                .replace(Regex("(?i)rs\\.?|pkr|/-"), "")
                .replace(",", "").replace(" ", "")
            else -> return null
        }
        return text.takeIf { Regex("[0-9]{1,11}(\\.[0-9]{1,2})?").matches(it) }
    }
    private fun normalizeType(raw:String):String = when (raw.trim().uppercase()) {
        "DEBIT","OUTFLOW","SPENDING","SPENT" -> "EXPENSE"
        "CREDIT","INFLOW","EARNING","EARNED" -> "INCOME"
        else -> raw.trim().uppercase()
    }
    private fun normalizeDate(raw:String):String {
        val t = raw.trim()
        if (t.isBlank()) return LocalDate.now().toString()
        runCatching { return LocalDate.parse(t).toString() }
        // Day-first numeric dates: the prompt itself says DD/MM/YYYY is
        // day-first, yet validated() only accepts ISO — so a model obeying
        // the prompt was rejected outright.
        for (fmt in listOf("dd/MM/yyyy","dd-MM-yyyy","yyyy/MM/dd")) {
            runCatching { return LocalDate.parse(t, DateTimeFormatter.ofPattern(fmt)).toString() }
        }
        for (fmt in listOf("d MMM yyyy","d MMMM yyyy","MMM d, yyyy","MMMM d, yyyy")) {
            runCatching { return LocalDate.parse(t, DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)).toString() }
        }
        error("Bad date")
    }
    /**
     * Real model answers are rarely as tidy as the schema promises. Harmless
     * shape differences are normalized; what stays strictly rejected (tested
     * in AiDataTest): account-scoping keys, negative amounts, more than two
     * decimals, unparseable dates, unknown types, and non-JSON output. Money
     * and dates are never invented: a missing date falls back to today (as
     * the prompt says), a missing amount or type still fails, and an empty
     * transactions list decodes to empty — AiService retries that once.
     */
    fun decodeDrafts(content:String):AiDraftResult=try {
        val root=JSONObject(extractJsonObject(content))
        require(!root.hasDangerousKey() && root.has("transactions"))
        val message=root.optString("message").take(1000)
        val rows=when (val tx=root.opt("transactions")) {
            is JSONArray -> tx
            is JSONObject -> JSONArray().put(tx)
            else -> error("Expected transactions array")
        }
        val entries=mutableListOf<TransactionDraft>()
        for (i in 0 until rows.length()) {
            if (entries.size >= MAX_DRAFTS) break
            val row=rows.optJSONObject(i) ?: continue
            if (row.hasDangerousKey()) error("Dangerous key")
            if (!row.has("type") || !row.has("amount_pkr")) error("Missing type or amount")
            val amount=cleanAmount(row.opt("amount_pkr")) ?: error("Bad amount")
            val type=normalizeType(row.optString("type"))
            val category=row.optString("category").trim().ifBlank{"Other"}.take(60)
            val note=row.optString("note").take(500)
            val date=normalizeDate(row.optString("date"))
            entries += TransactionDraft(type,BigDecimal(amount).movePointRight(2).longValueExact(),category,note,date).validated()
        }
        AiDraftResult(message.ifBlank{"Entries check karke Save karein."},entries)
    }catch(_:Exception){throw AiException("AI returned an invalid entry. Nothing was saved. Try a clearer description.")}
    /**
     * Suggestions used to be decoded strictly (exact keys, title 1..80,
     * detail 1..600, no fences) while the insights prompt never states those
     * limits — so a refresh only "worked" when the model happened to obey
     * them, i.e. after 5-7 taps. Same tolerance as drafts: fences/prose
     * stripped, extra keys ignored, long text truncated, rows capped at 3.
     * A truly empty answer still fails with the existing message.
     */
    fun decodeInsights(content:String):List<SpendingTip> = try {
        val root=JSONObject(extractJsonObject(content))
        require(!root.hasDangerousKey())
        val rows=when (val s=root.opt("suggestions")) {
            is JSONArray -> s
            is JSONObject -> JSONArray().put(s)
            else -> error("Expected suggestions array")
        }
        val tips=mutableListOf<SpendingTip>()
        for (i in 0 until rows.length()) {
            if (tips.size >= 3) break
            val row=rows.optJSONObject(i) ?: continue
            if (row.hasDangerousKey()) error("Dangerous key")
            val title=row.optString("title").trim().take(80)
            if (title.isBlank()) continue
            tips += SpendingTip(title,row.optString("detail").trim().take(600))
        }
        require(tips.isNotEmpty())
        tips
    }catch(_:Exception){throw AiException("AI suggestions are unavailable. Local insights still work.")}
    fun context(s:SpendingSnapshot):JSONObject {
        fun money(v:Long)=BigDecimal.valueOf(v,2).toPlainString()
        return JSONObject().put("month",s.month).put("through",s.through).put("salary",money(s.summary.salary)).put("opening_balance",money(s.summary.openingBalanceMinor)).put("expenses",money(s.summary.expenses)).put("kameti_contributions",money(s.summary.committeeContributions)).put("savings_contributions",money(s.summary.savingsContributions)).put("udhar_payments",money(s.summary.udharPayments)).put("remaining",money(s.summary.remaining)).put("other_income",money(s.summary.otherIncome)).put("comparison_days",s.comparisonDays).put("previous_comparable",money(s.previousComparable)).put("current_comparable",money(s.currentComparable)).put("record_count",s.count).put("saved_this_month",money(s.savedThisMonth)).put("potential_saving",money(s.potentialSaving)).put("saving_rate_percent",s.savingRatePercent).put("health_score",s.healthScore)
            .put("categories",JSONArray().apply{s.categories.take(12).forEach{(name,value)->put(JSONObject().put("name",name.take(60)).put("amount",money(value)))}})
    }
}
