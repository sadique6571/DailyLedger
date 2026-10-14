package com.sadique.dailyledger.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Late historical installments update the kameti ledger, not today's cash balance.
 * Keep the original charge inside its salary cycle even if the calendar month changes.
 */
fun CommitteePaymentEntity.chargesSalaryBalance(zone: ZoneId = ZoneId.systemDefault()): Boolean =
    runCatching { YearMonth.from(Instant.ofEpochMilli(paidAt).atZone(zone)).toString() == month }.getOrDefault(false)

data class SalarySummary(
    val salary: Long,
    val expenses: Long,
    val otherIncome: Long,
    val committeeContributions: Long = 0,
    val savingsContributions: Long = 0,
    val openingBalanceMinor: Long = 0,
) {
    val remaining: Long get() = openingBalanceMinor + salary + otherIncome - expenses - committeeContributions - savingsContributions
    val totalOutflow: Long get() = expenses + committeeContributions + savingsContributions
}

fun ledgerCycleSummary(
    transactions: List<TransactionEntity>,
    committeePayments: List<CommitteePaymentEntity>,
    savings: List<SavingEntity>,
    cycle: LedgerCycleEntity,
    now: Long = System.currentTimeMillis(),
): SalarySummary {
    val currentTransactions = transactions.filter { it.createdAt in cycle.startAt..now }
    return SalarySummary(
        salary = currentTransactions.filter { it.isSalary() }.sumOf { it.amountMinor },
        expenses = currentTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor },
        otherIncome = currentTransactions.filter { it.type == "INCOME" && !it.isSalary() }.sumOf { it.amountMinor },
        committeeContributions = committeePayments.filter {
            it.paidAt in cycle.startAt..now && it.chargesSalaryBalance()
        }.sumOf { it.amountMinor },
        savingsContributions = savings.filter { it.createdAt in cycle.startAt..now }.sumOf { it.amountMinor },
        openingBalanceMinor = cycle.openingBalanceMinor,
    )
}

fun TransactionEntity.isSalary(): Boolean = type == "INCOME" &&
    (category.trim().equals("Salary", ignoreCase = true) ||
        category.trim().equals("Monthly salary", ignoreCase = true) || category.trim() == "تنخواہ")

fun monthlySalarySummary(
    transactions: List<TransactionEntity>,
    month: String,
    committeePayments: List<CommitteePaymentEntity> = emptyList(),
    savings: List<SavingEntity> = emptyList(),
): SalarySummary {
    val current = transactions.filter { it.date.startsWith("$month-") }
    return SalarySummary(
        salary = current.filter { it.isSalary() }.sumOf { it.amountMinor },
        expenses = current.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor },
        otherIncome = current.filter { it.type == "INCOME" && !it.isSalary() }.sumOf { it.amountMinor },
        committeeContributions = committeePayments.filter { it.month == month }.sumOf { it.amountMinor },
        savingsContributions = savings.filter { it.date.startsWith("$month-") }.sumOf { it.amountMinor },
    )
}

fun CommitteeEntity.monthlyContribution(): Long = Math.multiplyExact(monthlyAmountMinor, shares.toLong())
fun CommitteeEntity.expectedPayout(): Long = Math.multiplyExact(monthlyContribution(), totalInstallments.toLong())

data class CommitteeBalance(val expected: Long, val received: Long) {
    val remaining: Long get() = (expected - received).coerceAtLeast(0L)
    val complete: Boolean get() = expected > 0L && received >= expected
    val status: String get() = when { complete -> "Fully received"; received > 0L -> "Partially received"; else -> "Not received" }
}

fun committeeBalance(committee: CommitteeEntity, receipts: List<CommitteeReceiptEntity>): CommitteeBalance =
    CommitteeBalance(committee.expectedPayout(), receipts.filter { it.committeeId == committee.id && it.ownerId == committee.ownerId }.fold(0L) { total, receipt -> Math.addExact(total, receipt.amountMinor) })
