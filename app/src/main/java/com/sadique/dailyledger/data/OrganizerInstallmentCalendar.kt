package com.sadique.dailyledger.data

import java.time.YearMonth

/** Read-only installment calendar; missing member slots are never treated as paid. */
data class OrganizerInstallmentMonth(
    val month: YearMonth,
    val collectedMinor: Long,
    val expectedAssignedMinor: Long,
    val fullMinor: Long,
    val partialMinor: Long,
    val unpaidMinor: Long,
    val paidMembers: Int,
    val partialMembers: Int,
    val unpaidMembers: Int,
)

fun organizerInstallmentCalendar(
    circle: OrganizerCircleEntity,
    members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
): List<OrganizerInstallmentMonth> {
    require(circle.totalSlots in 2..120)
    val start = YearMonth.parse(circle.startMonth)
    val roster = members.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }
    require(roster.map { it.id }.distinct().size == roster.size)
    val ids = roster.map { it.id }.toSet()
    val receipts = collections.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }
    require(receipts.all { it.memberId in ids && it.amountMinor > 0L })
    val indexed = receipts.groupBy { it.month to it.memberId }.mapValues { (_, rows) ->
        rows.fold(0L) { total, r -> Math.addExact(total, r.amountMinor) }
    }
    return (0 until circle.totalSlots).map { step ->
        val month = start.plusMonths(step.toLong())
        var collected = 0L
        var assigned = 0L
        var full = 0L
        var partial = 0L
        var unpaid = 0L
        var paidCount = 0
        var partialCount = 0
        var unpaidCount = 0
        roster.forEach { member ->
            val due = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
            val received = indexed[month.toString() to member.id] ?: 0L
            require(received in 0L..due) { "Collection exceeds scheduled installment." }
            assigned = Math.addExact(assigned, due)
            collected = Math.addExact(collected, received)
            when {
                received == due -> { full = Math.addExact(full, due); paidCount++ }
                received > 0L -> { partial = Math.addExact(partial, received); partialCount++ }
                else -> { unpaid = Math.addExact(unpaid, due); unpaidCount++ }
            }
        }
        OrganizerInstallmentMonth(month, collected, assigned, full, partial, unpaid,
            paidCount, partialCount, unpaidCount)
    }
}
