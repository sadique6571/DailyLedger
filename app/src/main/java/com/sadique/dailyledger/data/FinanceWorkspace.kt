package com.sadique.dailyledger.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Financial tools live in the encrypted profile row and participate in the same Room backup transaction. */
enum class FinanceKind { ACCOUNT, TRANSFER, ADJUSTMENT, ALLOCATION, PLANNED, SHOPPING, GOAL, SHORTCUT, INSTALLMENT, REIMBURSEMENT, CLAIM_RECEIPT, GOAL_CONTRIBUTION, INSTALLMENT_PURCHASE }
data class FinanceItem(
    val id: String = UUID.randomUUID().toString(), val kind: FinanceKind, val title: String,
    val amount: Long, val date: String, val category: String = "Other", val account: String = "Cash",
    val reference: String = "", val transactionId: String = "", val quantity: String = "", val unit: String = "",
    val rate: Long = 0, val settled: Long = 0, val done: Boolean = false, val note: String = "", val opening:Long=0,
) {
    fun validated(): FinanceItem {
        UUID.fromString(id); require(title.isNotBlank() && title.length <= 100)
        require(amount in -1_000_000_000_000L..1_000_000_000_000L && settled in 0..1_000_000_000_000L)
        if (kind !in listOf(FinanceKind.ACCOUNT, FinanceKind.ADJUSTMENT)) require(amount > 0)
        require(category.length in 1..60 && account.length in 1..60 && note.length <= 1500 && reference.length <= 200 && transactionId.length <= 250)
        require(opening in 0..1_000_000_000_000L)
        LocalDate.parse(date); require(unit.length <= 30 && quantity.length <= 30 && rate in 0..1_000_000_000_000L)
        if(kind==FinanceKind.ACCOUNT)require(title.length<=60) { "Account names must be 60 characters or fewer." }
        if (quantity.isNotBlank()) require(unit.isNotBlank() && quantityAmount(quantity,rate)==amount) { "Enter a unit and a matching quantity × rate amount." }
        if (kind in listOf(FinanceKind.REIMBURSEMENT,FinanceKind.GOAL)) require(settled <= amount)
        if (kind == FinanceKind.TRANSFER) require(reference.isNotBlank() && reference != account)
        return this
    }
    fun json() = JSONObject().put("id",id).put("kind",kind.name).put("title",title).put("amount",amount).put("date",date)
        .put("category",category).put("account",account).put("reference",reference).put("transactionId",transactionId)
        .put("quantity",quantity).put("unit",unit).put("rate",rate).put("settled",settled).put("done",done).put("note",note).put("opening",opening)
    companion object { fun decode(o: JSONObject) = FinanceItem(o.getString("id"), FinanceKind.valueOf(o.getString("kind")), o.getString("title"),
        o.getLong("amount"),o.getString("date"),o.getString("category"),o.getString("account"),o.getString("reference"),o.getString("transactionId"),
        o.getString("quantity"),o.getString("unit"),o.getLong("rate"),o.getLong("settled"),o.getBoolean("done"),o.getString("note"),o.optLong("opening")).validated() }
}
data class EntryRevision(val id: String, val transactionId: String, val at: Long, val action: String, val before: String, val after: String) {
    fun json() = JSONObject().put("id",id).put("transactionId",transactionId).put("at",at).put("action",action).put("before",before).put("after",after)
}
data class FinanceWorkspace(val items: List<FinanceItem> = emptyList(), val revisions: List<EntryRevision> = emptyList(),
    val checked: Set<String> = emptySet(), val widgetBalance: Boolean = false) {
    val hasContent get() = items.isNotEmpty() || revisions.isNotEmpty() || checked.isNotEmpty() || widgetBalance
    val accounts get() = (listOf("Cash", "Bank", "Easypaisa", "JazzCash") + items.filter { it.kind == FinanceKind.ACCOUNT }.map { it.title }).distinct()
    fun validated(): FinanceWorkspace {
        require(items.size <= 10000 && revisions.size <= 50000 && checked.size <= 5000)
        require(items.map { it.id }.distinct().size == items.size && revisions.map { it.id }.distinct().size == revisions.size)
        items.forEach { it.validated() }
        require(items.filter { it.kind == FinanceKind.ACCOUNT }.map { it.title }.distinct().size == items.count { it.kind == FinanceKind.ACCOUNT })
        items.forEach { require(it.account in accounts); if(it.kind == FinanceKind.TRANSFER) require(it.reference in accounts) }
        revisions.forEach { UUID.fromString(it.id); require(it.transactionId.length in 1..250 && it.at > 0 && it.action in listOf("CREATE","EDIT","DELETE","DETAILS")); require(it.before.length <= 10000 && it.after.length <= 10000)
            listOf(it.before,it.after).filter{snapshot->snapshot.isNotBlank()}.forEach { snapshot ->
                val row=JSONObject(snapshot);require(row.getString("id")==it.transactionId)
                require(row.getString("type") in listOf("INCOME","EXPENSE") && row.getLong("amount") in 1..1_000_000_000_000L)
                LocalDate.parse(row.getString("date"));require(row.getString("note").length<=1500 && row.getString("category").length<=60)
            }
        }
        checked.forEach { require(it.length in 1..200) }
        return this
    }
    fun put(item: FinanceItem) = copy(items = items.filterNot { it.id == item.id } + item.validated()).validated()
    fun json() = JSONObject().put("version",1).put("items",JSONArray().apply { items.forEach { put(it.json()) } })
        .put("revisions",JSONArray().apply { revisions.forEach { put(it.json()) } }).put("checked",JSONArray(checked.sorted())).put("widgetBalance",widgetBalance)
    fun remap(owner: String): FinanceWorkspace {
        fun id(s:String) = if(s.isBlank()) s else "$owner:${s.substringAfterLast(':')}"
        return copy(items = items.map { it.copy(transactionId = id(it.transactionId),reference=if(it.kind==FinanceKind.REIMBURSEMENT)id(it.reference) else it.reference) }, revisions = revisions.map { r ->
            fun snapshot(s:String) = if(s.isBlank()) s else JSONObject(s).put("id",id(JSONObject(s).getString("id"))).put("refundOf",id(JSONObject(s).optString("refundOf"))).put("ownerId",owner).toString()
            r.copy(transactionId = id(r.transactionId), before = snapshot(r.before), after = snapshot(r.after))
        })
    }
    fun reconcileInstallments(tx:List<TransactionEntity>):FinanceWorkspace = copy(items=items.map { item ->
        if(item.kind!=FinanceKind.INSTALLMENT_PURCHASE)item else {
            val advance=tx.find{it.id==item.transactionId}?.amountMinor ?: 0
            val paid=items.filter{it.kind==FinanceKind.INSTALLMENT && it.reference==item.id}.sumOf{part->tx.find{it.id==part.transactionId}?.amountMinor ?: 0}
            item.copy(settled=advance+paid,done=advance+paid>=item.amount)
        }
    }).validated()
    fun merge(other: FinanceWorkspace) = copy(items = (items + other.items).distinctBy { it.id }, revisions = (revisions + other.revisions).distinctBy { it.id }, checked = checked + other.checked).validated()
    companion object {
        fun decode(o:JSONObject): FinanceWorkspace {
            require(o.getInt("version") == 1)
            val items=o.getJSONArray("items"); val revisions=o.getJSONArray("revisions");val checked=o.getJSONArray("checked")
            return FinanceWorkspace((0 until items.length()).map { FinanceItem.decode(items.getJSONObject(it)) },
                (0 until revisions.length()).map { revisions.getJSONObject(it).let { r -> EntryRevision(r.getString("id"),r.getString("transactionId"),r.getLong("at"),r.getString("action"),r.getString("before"),r.getString("after")) } },
                (0 until checked.length()).map { checked.getString(it) }.toSet(),o.getBoolean("widgetBalance")).validated()
        }
    }
}

