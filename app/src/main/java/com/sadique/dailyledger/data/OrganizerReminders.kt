package com.sadique.dailyledger.data

import java.time.LocalDate
import java.time.YearMonth

/** Pure reminder candidates, never sends messages or books money. */
data class OrganizerReminderCandidate(
    val memberId: String,
    val name: String,
    val phone: String,
    val whatsapp: String,
    val dueMinor: Long,
    val paidMinor: Long,
    val outstandingMinor: Long,
    val overdue: Boolean,
)
fun organizerReminderCandidates(
    circle: OrganizerCircleEntity,
    members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
    month: YearMonth,
    today: LocalDate,
    dueDay: Int = 10,
): List<OrganizerReminderCandidate> {
    require(dueDay in 1..31)
    val start = YearMonth.parse(circle.startMonth)
    require(!month.isBefore(start) && month.isBefore(start.plusMonths(circle.totalSlots.toLong())))
    val dueDate = month.atDay(minOf(dueDay, month.lengthOfMonth()))
    val roster = members.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }
    require(roster.map { it.id }.distinct().size == roster.size)
    val receipts = collections.filter { it.ownerId == circle.ownerId && it.circleId == circle.id && it.month == month.toString() }
    val idSet = roster.map { it.id }.toSet()
    require(receipts.all { it.memberId in idSet && it.amountMinor > 0L })
    val totals = receipts.groupBy { it.memberId }.mapValues { (_, rows) ->
        rows.fold(0L) { sum, x -> Math.addExact(sum, x.amountMinor) }
    }
    return roster.mapNotNull { member ->
        val due = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
        val paid = totals[member.id] ?: 0L
        require(paid in 0L..due)
        if (paid == due) null else OrganizerReminderCandidate(
            member.id, member.name, member.phone, member.whatsapp, due, paid, due - paid, today.isAfter(dueDate))
    }.sortedWith(compareByDescending<OrganizerReminderCandidate> { it.overdue }.thenByDescending { it.outstandingMinor })
}

fun organizerReminderMessage(circleName: String, month: YearMonth, candidate: OrganizerReminderCandidate): String {
    require(circleName.isNotBlank() && candidate.outstandingMinor > 0L)
    val remaining = candidate.outstandingMinor / 100L
    return "Assalam o Alaikum ${candidate.name}, ${circleName} ki ${month} Kameti installment mein Rs ${remaining} remaining hain. Shukriya."
}
