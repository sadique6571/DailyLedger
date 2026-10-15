package com.sadique.dailyledger.export
import com.sadique.dailyledger.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class StatementDataTest {
    private val committee = CommitteeEntity("c", "me", "Office kameti", 100000, 10, "2026-09", null, false, true, "", 1)
    private val payments = listOf(CommitteePaymentEntity("p1", "me", "c", 1, "2026-09", 100000, 1792022400000), CommitteePaymentEntity("p2", "me", "c", 2, "2026-10", 100000, 1792022400000))
    private val receipts = listOf(CommitteeReceiptEntity("r1", "me", "c", 200000, "2026-10-01", "First partial", 1), CommitteeReceiptEntity("r2", "me", "c", 300000, "2026-10-15", "Second partial", 2), CommitteeReceiptEntity("old", "me", "c", 400000, null, "Legacy receiving", 3))
    @Test fun kametiPaymentsAndEveryPartialReceiptHaveSeparateTotalsFromSalary() {
        val tx = listOf(TransactionEntity("salary", "me", "INCOME", 5000000, "Salary", "", "2026-10-01", 1, 1))
        val s = StatementData.sections(tx, listOf(committee), payments, receipts, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals(2, s.size); assertEquals(5000000L, s[0].moneyIn); assertEquals(0L, s[0].moneyOut)
        assertEquals(100000L, s[1].moneyOut); assertEquals(500000L, s[1].moneyIn)
        assertEquals(3, s[1].rows.size); assertEquals(2, s[1].rows.count { it.type == "RECEIVED" })
        assertTrue(s[1].rows.all { it.title == "Office kameti" })
    }
    @Test fun allRecordsIncludeUndatedReceiptsAndHistoricalInstallments() {
        val s = StatementData.sections(emptyList(), listOf(committee), payments, receipts, null, null).single()
        assertTrue(s.kameti); assertEquals(5, s.rows.size); assertEquals(900000L, s.moneyIn); assertTrue(s.rows.any { it.date == "Undated" })
    }
    @Test fun datesDoNotMoveOldInstallmentsIntoTheMonthTheyWereEntered() {
        val rows = StatementData.sections(emptyList(), listOf(committee), payments, receipts, LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-15")).last().rows
        assertEquals(1, rows.size); assertEquals("Second partial · Cash", rows.single().detail)
    }
    @Test fun emptyAndMultiPageSectionsNeverDropOrRepeatRows() {
        assertEquals(1, StatementData.sections(emptyList(), emptyList(), emptyList(), emptyList(), null, null).single().pages().size)
        val s = StatementData.sections(emptyList(), listOf(committee), (1..32).map { payments[0].copy(id = "$it", installmentNumber = it) }, emptyList(), null, null).single()
        assertEquals(listOf(12, 15, 5), s.pages().map { it.size }); assertEquals(s.rows, s.pages().flatten())
    }
}