fun quantityAmount(quantity: String, rateMinor: Long): Long {
    require(rateMinor > 0); val q=BigDecimal(quantity); require(q > BigDecimal.ZERO && q.scale() <= 6)
    return q.multiply(BigDecimal(rateMinor)).setScale(0,RoundingMode.HALF_UP).longValueExact().also { require(it in 1..1_000_000_000_000L) }
}
fun TransactionEntity.expenseEffect(): Long = if(type != "EXPENSE") 0 else if(refundOf.isNotBlank()) -amountMinor else amountMinor
fun TransactionEntity.cashEffect(): Long = if(type=="INCOME" || refundOf.isNotBlank()) amountMinor else -amountMinor
fun transactionSnapshot(t:TransactionEntity) = JSONObject().put("id",t.id).put("ownerId",t.ownerId).put("type",t.type).put("amount",t.amountMinor)
    .put("category",t.category).put("note",t.note).put("date",t.date).put("account",t.account).put("quantity",t.quantity).put("unit",t.unit)
    .put("rate",t.unitPriceMinor).put("refundOf",t.refundOf).put("private",t.privateEntry).toString()

data class SurvivalForecast(val daily: Long, val days: Long, val needed: Long, val shortfall: Long, val daysCovered: Long?)
object FinanceMath {
    fun forecast(remaining:Long, transactions:List<TransactionEntity>, start:LocalDate, salary:LocalDate, today:LocalDate): SurvivalForecast {
        val elapsed=(ChronoUnit.DAYS.between(start,today)+1).coerceAtLeast(1)
        val spent=transactions.filter { runCatching { LocalDate.parse(it.date) in start..today }.getOrDefault(false) }.sumOf { it.expenseEffect() }.coerceAtLeast(0)
        val average=spent / elapsed;val days=ChronoUnit.DAYS.between(today,salary).coerceAtLeast(1)
        val needed=Math.multiplyExact(average,days)
        return SurvivalForecast(average,days,needed,(needed-remaining).coerceAtLeast(0),if(average>0) remaining.coerceAtLeast(0)/average else null)
    }
    fun accountBalance(account:String, workspace:FinanceWorkspace, tx:List<TransactionEntity>):Long {
        var balance=workspace.items.filter { it.kind==FinanceKind.ACCOUNT && it.title==account }.sumOf { it.amount }
        tx.filter { it.account==account }.forEach { balance=Math.addExact(balance,it.cashEffect()) }
        workspace.items.filter { it.kind==FinanceKind.ADJUSTMENT && it.account==account }.forEach { balance=Math.addExact(balance,it.amount) }
        workspace.items.filter { it.kind==FinanceKind.TRANSFER }.forEach { if(it.account==account) balance=Math.subtractExact(balance,it.amount);if(it.reference==account)balance=Math.addExact(balance,it.amount) }
        return balance
    }
    fun goalPerSalary(target:Long, already:Long, deadline:LocalDate, salary:LocalDate):Long {
        require(target>0 && already>=0 && deadline>=LocalDate.now() && deadline>=salary)
        val count=(ChronoUnit.MONTHS.between(salary.withDayOfMonth(1),deadline.withDayOfMonth(1))+1).coerceAtLeast(1)
        val left=(target-already).coerceAtLeast(0);return (left+count-1)/count
    }
}

