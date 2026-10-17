package com.sadique.dailyledger.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Identifiers are derived from immutable ledger IDs and never assigned from row order. */
fun organizerReceiptNumber(collection: OrganizerCollectionEntity): String {
    require(collection.id.isNotBlank() && collection.ownerId.isNotBlank())
    return "KMT-" + collection.id.uppercase(java.util.Locale.ROOT)
}

fun organizerReceiptText(
    circle: OrganizerCircleEntity,
    member: OrganizerMemberEntity,
    receipt: OrganizerCollectionEntity,
): String {
    require(circle.ownerId == member.ownerId && member.ownerId == receipt.ownerId)
    require(circle.id == member.circleId && member.circleId == receipt.circleId)
    require(member.id == receipt.memberId && receipt.amountMinor > 0)
    LocalDate.parse(receipt.date)
    YearMonth.parse(receipt.month)
    return buildString {
        appendLine("Kameti payment receipt")
        appendLine("Receipt: ${organizerReceiptNumber(receipt)}")
        appendLine("Kameti: ${circle.name}")
        appendLine("Member: ${member.name}")
        appendLine("Installment: ${receipt.month}")
        appendLine("Paid: PKR ${receipt.amountMinor / 100}.${(receipt.amountMinor % 100).toString().padStart(2, '0')}")
        appendLine("Date: ${receipt.date}")
        appendLine("Method: ${receipt.method}")
        append("Organizer payment record (not bank settlement verification)")
    }
}

/** Calendar-month estimate, not a scheduled day or payment guarantee. */
fun organizerTurnCountdown(start: String, turn: Int, today: LocalDate): Long? {
    if (turn <= 0) return null
    val month = YearMonth.parse(start).plusMonths((turn - 1).toLong())
    return ChronoUnit.DAYS.between(today, month.atDay(1)).coerceAtLeast(0L)
}

data class OrganizerCircleHealth(
    val recordedCollectionsMinor: Long,
    val recordedPayoutsMinor: Long,
    val pooledBalanceMinor: Long,
    val membersWithOutstanding: Int,
)
/** Recorded ≠ verified; do not use this as cash closing or payout authorization. */
fun organizerCircleHealth(
    circle: OrganizerCircleEntity, members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>, payouts: List<OrganizerPayoutEntity>,
    month: YearMonth,
): OrganizerCircleHealth {
    val memberIds = members.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }.map { it.id }.toSet()
    val allCollections = collections.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }
    val allPayouts = payouts.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }
    require((allCollections.map { it.memberId } + allPayouts.map { it.memberId }).all { it in memberIds })
    val totalIn = allCollections.fold(0L) { sum, x -> require(x.amountMinor > 0); Math.addExact(sum, x.amountMinor) }
    val totalOut = allPayouts.fold(0L) { sum, x -> require(x.amountMinor > 0); Math.addExact(sum, x.amountMinor) }
    val monthPaid = allCollections.filter { it.month == month.toString() }.groupBy { it.memberId }
    val outstanding = members.filter { it.id in memberIds }.count { member ->
        val paid = monthPaid[member.id].orEmpty().fold(0L) { sum, x -> Math.addExact(sum, x.amountMinor) }
        paid < halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
    }
    return OrganizerCircleHealth(totalIn, totalOut, Math.subtractExact(totalIn, totalOut), outstanding)
}
