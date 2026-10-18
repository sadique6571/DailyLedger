package com.sadique.dailyledger.planning

import android.content.Context
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.security.SecureSecretStore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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

/** Account-scoped plans stored transactionally in the encrypted database and Drive snapshot. */
class PlannerStore(private val context: Context, val ownerId: String, scope: kotlinx.coroutines.CoroutineScope? = null) {
    private val repo = LedgerRepository(AppDatabase.get(context), ownerId)

    private val secret = SecureSecretStore(context.applicationContext, "planner-v1:$ownerId")
    private var loadFailure: Throwable? = null
    private val state = MutableStateFlow(runCatching { read() }.getOrElse { loadFailure = it; PlannerData() })
    val data = state.asStateFlow()
    val error get() = loadFailure?.let { "Planning preferences could not be read. Existing files were kept." }
    private fun read(): PlannerData = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { repo.profileSnapshot().planner }
    init { scope?.launchPlanningObserver() }
    private fun kotlinx.coroutines.CoroutineScope.launchPlanningObserver() = launch(kotlinx.coroutines.Dispatchers.IO) {
        repo.profileBackup.catch { loadFailure = it }.collect { state.value = it.planner }
    }
    @Synchronized private fun update(alreadyLocked: Boolean = false, transform: (PlannerData) -> PlannerData) {
        check(loadFailure == null) { error.orEmpty() }
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            suspend fun commit() {
                SettingsStore(context).markDrivePending(ownerId)
                val next = repo.updateProfile { it.copy(planner = transform(it.planner)) }
                state.value = next.planner
            }
            if (alreadyLocked) commit() else com.sadique.dailyledger.sync.BackupLock.mutex.withLock { commit() }
            runCatching { com.sadique.dailyledger.sync.SyncScheduler.syncNow(context, ownerId) }
        }
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
    fun markHandled(id: String, due: LocalDate, alreadyLocked: Boolean = false) = update(alreadyLocked) { current ->
        current.copy(bills = current.bills.map { if (it.id == id && it.nextDue() == due) it.copy(handledThrough = YearMonth.from(due).toString()) else it })
    }
}
