package com.sadique.dailyledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT ownerId FROM transactions WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM loans WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM loan_payments WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM committees WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM committee_payments WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM committee_receipts WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM committee_members WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM savings WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM credit_purchases WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM credit_payments WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM ledger_cycles WHERE ownerId LIKE 'offline-%' UNION SELECT ownerId FROM ledger_archives WHERE ownerId LIKE 'offline-%'")
    fun observeOfflineOwners(): Flow<List<String>>

    @Query("SELECT (SELECT COUNT(*) FROM transactions WHERE ownerId=:owner) + (SELECT COUNT(*) FROM loans WHERE ownerId=:owner) + (SELECT COUNT(*) FROM loan_payments WHERE ownerId=:owner) + (SELECT COUNT(*) FROM committees WHERE ownerId=:owner) + (SELECT COUNT(*) FROM committee_payments WHERE ownerId=:owner) + (SELECT COUNT(*) FROM committee_receipts WHERE ownerId=:owner) + (SELECT COUNT(*) FROM committee_members WHERE ownerId=:owner) + (SELECT COUNT(*) FROM savings WHERE ownerId=:owner) + (SELECT COUNT(*) FROM credit_purchases WHERE ownerId=:owner) + (SELECT COUNT(*) FROM credit_payments WHERE ownerId=:owner) + (SELECT COUNT(*) FROM ledger_archives WHERE ownerId=:owner)")
    suspend fun recordCount(owner: String): Int

    @Query("SELECT * FROM transactions WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeTransactions(owner: String): Flow<List<TransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransaction(item: TransactionEntity)
    @Delete suspend fun deleteTransaction(item: TransactionEntity)

    @Query("SELECT * FROM loans WHERE ownerId=:owner ORDER BY closed ASC, createdAt DESC")
    fun observeLoans(owner: String): Flow<List<LoanEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLoan(item: LoanEntity)
    @Delete suspend fun deleteLoan(item: LoanEntity)
    @Query("SELECT * FROM loans WHERE ownerId=:owner AND id=:id") suspend fun loanNow(owner: String, id: String): LoanEntity?
    @Query("DELETE FROM loan_payments WHERE ownerId=:owner AND loanId=:loanId") suspend fun deleteLoanPayments(owner: String, loanId: String)

    @Query("SELECT * FROM loan_payments WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeLoanPayments(owner: String): Flow<List<LoanPaymentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLoanPayment(item: LoanPaymentEntity)
    @Delete suspend fun deleteLoanPayment(item: LoanPaymentEntity)

    @Query("SELECT * FROM credit_purchases WHERE ownerId=:owner ORDER BY closed ASC, purchaseDate DESC, createdAt DESC")
    fun observeCreditPurchases(owner: String): Flow<List<CreditPurchaseEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCreditPurchase(item: CreditPurchaseEntity)
    @Delete suspend fun deleteCreditPurchase(item: CreditPurchaseEntity)
    @Query("SELECT * FROM credit_purchases WHERE ownerId=:owner AND id=:id") suspend fun creditPurchaseNow(owner: String, id: String): CreditPurchaseEntity?
    @Query("DELETE FROM credit_payments WHERE ownerId=:owner AND creditId=:creditId") suspend fun deleteCreditPayments(owner: String, creditId: String)

    @Query("SELECT * FROM credit_payments WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeCreditPayments(owner: String): Flow<List<CreditPaymentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCreditPayment(item: CreditPaymentEntity)
    @Delete suspend fun deleteCreditPayment(item: CreditPaymentEntity)

    @Query("SELECT * FROM organizer_circles WHERE ownerId=:owner ORDER BY createdAt DESC")
    fun observeOrganizerCircles(owner: String): Flow<List<OrganizerCircleEntity>>
    @Query("SELECT * FROM organizer_members WHERE ownerId=:owner ORDER BY createdAt ASC")
    fun observeOrganizerMembers(owner: String): Flow<List<OrganizerMemberEntity>>
    @Query("SELECT * FROM organizer_collections WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeOrganizerCollections(owner: String): Flow<List<OrganizerCollectionEntity>>
    @Query("SELECT * FROM organizer_payouts WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeOrganizerPayouts(owner: String): Flow<List<OrganizerPayoutEntity>>
    @Query("SELECT * FROM organizer_circles WHERE ownerId=:owner ORDER BY id")
    suspend fun organizerCirclesNow(owner: String): List<OrganizerCircleEntity>
    @Query("SELECT * FROM organizer_members WHERE ownerId=:owner ORDER BY id")
    suspend fun organizerMembersNow(owner: String): List<OrganizerMemberEntity>
    @Query("SELECT * FROM organizer_collections WHERE ownerId=:owner ORDER BY id")
    suspend fun organizerCollectionsNow(owner: String): List<OrganizerCollectionEntity>
    @Query("SELECT * FROM organizer_payouts WHERE ownerId=:owner ORDER BY id")
    suspend fun organizerPayoutsNow(owner: String): List<OrganizerPayoutEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOrganizerCircle(item: OrganizerCircleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOrganizerMember(item: OrganizerMemberEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOrganizerCollection(item: OrganizerCollectionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOrganizerPayout(item: OrganizerPayoutEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrganizerCircles(items: List<OrganizerCircleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrganizerMembers(items: List<OrganizerMemberEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrganizerCollections(items: List<OrganizerCollectionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrganizerPayouts(items: List<OrganizerPayoutEntity>)
    @Query("DELETE FROM organizer_payouts WHERE ownerId=:owner") suspend fun clearOrganizerPayouts(owner: String)
    @Query("DELETE FROM organizer_collections WHERE ownerId=:owner") suspend fun clearOrganizerCollections(owner: String)
    @Query("DELETE FROM organizer_members WHERE ownerId=:owner") suspend fun clearOrganizerMembers(owner: String)
    @Query("DELETE FROM organizer_circles WHERE ownerId=:owner") suspend fun clearOrganizerCircles(owner: String)
    @Query("UPDATE organizer_circles SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateOrganizerCircles(oldOwner: String, newOwner: String)
    @Query("UPDATE organizer_members SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateOrganizerMembers(oldOwner: String, newOwner: String)
    @Query("UPDATE organizer_collections SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateOrganizerCollections(oldOwner: String, newOwner: String)
    @Query("UPDATE organizer_payouts SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateOrganizerPayouts(oldOwner: String, newOwner: String)
    @Delete suspend fun deleteOrganizerCircle(item: OrganizerCircleEntity)
    @Delete suspend fun deleteOrganizerMember(item: OrganizerMemberEntity)
    @Query("DELETE FROM organizer_collections WHERE ownerId=:owner AND memberId=:memberId") suspend fun clearMemberCollections(owner: String, memberId: String)
    @Query("DELETE FROM organizer_payouts WHERE ownerId=:owner AND memberId=:memberId") suspend fun clearMemberPayouts(owner: String, memberId: String)
    @Query("DELETE FROM organizer_members WHERE ownerId=:owner AND circleId=:circleId") suspend fun clearCircleMembers(owner: String, circleId: String)
    @Query("DELETE FROM organizer_collections WHERE ownerId=:owner AND circleId=:circleId") suspend fun clearCircleCollections(owner: String, circleId: String)
    @Query("DELETE FROM organizer_payouts WHERE ownerId=:owner AND circleId=:circleId") suspend fun clearCirclePayouts(owner: String, circleId: String)

    @Query("SELECT * FROM committees WHERE ownerId=:owner ORDER BY active DESC, createdAt DESC")
    fun observeCommittees(owner: String): Flow<List<CommitteeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCommittee(item: CommitteeEntity)
    @Delete suspend fun deleteCommittee(item: CommitteeEntity)
    @Query("SELECT * FROM committees WHERE ownerId=:owner AND id=:id") suspend fun committeeNow(owner: String, id: String): CommitteeEntity?
    @Query("DELETE FROM committee_payments WHERE ownerId=:owner AND committeeId=:committeeId") suspend fun deleteCommitteePayments(owner: String, committeeId: String)

    @Query("SELECT * FROM committee_payments WHERE ownerId=:owner ORDER BY month DESC, installmentNumber DESC")
    fun observeCommitteePayments(owner: String): Flow<List<CommitteePaymentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCommitteePayment(item: CommitteePaymentEntity)
    @Delete suspend fun deleteCommitteePayment(item: CommitteePaymentEntity)

    @Query("SELECT * FROM committee_receipts WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeCommitteeReceipts(owner: String): Flow<List<CommitteeReceiptEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCommitteeReceipt(item: CommitteeReceiptEntity)
    @Delete suspend fun deleteCommitteeReceipt(item: CommitteeReceiptEntity)
    @Query("DELETE FROM committee_receipts WHERE ownerId=:owner AND committeeId=:committeeId") suspend fun deleteCommitteeReceipts(owner: String, committeeId: String)
    @Query("SELECT * FROM committee_receipts WHERE ownerId=:owner ORDER BY id") suspend fun committeeReceiptsNow(owner: String): List<CommitteeReceiptEntity>
    @Query("DELETE FROM committee_receipts WHERE ownerId=:owner") suspend fun clearCommitteeReceipts(owner: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommitteeReceipts(items: List<CommitteeReceiptEntity>)

    @Query("SELECT * FROM committee_members WHERE ownerId=:owner ORDER BY turnMonth ASC, turnNumber ASC, createdAt ASC")
    fun observeCommitteeMembers(owner: String): Flow<List<CommitteeMemberEntity>>
    @Query("SELECT * FROM committee_members WHERE ownerId=:owner AND id=:id") suspend fun committeeMemberNow(owner: String, id: String): CommitteeMemberEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCommitteeMember(item: CommitteeMemberEntity)
    @Delete suspend fun deleteCommitteeMember(item: CommitteeMemberEntity)
    @Query("SELECT * FROM committee_members WHERE ownerId=:owner ORDER BY id") suspend fun committeeMembersNow(owner: String): List<CommitteeMemberEntity>
    @Query("DELETE FROM committee_members WHERE ownerId=:owner") suspend fun clearCommitteeMembers(owner: String)
    @Query("DELETE FROM committee_members WHERE ownerId=:owner AND committeeId=:committeeId") suspend fun deleteCommitteeMembers(owner: String, committeeId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommitteeMembers(items: List<CommitteeMemberEntity>)

    @Query("SELECT * FROM savings WHERE ownerId=:owner ORDER BY date DESC, createdAt DESC")
    fun observeSavings(owner: String): Flow<List<SavingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSaving(item: SavingEntity)
    @Delete suspend fun deleteSaving(item: SavingEntity)

    @Query("SELECT * FROM transactions WHERE ownerId=:owner ORDER BY id") suspend fun transactionsNow(owner: String): List<TransactionEntity>
    @Query("SELECT * FROM loans WHERE ownerId=:owner ORDER BY id") suspend fun loansNow(owner: String): List<LoanEntity>
    @Query("SELECT * FROM loan_payments WHERE ownerId=:owner ORDER BY id") suspend fun loanPaymentsNow(owner: String): List<LoanPaymentEntity>
    @Query("SELECT * FROM credit_purchases WHERE ownerId=:owner ORDER BY id") suspend fun creditPurchasesNow(owner: String): List<CreditPurchaseEntity>
    @Query("SELECT * FROM credit_payments WHERE ownerId=:owner ORDER BY id") suspend fun creditPaymentsNow(owner: String): List<CreditPaymentEntity>
    @Query("SELECT * FROM committees WHERE ownerId=:owner ORDER BY id") suspend fun committeesNow(owner: String): List<CommitteeEntity>
    @Query("SELECT * FROM committee_payments WHERE ownerId=:owner ORDER BY id") suspend fun committeePaymentsNow(owner: String): List<CommitteePaymentEntity>
    @Query("SELECT * FROM savings WHERE ownerId=:owner ORDER BY id") suspend fun savingsNow(owner: String): List<SavingEntity>

    @Query("DELETE FROM transactions WHERE ownerId=:owner") suspend fun clearTransactions(owner: String)
    @Query("DELETE FROM loans WHERE ownerId=:owner") suspend fun clearLoans(owner: String)
    @Query("DELETE FROM loan_payments WHERE ownerId=:owner") suspend fun clearLoanPayments(owner: String)
    @Query("DELETE FROM credit_purchases WHERE ownerId=:owner") suspend fun clearCreditPurchases(owner: String)
    @Query("DELETE FROM credit_payments WHERE ownerId=:owner") suspend fun clearCreditPayments(owner: String)
    @Query("DELETE FROM committees WHERE ownerId=:owner") suspend fun clearCommittees(owner: String)
    @Query("DELETE FROM committee_payments WHERE ownerId=:owner") suspend fun clearCommitteePayments(owner: String)
    @Query("DELETE FROM savings WHERE ownerId=:owner") suspend fun clearSavings(owner: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLoans(items: List<LoanEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLoanPayments(items: List<LoanPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCreditPurchases(items: List<CreditPurchaseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCreditPayments(items: List<CreditPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommittees(items: List<CommitteeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommitteePayments(items: List<CommitteePaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSavings(items: List<SavingEntity>)

    @Query("SELECT * FROM ledger_cycles WHERE ownerId=:owner")
    fun observeLedgerCycle(owner: String): Flow<LedgerCycleEntity?>
    @Query("SELECT * FROM ledger_cycles WHERE ownerId=:owner")
    suspend fun ledgerCycleNow(owner: String): LedgerCycleEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLedgerCycle(item: LedgerCycleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLedgerCycle(item: LedgerCycleEntity)
    @Query("SELECT * FROM ledger_archives WHERE ownerId=:owner ORDER BY archivedAt DESC")
    fun observeLedgerArchives(owner: String): Flow<List<LedgerArchiveEntity>>
    @Query("SELECT * FROM ledger_archives WHERE ownerId=:owner ORDER BY archivedAt DESC")
    suspend fun ledgerArchivesNow(owner: String): List<LedgerArchiveEntity>
    @Query("SELECT * FROM ledger_archives WHERE ownerId=:owner AND archiveKey=:key")
    suspend fun ledgerArchiveNow(owner: String, key: String): LedgerArchiveEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLedgerArchive(item: LedgerArchiveEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLedgerArchives(items: List<LedgerArchiveEntity>)
    @Query("DELETE FROM ledger_cycles WHERE ownerId=:owner") suspend fun clearLedgerCycle(owner: String)
    @Query("DELETE FROM ledger_archives WHERE ownerId=:owner") suspend fun clearLedgerArchives(owner: String)
    @Query("UPDATE ledger_cycles SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateLedgerCycle(oldOwner: String, newOwner: String)
    @Query("UPDATE ledger_archives SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateLedgerArchives(oldOwner: String, newOwner: String)

    @Query("UPDATE transactions SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateTransactions(oldOwner: String, newOwner: String)
    @Query("UPDATE loans SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateLoans(oldOwner: String, newOwner: String)
    @Query("UPDATE loan_payments SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateLoanPayments(oldOwner: String, newOwner: String)
    @Query("UPDATE credit_purchases SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCreditPurchases(oldOwner: String, newOwner: String)
    @Query("UPDATE credit_payments SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCreditPayments(oldOwner: String, newOwner: String)
    @Query("UPDATE committees SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCommittees(oldOwner: String, newOwner: String)
    @Query("UPDATE committee_payments SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCommitteePayments(oldOwner: String, newOwner: String)
    @Query("UPDATE committee_receipts SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCommitteeReceipts(oldOwner: String, newOwner: String)
    @Query("UPDATE committee_members SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateCommitteeMembers(oldOwner: String, newOwner: String)
    @Query("UPDATE savings SET ownerId=:newOwner WHERE ownerId=:oldOwner") suspend fun migrateSavings(oldOwner: String, newOwner: String)
}
