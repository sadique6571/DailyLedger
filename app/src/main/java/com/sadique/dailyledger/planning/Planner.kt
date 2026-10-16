package com.sadique.dailyledger.planning

import android.content.Context
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.security.SecureSecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class CategoryBudget(val category: String, val limitMinor: Long)
data class RecurringBill(
    val id: String, val title: String, val type: String, val category: String, val amountMinor: Long,
    val day: Int, val startMonth: String, val handledThrough: String = "", val enabled: Boolean = true,
) {
    fun nextDue(): LocalDate {
        val month = if (handledThrough.isBlank()) YearMonth.parse(startMonth) else YearMonth.parse(handledThrough).plusMonths(1)
        return month.atDay(day.coerceAtMost(month.lengthOfMonth()))
    }
    fun batchId(): String = UUID.nameUUIDFromBytes("recurring:$id:${nextDue()}".toByteArray()).toString()
}
data class PlannerData(val budgets: List<CategoryBudget> = emptyList(), val bills: List<RecurringBill> = emptyList())
data class BudgetProgress(val category: String, val spent: Long, val limit: Long) {
    val fraction get() = if (limit > 0) (spent.toDouble() / limit).coerceIn(0.0, 1.0).toFloat() else 0f
    val nearLimit get() = spent >= limit * 0.8
    val exceeded get() = spent >= limit
}
object PlannerMath {
    fun budgets(owner: String, rules: List<CategoryBudget>, records: List<TransactionEntity>, month: YearMonth): List<BudgetProgress> {
        val totals = records.filter { it.ownerId == owner && it.type == "EXPENSE" && it.date.startsWith("$month-") }
            .groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
        return rules.map { BudgetProgress(it.category, totals[it.category] ?: 0, it.limitMinor) }
    }
    fun due(bills: List<RecurringBill>, today: LocalDate) = bills.filter { it.enabled && it.nextDue() <= today }.sortedBy { it.nextDue() }
}

/** Account-scoped encrypted planning preferences. Ledger schema and Drive format stay compatible. */
class PlannerStore(context: Context, val ownerId: String) {
    private val secret = SecureSecretStore(context.applicationContext, "planner-v1:$ownerId")
    private var loadFailure: Throwable? = null
    private val state = MutableStateFlow(runCatching { read() }.getOrElse { loadFailure = it; PlannerData() })
    val data = state.asStateFlow()
    val error get() = loadFailure?.let { "Planning preferences could not be read. Existing files were kept." }
    private fun read(): PlannerData {
        val bytes = secret.loadBytes() ?: return PlannerData()
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8)); require(root.getInt("version") == 1)
            val budgets = root.getJSONArray("budgets").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { CategoryBudget(it.getString("category"), it.getLong("limit")) } } }
            val bills = root.getJSONArray("bills").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let {
                RecurringBill(it.getString("id"), it.getString("title"), it.getString("type"), it.getString("category"), it.getLong("amount"), it.getInt("day"), it.getString("start"), it.optString("handled"), it.optBoolean("enabled", true))
            } } }
            require(budgets.all { it.category.isNotBlank() && it.limitMinor in 1..1_000_000_000_000L })
            require(bills.all { it.day in 1..31 && it.type in listOf("INCOME", "EXPENSE") && it.amountMinor in 1..1_000_000_000_000L && it.category.isNotBlank() && runCatching { it.nextDue() }.isSuccess })
            return PlannerData(budgets, bills)
        } finally { bytes.fill(0) }
    }
    @Synchronized private fun update(transform: (PlannerData) -> PlannerData) {
        check(loadFailure == null) { error.orEmpty() }
        val next = transform(state.value)
        val root = JSONObject().put("version", 1).put("budgets", JSONArray().apply {
            next.budgets.forEach { put(JSONObject().put("category", it.category).put("limit", it.limitMinor)) }
        }).put("bills", JSONArray().apply {
            next.bills.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("type", it.type).put("category", it.category).put("amount", it.amountMinor).put("day", it.day).put("start", it.startMonth).put("handled", it.handledThrough).put("enabled", it.enabled)) }
        })
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        try { secret.saveBytes(bytes); state.value = next } finally { bytes.fill(0) }
    }
    fun setBudget(category: String, amount: Long) {
        require(category.isNotBlank() && category.length <= 60 && amount in 1..1_000_000_000_000L) { "Enter a valid category and positive limit." }
        update { it.copy(budgets = (it.budgets.filterNot { b -> b.category == category } + CategoryBudget(category, amount)).take(300)) }
    }
    fun removeBudget(category: String) = update { it.copy(budgets = it.budgets.filterNot { b -> b.category == category }) }
    fun saveBill(bill: RecurringBill) {
        require(bill.title.isNotBlank() && bill.title.length <= 100 && bill.category.isNotBlank() && bill.category.length <= 60 && bill.day in 1..31 && bill.type in listOf("INCOME", "EXPENSE") && bill.amountMinor in 1..1_000_000_000_000L) { "Check the title, category, amount and day." }
        YearMonth.parse(bill.startMonth); UUID.fromString(bill.id)
        update { require(it.bills.size < 100 || it.bills.any { old -> old.id == bill.id }) { "Up to 100 reminders are supported." }; it.copy(bills = it.bills.filterNot { b -> b.id == bill.id } + bill) }
    }
    fun removeBill(id: String) = update { it.copy(bills = it.bills.filterNot { b -> b.id == id }) }
    fun markHandled(id: String, due: LocalDate) = update { current ->
        current.copy(bills = current.bills.map { if (it.id == id && it.nextDue() == due) it.copy(handledThrough = YearMonth.from(due).toString()) else it })
    }
}
