package com.sadique.dailyledger.export

import com.sadique.dailyledger.data.*
import java.time.LocalDate
import java.time.YearMonth

internal data class StatementRow(val date: String, val title: String, val detail: String,
    val type: String, val amount: Long, val moneyIn: Boolean, val recordedAt: Long)
internal data class StatementSection(val kameti: Boolean, val rows: List<StatementRow>) {
    val moneyIn: Long get() = rows.filter { it.moneyIn }.sumOf { it.amount }
    val moneyOut: Long get() = rows.filterNot { it.moneyIn }.sumOf { it.amount }
    fun pages(): List<List<StatementRow>> = listOf(rows.take(12)) + rows.drop(12).chunked(15)
}
internal object StatementData {
    fun sections(transactions: List<TransactionEntity>, committees: List<CommitteeEntity>,
        payments: List<CommitteePaymentEntity>, receipts: List<CommitteeReceiptEntity>,
        from: LocalDate?, through: LocalDate?): List<StatementSection> {
        require(from == null || through == null || !from.isAfter(through)) { "Start date must be on or before end date." }
        val activity = transactions.filter { it.date.inStatementPeriod(from, through) }.map {
            StatementRow(it.date, it.note.ifBlank { it.category }, it.category, it.type,
                it.amountMinor, it.type == "INCOME", it.createdAt)
        }
        val names = committees.associate { it.id to it.name }
        val kameti = payments.filter { it.month.inStatementPeriod(from, through) }.map {
            StatementRow(it.month, names[it.committeeId]?.ifBlank { "Kameti" } ?: "Kameti",
                "Installment #${it.installmentNumber} · ${it.method}", "PAID", it.amountMinor, false, it.paidAt)
        } + receipts.filter { it.date.orEmpty().inStatementPeriod(from, through) }.map {
            StatementRow(it.date ?: "Undated", names[it.committeeId]?.ifBlank { "Kameti" } ?: "Kameti",
                listOf(it.note.ifBlank { "Receiving" }, it.method).joinToString(" · "),
                "RECEIVED", it.amountMinor, true, it.createdAt)
        }
        val order = compareBy<StatementRow> { it.date }.thenBy { it.recordedAt }
        return buildList {
            if (activity.isNotEmpty() || kameti.isEmpty()) add(StatementSection(false, activity.sortedWith(order)))
            if (committees.isNotEmpty() || kameti.isNotEmpty()) add(StatementSection(true, kameti.sortedWith(order)))
        }
    }
}
/** Installments use their recorded month. Undated receipts appear only in All records. */
internal fun String.inStatementPeriod(from: LocalDate?, through: LocalDate?): Boolean {
    if (from == null && through == null) return true
    val date = runCatching { if (length == 7) YearMonth.parse(this).atDay(1) else LocalDate.parse(this) }.getOrNull() ?: return false
    return (from == null || !date.isBefore(from)) && (through == null || !date.isAfter(through))
}
