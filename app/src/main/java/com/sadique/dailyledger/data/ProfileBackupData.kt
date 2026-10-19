package com.sadique.dailyledger.data

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sadique.dailyledger.planning.*
import com.sadique.dailyledger.security.SecureSecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Entity(tableName = "profile_backup")
data class ProfileBackupEntity(@PrimaryKey val ownerId: String, val payload: String)

data class ProfileBackupData(
    val planner: PlannerData = PlannerData(), val salaryDay: Int? = null,
    val reference: String? = null, val expected: String? = null, val lastRecorded: String? = null,
) {
    val hasContent get() = planner.budgets.isNotEmpty() || planner.bills.isNotEmpty() || salaryDay != null || reference != null || expected != null
    fun validated(): ProfileBackupData {
        require(salaryDay == null || salaryDay in 1..31)
        listOfNotNull(reference, expected, lastRecorded).forEach { LocalDate.parse(it) }
        require(planner.budgets.size <= 300 && planner.bills.size <= 100)
        require(planner.budgets.map { it.category }.distinct().size == planner.budgets.size)
        require(planner.bills.map { it.id }.distinct().size == planner.bills.size)
        planner.budgets.forEach { require(it.category.isNotBlank() && it.category.length <= 60 && it.limitMinor in 1..1_000_000_000_000L) }
        planner.bills.forEach {
            UUID.fromString(it.id); require(it.title.isNotBlank() && it.title.length <= 100)
            require(it.type in listOf("INCOME", "EXPENSE") && it.category.isNotBlank() && it.category.length <= 60)
            require(it.amountMinor in 1..1_000_000_000_000L && it.day in 1..31)
            YearMonth.parse(it.startMonth); if (it.handledThrough.isNotBlank()) require(YearMonth.parse(it.handledThrough) >= YearMonth.parse(it.startMonth))
            it.nextDue()
        }
        return this
    }
    fun json(): JSONObject = JSONObject().put("version", 1).put("planner", plannerJson(planner))
        .put("salaryDay", salaryDay ?: JSONObject.NULL).put("reference", reference ?: JSONObject.NULL)
        .put("expected", expected ?: JSONObject.NULL).put("lastRecorded", lastRecorded ?: JSONObject.NULL)
    companion object {
        fun plannerJson(data: PlannerData) = JSONObject().put("version", 1).put("budgets", JSONArray().apply {
            data.budgets.forEach { put(JSONObject().put("category", it.category).put("limit", it.limitMinor)) }
        }).put("bills", JSONArray().apply { data.bills.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("type", it.type).put("category", it.category)
                .put("amount", it.amountMinor).put("day", it.day).put("start", it.startMonth)
                .put("handled", it.handledThrough).put("enabled", it.enabled))
        } })
        fun plannerDecode(root: JSONObject): PlannerData {
            require(root.getInt("version") == 1)
            val budgets = root.getJSONArray("budgets").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> CategoryBudget(o.getString("category"), o.getLong("limit")) } } }
            val bills = root.getJSONArray("bills").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o ->
                RecurringBill(o.getString("id"), o.getString("title"), o.getString("type"), o.getString("category"), o.getLong("amount"), o.getInt("day"), o.getString("start"), o.optString("handled"), o.optBoolean("enabled", true))
            } } }
            return PlannerData(budgets, bills).also { ProfileBackupData(planner = it).validated() }
        }
        fun decode(root: JSONObject): ProfileBackupData {
            require(root.getInt("version") == 1)
            fun date(key: String) = if (root.isNull(key)) null else root.getString(key)
            return ProfileBackupData(plannerDecode(root.getJSONObject("planner")), if(root.isNull("salaryDay")) null else root.getInt("salaryDay"), date("reference"), date("expected"), date("lastRecorded")).validated()
        }
        fun legacy(context: Context?, owner: String): ProfileBackupData {
            if (context == null) return ProfileBackupData()
            val bytes = SecureSecretStore(context, "planner-v1:$owner").loadBytes()
            val plan = try { bytes?.let { plannerDecode(JSONObject(String(it, Charsets.UTF_8))) } ?: PlannerData() } finally { bytes?.fill(0) }
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
            val prefs = context.getSharedPreferences("salary_schedule_$key", Context.MODE_PRIVATE)
            return ProfileBackupData(plan, prefs.getInt("day", 0).takeIf { it in 1..31 }, prefs.getString("reference", null), prefs.getString("expected", null), prefs.getString("lastRecorded", null)).validated()
        }
    }
}
