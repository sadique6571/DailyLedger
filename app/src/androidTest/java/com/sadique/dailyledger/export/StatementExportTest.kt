package com.sadique.dailyledger.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.data.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatementExportTest {
    @Test fun multiPageStatementRendersAndEmptyStatementStillHasOnePage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = (1..32).map { i ->
            TransactionEntity("$i", "sample", if (i == 1) "INCOME" else "EXPENSE", if (i == 1) 10000000 else i * 12550L,
                if (i == 1) "Salary" else listOf("Moong dal", "Vegetables", "Electricity", "School fees")[i % 4],
                if (i == 1) "Monthly salary" else "Household purchase $i - description for the statement", "2026-10-${((i - 1) % 28 + 1).toString().padStart(2, '0')}", i.toLong(), i.toLong())
        }
        val pdf = File(context.getExternalFilesDir(null), "dailyledger-export.pdf")
        pdf.writeBytes(ExportManager.statementBytes(rows, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(3, renderer.pageCount)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val preview = File(context.getExternalFilesDir(null), "dailyledger-export-${index + 1}.png")
                preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                copyForReport(preview); bitmap.recycle()
            }
        }
        copyForReport(pdf)
        pdf.writeBytes(ExportManager.statementBytes(emptyList(), null, null))
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(1, it.pageCount) }
    }
    @Test fun kametiStatementRendersPaymentsAndSeparatePartialReceiptsAcrossPages() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val committee = CommitteeEntity("c", "sample", "Office kameti", 100000, 20, "2026-01", null, false, true, "", 1)
        val payments = (1..13).map { CommitteePaymentEntity("p$it", "sample", "c", it, "2026-10", 100000, it.toLong()) }
        val receipts = listOf(CommitteeReceiptEntity("r1", "sample", "c", 300000, "2026-10-05", "First partial receiving", 20),
            CommitteeReceiptEntity("r2", "sample", "c", 400000, "2026-10-10", "Second partial receiving", 21))
        val tx = listOf(TransactionEntity("salary", "sample", "INCOME", 5000000, "Salary", "Monthly salary", "2026-10-01", 1, 1))
        val pdf = File(context.getExternalFilesDir(null), "dailyledger-kameti-export.pdf")
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.ledgerDao(); dao.insertTransactions(tx); dao.insertCommittees(listOf(committee))
            dao.insertCommitteePayments(payments); dao.insertCommitteeReceipts(receipts)
            dao.upsertCommitteeReceipt(receipts.first().copy(id = "another-owner", ownerId = "other", amountMinor = 99999999))
            pdf.writeBytes(ExportManager.statementBytes(LedgerRepository(db, "sample"), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        } finally { db.close() }
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(3, renderer.pageCount)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val preview = File(context.getExternalFilesDir(null), "dailyledger-kameti-export-${index + 1}.png")
                preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; copyForReport(preview); bitmap.recycle()
            }
        }
        copyForReport(pdf)
    }
    private fun copyForReport(file: File) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/${file.name}")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
