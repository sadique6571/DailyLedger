package com.sadique.dailyledger.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import com.sadique.dailyledger.ai.LearningSnapshot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class LedgerRepository(private val db: AppDatabase, val ownerId: String,
    private val legacyLearning: (() -> LearningSnapshot)? = null) {
    internal val database get() = db
    val finance = FinanceRepository(this)
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

    val profileBackup = dao.observeProfileBackup(ownerId).distinctUntilChanged().map { it?.let { row -> ProfileBackupData.decode(JSONObject(row.payload)) } ?: ProfileBackupData() }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    suspend fun profileSnapshot(): ProfileBackupData = db.withTransaction {
        dao.profileBackupNow(ownerId)?.let { ProfileBackupData.decode(JSONObject(it.payload)) } ?: ProfileBackupData.legacy(db.backupContext, ownerId).also {
            dao.upsertProfileBackup(ProfileBackupEntity(ownerId, it.json().toString()))
        }
    }
    suspend fun updateProfile(change: (ProfileBackupData) -> ProfileBackupData): ProfileBackupData = db.withTransaction {
        change(profileSnapshot()).validated().also { dao.upsertProfileBackup(ProfileBackupEntity(ownerId, it.json().toString())) }
    }
    val aiLearning = dao.observeAiLearning(ownerId).distinctUntilChanged().map {
        it?.let { row -> LearningSnapshot.decode(JSONObject(row.payload)) } ?: LearningSnapshot()
    }
    suspend fun learningSnapshot(): LearningSnapshot = db.withTransaction {
        val row = dao.aiLearningNow(ownerId)
        if (row != null) LearningSnapshot.decode(JSONObject(row.payload)) else {
            val migrated = (legacyLearning?.invoke() ?: LearningSnapshot()).validated()
            dao.upsertAiLearning(AiLearningEntity(ownerId, migrated.json().toString()))
            migrated
        }
    }
    suspend fun updateLearning(change: (LearningSnapshot) -> LearningSnapshot) = db.withTransaction {
        val next = change(learningSnapshot()).validated()
        dao.upsertAiLearning(AiLearningEntity(ownerId, next.json().toString()))
    }

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
            loans = dao.loansNow(ownerId), loanPayments = dao.loanPaymentsNow(ownerId),
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
        val summary = monthlySalarySummary(monthTransactions, month.toString(), monthCommitteePayments, monthSavings, monthCreditPayments, loans = dao.loansNow(ownerId), loanPayments = dao.loanPaymentsNow(ownerId))
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
        val row = (existing ?: TransactionEntity(newId(), ownerId, type, amountMinor, category.trim(), note.trim(), date, now, now)).copy(
            type=type, amountMinor=amountMinor, category=category.trim(), note=note.trim(), date=date, updatedAt=now)
        saveTransactionRow(row, existing?.updatedAt)
    }

    suspend fun saveDraftBatch(drafts: List<TransactionDraft>, batchId: String) = db.withTransaction {
        require(drafts.size in 1..com.sadique.dailyledger.ai.AiProtocol.MAX_DRAFTS)
        UUID.fromString(batchId)
        val checked = drafts.map { it.validated() }
        val now = System.currentTimeMillis()
        val rows = checked.mapIndexed { i, d -> TransactionEntity("$ownerId:ai-$batchId-$i", ownerId, d.type, d.amountMinor, d.category, d.note, d.date, now, now) }
        val existing = dao.transactionsNow(ownerId).filter { old -> rows.any { it.id == old.id } }
        if (existing.isNotEmpty()) {
            require(existing.size == rows.size && rows.all { r -> existing.any { it.id == r.id && it.type == r.type && it.amountMinor == r.amountMinor && it.category == r.category && it.note == r.note && it.date == r.date } }) {
                "This draft was already saved. Start a new Auto Fill entry."
            }
        } else saveNewBatchRows(rows)
    }

    /** New standalone rows are validated once and append history once, rather than rewriting the profile per item. */
    internal suspend fun saveNewBatchRows(rows:List<TransactionEntity>) = db.withTransaction {
        require(rows.size in 1..100 && rows.map{it.id}.distinct().size==rows.size)
        val profile=profileSnapshot();val existing=dao.transactionsNow(ownerId).map{it.id}.toSet()
        rows.forEach { row ->
            require(row.ownerId==ownerId && row.id.startsWith("$ownerId:") && row.id.length<=250 && row.id !in existing)
            require(row.type in listOf("INCOME","EXPENSE") && row.amountMinor in 1..1_000_000_000_000L)
            require(!row.privateEntry && row.refundOf.isBlank() && row.account in profile.workspace.accounts)
            require(row.category.isNotBlank() && row.category.length<=60 && row.note.length<=1500 && row.quantity.length<=30 && row.unit.length<=30)
            LocalDate.parse(row.date)
            if(row.quantity.isNotBlank())require(row.unit.isNotBlank() && quantityAmount(row.quantity,row.unitPriceMinor)==row.amountMinor)
        }
        val revisions=rows.map{row->EntryRevision(UUID.randomUUID().toString(),row.id,System.currentTimeMillis(),"CREATE","",transactionSnapshot(row))}
        val next=profile.copy(workspace=profile.workspace.copy(revisions=profile.workspace.revisions+revisions)).validated()
        dao.insertTransactions(rows)
        dao.upsertProfileBackup(ProfileBackupEntity(ownerId,next.json().toString()))
    }
    internal suspend fun saveTransactionRow(row: TransactionEntity, expectedUpdatedAt:Long?=null) = db.withTransaction {
        require(row.ownerId==ownerId && row.amountMinor in 1..1_000_000_000_000L && row.type in listOf("INCOME","EXPENSE"))
        require(row.category.isNotBlank() && row.category.length<=60 && row.note.length<=1500)
        LocalDate.parse(row.date);val records=dao.transactionsNow(ownerId);val old=records.find { it.id==row.id }
        if(old==null)require(row.id.startsWith("$ownerId:")) { "New entry ID does not belong to this account." }
        if(expectedUpdatedAt!=null) require(old!=null && old.updatedAt==expectedUpdatedAt) { "Entry changed. Reopen it before saving." }
        val profile=profileSnapshot();require(row.account in profile.workspace.accounts)
        if(row.quantity.isNotBlank()) require(row.unit.isNotBlank() && quantityAmount(row.quantity,row.unitPriceMinor)==row.amountMinor) { "Quantity × rate must equal the amount. Edit quantity/rate in Money tools." }
        require(row.unit.length<=30 && row.quantity.length<=30 && row.id.length in 1..250)
        if(row.refundOf.isNotBlank()) {
            val parent=requireNotNull(records.find { it.id==row.refundOf && it.type=="EXPENSE" && it.refundOf.isBlank() }) { "Refund purchase is missing." }
            require(row.type=="EXPENSE" && row.category==parent.category && (!parent.privateEntry || row.privateEntry))
            require(records.filter { it.id!=row.id && it.refundOf==parent.id }.sumOf { it.amountMinor }+row.amountMinor<=parent.amountMinor) { "Refund exceeds the purchase amount." }
        } else {
            val refunds=records.filter { it.refundOf==row.id }
            require(refunds.isEmpty() || (row.type=="EXPENSE" && refunds.sumOf { it.amountMinor }<=row.amountMinor && refunds.all { it.category==row.category && (!row.privateEntry || it.privateEntry) })) { "Correct linked refunds before changing this purchase." }
        }
        if(row.privateEntry)require(profile.workspace.items.none { it.transactionId==row.id || (it.kind==FinanceKind.REIMBURSEMENT && it.reference==row.id) }) { "Linked planning or claim records remain public. Use a standalone entry for private records." }
        val linkedPlan=profile.workspace.items.find { it.transactionId==row.id && it.kind in listOf(FinanceKind.SHOPPING,FinanceKind.PLANNED,FinanceKind.INSTALLMENT,FinanceKind.INSTALLMENT_PURCHASE) }
        if(linkedPlan!=null) require(row.type=="EXPENSE" && row.refundOf.isBlank() && row.amountMinor==(if(linkedPlan.kind==FinanceKind.INSTALLMENT_PURCHASE)linkedPlan.opening else linkedPlan.amount)) { "Delete and re-record this planned payment to change its amount." }
        val linkedReceipt=profile.workspace.items.find { it.kind==FinanceKind.CLAIM_RECEIPT && it.transactionId==row.id }
        if(linkedReceipt!=null)require(row.type=="INCOME" && row.amountMinor==linkedReceipt.amount) { "Delete and re-record a claim receipt to change its amount." }
        val linkedClaim=profile.workspace.items.find { it.kind==FinanceKind.REIMBURSEMENT && it.reference==row.id }
        if(linkedClaim!=null)require(row.type=="EXPENSE" && row.refundOf.isBlank() && row.amountMinor==linkedClaim.amount) { "This amount belongs to a reimbursement claim." }
        val revision=EntryRevision(UUID.randomUUID().toString(),row.id,System.currentTimeMillis(),if(old==null) "CREATE" else "EDIT",old?.let(::transactionSnapshot).orEmpty(),transactionSnapshot(row))
        updateProfile { it.copy(workspace=it.workspace.copy(revisions=it.workspace.revisions+revision,items=it.workspace.items.map { item -> if(item.transactionId==row.id && item.kind!=FinanceKind.INSTALLMENT_PURCHASE) item.copy(date=if(item.kind in listOf(FinanceKind.SHOPPING,FinanceKind.PLANNED,FinanceKind.INSTALLMENT))item.date else row.date,account=row.account,settled=if(item.kind==FinanceKind.CLAIM_RECEIPT)item.settled else row.amountMinor) else item })) }
        dao.upsertTransaction(row)
        if(profile.workspace.items.any{it.kind==FinanceKind.INSTALLMENT_PURCHASE}) { val actual=dao.transactionsNow(ownerId); updateProfile { it.copy(workspace=it.workspace.reconcileInstallments(actual)) } }
    }
    suspend fun deleteTransaction(item: TransactionEntity) = db.withTransaction {
        require(item.ownerId==ownerId);val records=dao.transactionsNow(ownerId);val current=records.find { it.id==item.id } ?: return@withTransaction
        require(records.none { it.refundOf==current.id }) { "Delete linked refunds before deleting the purchase." }
        val w=profileSnapshot().workspace
        require(w.items.none { it.kind==FinanceKind.REIMBURSEMENT && it.reference==current.id }) { "This entry belongs to a reimbursement claim." }
        val revision=EntryRevision(UUID.randomUUID().toString(),current.id,System.currentTimeMillis(),"DELETE",transactionSnapshot(current),"")
        val receipt=w.items.find { it.kind==FinanceKind.CLAIM_RECEIPT && it.transactionId==current.id }
        updateProfile { it.copy(workspace=w.copy(revisions=w.revisions+revision,items=w.items.filterNot { r -> r.id==receipt?.id }.map { r ->
            when { receipt!=null && r.id==receipt.reference -> r.copy(settled=r.settled-receipt.amount,done=false)
                r.transactionId==current.id -> r.copy(transactionId="",done=false,settled=0)
                else -> r }
        })) }
        dao.deleteTransaction(current)
        if(w.items.any{it.kind==FinanceKind.INSTALLMENT_PURCHASE}) { val actual=dao.transactionsNow(ownerId); updateProfile { it.copy(workspace=it.workspace.reconcileInstallments(actual)) } }
    }

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
        pruneProofs()
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
        pruneProofs()
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

    suspend fun saveCommittee(name: String, monthlyMinor: Long, total: Int, startMonth: String, payout: Int?, note: String, shares: Int = 1, organizerPhone: String = "", memberSchedule: String = "", sharesHalfUnits: Int = shares * 2) {
        require(monthlyMinor > 0 && total > 0 && sharesHalfUnits > 0 && sharesHalfUnits <= total * 2) { "Enter valid Kameti shares and installments." }
        require(sharesHalfUnits % 2 == 0 || monthlyMinor % 2 == 0L) { "This amount cannot be split exactly into two halves." }
        Math.multiplyExact(Math.addExact(Math.multiplyExact(monthlyMinor, (sharesHalfUnits / 2).toLong()), if (sharesHalfUnits % 2 == 1) monthlyMinor / 2 else 0L), total.toLong())
        require(payout == null || payout in 1..total) { "Payout installment must be within the total installments." }
        YearMonth.parse(startMonth)
        dao.upsertCommittee(CommitteeEntity(newId(), ownerId, name.trim().ifBlank { "Kameti" }, monthlyMinor, total, startMonth, payout, false, true, note.trim(), System.currentTimeMillis(), shares, organizerPhone.trim(), memberSchedule.trim(), sharesHalfUnits))
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

    /** Receive money for one identified personal turn. Legacy unassigned receipts remain untouched. */
    suspend fun addPersonalTurnReceipt(memberId: String, amountMinor: Long, date: String, note: String, method: String) = db.withTransaction {
        val member = requireNotNull(dao.committeeMemberNow(ownerId, memberId)) { "Turn not found." }
        require(member.isMe) { "Select one of your own turns." }
        val committee = requireNotNull(dao.committeeNow(ownerId, member.committeeId)) { "Kameti not found." }
        val perMonth = Math.addExact(Math.multiplyExact(committee.monthlyAmountMinor, (member.shareHalfUnits / 2).toLong()),
            if (member.shareHalfUnits == 1) committee.monthlyAmountMinor / 2 else 0L)
        val expected = Math.multiplyExact(perMonth, committee.totalInstallments.toLong())
        val previous = dao.committeeReceiptsNow(ownerId).filter { it.memberId == member.id }.sumOf { it.amountMinor }
        val totalBalance = committeeBalance(committee, dao.committeeReceiptsNow(ownerId))
        require(amountMinor > 0 && amountMinor <= expected - previous && amountMinor <= totalBalance.remaining) {
            "Receiving exceeds this turn's remaining share."
        }
        LocalDate.parse(date)
        dao.upsertCommitteeReceipt(CommitteeReceiptEntity(newId(), ownerId, committee.id, amountMinor, date,
            note.trim(), System.currentTimeMillis(), method.ifBlank { "Cash" }, member.id))
        if (previous + amountMinor == expected) dao.upsertCommitteeMember(member.copy(received = true, receivedDate = date))
        if (totalBalance.received + amountMinor == totalBalance.expected) dao.upsertCommittee(committee.copy(received = true))
    }

    suspend fun deleteCommitteeReceipt(receipt: CommitteeReceiptEntity) = db.withTransaction {
        require(receipt.ownerId == ownerId) { "This receipt belongs to another account." }
        dao.deleteCommitteeReceipt(receipt)
        if (receipt.memberId.isNotBlank()) dao.committeeMemberNow(ownerId, receipt.memberId)?.let { member ->
            val committee = dao.committeeNow(ownerId, member.committeeId)
            if (committee != null) {
                val perMonth = (committee.monthlyAmountMinor * (member.shareHalfUnits / 2)) +
                    (if (member.shareHalfUnits == 1) committee.monthlyAmountMinor / 2 else 0L)
                val total = Math.multiplyExact(perMonth, committee.totalInstallments.toLong())
                val received = dao.committeeReceiptsNow(ownerId).filter { it.memberId == member.id }.sumOf { it.amountMinor }
                dao.upsertCommitteeMember(member.copy(received = received >= total, receivedDate = if (received >= total) member.receivedDate else null))
            }
        }
        dao.committeeNow(ownerId, receipt.committeeId)?.let { committee ->
            dao.upsertCommittee(committee.copy(received = committeeBalance(committee, dao.committeeReceiptsNow(ownerId)).complete))
        }
    }

    suspend fun saveCommitteeMember(
        committeeId: String, name: String, phone: String, whatsapp: String, turnNumber: Int,
        turnMonth: String, isMe: Boolean, note: String, halfUnits: Int = 2,
    ) = saveCommitteeMembers(committeeId,
        listOf(PersonalTurnDraft(name, phone, whatsapp, turnNumber, turnMonth, isMe, note, halfUnits)), UUID.randomUUID().toString())

    suspend fun saveCommitteeMembers(committeeId: String, drafts: List<PersonalTurnDraft>, requestId: String) = db.withTransaction {
        val committee = requireNotNull(dao.committeeNow(ownerId, committeeId)) { "This kameti no longer exists." }
        val checked = drafts.map { it.validated() }
        val batch = KametiBatch(ownerId, "personal-turn", requestId, committeeId, checked.map { it.fields() })
        val all = dao.committeeMembersNow(ownerId)
        if (batch.alreadySaved(all.map { it.id })) return@withTransaction
        val members = all.filter { it.committeeId == committeeId }.toMutableList()
        val now = System.currentTimeMillis()
        val rows = checked.mapIndexed { index, d ->
            require(d.turn in 1..committee.totalInstallments) { "Turn number must be between 1 and ${committee.totalInstallments}." }
            val expected = YearMonth.parse(committee.startMonth).plusMonths((d.turn - 1).toLong())
            require(YearMonth.parse(d.month) == expected) { "Turn #${d.turn} should be $expected." }
            require(d.halfUnits in 1..2) { "Choose a half or full share." }
            require(d.halfUnits != 1 || committee.monthlyAmountMinor % 2L == 0L) { "This installment cannot be split into exact half shares." }
            require(members.filter { it.turnNumber == d.turn }.sumOf { it.shareHalfUnits } + d.halfUnits <= 2) { "Turn #${d.turn} has no room for this share." }
            if (d.isMe) require(members.filter { it.isMe }.sumOf { it.shareHalfUnits } + d.halfUnits <= committee.sharesHalfUnits) { "All your Kameti shares are already assigned." }
            CommitteeMemberEntity(batch.ids[index], ownerId, committeeId, d.name, d.phone, d.whatsapp,
                d.turn, expected.toString(), d.isMe, false, null, d.note, now, d.halfUnits).also { members.add(it) }
        }
        rows.forEach { dao.upsertCommitteeMember(it) }
    }

    suspend fun markCommitteeMemberReceived(member: CommitteeMemberEntity, date: String) = db.withTransaction {
        require(member.ownerId == ownerId)
        LocalDate.parse(date)
        val current = requireNotNull(dao.committeeMemberNow(ownerId, member.id)) { "This member no longer exists." }
        requireNotNull(dao.committeeNow(ownerId, current.committeeId)) { "This kameti no longer exists." }
        dao.upsertCommitteeMember(current.copy(received = true, receivedDate = date))
    }

    suspend fun deleteCommitteeMember(member: CommitteeMemberEntity) = db.withTransaction {
        require(member.ownerId == ownerId)
        val current = dao.committeeMemberNow(ownerId, member.id) ?: return@withTransaction
        require(dao.committeeReceiptsNow(ownerId).none { it.memberId == current.id }) {
            "This turn has receiving history. Delete or correct its receiving first."
        }
        dao.deleteCommitteeMember(current)
    }

    suspend fun deleteCommittee(item: CommitteeEntity) = db.withTransaction {
        require(item.ownerId == ownerId)
        dao.deleteCommitteePayments(ownerId, item.id)
        dao.deleteCommitteeReceipts(ownerId, item.id)
        dao.deleteCommitteeMembers(ownerId, item.id)
        dao.deleteCommittee(item)
        pruneProofs()
    }

    suspend fun saveSaving(kind: String, amountMinor: Long, date: String, note: String) {
        require(amountMinor > 0L)
        LocalDate.parse(date)
        dao.upsertSaving(SavingEntity(newId(), ownerId, kind, amountMinor, date, note.trim(), System.currentTimeMillis()))
    }

    suspend fun deleteSaving(item: SavingEntity) = db.withTransaction {
        require(item.ownerId==ownerId);val w=profileSnapshot().workspace
        val contribution=w.items.find { it.kind==FinanceKind.GOAL_CONTRIBUTION && it.transactionId==item.id }
        if(contribution!=null)updateProfile { it.copy(workspace=w.copy(items=w.items.filterNot { row -> row.id==contribution.id }.map { row -> if(row.kind==FinanceKind.GOAL && row.id==contribution.reference)row.copy(settled=row.settled-contribution.amount,done=false) else row })) }
        dao.deleteSaving(item)
    }

    private fun newId() = "$ownerId:${UUID.randomUUID()}"
    private fun ownedId(id: String) = "$ownerId:${id.substringAfterLast(':')}"

    suspend fun hasRecords(): Boolean = dao.recordCount(ownerId) > 0 ||
        (dao.ledgerCycleNow(ownerId)?.openingBalanceMinor ?: 0L) != 0L || learningSnapshot().hasContent || profileSnapshot().hasContent

    suspend fun migrateOwner(oldOwner: String) = db.withTransaction {
        if (oldOwner != ownerId) {
            val olderProfile = LedgerRepository(db, oldOwner).profileSnapshot()
            if (olderProfile.hasContent) {
                val current = profileSnapshot()
                val mergedPlans = current.planner.copy(
                    budgets = (current.planner.budgets + olderProfile.planner.budgets).distinctBy { it.category },
                    bills = (current.planner.bills + olderProfile.planner.bills).distinctBy { it.id },
                )
                val merged = (if (current.expected != null || current.salaryDay != null) current else olderProfile).copy(planner = mergedPlans, workspace=current.workspace.merge(olderProfile.workspace)).validated()
                dao.upsertProfileBackup(ProfileBackupEntity(ownerId, merged.json().toString()))
                dao.upsertProfileBackup(ProfileBackupEntity(oldOwner, ProfileBackupData().json().toString()))
            }
            dao.aiLearningNow(oldOwner)?.let { old ->
                val source = LearningSnapshot.decode(JSONObject(old.payload))
                if (source.hasContent) {
                    val merged = learningSnapshot().mergeOlder(source)
                    dao.upsertAiLearning(AiLearningEntity(ownerId, merged.json().toString()))
                    // Keep a migration marker so a future login cannot resurrect the old preference copy.
                    dao.upsertAiLearning(AiLearningEntity(oldOwner, LearningSnapshot().json().toString()))
                }
            }
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
            dao.migratePaymentProofs(oldOwner, ownerId)
            dao.migrateProofChunks(oldOwner, ownerId)
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
        val root = JSONObject().put("version", 11).put("ownerId", ownerId).put("exportedAt", System.currentTimeMillis())
        root.put("paymentProofs", JSONArray().apply { validProofs().sortedBy { ownedId(it.id) }.forEach { x ->
            put(JSONObject().put("id",ownedId(x.id)).put("paymentId",ownedId(x.paymentId)).put("kind",x.kind)
                .put("mimeType",x.mimeType).put("data",x.data).put("sizeBytes",x.sizeBytes).put("createdAt",x.createdAt))
        } })
        root.put("profileSettings", profileSnapshot().let { it.copy(workspace=it.workspace.remap(ownerId)) }.json())
        root.put("transactions", JSONArray().apply { dao.transactionsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("type",x.type).put("amountMinor",x.amountMinor).put("category",x.category).put("note",x.note).put("date",x.date).put("createdAt",x.createdAt).put("updatedAt",x.updatedAt).put("account",x.account).put("quantity",x.quantity).put("unit",x.unit).put("unitPriceMinor",x.unitPriceMinor).put("refundOf",if(x.refundOf.isBlank()) "" else ownedId(x.refundOf)).put("privateEntry",x.privateEntry)) } })
        root.put("loans", JSONArray().apply { dao.loansNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("direction",x.direction).put("person",x.person).put("principalMinor",x.principalMinor).putNullable("dueDate",x.dueDate).put("note",x.note).put("createdAt",x.createdAt).put("closed",x.closed).put("phone",x.phone).put("whatsapp",x.whatsapp)) } })
        root.put("loanPayments", JSONArray().apply { dao.loanPaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("loanId",ownedId(x.loanId)).put("amountMinor",x.amountMinor).put("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method)) } })
        root.put("creditPurchases", JSONArray().apply { dao.creditPurchasesNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("creditor",x.creditor).put("item",x.item).put("amountMinor",x.amountMinor).put("category",x.category).put("purchaseDate",x.purchaseDate).putNullable("dueDate",x.dueDate).put("note",x.note).put("createdAt",x.createdAt).put("closed",x.closed).put("phone",x.phone).put("whatsapp",x.whatsapp)) } })
        root.put("creditPayments", JSONArray().apply { dao.creditPaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("creditId",ownedId(x.creditId)).put("amountMinor",x.amountMinor).put("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method)) } })
        root.put("committees", JSONArray().apply { dao.committeesNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("name",x.name).put("monthlyAmountMinor",x.monthlyAmountMinor).put("totalInstallments",x.totalInstallments).put("startMonth",x.startMonth).putNullable("payoutInstallment",x.payoutInstallment).put("received",x.received).put("shares",x.shares).put("sharesHalfUnits",x.sharesHalfUnits).put("active",x.active).put("note",x.note).put("createdAt",x.createdAt).put("organizerPhone",x.organizerPhone).put("memberSchedule",x.memberSchedule)) } })
        root.put("committeePayments", JSONArray().apply { dao.committeePaymentsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("installmentNumber",x.installmentNumber).put("month",x.month).put("amountMinor",x.amountMinor).put("paidAt",x.paidAt).put("method",x.method)) } })
        root.put("committeeReceipts", JSONArray().apply { dao.committeeReceiptsNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("amountMinor",x.amountMinor).putNullable("date",x.date).put("note",x.note).put("createdAt",x.createdAt).put("method",x.method).put("memberId",ownedId(x.memberId).takeIf { x.memberId.isNotBlank() } ?: "")) } })
        root.put("committeeMembers", JSONArray().apply { dao.committeeMembersNow(ownerId).sortedBy { ownedId(it.id) }.forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("committeeId",ownedId(x.committeeId)).put("name",x.name).put("phone",x.phone).put("whatsapp",x.whatsapp).put("turnNumber",x.turnNumber).put("turnMonth",x.turnMonth).put("isMe",x.isMe).put("shareHalfUnits",x.shareHalfUnits).put("received",x.received).putNullable("receivedDate",x.receivedDate).put("note",x.note).put("createdAt",x.createdAt)) } })
        root.put("organizerCircles", JSONArray().apply { dao.organizerCirclesNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("name",x.name).put("fullMonthlyMinor",x.fullMonthlyMinor).put("totalSlots",x.totalSlots).put("startMonth",x.startMonth).put("createdAt",x.createdAt)) } })
        root.put("organizerMembers", JSONArray().apply { dao.organizerMembersNow(ownerId).forEach { x -> put(JSONObject().put("id",ownedId(x.id)).put("circleId",ownedId(x.circleId)).put("name",x.name).put("phone",x.phone).put("whatsapp",x.whatsapp).put("halfUnits",x.halfUnits).put("turnNumber",x.turnNumber).put("drawMethod",x.drawMethod).put("drawnAt",x.drawnAt).put("createdAt",x.createdAt)) } })
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
        root.put("aiLearning", learningSnapshot().json())
        root.toString()
    }

    suspend fun clearAllForOwner() = db.withTransaction {
        dao.clearPaymentProofs(ownerId)
        dao.clearProofChunks(ownerId)
        dao.upsertProfileBackup(ProfileBackupEntity(ownerId, ProfileBackupData().json().toString()))
        dao.upsertAiLearning(AiLearningEntity(ownerId, LearningSnapshot().json().toString()))
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
        require(version in 1..11) { "Unsupported backup version. Update the app to restore it." }
        if(version<10) {
            val currentWorkspace=profileSnapshot().workspace
            require(currentWorkspace.items.none { it.kind in listOf(FinanceKind.ACCOUNT,FinanceKind.TRANSFER,FinanceKind.ADJUSTMENT,FinanceKind.REIMBURSEMENT,FinanceKind.CLAIM_RECEIPT,FinanceKind.GOAL_CONTRIBUTION) || it.transactionId.isNotBlank() } && dao.transactionsNow(ownerId).none { it.privateEntry || it.account!="Cash" || it.quantity.isNotBlank() || it.refundOf.isNotBlank() }) { "This older backup does not include your Money tools/private data. Use a current backup to protect it." }
        }
        if(version<11) require(validProofs().isEmpty()) { "This backup does not include your payment proofs. Use a current backup to protect them." }
        if(version>=11) require(root.optJSONArray("paymentProofs")!=null) { "Backup is missing payment proofs." }
        // Validate learning before any delete/write; Room commits both ledger and learning together.
        val restoredProfile = if (version >= 9) ProfileBackupData.decode(root.getJSONObject("profileSettings")).let { it.copy(workspace=it.workspace.remap(ownerId)) } else null
        val restoredLearning = if (version >= 8) {
            require(root.optJSONObject("aiLearning") != null) { "Backup is missing AI learning. Your records were not changed." }
            LearningSnapshot.decode(root.getJSONObject("aiLearning"))
        } else null
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
                o.optString("account","Cash"),o.optString("quantity"),o.optString("unit"),o.optLong("unitPriceMinor"),
                o.optString("refundOf").let { if(it.isBlank()) "" else ownedId(it) },o.optBoolean("privateEntry"),
            )
        }
        require(restoredTransactions.map { it.id }.distinct().size == restoredTransactions.size) {
            "Duplicate transaction IDs in backup. Your local data was not changed."
        }
        restoredTransactions.forEach { row ->
            require(row.type == "INCOME" || row.type == "EXPENSE") { "Invalid transaction type in backup." }
            require(row.amountMinor in 1..1_000_000_000_000L) { "Invalid transaction amount in backup." }
            LocalDate.parse(row.date)
        }
        restoredTransactions.forEach { row ->
            if(row.quantity.isNotBlank()) require(row.unit.isNotBlank() && quantityAmount(row.quantity,row.unitPriceMinor)==row.amountMinor)
            require(row.unit.length<=30 && row.quantity.length<=30 && row.account in (restoredProfile?.workspace?.accounts ?: listOf("Cash","Bank","Easypaisa","JazzCash")))
            if(row.refundOf.isNotBlank()) {
                val parent=requireNotNull(restoredTransactions.find { it.id==row.refundOf && it.type=="EXPENSE" && it.refundOf.isBlank() })
                require(row.type=="EXPENSE" && row.category==parent.category && (!parent.privateEntry || row.privateEntry))
            }
        }
        restoredTransactions.filter { it.refundOf.isNotBlank() }.groupBy { it.refundOf }.forEach { (id,rows) -> require(rows.sumOf { it.amountMinor }<=restoredTransactions.first { it.id==id }.amountMinor) }
        restoredProfile?.workspace?.items?.forEach { item ->
            if(item.kind in listOf(FinanceKind.SHOPPING,FinanceKind.PLANNED,FinanceKind.INSTALLMENT)) {
                require(item.done==item.transactionId.isNotBlank())
                if(item.done)require(restoredTransactions.any { it.id==item.transactionId && it.type=="EXPENSE" && it.refundOf.isBlank() && it.amountMinor==item.amount })
            }
            if(item.transactionId.isNotBlank() && item.kind!=FinanceKind.GOAL_CONTRIBUTION)require(restoredTransactions.any { it.id==item.transactionId }) { "A tool item refers to a missing ledger entry." }
            if(item.kind==FinanceKind.REIMBURSEMENT)require(restoredTransactions.any { it.id==item.reference && it.type=="EXPENSE" && it.refundOf.isBlank() && it.amountMinor==item.amount }) { "Claim purchase is missing or inconsistent." }
        }
        restoredProfile?.workspace?.let { w -> w.items.filter { it.kind==FinanceKind.REIMBURSEMENT }.forEach { claim ->
            require(w.items.filter { it.kind==FinanceKind.CLAIM_RECEIPT && it.reference==claim.id }.sumOf { it.amount }==claim.settled) { "Claim receipts do not match its received total." }
        }; w.items.filter { it.kind==FinanceKind.CLAIM_RECEIPT }.forEach { receipt ->
            require(w.items.any { it.id==receipt.reference && it.kind==FinanceKind.REIMBURSEMENT })
            require(restoredTransactions.any { it.id==receipt.transactionId && it.type=="INCOME" && it.amountMinor==receipt.amount })
        } }
        restoredProfile?.workspace?.let { w ->
            w.items.filter{it.kind==FinanceKind.INSTALLMENT_PURCHASE}.forEach{root->
                val parts=w.items.filter{it.kind==FinanceKind.INSTALLMENT && it.reference==root.id}
                require(parts.isNotEmpty() && parts.sumOf{it.amount}+root.opening==root.amount)
            }
            w.items.filter{it.kind==FinanceKind.INSTALLMENT}.forEach{part->require(w.items.any{it.id==part.reference && it.kind==FinanceKind.INSTALLMENT_PURCHASE})}
            require(w.reconcileInstallments(restoredTransactions)==w) { "Installment paid totals are inconsistent." }
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
        dao.insertCommittees(committees.objects().map { o -> CommitteeEntity(ownedId(o.getString("id")),ownerId,o.optString("name"),o.getLong("monthlyAmountMinor"),o.getInt("totalInstallments"),o.getString("startMonth"),if(o.isNull("payoutInstallment")) null else o.getInt("payoutInstallment"),o.optBoolean("received"),o.optBoolean("active",true),o.optString("note"),o.getLong("createdAt"),o.optInt("shares",1),o.optString("organizerPhone"),o.optString("memberSchedule"),o.optInt("sharesHalfUnits",o.optInt("shares",1)*2)) })
        dao.insertCommitteePayments(cp.objects().map { o -> CommitteePaymentEntity(ownedId(o.getString("id")),ownerId,ownedId(o.getString("committeeId")),o.getInt("installmentNumber"),o.getString("month"),o.getLong("amountMinor"),o.getLong("paidAt"),o.optString("method","Cash")) })
        dao.insertSavings(savings.objects().map { o -> SavingEntity(ownedId(o.getString("id")),ownerId,o.getString("kind"),o.getLong("amountMinor"),o.getString("date"),o.optString("note"),o.getLong("createdAt")) })
        restoredProfile?.workspace?.let { w ->
            val savedSavings=dao.savingsNow(ownerId)
            w.items.filter { it.kind==FinanceKind.GOAL_CONTRIBUTION }.forEach { c ->
                require(savedSavings.any { it.id==c.transactionId && it.amountMinor==c.amount } && w.items.any { it.id==c.reference && it.kind==FinanceKind.GOAL })
            }
            w.items.filter { it.kind==FinanceKind.GOAL }.forEach { goal -> require(goal.opening+w.items.filter { it.kind==FinanceKind.GOAL_CONTRIBUTION && it.reference==goal.id }.sumOf { it.amount }==goal.settled) }
        }
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
            require(listOf(archive.salaryMinor, archive.otherIncomeMinor, archive.committeeMinor, archive.savingsMinor).all { it >= 0L })
            require(if(version>=10) archive.expensesMinor in -1_000_000_000_000_000L..1_000_000_000_000_000L else archive.expensesMinor>=0L)
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
                CommitteeReceiptEntity(ownedId(o.getString("id")), ownerId, ownedId(o.getString("committeeId")), o.getLong("amountMinor"), o.optString("date").takeIf { it.isNotBlank() && it != "null" }, o.optString("note"), o.getLong("createdAt"), o.optString("method","Cash"), o.optString("memberId").takeIf { it.isNotBlank() }?.let(::ownedId) ?: "")
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
                    shareHalfUnits = o.optInt("shareHalfUnits", 2),
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
            require(members.groupBy { it.committeeId to it.turnNumber }.values.all { rows -> rows.sumOf { it.shareHalfUnits } <= 2 }) { "Kameti turns exceed one full share." }
            if (restoredProfile != null) dao.upsertProfileBackup(ProfileBackupEntity(ownerId, restoredProfile.json().toString()))
            if (restoredLearning != null) dao.upsertAiLearning(AiLearningEntity(ownerId, restoredLearning.json().toString()))
        // Backups v1–v7 did not contain learning. Keep the current account's learning intact.

        if(version>=11) {
            val proofs=root.getJSONArray("paymentProofs").objects().map { o ->
                PaymentProofEntity(ownedId(o.getString("id")),ownerId,ownedId(o.getString("paymentId")),o.getString("kind"),
                    o.getString("mimeType"),o.getString("data"),o.getLong("sizeBytes"),o.getLong("createdAt"))
            }
            require(proofs.size<=200 && proofs.map{it.id}.distinct().size==proofs.size) { "Invalid proof list." }
            require(proofs.sumOf{it.sizeBytes.coerceIn(0,MAX_PROOF_BYTES.toLong())}<=MAX_ALL_PROOF_BYTES) { "Proof backup is too large." }
            proofs.forEach { validateProof(it) }
            dao.clearPaymentProofs(ownerId)
        dao.clearProofChunks(ownerId)
            persistProofs(proofs)
        }
        savedCommittees.forEach { committee ->
                require(committee.sharesHalfUnits > 0 && members.filter { it.committeeId == committee.id && it.isMe }.sumOf { it.shareHalfUnits } <= committee.sharesHalfUnits) { "Too many personal kameti shares in backup." }
            }
            dao.insertCommitteeMembers(members)
            if (version >= 7) {
                receipts.filter { it.memberId.isNotBlank() }.forEach { receipt ->
                    require(members.any { it.id == receipt.memberId && it.committeeId == receipt.committeeId && it.isMe }) {
                        "Personal Kameti receiving refers to a missing own turn."
                    }
                }
                receipts.filter { it.memberId.isNotBlank() }.groupBy { it.memberId }.forEach { (memberId, rows) ->
                    val member = members.first { it.id == memberId }
                    val committee = savedCommittees.first { it.id == member.committeeId }
                    val perMonth = Math.addExact(
                        Math.multiplyExact(committee.monthlyAmountMinor, (member.shareHalfUnits / 2).toLong()),
                        if (member.shareHalfUnits == 1) committee.monthlyAmountMinor / 2 else 0L,
                    )
                    require(rows.sumOf { it.amountMinor } <= Math.multiplyExact(perMonth, committee.totalInstallments.toLong())) {
                        "Personal Kameti turn is overpaid in the backup."
                    }
                }
            }
        }
        if (version >= 7) {
            val circles = root.getJSONArray("organizerCircles").objects().map { o ->
                OrganizerCircleEntity(ownedId(o.getString("id")), ownerId, o.getString("name"), o.getLong("fullMonthlyMinor"), o.getInt("totalSlots"), o.getString("startMonth"), o.getLong("createdAt"))
            }
            require(circles.map { it.id }.distinct().size == circles.size)
            circles.forEach { require(it.totalSlots in 2..120 && it.fullMonthlyMinor > 0 && it.fullMonthlyMinor % 2 == 0L && it.fullMonthlyMinor <= Long.MAX_VALUE / it.totalSlots); YearMonth.parse(it.startMonth) }
            val roster = root.getJSONArray("organizerMembers").objects().map { o ->
                OrganizerMemberEntity(ownedId(o.getString("id")), ownerId, ownedId(o.getString("circleId")), o.getString("name"), o.optString("phone"), o.optString("whatsapp"), o.getInt("halfUnits"), o.getInt("turnNumber"), o.getLong("createdAt"), o.optString("drawMethod", "MANUAL"), o.optLong("drawnAt",0L))
            }
            require(roster.map { it.id }.distinct().size == roster.size)
            roster.forEach { m ->
                val circle = circles.firstOrNull { it.id == m.circleId } ?: error("Missing Kameti for member.")
                require(m.name.isNotBlank() && m.halfUnits in 1..2 && m.turnNumber in 0..circle.totalSlots)
                require(m.drawMethod in setOf("MANUAL", "RANDOM") && m.drawnAt >= 0L)
            }
            circles.forEach { circle ->
                val subset = roster.filter { it.circleId == circle.id }
                require(subset.sumOf { it.halfUnits } <= circle.totalSlots * 2)
                (1..circle.totalSlots).forEach { require(filledTurnUnits(subset, it) <= 2) }
            }
            val collections = root.getJSONArray("organizerCollections").objects().map { o ->
                OrganizerCollectionEntity(ownedId(o.getString("id")), ownerId, ownedId(o.getString("circleId")), ownedId(o.getString("memberId")), o.getString("month"), o.getLong("amountMinor"), o.getString("date"), o.optString("method","Cash"), o.getLong("createdAt"))
            }
            require(collections.map { it.id }.distinct().size == collections.size)
            collections.forEach { x ->
                val m = roster.firstOrNull { it.id == x.memberId && it.circleId == x.circleId } ?: error("Collection has no member.")
                val circle = circles.first { it.id == x.circleId }
                require(x.amountMinor > 0)
                LocalDate.parse(x.date)
                val month = YearMonth.parse(x.month)
                val start = YearMonth.parse(circle.startMonth)
                require(!month.isBefore(start) && month.isBefore(start.plusMonths(circle.totalSlots.toLong())))
                require(m.halfUnits in 1..2)
            }
            collections.groupBy { it.memberId to it.month }.forEach { (key, rows) ->
                val m = roster.first { it.id == key.first }
                val circle = circles.first { it.id == m.circleId }
                require(rows.sumOf { it.amountMinor } <= halfShareMoney(circle.fullMonthlyMinor, m.halfUnits))
            }
            val payouts = root.getJSONArray("organizerPayouts").objects().map { o ->
                OrganizerPayoutEntity(ownedId(o.getString("id")), ownerId, ownedId(o.getString("circleId")), ownedId(o.getString("memberId")), o.getInt("turnNumber"), o.getLong("amountMinor"), o.getString("date"), o.optString("method","Cash"), o.getLong("createdAt"))
            }
            require(payouts.map { it.id }.distinct().size == payouts.size)
            payouts.forEach { x ->
                val m = roster.firstOrNull { it.id == x.memberId && it.circleId == x.circleId } ?: error("Payout has no member.")
                require(x.amountMinor > 0 && m.turnNumber == x.turnNumber)
                LocalDate.parse(x.date)
            }
            payouts.groupBy { it.memberId }.forEach { (memberId, rows) ->
                val m = roster.first { it.id == memberId }
                val circle = circles.first { it.id == m.circleId }
                require(rows.sumOf { it.amountMinor } <= Math.multiplyExact(halfShareMoney(circle.fullMonthlyMinor, m.halfUnits), circle.totalSlots.toLong()))
            }
            dao.insertOrganizerCircles(circles)
            dao.insertOrganizerMembers(roster)
            dao.insertOrganizerCollections(collections)
            dao.insertOrganizerPayouts(payouts)
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