data class RateComparison(val name:String,val oldRate:Long,val newRate:Long,val quantityEffect:Long,val rateEffect:Long,val totalChange:Long)
fun priceComparisons(tx:List<TransactionEntity>):List<RateComparison> = tx.filter { it.type=="EXPENSE" && it.refundOf.isBlank() && it.quantity.isNotBlank() && it.unitPriceMinor>0 && !it.privateEntry }
    .groupBy { (it.note.ifBlank { it.category }.trim().lowercase() to it.unit.trim().lowercase()) }.values.mapNotNull { records ->
        val latest=records.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt });if(latest.size<2)return@mapNotNull null
        val now=latest[0];val old=latest[1];val oq=BigDecimal(old.quantity);val nq=BigDecimal(now.quantity)
        val qEffect=nq.subtract(oq).multiply(BigDecimal(old.unitPriceMinor)).setScale(0,RoundingMode.HALF_UP).longValueExact()
        RateComparison(now.note.ifBlank { now.category },old.unitPriceMinor,now.unitPriceMinor,qEffect,now.amountMinor-old.amountMinor-qEffect,now.amountMinor-old.amountMinor)
    }

/** Same elapsed calendar days; uses weighted purchase rates and separates quantity from rate changes exactly in paisa. */
fun monthlyRateComparisons(tx:List<TransactionEntity>,today:LocalDate):List<RateComparison> {
    val current=java.time.YearMonth.from(today);val previous=current.minusMonths(1);val days=minOf(today.dayOfMonth,previous.lengthOfMonth())
    return tx.filter { it.type=="EXPENSE" && !it.privateEntry && it.refundOf.isBlank() && it.quantity.isNotBlank() && it.unitPriceMinor>0 }
        .groupBy { it.note.ifBlank{it.category}.trim().lowercase() to it.unit.trim().lowercase() }.values.mapNotNull { rows ->
            fun inMonth(month:java.time.YearMonth)=rows.filter { it.date.startsWith("$month-") && LocalDate.parse(it.date).dayOfMonth<=days }
            val old=inMonth(previous);val now=inMonth(current);if(old.isEmpty() || now.isEmpty())return@mapNotNull null
            val oq=old.fold(BigDecimal.ZERO){sum,r->sum+BigDecimal(r.quantity)};val nq=now.fold(BigDecimal.ZERO){sum,r->sum+BigDecimal(r.quantity)}
            val ot=old.sumOf{it.amountMinor};val nt=now.sumOf{it.amountMinor}
            val oldRate=BigDecimal(ot).divide(oq,12,RoundingMode.HALF_UP);val newRate=BigDecimal(nt).divide(nq,12,RoundingMode.HALF_UP)
            val quantityEffect=(nq-oq).multiply(oldRate).setScale(0,RoundingMode.HALF_UP).longValueExact()
            RateComparison(now.first().note.ifBlank{now.first().category},oldRate.setScale(0,RoundingMode.HALF_UP).longValueExact(),newRate.setScale(0,RoundingMode.HALF_UP).longValueExact(),quantityEffect,nt-ot-quantityEffect,nt-ot)
        }
}

fun privateRevisionIds(tx:List<TransactionEntity>,revisions:List<EntryRevision>):Set<String> = buildSet {
    tx.filter{it.privateEntry}.forEach{add(it.id)}
    revisions.forEach { revision -> if(listOf(revision.before,revision.after).any{it.isNotBlank() && JSONObject(it).optBoolean("private")})add(revision.transactionId) }
}
