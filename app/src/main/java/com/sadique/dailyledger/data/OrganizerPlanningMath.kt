package com.sadique.dailyledger.data

import java.time.LocalDate
import java.time.YearMonth

/** Pure, read-only organizer calculations. No personal ledger postings or database writes. */
data class OrganizerMonthSummary(
    val expectedMinor: Long,
    val collectedMinor: Long,
    val outstandingMinor: Long,
    val fullyPaidShares: Int,
    val partialShares: Int,
    val unpaidShares: Int,
)

enum class OrganizerPaymentState { UNPAID, PARTIAL, PAID, OVERDUE }

/** On-time/late status is based on an explicit due date, not simply the beginning of a month. */
fun organizerPaymentState(paid: Long, due: Long, dueDate: LocalDate?, today: LocalDate): OrganizerPaymentState {
    require(due > 0 && paid >= 0 && paid <= due)
    if (paid == due) return OrganizerPaymentState.PAID
    if (dueDate != null && today.isAfter(dueDate)) return OrganizerPaymentState.OVERDUE
    return if (paid == 0L) OrganizerPaymentState.UNPAID else OrganizerPaymentState.PARTIAL
}

fun organizerMonthlySummary(
    circle: OrganizerCircleEntity,
    members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
    month: YearMonth,
): OrganizerMonthSummary {
    val start = YearMonth.parse(circle.startMonth)
    require(!month.isBefore(start) && month.isBefore(start.plusMonths(circle.totalSlots.toLong())))
    val roster = members.filter { it.circleId == circle.id }
    val monthPaid = collections.filter { it.circleId == circle.id && it.month == month.toString() }
    val amounts = monthPaid.groupBy { it.memberId }.mapValues { (_, rows) ->
        rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
    }
    val knownIds = roster.map { it.id }.toSet()
    require(monthPaid.all { it.memberId in knownIds && it.amountMinor > 0L })
    var full = 0
    var partial = 0
    var unpaid = 0
    roster.forEach { member ->
        val required = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
        val received = amounts[member.id] ?: 0L
        require(received in 0L..required)
        when {
            received == required -> full++
            received == 0L -> unpaid++
            else -> partial++
        }
    }
    val target = Math.multiplyExact(circle.fullMonthlyMinor, circle.totalSlots.toLong())
    val collected = monthPaid.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
    require(collected <= target)
    return OrganizerMonthSummary(target, collected, target - collected, full, partial, unpaid)
}

/** Distributes advance cash to due months without mutating the source records. */
fun allocateAdvanceInstallment(
    monthlyMinor: Long,
    firstMonth: YearMonth,
    months: Int,
    alreadyPaid: Map<YearMonth, Long>,
    advanceMinor: Long,
): Map<YearMonth, Long> {
    require(monthlyMinor > 0L && months in 1..120 && advanceMinor >= 0L)
    var left = advanceMinor
    val assigned = linkedMapOf<YearMonth, Long>()
    repeat(months) { index ->
        val month = firstMonth.plusMonths(index.toLong())
        val previous = alreadyPaid[month] ?: 0L
        require(previous in 0L..monthlyMinor)
        val use = minOf(left, monthlyMinor - previous)
        if (use > 0L) assigned[month] = use
        left -= use
    }
    require(left == 0L) { "Advance exceeds remaining scheduled installments." }
    return assigned
}

/** Expected payout minus known available pooled cash; projections are not booked money. */
fun organizerPayoutShortfall(expectedPayoutMinor: Long, availablePooledCashMinor: Long): Long {
    require(expectedPayoutMinor >= 0L && availablePooledCashMinor >= 0L)
    return (expectedPayoutMinor - availablePooledCashMinor).coerceAtLeast(0L)
}
