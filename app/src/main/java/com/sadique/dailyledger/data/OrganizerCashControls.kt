package com.sadique.dailyledger.data

import java.time.LocalDate

/** Read-only cash closing and payout checks, awaiting audited persistence in a later PR. */
data class OrganizerCashClosingPreview(
    val openingMinor: Long,
    val collectionsMinor: Long,
    val payoutsMinor: Long,
    val expectedClosingMinor: Long,
    val countedMinor: Long,
    val discrepancyMinor: Long,
)

/**
 * This uses only the recorded cash movements for ONE owner/circle/day.
 * A bank or wallet transfer is not physical cash. Payment verification needs
 * its own persisted state before non-cash settlement can be classified.
 */
fun organizerCashClosingPreview(
    circle: OrganizerCircleEntity,
    date: LocalDate,
    openingMinor: Long,
    countedMinor: Long,
    collections: List<OrganizerCollectionEntity>,
    payouts: List<OrganizerPayoutEntity>,
): OrganizerCashClosingPreview {
    require(openingMinor >= 0L && countedMinor >= 0L)
    val day = date.toString()
    fun isCash(method: String) = method.trim().equals("Cash", ignoreCase = true)
    val received = collections.asSequence()
        .filter { it.ownerId == circle.ownerId && it.circleId == circle.id && it.date == day && isCash(it.method) }
        .fold(0L) { total, item ->
            require(item.amountMinor > 0L)
            Math.addExact(total, item.amountMinor)
        }
    val paid = payouts.asSequence()
        .filter { it.ownerId == circle.ownerId && it.circleId == circle.id && it.date == day && isCash(it.method) }
        .fold(0L) { total, item ->
            require(item.amountMinor > 0L)
            Math.addExact(total, item.amountMinor)
        }
    val expected = Math.subtractExact(Math.addExact(openingMinor, received), paid)
    val difference = Math.subtractExact(countedMinor, expected)
    return OrganizerCashClosingPreview(openingMinor, received, paid, expected, countedMinor, difference)
}

data class OrganizerPayoutReadiness(
    val expectedMinor: Long,
    val alreadyPaidMinor: Long,
    val remainingMinor: Long,
    val pooledCashMinor: Long,
    val shortfallMinor: Long,
    val turnAssigned: Boolean,
    val turnFilled: Boolean,
    val canProceed: Boolean,
)

/** Preview only. Explicit approval and fresh transactional checks are required before payout. */
fun organizerPayoutReadiness(
    circle: OrganizerCircleEntity,
    member: OrganizerMemberEntity,
    roster: List<OrganizerMemberEntity>,
    payouts: List<OrganizerPayoutEntity>,
    pooledCashMinor: Long,
): OrganizerPayoutReadiness {
    require(member.ownerId == circle.ownerId && member.circleId == circle.id)
    require(pooledCashMinor >= 0L)
    val expected = Math.multiplyExact(halfShareMoney(circle.fullMonthlyMinor, member.halfUnits), circle.totalSlots.toLong())
    val paid = payouts.filter { it.ownerId == circle.ownerId && it.circleId == circle.id && it.memberId == member.id }
        .fold(0L) { sum, item ->
            require(item.amountMinor > 0L)
            Math.addExact(sum, item.amountMinor)
        }
    require(paid <= expected)
    val remaining = expected - paid
    val assigned = member.turnNumber in 1..circle.totalSlots
    val filled = assigned && filledTurnUnits(roster.filter { it.ownerId == circle.ownerId && it.circleId == circle.id }, member.turnNumber) == 2
    val shortfall = (remaining - pooledCashMinor).coerceAtLeast(0L)
    return OrganizerPayoutReadiness(expected, paid, remaining, pooledCashMinor, shortfall, assigned, filled,
        remaining > 0L && assigned && filled && shortfall == 0L)
}
