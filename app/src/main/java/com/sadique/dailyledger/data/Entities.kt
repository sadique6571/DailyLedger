package com.sadique.dailyledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index("ownerId"), Index("date"), Index(value = ["ownerId", "date", "createdAt"], orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC], name = "index_transactions_owner_date_created")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val type: String, // INCOME | EXPENSE
    val amountMinor: Long,
    val category: String,
    val note: String,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'Cash'") val account: String = "Cash",
    @ColumnInfo(defaultValue = "''") val quantity: String = "",
    @ColumnInfo(defaultValue = "''") val unit: String = "",
    @ColumnInfo(defaultValue = "0") val unitPriceMinor: Long = 0,
    @ColumnInfo(defaultValue = "''") val refundOf: String = "",
    @ColumnInfo(defaultValue = "0") val privateEntry: Boolean = false,
)

@Entity(tableName = "loans", indices = [Index("ownerId")])
data class LoanEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val direction: String, // BORROWED | LENT
    val person: String,
    val principalMinor: Long,
    val dueDate: String?,
    val note: String,
    val createdAt: Long,
    val closed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val whatsapp: String = "",
)

@Entity(tableName = "loan_payments", indices = [Index("ownerId"), Index("loanId")])
data class LoanPaymentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val loanId: String,
    val amountMinor: Long,
    val date: String,
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "Cash") val method: String = "Cash",
)

@Entity(tableName = "committees", indices = [Index("ownerId")])
data class CommitteeEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val monthlyAmountMinor: Long,
    val totalInstallments: Int,
    val startMonth: String,
    val payoutInstallment: Int?,
    val received: Boolean,
    val active: Boolean,
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1") val shares: Int = 1,
    @ColumnInfo(defaultValue = "''") val organizerPhone: String = "",
    // Legacy v3 free-text schedule. New records use committee_members.
    @ColumnInfo(defaultValue = "''") val memberSchedule: String = "",
    @ColumnInfo(defaultValue = "2") val sharesHalfUnits: Int = shares * 2,
)

@Entity(tableName = "committee_payments", indices = [Index("ownerId"), Index("committeeId")])
data class CommitteePaymentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val committeeId: String,
    val installmentNumber: Int,
    val month: String,
    val amountMinor: Long,
    val paidAt: Long,
    @ColumnInfo(defaultValue = "Cash") val method: String = "Cash",
)

@Entity(tableName = "committee_receipts", indices = [Index("ownerId"), Index("committeeId")])
data class CommitteeReceiptEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val committeeId: String,
    val amountMinor: Long,
    val date: String?, // Older "received" flags did not record a receiving date.
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "Cash") val method: String = "Cash",
    @ColumnInfo(defaultValue = "''") val memberId: String = "",
)

@Entity(
    tableName = "committee_members",
    indices = [Index("ownerId"), Index("committeeId"), Index("turnMonth")],
)
data class CommitteeMemberEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val committeeId: String,
    val name: String,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val whatsapp: String = "",
    val turnNumber: Int,
    val turnMonth: String,
    @ColumnInfo(defaultValue = "0") val isMe: Boolean = false,
    @ColumnInfo(defaultValue = "0") val received: Boolean = false,
    val receivedDate: String? = null,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val createdAt: Long,
    @ColumnInfo(defaultValue = "2") val shareHalfUnits: Int = 2
)

@Entity(tableName = "savings", indices = [Index("ownerId"), Index("date")])
data class SavingEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val kind: String, // DIRECT | LEFTOVER
    val amountMinor: Long,
    val date: String,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "credit_purchases", indices = [Index("ownerId"), Index("purchaseDate"), Index("dueDate")])
data class CreditPurchaseEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val creditor: String,
    val item: String,
    val amountMinor: Long,
    val category: String,
    val purchaseDate: String,
    val dueDate: String?,
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val closed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val whatsapp: String = "",
)

@Entity(tableName = "credit_payments", indices = [Index("ownerId"), Index("creditId"), Index("date")])
data class CreditPaymentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val creditId: String,
    val amountMinor: Long,
    val date: String,
    val note: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'Cash'") val method: String = "Cash",
)

@Entity(tableName = "ledger_cycles")
data class LedgerCycleEntity(
    @PrimaryKey val ownerId: String,
    val startAt: Long,
    val openingBalanceMinor: Long,
)

@Entity(
    tableName = "ledger_archives",
    indices = [Index(value = ["ownerId", "archiveKey"], unique = true), Index("ownerId")],
)
data class LedgerArchiveEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val archiveKey: String,
    val label: String,
    val startAt: Long,
    val endAt: Long,
    val salaryMinor: Long,
    val otherIncomeMinor: Long,
    val expensesMinor: Long,
    val committeeMinor: Long,
    val savingsMinor: Long,
    val openingBalanceMinor: Long,
    val closingBalanceMinor: Long,
    val archivedAt: Long,
)
