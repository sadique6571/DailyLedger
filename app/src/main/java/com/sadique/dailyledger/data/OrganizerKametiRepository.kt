package com.sadique.dailyledger.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** A Kameti organizer holds members' pooled funds, not personal income. */
class OrganizerKametiRepository(private val db: AppDatabase, private val ownerId: String) {
    private val dao = db.ledgerDao()
    val circles = dao.observeOrganizerCircles(ownerId)
    val members = dao.observeOrganizerMembers(ownerId)
    val collections = dao.observeOrganizerCollections(ownerId)
    val payouts = dao.observeOrganizerPayouts(ownerId)

    private fun id() = UUID.randomUUID().toString()

    suspend fun addCircle(name: String, monthlyMinor: Long, slots: Int, start: String) {
        require(name.isNotBlank()) { "Enter a Kameti name." }
        require(monthlyMinor > 0 && monthlyMinor % 2L == 0L) { "A full monthly share must split equally into two half shares." }
        require(slots in 2..120) { "Choose between 2 and 120 numbered turns." }
        require(monthlyMinor <= Long.MAX_VALUE / slots) { "The total Kameti amount is too large." }
        YearMonth.parse(start)
        dao.upsertOrganizerCircle(OrganizerCircleEntity(id(), ownerId, name.trim(), monthlyMinor, slots, start, System.currentTimeMillis()))
    }

    suspend fun addMember(circleId: String, name: String, phone: String, whatsapp: String, halfUnits: Int, turn: Int) =
        addMembers(circleId, listOf(OrganizerMemberDraft(name, phone, whatsapp, halfUnits, turn)), id())

    suspend fun addMembers(circleId: String, drafts: List<OrganizerMemberDraft>, requestId: String) = db.withTransaction {
        val c = requireNotNull(dao.organizerCirclesNow(ownerId).firstOrNull { it.id == circleId }) { "Kameti not found." }
        val checked = drafts.map { it.validated() }
        val batch = KametiBatch(ownerId, "organizer", requestId, circleId, checked.map { it.fields() })
        val all = dao.organizerMembersNow(ownerId)
        if (batch.alreadySaved(all.map { it.id })) return@withTransaction
        val roster = all.filter { it.circleId == c.id }.toMutableList()
        val now = System.currentTimeMillis()
        val rows = checked.mapIndexed { index, d ->
            require(d.units in 1..2) { "Choose half or full share." }
            require(roster.sumOf { it.halfUnits } + d.units <= c.totalSlots * 2) { "All share places are occupied." }
            require(d.turn == 0 || (d.turn in 1..c.totalSlots && filledTurnUnits(roster, d.turn) + d.units <= 2)) {
                "That turn is full. Pair a half share with another half, not with a full."
            }
            OrganizerMemberEntity(batch.ids[index], ownerId, c.id, d.name, d.phone, d.whatsapp,
                d.units, d.turn, now, "MANUAL", if (d.turn == 0) 0L else now).also { roster.add(it) }
        }
        rows.forEach { dao.upsertOrganizerMember(it) }
    }

    suspend fun updateContact(memberId: String, name: String, phone: String, whatsapp: String) = db.withTransaction {
        val member = dao.organizerMembersNow(ownerId).firstOrNull { it.id == memberId } ?: error("Member not found.")
        require(name.isNotBlank()) { "Enter the member's name." }
        dao.upsertOrganizerMember(member.copy(name = name.trim(), phone = phone.trim(), whatsapp = whatsapp.trim()))
    }

    suspend fun assignTurn(memberId: String, turn: Int, method: String = "MANUAL") = db.withTransaction {
        val member = dao.organizerMembersNow(ownerId).firstOrNull { it.id == memberId } ?: error("Member not found.")
        val circle = dao.organizerCirclesNow(ownerId).firstOrNull { it.id == member.circleId } ?: error("Kameti not found.")
        require(member.turnNumber == 0) { "Turn already confirmed; changes need review." }
        val options = availableDraws(dao.organizerMembersNow(ownerId).filter { it.circleId == circle.id }, member, circle.totalSlots)
        require(turn in options) { "This turn has no room for that share." }
        require(method in setOf("MANUAL", "RANDOM"))
        dao.upsertOrganizerMember(member.copy(turnNumber = turn, drawMethod = method, drawnAt = System.currentTimeMillis()))
    }

    suspend fun collect(memberId: String, month: String, amount: Long, date: String, method: String) = db.withTransaction {
        val member = dao.organizerMembersNow(ownerId).firstOrNull { it.id == memberId } ?: error("Member not found.")
        val circle = dao.organizerCirclesNow(ownerId).first { it.id == member.circleId }
        val scheduled = YearMonth.parse(month)
        val start = YearMonth.parse(circle.startMonth)
        require(!scheduled.isBefore(start) && scheduled.isBefore(start.plusMonths(circle.totalSlots.toLong()))) { "Month is outside this Kameti." }
        LocalDate.parse(date)
        val expected = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
        val previous = dao.organizerCollectionsNow(ownerId).filter { it.memberId == member.id && it.month == month }.sumOf { it.amountMinor }
        require(amount > 0 && amount <= expected - previous) { "Payment exceeds the member's remaining installment." }
        dao.upsertOrganizerCollection(OrganizerCollectionEntity(id(), ownerId, circle.id, member.id, month, amount, date, method.ifBlank { "Cash" }, System.currentTimeMillis()))
    }

    suspend fun payOut(memberId: String, amount: Long, date: String, method: String) = db.withTransaction {
        val member = dao.organizerMembersNow(ownerId).firstOrNull { it.id == memberId } ?: error("Member not found.")
        val circle = dao.organizerCirclesNow(ownerId).first { it.id == member.circleId }
        require(member.turnNumber in 1..circle.totalSlots) { "First assign a confirmed turn." }
        val allocated = dao.organizerMembersNow(ownerId).filter { it.circleId == circle.id }
        require(filledTurnUnits(allocated, member.turnNumber) == 2) { "Payout requires a complete full-share turn (two halves or one full)." }
        LocalDate.parse(date)
        val perMember = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
        val expected = Math.multiplyExact(perMember, circle.totalSlots.toLong())
        val paid = dao.organizerPayoutsNow(ownerId).filter { it.memberId == member.id }.sumOf { it.amountMinor }
        require(amount > 0 && amount <= expected - paid) { "Payout exceeds the member's share." }
        dao.upsertOrganizerPayout(OrganizerPayoutEntity(id(), ownerId, circle.id, member.id, member.turnNumber, amount, date, method.ifBlank { "Cash" }, System.currentTimeMillis()))
    }

    suspend fun deleteMember(memberId: String) = db.withTransaction {
        val member = dao.organizerMembersNow(ownerId).firstOrNull { it.id == memberId } ?: return@withTransaction
        require(dao.organizerCollectionsNow(ownerId).none { it.memberId == memberId } &&
                dao.organizerPayoutsNow(ownerId).none { it.memberId == memberId }) {
            "Member has a payment history. Do not delete financial records."
        }
        dao.deleteOrganizerMember(member)
    }

    suspend fun deleteCircle(circleId: String) = db.withTransaction {
        val circle = dao.organizerCirclesNow(ownerId).firstOrNull { it.id == circleId } ?: return@withTransaction
        require(dao.organizerCollectionsNow(ownerId).none { it.circleId == circleId } &&
                dao.organizerPayoutsNow(ownerId).none { it.circleId == circleId }) {
            "This Kameti has money records; it cannot be deleted."
        }
        dao.clearCircleMembers(ownerId, circleId)
        dao.deleteOrganizerCircle(circle)
    }
}
