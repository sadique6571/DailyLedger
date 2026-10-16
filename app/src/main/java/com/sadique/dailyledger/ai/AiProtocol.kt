package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.TransactionDraft
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

class AiException(message:String):Exception(message)
data class AiDraftResult(val message:String,val entries:List<TransactionDraft>)
object AiProtocol {
    const val MAX_INPUT=1500
    private fun JSONObject.only(expected:Set<String>){require(keys().asSequence().toSet()==expected)}
    private fun JSONObject.text(key:String):String=(get(key) as? String)?:error("Expected text")
    fun decodeDrafts(content:String):AiDraftResult=try {
        val root=JSONObject(content);root.only(setOf("message","transactions"));val message=root.text("message").also{require(it.length<=1000)}
        val rows=root.getJSONArray("transactions");require(rows.length()<=10)
        val entries=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i);row.only(setOf("type","amount_pkr","category","note","date"))
            val amount=row.text("amount_pkr");require(Regex("[0-9]{1,11}(\\.[0-9]{1,2})?").matches(amount))
            TransactionDraft(row.text("type"),BigDecimal(amount).movePointRight(2).longValueExact(),row.text("category"),row.text("note"),row.text("date")).validated()
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
