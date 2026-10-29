package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.TransactionDraft
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

open class AiException(message:String):Exception(message)
/** A model answer that failed to decode (bad shape, truncated JSON, bad row
 * value). Same user message as AiException; the distinct type lets the
 * caller retry a decode failure once without also retrying infrastructure
 * failures (network/limit), which the runner already handles. */
class AiDecodeException(message:String):AiException(message)
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
    /** A model answering without json_object mode may return a bare JSON array
     * instead of {"suggestions": [...]}. Same guards as the object path:
     * balanced brackets with strings respected; dangerous keys are still
     * rejected per row by the caller. */
    private fun extractJsonArray(content:String):JSONArray? {
        val t = content.trim()
        val start = t.indexOf('[')
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false; var i = start
        while (i < t.length) {
            val c = t[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false }
            else when (c) { '"' -> inStr = true; '[' -> depth++; ']' -> { depth--; if (depth == 0) return runCatching { JSONArray(t.substring(start, i + 1)) }.getOrNull() } }
            i++
        }
        return null
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
    private fun normalizeType(raw:String):String {
        val t = raw.trim().uppercase()
        // The model sometimes echoes the input's own noun ("bill", "purchase")
        // instead of EXPENSE/INCOME, and validated() only accepts those two —
        // one wrong word used to kill the whole batch (e.g. "gas bill 475").
        // Map the common synonyms; anything unmapped (DELETE, TRANSFER, ...)
        // falls through so validated() rejects it as before.
        if (t in setOf("INCOME","CREDIT","INFLOW","EARNING","EARNED","SALARY","TANKHWAH","TANKHA","PAY","WAGE","WAGES","PROFIT","MUNAFA","AMDAN")) return "INCOME"
        if (t in setOf("EXPENSE","DEBIT","OUTFLOW","SPENDING","SPENT","BILL","BILLS","PAYMENT","PAID","PURCHASE","PURCHASED","SHOPPING","KHARCHA","KHARCH","KHARCHAY","COST","COSTS","FEE","FEES","CHARGE","CHARGES","EMI","RENT","KIRAYA","UTILITY","UTILITIES","GROCERY","GROCERIES","FOOD","DINING","DAIRY","FUEL","TRANSPORT","MEDICAL","HEALTH")) return "EXPENSE"
        return t
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
     * decimals, unparseable dates, unmapped types (DELETE/TRANSFER/LOAN/...),
     * and non-JSON output. Money
     * and dates are never invented: a missing date falls back to today (as
     * the prompt says), a missing amount or type still fails, and an empty
     * transactions list decodes to empty — AiService retries that once.
     */
    fun decodeDrafts(content:String):AiDraftResult=try {
        val rootObj = runCatching { JSONObject(extractJsonObject(content)) }.getOrNull()
        // A smuggled account-scoping key rejects the whole answer even if a
        // transactions array is also present — the array fallback below must
        // never bypass this check.
        if (rootObj != null && rootObj.hasDangerousKey()) error("Dangerous key")
        val message: String
        val rows: JSONArray
        if (rootObj != null && rootObj.has("transactions")) {
            message = rootObj.optString("message").take(1000)
            rows = when (val tx = rootObj.opt("transactions")) {
                is JSONArray -> tx
                is JSONObject -> JSONArray().put(tx)
                else -> error("Expected transactions array")
            }
        } else {
            // Bare-array answer: json_object mode was rejected (or ignored) and
            // the model answered without the wrapper object. Insights already
            // accept this shape; drafts did not, so a bare array failed the
            // whole batch even though every row was fine.
            message = ""
            rows = extractJsonArray(content) ?: error("Expected transactions array")
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
    }catch(_:Exception){throw AiDecodeException("AI returned an invalid entry. Nothing was saved. Try a clearer description.")}
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
        // The prompt names "suggestions", but models often answer under
        // "tips"/"insights"/"recommendations", as a bare array, or as one
        // tip object. Accept all of those; the guards below (dangerous keys,
        // blank titles skipped, at least one tip) still apply.
        val rows=when (val s=root.opt("suggestions")) {
            is JSONArray -> s
            is JSONObject -> JSONArray().put(s)
            else -> {
                var found: JSONArray? = null
                for (key in arrayOf("tips", "insights", "recommendations")) {
                    val v = root.opt(key)
                    if (v is JSONArray) { found = v; break }
                    if (v is JSONObject) { found = JSONArray().put(v); break }
                }
                found ?: extractJsonArray(content) ?: run {
                    if (root.has("title") || root.has("tip") || root.has("suggestion") || root.has("heading")) JSONArray().put(root)
                    else error("Expected suggestions array")
                }
            }
        }
        val tips=mutableListOf<SpendingTip>()
        fun firstNonBlank(row: JSONObject, vararg keys: String): String {
            for (k in keys) { val v = row.optString(k).trim(); if (v.isNotBlank()) return v }
            return ""
        }
        for (i in 0 until rows.length()) {
            if (tips.size >= 3) break
            val row=rows.optJSONObject(i)
            if (row == null) {
                // A plain string row is a tip with no detail.
                val raw = rows.opt(i)
                if (raw is String) {
                    val text = raw.trim().take(80)
                    if (text.isNotBlank()) tips += SpendingTip(text, "")
                }
                continue
            }
            if (row.hasDangerousKey()) error("Dangerous key")
            val title=firstNonBlank(row, "title", "tip", "suggestion", "heading").take(80)
            if (title.isBlank()) continue
            val detail=firstNonBlank(row, "detail", "description", "text", "action", "advice").take(600)
            tips += SpendingTip(title, detail)
        }
        require(tips.isNotEmpty())
        tips
    }catch(_:Exception){throw AiDecodeException("AI suggestions are unavailable. Local insights still work.")}
    fun context(s:SpendingSnapshot):JSONObject {
        fun money(v:Long)=BigDecimal.valueOf(v,2).toPlainString()
        return JSONObject().put("month",s.month).put("through",s.through).put("salary",money(s.summary.salary)).put("opening_balance",money(s.summary.openingBalanceMinor)).put("expenses",money(s.summary.expenses)).put("kameti_contributions",money(s.summary.committeeContributions)).put("savings_contributions",money(s.summary.savingsContributions)).put("udhar_payments",money(s.summary.udharPayments)).put("remaining",money(s.summary.remaining)).put("other_income",money(s.summary.otherIncome)).put("comparison_days",s.comparisonDays).put("previous_comparable",money(s.previousComparable)).put("current_comparable",money(s.currentComparable)).put("record_count",s.count).put("saved_this_month",money(s.savedThisMonth)).put("potential_saving",money(s.potentialSaving)).put("saving_rate_percent",s.savingRatePercent).put("health_score",s.healthScore)
            .put("categories",JSONArray().apply{s.categories.take(12).forEach{(name,value)->put(JSONObject().put("name",name.take(60)).put("amount",money(value)))}})
    }
}

