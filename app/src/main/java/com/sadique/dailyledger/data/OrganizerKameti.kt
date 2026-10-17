package com.sadique.dailyledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Organizer's pooled money is separate from the owner's personal salary/expense ledger. */
@Entity(tableName = "organizer_circles", indices = [Index("ownerId")])
data class OrganizerCircleEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val fullMonthlyMinor: Long,
    val totalSlots: Int,
    val startMonth: String,
    val createdAt: Long,
)

/** One full (2 half-units) or one half (1 half-unit) slot ownership.
 * A person owning 1.5 has TWO rows: full + half, possibly different draw turns.
 * Turn 0 denotes a member who has not drawn a number yet.
 */
@Entity(tableName = "organizer_members", indices = [Index("ownerId"), Index("circleId")])
data class OrganizerMemberEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val circleId: String,
    val name: String,
    val phone: String = "",
    val whatsapp: String = "",
    val halfUnits: Int = 2,
    val turnNumber: Int = 0,
    val createdAt: Long,
    val drawMethod: String = "MANUAL",
    val drawnAt: Long = 0L,
)

@Entity(tableName = "organizer_collections", indices = [Index("ownerId"), Index("circleId"), Index("memberId")])
data class OrganizerCollectionEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val circleId: String,
    val memberId: String,
    val month: String,
    val amountMinor: Long,
    val date: String,
    val method: String,
    val createdAt: Long,
)

@Entity(tableName = "organizer_payouts", indices = [Index("ownerId"), Index("circleId"), Index("memberId")])
data class OrganizerPayoutEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val circleId: String,
    val memberId: String,
    val turnNumber: Int,
    val amountMinor: Long,
    val date: String,
    val method: String,
    val createdAt: Long,
)

/** Integers avoid floating-point share and cash rounding problems. */
fun halfShareMoney(fullMinor: Long, halfUnits: Int): Long {
    require(fullMinor > 0 && fullMinor % 2L == 0L)
    require(halfUnits == 1 || halfUnits == 2)
    return Math.multiplyExact(fullMinor / 2L, halfUnits.toLong())
}

fun filledTurnUnits(members: List<OrganizerMemberEntity>, turn: Int): Int =
    members.filter { it.turnNumber == turn }.sumOf { it.halfUnits }

fun availableDraws(members: List<OrganizerMemberEntity>, member: OrganizerMemberEntity, totalSlots: Int): List<Int> =
    (1..totalSlots).filter { slot ->
        slot == member.turnNumber || filledTurnUnits(members.filterNot { it.id == member.id }, slot) + member.halfUnits <= 2
    }

/** Only exact multiples of 0.5 shares are allowed: 0.5, 1, 1.5, 2 and so on. */
fun parseHalfShareUnits(input: String): Int? {
    val parts = input.trim().split('.')
    if (parts.size !in 1..2 || parts[0].isEmpty() || parts[0].any { !it.isDigit() }) return null
    val whole = parts[0].toIntOrNull() ?: return null
    if (parts.size == 2 && parts[1] !in setOf("0", "5")) return null
    return runCatching { Math.addExact(Math.multiplyExact(whole, 2), if (parts.size == 2 && parts[1] == "5") 1 else 0) }
        .getOrNull()?.takeIf { it > 0 }
}
