package com.sadique.dailyledger.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.data.CommitteeEntity
import com.sadique.dailyledger.data.CommitteePaymentEntity
import com.sadique.dailyledger.data.CommitteeReceiptEntity
import com.sadique.dailyledger.ui.plainAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ExportPeriod { CURRENT_MONTH, ALL_TIME, CUSTOM }

object ExportManager {
    private fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))

    suspend fun csv(
        context: Context,
        repo: LedgerRepository,
        from: LocalDate? = null,
        through: LocalDate? = null,
    ): String = withContext(Dispatchers.IO) {
        require(from == null || through == null || !from.isAfter(through)) { "Start date must be on or before end date." }
        val rows = mutableListOf("section,date,type,category,description,amount_pkr")
        repo.transactions.first().filterNot { it.privateEntry }.filter { it.isWithin(from, through) }.forEach {
            rows += listOf("transaction", it.date, if(it.refundOf.isNotBlank())"REFUND" else it.type, it.category, it.note, plainAmount(it.amountMinor))
                .joinToString(",") { value -> esc(value) }
        }
        repo.savings.first().filter { it.date.isWithin(from, through) }.forEach {
            rows += listOf("saving", it.date, it.kind, "", it.note, plainAmount(it.amountMinor))
                .joinToString(",") { value -> esc(value) }
        }
        val loans = repo.loans.first()
        repo.loanPayments.first().filter { it.date.isWithin(from, through) }.forEach { p ->
            val who = loans.firstOrNull { it.id == p.loanId }?.person.orEmpty()
            rows += listOf("loan_payment", p.date, "", who, p.note, plainAmount(p.amountMinor))
                .joinToString(",") { value -> esc(value) }
        }
        val committees = repo.committees.first().associateBy { it.id }
        repo.committeePayments.first().filter { it.month.isWithin(from, through) }.forEach { p ->
            val name = committees[p.committeeId]?.name.orEmpty()
            rows += listOf("kameti_payment", p.month, "PAYMENT", name, "Installment #${p.installmentNumber}", plainAmount(p.amountMinor))
                .joinToString(",") { value -> esc(value) }
        }
        repo.committeeReceipts.first().filter { it.date.orEmpty().isWithin(from, through) }.forEach { r ->
            val name = committees[r.committeeId]?.name.orEmpty()
            rows += listOf("kameti_receiving", r.date.orEmpty(), "RECEIVED", name, r.note, plainAmount(r.amountMinor))
                .joinToString(",") { value -> esc(value) }
        }
        save(context, "DailyLedger-${stamp()}.csv", "text/csv", rows.joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    suspend fun pdf(
        context: Context,
        repo: LedgerRepository,
        from: LocalDate? = null,
        through: LocalDate? = null,
        accountLabel: String = "",
    ): String = withContext(Dispatchers.IO) {
        require(from == null || through == null || !from.isAfter(through)) { "Start date must be on or before end date." }
        save(context, "DailyLedger-Statement-${stamp()}.pdf", "application/pdf", statementBytes(repo, from, through, accountLabel))
    }
    internal suspend fun savePreparedPdf(context: Context, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        save(context, "DailyLedger-Statement-${stamp()}.pdf", "application/pdf", bytes)
    }
    internal suspend fun statementBytes(repo: LedgerRepository, from: LocalDate?, through: LocalDate?, accountLabel: String = ""): ByteArray =
        statementBytes(repo.transactions.first().filterNot { it.privateEntry }, from, through,
            repo.committees.first(), repo.committeePayments.first(), repo.committeeReceipts.first(), accountLabel)

    internal fun statementBytes(
        transactions: List<TransactionEntity>, from: LocalDate?, through: LocalDate?,
        committees: List<CommitteeEntity> = emptyList(), payments: List<CommitteePaymentEntity> = emptyList(),
        receipts: List<CommitteeReceiptEntity> = emptyList(),
        accountLabel: String = "",
    ): ByteArray {
        val sections = StatementData.sections(transactions, committees, payments, receipts, from, through)
        val pageCount = sections.sumOf { it.pages().size }
        val doc = PdfDocument()
        try {
            var pageNumber = 0
            sections.forEach { section ->
                section.pages().forEachIndexed { index, rows ->
                    pageNumber++
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    val canvas = page.canvas
                    drawPageHeader(canvas, pageNumber, from, through, section, index == 0, accountLabel)
                    var y = if (index == 0) 242f else 150f
                    drawTableHeader(canvas, y, section.kameti)
                    y += HEADER_HEIGHT
                    rows.forEachIndexed { rowOnPage, row ->
                        drawRow(canvas, y, row, rowOnPage % 2 == 1)
                        y += ROW_HEIGHT
                    }
                    if (rows.isEmpty()) {
                        val label = if (section.kameti) "kameti payments or receiving" else "income or expenses"
                        canvas.drawText("No $label found for the selected period.", 42f, y + 28f, textPaint(11f, COLOR_MUTED))
                    }
                    drawFooter(canvas, pageNumber, pageCount, section.kameti)
                    doc.finishPage(page)
                }
            }
            val buffer = ByteArrayOutputStream()
            doc.writeTo(buffer)
            return buffer.toByteArray()
        } finally { doc.close() }
    }

    private fun drawPageHeader(
        canvas: Canvas,
        pageNumber: Int,
        from: LocalDate?,
        through: LocalDate?,
        section: StatementSection,
        showSummary: Boolean,
        accountLabel: String = "",
    ) {
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(24f, 24f, 571f, 120f, COLOR_NAVY, COLOR_TEAL, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(24f, 24f, 571f, 116f), 14f, 14f, header)
        canvas.drawText("DAILY LEDGER", 42f, 59f, textPaint(22f, Color.WHITE, Typeface.BOLD))
        canvas.drawText(if (section.kameti) "Kameti statement · payments & receiving" else "Your income & expense statement", 42f, 82f, textPaint(10f, Color.WHITE))
        canvas.drawText("PKR", 536f, 60f, textPaint(12f, Color.WHITE, Typeface.BOLD).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))}  |  Page $pageNumber", 42f, 101f, textPaint(8f, Color.rgb(200, 240, 240)))
        val period = when {
            from == null && through == null -> "All available records"
            from == null -> "Through ${through!!.format(DATE_FORMAT)}"
            through == null -> "From ${from.format(DATE_FORMAT)}"
            else -> "${from.format(DATE_FORMAT)} - ${through.format(DATE_FORMAT)}"
        }
        canvas.drawText(period, 42f, 136f, textPaint(10f, COLOR_INK, Typeface.BOLD))
        if (accountLabel.isNotBlank()) {
            val accountPaint = textPaint(8f, COLOR_MUTED).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(fitText("Account: $accountLabel", accountPaint, 220f), 556f, 136f, accountPaint)
        }
        if (showSummary) {
            val cardY = 150f
            val cardH = 64f
            val cardW = 163f
            val gap = 11f
            drawSummaryCard(canvas, 42f, cardY, cardW, cardH, if (section.kameti) "RECEIVED" else "MONEY IN", "PKR ${plainAmount(section.moneyIn)}", COLOR_GREEN)
            drawSummaryCard(canvas, 42f + cardW + gap, cardY, cardW, cardH, if (section.kameti) "PAID" else "MONEY OUT", "PKR ${plainAmount(section.moneyOut)}", COLOR_RED)
            drawSummaryCard(canvas, 42f + (cardW + gap) * 2, cardY, cardW, cardH, if (section.kameti) "ENTRIES" else "NET MOVEMENT",
                if (section.kameti) "${section.rows.size}" else "PKR ${plainAmount(section.moneyIn - section.moneyOut)}", COLOR_NAVY)
            canvas.drawText(if (section.kameti) "KAMETI ACTIVITY · SEPARATE FROM SALARY" else "TRANSACTION ACTIVITY", 42f, 232f, textPaint(9f, COLOR_MUTED, Typeface.BOLD))
        }
    }

    private fun drawSummaryCard(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, value: String, color: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(247, 250, 253) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 8f, 8f, fill)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 8f, 8f, border)
        canvas.drawText(label, x + 10f, y + 20f, textPaint(9f, COLOR_MUTED, Typeface.BOLD))
        val amountPaint = textPaint(12f, color, Typeface.BOLD)
        while (amountPaint.measureText(value) > width - 20f && amountPaint.textSize > 6f) amountPaint.textSize -= .5f
        canvas.drawText(value, x + 10f, y + 44f, amountPaint)
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, kameti: Boolean) {
        canvas.drawRoundRect(RectF(38f, y, 557f, y + HEADER_HEIGHT), 6f, 6f, Paint().apply { color = COLOR_NAVY })
        val paint = textPaint(8.5f, Color.WHITE, Typeface.BOLD)
        canvas.drawText(if (kameti) "DATE / MONTH" else "DATE", 47f, y + 17f, paint)
        canvas.drawText(if (kameti) "KAMETI / INSTALLMENT" else "TRANSACTION / CATEGORY", 117f, y + 17f, paint)
        canvas.drawText("TYPE", 367f, y + 17f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT (PKR)", 546f, y + 17f, paint)
    }

    private fun drawRow(canvas: Canvas, y: Float, transaction: StatementRow, alternate: Boolean) {
        if (alternate) canvas.drawRect(39f, y, 556f, y + ROW_HEIGHT, Paint().apply { color = Color.rgb(244, 249, 250) })
        canvas.drawLine(39f, y + ROW_HEIGHT, 556f, y + ROW_HEIGHT, Paint().apply { color = COLOR_BORDER; strokeWidth = .5f })
        val body = textPaint(9f, COLOR_INK)
        val income = transaction.moneyIn
        val typeColor = if (income) COLOR_GREEN else COLOR_RED
        canvas.drawText(transaction.date, 47f, y + 23f, textPaint(8f, COLOR_MUTED))
        canvas.drawText(fitText(transaction.title, body, 236f), 117f, y + 16f, body)
        canvas.drawText(fitText(transaction.detail, textPaint(8f, COLOR_MUTED), 236f), 117f, y + 30f, textPaint(8f, COLOR_MUTED))
        canvas.drawText(transaction.type, 367f, y + 23f, textPaint(8f, typeColor, Typeface.BOLD))
        val amount = "${if (income) "+" else "-"} ${plainAmount(transaction.amount)}"
        val amountPaint = textPaint(10f, typeColor, Typeface.BOLD).apply { textAlign = Paint.Align.RIGHT }
        while (amountPaint.measureText(amount) > 115f && amountPaint.textSize > 6f) amountPaint.textSize -= .5f
        canvas.drawText(amount, 546f, y + 23f, amountPaint)
    }

    private fun drawFooter(canvas: Canvas, page: Int, totalPages: Int, kameti: Boolean) {
        canvas.drawLine(42f, 794f, 553f, 794f, Paint().apply { color = COLOR_BORDER; strokeWidth = .8f })
        canvas.drawText(if (kameti) "Kameti totals stay separate from income and expenses." else "Net movement is not your account balance. Kameti has its own statement section.", 42f, 807f, textPaint(7.5f, COLOR_MUTED))
        canvas.drawText(if (kameti) "Payments use installment month. Undated receiving appears in All records only." else "Full descriptions and other ledgers are available in CSV export.", 42f, 819f, textPaint(7.5f, COLOR_MUTED))
        canvas.drawText("$page / $totalPages", 553f, 819f, textPaint(8f, COLOR_MUTED).apply { textAlign = Paint.Align.RIGHT })
    }

    private fun fitText(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) result = result.dropLast(1)
        return if (result.isEmpty()) "" else "$result…"
    }

    private fun textPaint(size: Float, color: Int, style: Int = Typeface.NORMAL) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", style)
        }

    /** Writes into Downloads on API 29+; older Android versions use the app's Downloads folder. */
    private fun save(context: Context, name: String, mime: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Cannot create $name in Downloads.")
            try {
                val out = context.contentResolver.openOutputStream(uri) ?: error("Cannot open $name in Downloads for writing.")
                out.use { it.write(bytes) }
            } catch (e: Exception) {
                try { context.contentResolver.delete(uri, null, null) }
                catch (cleanup: Exception) { e.addSuppressed(cleanup) }
                throw e
            }
            return "Downloads/$name"
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("App Downloads folder is unavailable.")
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create the app Downloads folder.")
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun String.isWithin(from: LocalDate?, through: LocalDate?) = inStatementPeriod(from, through)

    private fun TransactionEntity.isWithin(from: LocalDate?, through: LocalDate?) = date.isWithin(from, through)
    private fun esc(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val ROW_HEIGHT = 40f
    private const val HEADER_HEIGHT = 26f
    private const val COLOR_NAVY = 0xFF12324A.toInt()
    private const val COLOR_TEAL = 0xFF087F8C.toInt()
    private const val COLOR_INK = 0xFF263746.toInt()
    private const val COLOR_MUTED = 0xFF718096.toInt()
    private const val COLOR_BORDER = 0xFFD8E1E8.toInt()
    private const val COLOR_GREEN = 0xFF13805C.toInt()
    private const val COLOR_RED = 0xFFB83A45.toInt()
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy")
}
