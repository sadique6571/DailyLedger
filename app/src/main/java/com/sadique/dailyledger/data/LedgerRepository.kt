package com.sadique.dailyledger.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class LedgerRepository(private val db: AppDatabase, val ownerId: String) {
    private val dao = db.ledgerDao()
    val organizer = OrganizerKametiRepository(db, ownerId)
    val transactions = dao.observeTransactions(ownerId)
    val loans = dao.observeLoans(ownerId)
    val loanPayments = dao.observeLoanPayments(ownerId)
    val creditPurchases = dao.observeCreditPurchases(ownerId)
    val creditPayments = dao.observeCreditPayments(ownerId)
    val committees = dao.observeCommittees(ownerId)
    val committeePayments = dao.observeCommitteePayments(ownerId)
    val committeeReceipts = dao.observeCommitteeReceipts(ownerId)
    val committeeMembers = dao.observeCommitteeMembers(ownerId)
    val savings = dao.observeSavings(ownerId)
    val ledgerCycle = dao.observeLedgerCycle(ownerId)
    val ledgerArchives = dao.observeLedgerArchives(ownerId)

    suspend fun ensureLedgerCycle(now: Long = System.currentTimeMillis()) = db.withTransaction {
        val transactions = dao.transactionsNow(ownerId)
        val lastSalaryAt = transactions.filter { it.isSalary() && it.createdAt <= now }.maxOfOrNull { it.createdAt }
        val firstRecordAt = transactions.filter { it.createdAt <= now }.minOfOrNull { it.createdAt }
        val initialStart = lastSalaryAt ?: firstRecordAt
            ?: LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
                .withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.insertLedgerCycle(LedgerCycleEntity(ownerId, initialStart, 0L))
    }

    suspend fun startNewLedgerCycle(now: Long = System.currentTimeMillis()) = db.withTransaction {
        val cycle = requireNotNull(dao.ledgerCycleNow(ownerId)) { "Your salary cycle is still loading. Try again shortly." }
        require(now >= cycle.startAt) { "The device clock is earlier than the active salary cycle. Correct the date and try again." }
        val startedAt = if (now > cycle.startAt) now else Math.addExact(cycle.startAt, 1L)
        val summary = ledgerCycleSummary(
            dao.transactionsNow(ownerId), dao.committeePaymentsNow(ownerId), dao.savingsNow(ownerId), cycle, now,
            creditPayments = dao.creditPaymentsNow(ownerId),
        )
        val key = "cycle:${cycle.startAt}"
        val zone = ZoneId.systemDefault()
        val periodStart = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(cycle.startAt), zone)
        val periodEnd = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        dao.insertLedgerArchive(
            LedgerArchiveEntity(
                id = "$ownerId:archive:$key", ownerId = ownerId, archiveKey = key,
                label = "Salary cycle $periodStart to $periodEnd",
                startAt = cycle.startAt, endAt = now, salaryMinor = summary.salary,
                otherIncomeMinor = summary.otherIncome, expensesMinor = Math.addExact(summary.expenses, summary.udharPayments),
                committeeMinor = summary.committeeContributions, savingsMinor = summary.savingsContributions,
                openingBalanceMinor = summary.openingBalanceMinor, closingBalanceMinor = summary.remaining,
                archivedAt = now,
            ),
        )
        dao.upsertLedgerCycle(LedgerCycleEntity(ownerId, startedAt, summary.remaining))
    }

    suspend fun archivePreviousCalendarMonth(now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val month = YearMonth.from(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())).minusMonths(1)
        val key = "calendar:$month"
        if (dao.ledgerArchiveNow(ownerId, key) != null) return@withTransaction false
        val monthTransactions = dao.transactionsNow(ownerId).filter { it.date.startsWith("$month-") }
        val calendarStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendarEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthCommitteePayments = dao.committeePaymentsNow(ownerId).filter {
            it.month == month.toString() && it.paidAt in calendarStart until calendarEnd
        }
        val monthSavings = dao.savingsNow(ownerId).filter {
            it.date.startsWith("$month-") && it.createdAt in calendarStart until calendarEnd
        }
        val monthCreditPayments = dao.creditPaymentsNow(ownerId).filter {
            it.date.startsWith("$month-") && it.createdAt in calendarStart until calendarEnd
        }
        val summary = monthlySalarySummary(monthTransactions, month.toString(), monthCommitteePayments, monthSavings, monthCreditPayments)
        dao.insertLedgerArchive(
            LedgerArchiveEntity(
                id = "$ownerId:archive:$key", ownerId = ownerId, archiveKey = key,
                label = month.toString(), startAt = calendarStart, endAt = calendarEnd,
                salaryMinor = summary.salary, otherIncomeMinor = summary.otherIncome,
                expensesMinor = Math.addExact(summary.expenses, summary.udharPayments), committeeMinor = summary.committeeContributions,
                savingsMinor = summary.savingsContributions, openingBalanceMinor = 0L,
                closingBalanceMinor = summary.remaining, archivedAt = now,
            ),
        )
        true
    }

    suspend fun saveTransaction(type: String, amountMinor: Long, category: String, note: String, date: String, existing: TransactionEntity? = null) {
        require(type == "INCOME" || type == "EXPENSE") { "Choose income or expense." }
        require(amountMinor > 0L) { "Amount must be greater than zero." }
        LocalDate.parse(date)
        val now = System.currentTimeMillis()
        dao.upsertTransaction(TransactionEntity(existing?.id ?: newId(), ownerId, type, amountMinor, category.trim(), note.trim(), date, existing?.createdAt ?: now, now))
    }

    suspend fun saveDraftBatch(drafts: List<TransactionDraft>, batchId: String) = db.withTransaction {
        require(drafts.size in 1..10)
        UUID.fromString(batchId)
        val checked = drafts.map { it.validated() }
        val now = System.currentTimeMillis()
        val rows = checked.mapIndexed { i, d -> TransactionEntity("$ownerId:ai-$batchId-$i", ownerId, d.type, d.amountMinor, d.category, d.note, d.date, now, now) }
        val existing = dao.transactionsNow(ownerId).filter { old -> rows.any { it.id == old.id } }
        if (existing.isNotEmpty()) {
            require(existing.size == rows.size && rows.all { r -> existing.any { it.id == r.id && it.type == r.type && it.amountMinor == r.amountMinor && it.category == r.category && it.note == r.note && it.date == r.date } }) {
                "This draft was already saved. Start a new Auto Fill entry."
            }
        } else dao.insertTransactions(rows)
    }

    suspend fun deleteTransaction(item: TransactionEntity) = dao.deleteTransaction(item)

    suspend fun saveLoan(direction: String, person: String, principalMinor: Long, dueDate: String?, note: String, phone: String = "", whatsapp: String = "") {
        require(direction == "BORROWED" || direction == "LENT") { "Choose borrowed or lent." }
        require(principalMinor > 0L) { "Loan amount must be greater than zero." }
        dueDate?.let { LocalDate.parse(it) }
        dao.upsertLoan(LoanEntity(newId(), ownerId, direction, person.trim().ifBlank { "Unknown" }, principalMinor, dueDate, note.trim(), System.currentTimeMillis(), false, phone.trim(), whatsapp.trim()))
    }

    suspend fun deleteLoan(item: LoanEntity) = db.withTransaction {
        require(item.ownerId == ownerId)
        dao.deleteLoanPayments(ownerId, item.id)
        dao.deleteLoan(item)
    }

    suspend fun addLoanPayment(loanId: String, amountMinor: Long, date: String, note: String, method: String = "Cash") = db.withTransaction {
        val loan = requireNotNull(dao.loanNow(ownerId, loanId)) { "This loan no longer exists." }
        val paid = dao.loanPaymentsNow(ownerId).filter { it.loanId == loanId }.sumOf { it.amountMinor }
        val remaining = (loan.principalMinor - paid).coerceAtLeast(0L)
        require(remaining > 0L) { "This loan is already fully settled." }
        require(amountMinor in 1..remaining) { "Payment cannot be more than the remaining ${remaining / 100.0} PKR balance." }
        LocalDate.parse(date)
        dao.upsertLoanPayment(LoanPaymentEntity(newId(), ownerId, loanId, amountMinor, date, note.trim(), System.currentTimeMillis(), method.trim().ifBlank { "Cash" }))
        if (amountMinor == remaining) dao.upsertLoan(loan.copy(closed = true))
    }

    suspend fun saveCreditPurchase(
        creditor: String,
        item: String,
        amountMinor: Long,
        category: String,
        purchaseDate: String,
        dueDate: String?,
        note: String,
        phone: String = "",
        whatsapp: String = "",
    ) {
        val draft = CreditDraft(creditor, item, amountMinor, category, purchaseDate, dueDate, note, phone, whatsapp).validated()
        dao.upsertCreditPurchase(draft.entity(newId(), ownerId, System.currentTimeMillis()))
    }

    suspend fun deleteCreditPurchase(item: CreditPurchaseEntity) = db.withTransaction {
        require(item.ownerId == ownerId)
        dao.deleteCreditPayments(ownerId, item.id)
        dao.deleteCreditPurchase(item)
    }

    suspend fun saveCreditDrafts(drafts: List<CreditDraft>, batchId: String) = db.withTransaction {
        require(drafts.size in 1..10) { "Save 1 to 10 udhar records at a time." }
        UUID.fromString(batchId)
        val now = System.currentTimeMillis()
        val rows = drafts.mapIndexed { i, draft -> draft.validated().entity("$ownerId:credit-ai-$batchId-$i", ownerId, now) }
        val ids = rows.map { it.id }.toSet()
        val existing = dao.creditPurchasesNow(ownerId).filter { it.id in ids }
        if (existing.isNotEmpty()) {
            require(existing.size == rows.size && rows.all { row ->
                existing.any { old -> old.copy(createdAt = row.createdAt, closed = false) == row }
            }) { "These udhar drafts were already saved. Start a new entry." }
        } else dao.insertCreditPurchases(rows)
    }

    suspend fun addCreditPayment(creditId: String, amountMinor: Long, date: String, note: String, method: String = "Cash") = db.withTransaction {
        val credit = requireNotNull(dao.creditPurchaseNow(ownerId, creditId)) { "This udhar record no longer exists." }
        val paid = dao.creditPaymentsNow(ownerId).filter { it.creditId == creditId }.sumOf { it.amountMinor }
        val remaining = (credit.amountMinor - paid).coerceAtLeast(0L)
        require(remaining > 0L) { "This udhar is already fully paid." }
        require(amountMinor in 1..remaining) { "Payment cannot be more than the remaining ${remaining / 100.0} PKR balance." }
        LocalDate.parse(date)
        dao.upsertCreditPayment(CreditPaymentEntity(newId(), ownerId, creditId, amountMinor, date, note.trim(), System.currentTimeMillis(), method.trim().ifBlank { "Cash" }))
        if (amountMinor == remaining) dao.upsertCreditPurchase(credit.copy(closed = true))
    }

    suspend fun saveCommittee(name: String, monthlyMinor: Long, total: Int, startMonth: String, payout: Int?, note: String, shares: Int = 1, organizerPhone: String = "", memberSchedule: String = "") {
        require(monthlyMinor > 0 && total > 0 && shares > 0) { "Enter a valid monthly amount, number of kametis and installments." }
        Math.multiplyExact(Math.multiplyExact(monthlyMinor, shares.toLong()), total.toLong())
        require(payout == null || payout in 1..total) { "Payout installment must be within the total installments." }
        YearMonth.parse(startMonth)
        dao.upsertCommittee(CommitteeEntity(newId(), ownerId, name.trim().ifBlank { "Kameti" }, monthlyMinor, total, startMonth, payout, false, true, note.trim(), System.currentTimeMillis(), shares, organizerPhone.trim(), memberSchedule.trim()))
    }

    suspend fun markCommitteePaid(committee: CommitteeEntity, installment: Int, month: String, method: String = "Cash") = db.withTransaction {
        val current = requireNotNull(dao.committeeNow(ownerId, committee.id)) { "This kameti no longer exists." }
        require(installment in 1..current.totalInstallments) { "Invalid installment number." }
        val expectedMonth = YearMonth.parse(current.startMonth).plusMonths((installment - 1).toLong()).toString()
        require(month == expectedMonth) { "Installment #$installment belongs to $expectedMonth." }
        require(dao.committeePaymentsNow(ownerId).none { it.committeeId == current.id && it.installmentNumber == installment }) { "This installment is already paid." }
        dao.upsertCommitteePayment(CommitteePaymentEntity(newId(), ownerId, current.id, installment, month, current.monthlyContribution(), System.currentTimeMillis(), method.trim().ifBlank { "Cash" }))
    }

    suspend fun addCommitteeReceipt(committeeId: String, amountMinor: Long, date: String, note: String, method: String = "Cash") = db.withTransaction {
        val committee = requireNotNull(dao.committeeNow(ownerId, committeeId)) { "This kameti no longer exists." }
        val balance = committeeBalance(committee, dao.committeeReceiptsNow(ownerId))
        require(amountMinor > 0L && amountMinor <= balance.remaining) { "Received amount must be greater than zero and no more than the remaining payout." }
        LocalDate.parse(date)
        dao.upsertCommitteeReceipt(CommitteeReceiptEntity(newId(), ownerId, committeeId, amountMinor, date, note.trim(), System.currentTimeMillis(), method.trim().ifBlank { "Cash" }))
        dao.upsertCommittee(committee.copy(received = balance.received + amountMinor >= balance.expected))
    }

    suspend fun deleteCommitteeReceipt(receipt: CommitteeReceiptEntity) = db.withTransaction {
        require(receipt.ownerId == ownerId) { "This receipt belongs to another account." }
        dao.deleteCommitteeReceipt(receipt)
        dao.committeeNow(ownerId, receipt.committeeId)?.let { committee ->
            dao.upsertCommittee(committee.copy(received = committeeBalance(committee, dao.committeeReceiptsNow(ownerId)).complete))
        }
    }

    suspend fun saveCommitteeMember(
        committeeId: String,
        name: String,
        phone: String,
        whatsapp: String,
        turnNumber: Int,
        turnMonth: String,
        isMe: Boolean,
        note: String,
    ) = db.withTransaction {
        val committee = requireNotNull(dao.committeeNow(ownerId, committeeId)) { "This kameti no longer exists." }
        require(turnNumber in 1..committee.totalInstallments) { "Turn number must be between 1 and ${committee.totalInstallments}." }
        val parsedMonth = YearMonth.parse(turnMonth)
        val expected = YearMonth.parse(committee.startMonth).plusMonths((turnNumber - 1).toLong())
        require(parsedMonth == expected) { "Turn #$turnNumber should be $expected." }
        val members = dao.committeeMembersNow(ownerId).filter { it.committeeId == committeeId }
        require(members.none { it.turnNumber == turnNumber }) { "Turn #$turnNumber is already assigned." }
        if (isMe) require(members.count { it.isMe } < committee.shares) { "You already assigned all ${committee.shares} of your kameti turn(s)." }
        dao.upsertCommitteeMember(
            CommitteeMemberEntity(
                id = newId(),
                ownerId = ownerId,
                committeeId = committeeId,
                name = name.trim().ifBlank { if (isMe) "Me" else "Member" },
                phone = phone.trim(),
                whatsapp = whatsapp.trim(),
                turnNumber = turnNumber,
                turnMonth = turnMonth,
                isMe = isMe,
                received = false,
                receivedDate = null,
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun markCommitteeMemberReceived(member: CommitteeMemberEntity, date: String) = db.withTransaction {
        require(member.ownerId == ownerId)
        LocalDate.parse(date)
        val current = requireNotNull(dao.committeeMemberNow(ownerId, member.id)) { "This member no longer exists." }
        requireNotNull(dao.committeeNow(ownerId, current.committeeId)) { "This kameti no longer exists." }
        dao.upsertCommitteeMember(current.copy(received = true, receivedDate = date))
    }

    suspend fun deleteCommitteeMember(member: CommitteeMemberEntity) {
        require(member.ownerId == ownerId)
        dao.deleteCommitteeMember(member)
    }

    suspend fun deleteCommittee(item: CommitteeEntity) = db.withTransaction {
        require(item.ownerId == ownerId)
        dao.deleteCommitteePayments(ownerId, item.id)
        dao.deleteCommitteeReceipts(ownerId, item.id)
        dao.deleteCommitteeMembers(ownerId, item.id)
        dao.deleteCommittee(item)
    }

    suspend fun saveSaving(kind: String, amountMinor: Long, date: String, note: String) {
        require(amountMinor > 0L)
        LocalDate.parse(date)
        dao.upsertSaving(SavingEntity(newId(), ownerId, kind, amountMinor, date, note.trim(), System.currentTimeMillis()))
    }

    suspend fun deleteSaving(item: SavingEntity) = dao.deleteSaving(item)

    private fun newId() = "$ownerId:${UUID.randomUUID()}"
    private fun ownedId(id: String) = "$ownerId:${id.substringAfterLast(':')}"

    suspend fun hasRecords(): Boolean = dao.transactionsNow(ownerId).isNotEmpty() ||
        dao.loansNow(ownerId).isNotEmpty() || dao.loanPaymentsNow(ownerId).isNotEmpty() ||
        dao.committeesNow(ownerId).isNotEmpty() || dao.committeePaymentsNow(ownerId).isNotEmpty() ||
        dao.committeeReceiptsNow(ownerId).isNotEmpty() || dao.committeeMembersNow(ownerId).isNotEmpty() || dao.organizerCirclesNow(ownerId).isNotEmpty() ||
        dao.creditPurchasesNow(ownerId).isNotEmpty() || dao.creditPaymentsNow(ownerId).isNotEmpty() ||
        dao.savingsNow(ownerId).isNotEmpty() ||
        dao.ledgerArchivesNow(ownerId).any {
            it.salaryMinor != 0L || it.otherIncomeMinor != 0L || it.expensesMinor != 0L ||
                it.committeeMinor != 0L || it.savingsMinor != 0L || it.openingBalanceMinor != 0L ||
                it.closingBalanceMinor != 0L
        } ||
        (dao.ledgerCycleNow(ownerId)?.openingBalanceMinor ?: 0L) != 0L

    suspend fun migrateOwner(oldOwner: String) = db.withTransaction {
        if (oldOwner != ownerId) {
            dao.migrateTransactions(oldOwner, ownerId)
            dao.migrateLoans(oldOwner, ownerId)
            dao.migrateLoanPayments(oldOwner, ownerId)
            dao.migrateCreditPurchases(oldOwner, ownerId)
            dao.migrateCreditPayments(oldOwner, ownerId)
            dao.migrateCommittees(oldOwner, ownerId)
            dao.migrateCommitteePayments(oldOwner, ownerId)
            dao.migrateCommitteeReceipts(oldOwner, ownerId)
            dao.migrateCommitteeMembers(oldOwner, ownerId)
            dao.migrateOrganizerCircles(oldOwner, ownerId)
            dao.migrateOrganizerMembers(oldOwner, ownerId)
            dao.migrateOrganizerCollections(oldOwner, ownerId)
            dao.migrateOrganizerPayouts(oldOwner, ownerId)
            dao.migrateSavings(oldOwner, ownerId)
            dao.migrateLedgerCycle(oldOwner, ownerId)
            dao.migrateLedgerArchives(oldOwner, ownerId)
        }
    }

    suspend fun exportJson(): String = db.withTransaction {
        fun JSONObject.putNullable(key: String, value: Any?) = apply { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
        val cycle = dao.ledgerCycleNow(ownerId) ?: LedgerCycleEntity(
            ownerId,
            YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            0L,
        ).also { dao.insertLedgerCycle(it) }
        val root = JSONObject().put("version", 7).put("ownerId", ownerId).put("exportedAt", System.currentTimeMillis())
        root.put("transactions", JSONArray().apply { dao.transactionsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("type",x.type).put("amountMinor",x.amountMinor).put("category",x.category).put("note",x.note).put("date",x.date).put("createdAt",x.createdAt).put("updatedAt",x.updatedAt)) } })
        root.put("loans", JSONArray().apply { dao.loansNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("direction",x.direction).put("person",x.person).put("principalMinor",x.principalMinor).putNullable("dueDate",x.dueDate).put("note",x.note).put("createdAt",x.createdAt).put("closed",x.closed).put("phone",x.phone).put("whatsapp",x.whatsapp)) } })
        root.put("loanPayments", JSONArray().apply { dao.loanPaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("loanId",ownedId(x.loanId)).put("amountMinor",x.amountMinor).put("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method)) } })
        root.put("creditPurchases", JSONArray().apply { dao.creditPurchasesNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("creditor",x.creditor).put("item",x.item).put("amountMinor",x.amountMinor).put("category",x.category).put("purchaseDate",x.purchaseDate).putNullable("dueDate",x.dueDate).put("note",x.note).put("createdAt",x.createdAt).put("closed",x.closed).put("phone",x.phone).put("whatsapp",x.whatsapp)) } })
        root.put("creditPayments", JSONArray().apply { dao.creditPaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("creditId",ownedId(x.creditId)).put("amountMinor",x.amountMinor).put("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method)) } })
        root.put("committees", JSONArray().apply { dao.committeesNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("name",x.name).put("monthlyAmountMinor",x.monthlyAmountMinor).put("totalInstallments",x.totalInstallments).put("startMonth",x.startMonth).putNullable("payoutInstallment",x.payoutInstallment).put("received",x.received).put("shares",x.shares).put("active",x.active).put("note",x.note).put("createdAt",x.createdAt).put("organizerPhone",x.organizerPhone).put("memberSchedule",x.memberSchedule)) } })
        root.put("committeePayments", JSONArray().apply { dao.committeePaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("installmentNumber",x.installmentNumber).put("month",x.month).put("amountMinor",x.amountMinor).put("paidAt",x.paidAt).put("method",x.method)) } })
        root.put("committeeReceipts", JSONArray().apply { dao.committeeReceiptsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("amountMinor",x.amountMinor).putNullable("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method)) } })
        root.put("committeeMembers", JSONArray().apply { dao.committeeMembersNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("name",x.name).put("phone",x.phone).put("whatsapp",x.whatsapp).put("turnNumber",x.turnNumber).put("turnMonth",x.turnMonth).put("isMe",x.isMe).put("received",x.received).putNullable("receivedDate",x.receivedDate).put("note",x.note).put("createdAt",x.createdAt)) } })
        root.put("organizerCircles", JSONArray().apply { dao.organizerCirclesNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("name",x.name).put("fullMonthlyMinor",x.fullMonthlyMinor).put("totalSlots",x.totalSlots).put("startMonth",x.startMonth).put("createdAt",x.createdAt)) } })
        root.put("organizerMembers", JSONArray().apply { dao.organizerMembersNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("circleId",ownedId(x.circleId)).put("name",x.name).put("phone",x.phone).put("whatsapp",x.whatsapp).put("halfUnits",x.halfUnits).put("turnNumber",x.turnNumber).put("createdAt",x.createdAt)) } })
        root.put("organizerCollections", JSONArray().apply { dao.organizerCollectionsNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("circleId",ownedId(x.circleId)).put("memberId",ownedId(x.memberId)).put("month",x.month).put("amountMinor",x.amountMinor).put("date",x.date).put("method",x.method).put("createdAt",x.createdAt)) } })
        root.put("organizerPayouts", JSONArray().apply { dao.organizerPayoutsNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("circleId",ownedId(x.circleId)).put("memberId",ownedId(x.memberId)).put("turnNumber",x.turnNumber).put("amountMinor",x.amountMinor).put("date",x.date).put("method",x.method).put("createdAt",x.createdAt)) } })
        root.put("savings", JSONArray().apply { dao.savingsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("kind",x.kind).put("amountMinor",x.amountMinor).put("date",x.date).put("note",x.note).put("createdAt",x.createdAt)) } })
        root.put("ledgerCycle", JSONObject().put("startAt", cycle.startAt).put("openingBalanceMinor", cycle.openingBalanceMinor))
        root.put("ledgerArchives", JSONArray().apply {
            dao.ledgerArchivesNow(ownerId).sortedBy { it.archiveKey }.forEach { x ->
                put(JSONObject().put("id", ownedId(x.id)).put("archiveKey", x.archiveKey).put("label", x.label)
                    .put("startAt", x.startAt).put("endAt", x.endAt).put("salaryMinor", x.salaryMinor)
                    .put("otherIncomeMinor", x.otherIncomeMinor).put("expensesMinor", x.expensesMinor)
                    .put("committeeMinor", x.committeeMinor).put("savingsMinor", x.savingsMinor)
                    .put("openingBalanceMinor", x.openingBalanceMinor).put("closingBalanceMinor", x.closingBalanceMinor)
                    .put("archivedAt", x.archivedAt))
            }
        })
        root.toString()
    }

    suspend fun clearAllForOwner() = db.withTransaction {
        dao.clearTransactions(ownerId)
        dao.clearLoans(ownerId)
        dao.clearLoanPayments(ownerId)
        dao.clearCreditPurchases(ownerId)
        dao.clearCreditPayments(ownerId)
        dao.clearCommittees(ownerId)
        dao.clearCommitteePayments(ownerId)
        dao.clearCommitteeReceipts(ownerId)
        dao.clearCommitteeMembers(ownerId)
        dao.clearOrganizerPayouts(ownerId)
        dao.clearOrganizerCollections(ownerId)
        dao.clearOrganizerMembers(ownerId)
        dao.clearOrganizerCircles(ownerId)
        dao.clearSavings(ownerId)
        dao.clearLedgerCycle(ownerId)
        dao.clearLedgerArchives(ownerId)
        dao.insertLedgerCycle(
            LedgerCycleEntity(
                ownerId,
                YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                0L,
            ),
        )
    }

    suspend fun importJson(json: String) = db.withTransaction {
        val root = JSONObject(json)
        val version = root.optInt("version")
        require(version in 1..7) { "Unsupported backup version. Update the app to restore it." }
        require(version == 1 || root.optJSONArray("committeeReceipts") != null) { "Backup is missing kameti receipts; your local data was not changed." }
        if (version >= 3) require(root.optJSONArray("committeeMembers") != null) { "Backup is missing kameti members; your local data was not changed." }
        if (version >= 4) {
            require(root.optJSONArray("creditPurchases") != null && root.optJSONArray("creditPayments") != null) { "Backup is missing udhar records; your local data was not changed." }
        }
        if (version >= 7) {
            require(listOf("organizerCircles", "organizerMembers", "organizerCollections", "organizerPayouts").all { root.optJSONArray(it) != null }) { "Backup has missing organizer Kameti records." }
        }
        if (version >= 5) {
            require(root.optJSONObject("ledgerCycle") != null && root.optJSONArray("ledgerArchives") != null) { "Backup is missing salary-cycle data; your local data was not changed." }
        }
        listOf("transactions", "loans", "loanPayments", "committees", "committeePayments", "savings").forEach {
            require(root.optJSONArray(it) != null) { "Backup is incomplete; your local data was not changed." }
        }
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        val tx = root.getJSONArray("transactions")
        val loans = root.getJSONArray("loans")
        val lp = root.getJSONArray("loanPayments")
        val creditPurchases = if (version >= 4) root.getJSONArray("creditPurchases") else JSONArray()
        val creditPayments = if (version >= 4) root.getJSONArray("creditPayments") else JSONArray()
        val committees = root.getJSONArray("committees")
        val cp = root.getJSONArray("committeePayments")
        val savings = root.getJSONArray("savings")

        dao.clearOrganizerPayouts(ownerId)
        dao.clearOrganizerCollections(ownerId)
        dao.clearOrganizerMembers(ownerId)
        dao.clearOrganizerCircles(ownerId)
        dao.clearCommitteeMembers(ownerId)
        dao.clearLedgerCycle(ownerId)
        dao.clearLedgerArchives(ownerId)
        dao.clearCommitteeReceipts(ownerId)
        dao.clearCreditPayments(ownerId)
        dao.clearCreditPurchases(ownerId)
        dao.clearLoanPayments(ownerId)
        dao.clearCommitteePayments(ownerId)
        dao.clearTransactions(ownerId)
        dao.clearLoans(ownerId)
        dao.clearCommittees(ownerId)
        dao.clearSavings(ownerId)

        val restoredTransactions = tx.objects().map { o ->
            TransactionEntity(
                ownedId(o.getString("id")), ownerId, o.getString("type"), o.getLong("amountMinor"),
                o.optString("category"), o.optString("note"), o.getString("date"), o.getLong("createdAt"),
                o.optLong("updatedAt", o.getLong("createdAt")),
            )
        }
        require(restoredTransactions.map { it.id }.distinct().size == restoredTransactions.size) {
            "Duplicate transaction IDs in backup. Your local data was not changed."
        }
        dao.insertTransactions(restoredTransactions)
        require(dao.transactionsNow(ownerId).size == restoredTransactions.size) {
            "Not all transactions were restored. Your local data was not changed."
        }
        dao.insertLoans(loans.objects().map { o -> LoanEntity(ownedId(o.getString("id")),ownerId,o.getString("direction"),o.optString("person"),o.getLong("principalMinor"),o.optString("dueDate").takeIf{it.isNotBlank()&&it!="null"},o.optString("note"),o.getLong("createdAt"),o.optBoolean("closed"),o.optString("phone"),o.optString("whatsapp")) })
        dao.insertLoanPayments(lp.objects().map { o -> LoanPaymentEntity(ownedId(o.getString("id")),ownerId,ownedId(o.getString("loanId")),o.getLong("amountMinor"),o.getString("date"),o.optString("note"),o.getLong("createdAt"),o.optString("method","Cash")) })
        val restoredCreditPurchases = creditPurchases.objects().map { o -> CreditPurchaseEntity(ownedId(o.getString("id")),ownerId,o.optString("creditor","Unknown shop/person"),o.optString("item","Saman"),o.getLong("amountMinor"),o.optString("category","Other"),o.getString("purchaseDate"),o.optString("dueDate").takeIf{it.isNotBlank()&&it!="null"},o.optString("note"),o.getLong("createdAt"),o.optBoolean("closed"),o.optString("phone"),o.optString("whatsapp")) }
        require(restoredCreditPurchases.map { it.id }.distinct().size == restoredCreditPurchases.size) { "Duplicate udhar purchases in backup." }
        restoredCreditPurchases.forEach { c -> CreditDraft(c.creditor, c.item, c.amountMinor, c.category, c.purchaseDate, c.dueDate, c.note, c.phone, c.whatsapp).validated() }
        dao.insertCreditPurchases(restoredCreditPurchases)
        val restoredCreditPayments = creditPayments.objects().map { o -> CreditPaymentEntity(ownedId(o.getString("id")),ownerId,ownedId(o.getString("creditId")),o.getLong("amountMinor"),o.getString("date"),o.optString("note"),o.getLong("createdAt"),o.optString("method","Cash")) }
        require(restoredCreditPayments.map { it.id }.distinct().size == restoredCreditPayments.size) { "Duplicate udhar payments in backup." }
        restoredCreditPayments.forEach { p -> require(p.amountMinor > 0 && restoredCreditPurchases.any { it.id == p.creditId }); LocalDate.parse(p.date) }
        restoredCreditPayments.groupBy { it.creditId }.forEach { (creditId, rows) ->
            val credit = restoredCreditPurchases.first { it.id == creditId }
            require(rows.fold(0L) { total, payment -> Math.addExact(total, payment.amountMinor) } <= credit.amountMinor) { "Udhar payments exceed the purchase amount in backup." }
        }
        dao.insertCreditPayments(restoredCreditPayments)
        restoredCreditPurchases.forEach { c ->
            val paid = restoredCreditPayments.filter { it.creditId == c.id }.sumOf { it.amountMinor }
            dao.upsertCreditPurchase(c.copy(closed = paid >= c.amountMinor))
        }
        dao.insertCommittees(committees.objects().map { o -> CommitteeEntity(ownedId(o.getString("id")),ownerId,o.optString("name"),o.getLong("monthlyAmountMinor"),o.getInt("totalInstallments"),o.getString("startMonth"),if(o.isNull("payoutInstallment")) null else o.getInt("payoutInstallment"),o.optBoolean("received"),o.optBoolean("active",true),o.optString("note"),o.getLong("createdAt"),o.optInt("shares",1),o.optString("organizerPhone"),o.optString("memberSchedule")) })
        dao.insertCommitteePayments(cp.objects().map { o -> CommitteePaymentEntity(ownedId(o.getString("id")),ownerId,ownedId(o.getString("committeeId")),o.getInt("installmentNumber"),o.getString("month"),o.getLong("amountMinor"),o.getLong("paidAt"),o.optString("method","Cash")) })
        dao.insertSavings(savings.objects().map { o -> SavingEntity(ownedId(o.getString("id")),ownerId,o.getString("kind"),o.getLong("amountMinor"),o.getString("date"),o.optString("note"),o.getLong("createdAt")) })
        val defaultStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val restoredCycle = root.optJSONObject("ledgerCycle")?.let { o ->
            LedgerCycleEntity(ownerId, o.getLong("startAt"), o.getLong("openingBalanceMinor"))
        } ?: LedgerCycleEntity(ownerId, defaultStart, 0L)
        require(restoredCycle.startAt > 0L) { "Invalid salary-cycle data in backup." }
        dao.upsertLedgerCycle(restoredCycle)
        val restoredArchives = if (version >= 5) root.getJSONArray("ledgerArchives").objects().map { o ->
            LedgerArchiveEntity(
                id = ownedId(o.getString("id")), ownerId = ownerId, archiveKey = o.getString("archiveKey"),
                label = o.getString("label"), startAt = o.getLong("startAt"), endAt = o.getLong("endAt"),
                salaryMinor = o.getLong("salaryMinor"), otherIncomeMinor = o.getLong("otherIncomeMinor"),
                expensesMinor = o.getLong("expensesMinor"), committeeMinor = o.getLong("committeeMinor"),
                savingsMinor = o.getLong("savingsMinor"), openingBalanceMinor = o.getLong("openingBalanceMinor"),
                closingBalanceMinor = o.getLong("closingBalanceMinor"), archivedAt = o.getLong("archivedAt"),
            )
        } else emptyList()
        require(restoredArchives.map { it.archiveKey }.distinct().size == restoredArchives.size) { "Duplicate archived periods in backup." }
        restoredArchives.forEach { archive ->
            require(archive.archiveKey.isNotBlank() && archive.startAt <= archive.endAt)
            require(listOf(archive.salaryMinor, archive.otherIncomeMinor, archive.expensesMinor, archive.committeeMinor, archive.savingsMinor).all { it >= 0L })
            val expensesAndContributions = Math.addExact(archive.expensesMinor, Math.addExact(archive.committeeMinor, archive.savingsMinor))
            val expected = Math.subtractExact(
                Math.addExact(Math.addExact(archive.openingBalanceMinor, archive.salaryMinor), if (version >= 6) archive.otherIncomeMinor else 0L),
                expensesAndContributions,
            )
            val legacyExpected = Math.subtractExact(
                Math.addExact(archive.openingBalanceMinor, archive.salaryMinor),
                expensesAndContributions,
            )
            require(archive.closingBalanceMinor == expected || (version >= 6 && archive.closingBalanceMinor == legacyExpected)) {
                "Archived balance does not match its totals."
            }
        }
        dao.insertLedgerArchives(restoredArchives)

        val savedCommittees = dao.committeesNow(ownerId)
        savedCommittees.forEach { require(it.shares > 0 && it.totalInstallments > 0 && it.monthlyAmountMinor > 0); it.expectedPayout() }
        val receipts = if (version == 1) {
            savedCommittees.filter { it.received }.map { CommitteeReceiptEntity(it.id + "-payout", ownerId, it.id, it.expectedPayout(), null, "Earlier received status", it.createdAt) }
        } else {
            root.getJSONArray("committeeReceipts").objects().map { o ->
                CommitteeReceiptEntity(ownedId(o.getString("id")), ownerId, ownedId(o.getString("committeeId")), o.getLong("amountMinor"), o.optString("date").takeIf { it.isNotBlank() && it != "null" }, o.optString("note"), o.getLong("createdAt"), o.optString("method","Cash"))
            }
        }
        require(receipts.map { it.id }.distinct().size == receipts.size) { "Duplicate kameti receipts in backup." }
        receipts.forEach { receipt ->
            require(receipt.amountMinor > 0 && savedCommittees.any { it.id == receipt.committeeId }) { "Invalid kameti receipt in backup." }
            receipt.date?.let { LocalDate.parse(it) }
        }
        dao.insertCommitteeReceipts(receipts)

        if (version >= 3) {
            val members = root.getJSONArray("committeeMembers").objects().map { o ->
                CommitteeMemberEntity(
                    id = ownedId(o.getString("id")),
                    ownerId = ownerId,
                    committeeId = ownedId(o.getString("committeeId")),
                    name = o.optString("name"),
                    phone = o.optString("phone"),
                    whatsapp = o.optString("whatsapp"),
                    turnNumber = o.getInt("turnNumber"),
                    turnMonth = o.getString("turnMonth"),
                    isMe = o.optBoolean("isMe"),
                    received = o.optBoolean("received"),
                    receivedDate = o.optString("receivedDate").takeIf { it.isNotBlank() && it != "null" },
                    note = o.optString("note"),
                    createdAt = o.getLong("createdAt"),
                )
            }
            require(members.map { it.id }.distinct().size == members.size) { "Duplicate kameti members in backup." }
            members.forEach { m ->
                val committee = savedCommittees.firstOrNull { it.id == m.committeeId } ?: error("Kameti member refers to a missing kameti.")
                require(m.turnNumber in 1..committee.totalInstallments)
                require(YearMonth.parse(m.turnMonth) == YearMonth.parse(committee.startMonth).plusMonths((m.turnNumber - 1).toLong()))
                m.receivedDate?.let { LocalDate.parse(it) }
            }
            require(members.groupBy { it.committeeId to it.turnNumber }.values.none { it.size > 1 }) { "Duplicate kameti turns in backup." }
            savedCommittees.forEach { committee ->
                require(members.count { it.committeeId == committee.id && it.isMe } <= committee.shares) { "Too many personal kameti turns in backup." }
            }
            dao.insertCommitteeMembers(members)
        }

        savedCommittees.forEach { committee ->
            val balance = committeeBalance(committee, receipts)
            require(balance.received <= balance.expected) { "Kameti receipts exceed the expected payout." }
            dao.upsertCommittee(committee.copy(received = balance.complete))
        }
    }

    companion object {
        fun today() = LocalDate.now().toString()
        fun thisMonth() = YearMonth.now().toString()
    }
}
