package com.sadique.dailyledger.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.plainAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
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
        repo.transactions.first().filter { it.isWithin(from, through) }.forEach {
            rows += listOf("transaction", it.date, it.type, it.category, it.note, plainAmount(it.amountMinor))
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
    ): String = withContext(Dispatchers.IO) {
        require(from == null || through == null || !from.isAfter(through)) { "Start date must be on or before end date." }
        val transactions = repo.transactions.first()
            .filter { it.isWithin(from, through) }
            .sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.createdAt })
        val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        val expenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        val firstPageRows = 23
        val nextPageRows = 26
        val pageCount = if (transactions.size <= firstPageRows) 1
        else 1 + (transactions.size - firstPageRows + nextPageRows - 1) / nextPageRows

        val doc = PdfDocument()
        try {
            var rowIndex = 0
            for (pageNumber in 1..pageCount) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas
                drawPageHeader(canvas, pageNumber, from, through, income, expenses, pageNumber == 1)
                var y = if (pageNumber == 1) 230f else 142f
                drawTableHeader(canvas, y)
                y += ROW_HEIGHT
                val pageLimit = if (pageNumber == 1) firstPageRows else nextPageRows
                val rowsOnPage = minOf(pageLimit, transactions.size - rowIndex)
                repeat(rowsOnPage) { rowOnPage ->
                    drawRow(canvas, y, transactions[rowIndex], rowOnPage % 2 == 1)
                    y += ROW_HEIGHT
                    rowIndex++
                }
                if (transactions.isEmpty()) {
                    val paint = textPaint(12f, COLOR_MUTED)
                    canvas.drawText("No transactions found for the selected period.", 42f, y + 28f, paint)
                }
                drawFooter(canvas, pageNumber, pageCount)
                doc.finishPage(page)
            }
            val buffer = ByteArrayOutputStream()
            doc.writeTo(buffer)
            save(context, "DailyLedger-Statement-${stamp()}.pdf", "application/pdf", buffer.toByteArray())
        } finally {
            doc.close()
        }
    }

    private fun drawPageHeader(
        canvas: Canvas,
        pageNumber: Int,
        from: LocalDate?,
        through: LocalDate?,
        income: Long,
        expenses: Long,
        showSummary: Boolean,
    ) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(RectF(24f, 24f, 571f, 818f), 13f, 13f, border)
        val title = textPaint(23f, COLOR_NAVY, Typeface.BOLD)
        val label = textPaint(10f, COLOR_MUTED)
        canvas.drawText("DAILY LEDGER", 42f, 61f, title)
        canvas.drawText("ACCOUNT STATEMENT", 42f, 82f, textPaint(11f, COLOR_TEAL, Typeface.BOLD))
        canvas.drawText("Generated ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))}", 42f, 103f, label)
        val period = when {
            from == null && through == null -> "All available records"
            from == null -> "Through ${through!!.format(DATE_FORMAT)}"
            through == null -> "From ${from.format(DATE_FORMAT)}"
            else -> "${from.format(DATE_FORMAT)}  —  ${through.format(DATE_FORMAT)}"
        }
        canvas.drawText("STATEMENT PERIOD  $period", 42f, 127f, textPaint(10f, COLOR_INK, Typeface.BOLD))

        if (showSummary) {
            val cardY = 145f
            val cardH = 62f
            val cardW = 165f
            val gap = 10f
            drawSummaryCard(canvas, 42f, cardY, cardW, cardH, "MONEY IN", "+ PKR ${plainAmount(income)}", COLOR_GREEN)
            drawSummaryCard(canvas, 42f + cardW + gap, cardY, cardW, cardH, "MONEY OUT", "− PKR ${plainAmount(expenses)}", COLOR_RED)
            drawSummaryCard(canvas, 42f + (cardW + gap) * 2, cardY, cardW, cardH, "NET MOVEMENT", "PKR ${plainAmount(income - expenses)}", COLOR_NAVY)
        }
        canvas.drawText("Page $pageNumber", 515f, 103f, label)
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
        canvas.drawText(fitText(value, textPaint(11f, color, Typeface.BOLD), width - 18f), x + 10f, y + 43f, textPaint(11f, color, Typeface.BOLD))
    }

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        val background = Paint().apply { color = COLOR_NAVY }
        canvas.drawRoundRect(RectF(38f, y, 557f, y + ROW_HEIGHT), 4f, 4f, background)
        val paint = textPaint(9f, Color.WHITE, Typeface.BOLD)
        canvas.drawText("DATE", 47f, y + 16f, paint)
        canvas.drawText("TYPE", 112f, y + 16f, paint)
        canvas.drawText("CATEGORY", 171f, y + 16f, paint)
        canvas.drawText("DESCRIPTION", 304f, y + 16f, paint)
        canvas.drawText("AMOUNT (PKR)", 468f, y + 16f, paint)
    }

    private fun drawRow(canvas: Canvas, y: Float, transaction: TransactionEntity, alternate: Boolean) {
        if (alternate) {
            canvas.drawRect(39f, y, 556f, y + ROW_HEIGHT, Paint().apply { color = Color.rgb(247, 250, 253) })
        }
        val divider = Paint().apply { color = COLOR_BORDER; strokeWidth = .6f }
        canvas.drawLine(39f, y + ROW_HEIGHT, 556f, y + ROW_HEIGHT, divider)
        val body = textPaint(8.5f, COLOR_INK)
        val typeColor = if (transaction.type == "INCOME") COLOR_GREEN else COLOR_RED
        canvas.drawText(fitText(transaction.date, body, 57f), 47f, y + 16f, body)
        canvas.drawText(fitText(transaction.type, textPaint(8f, typeColor, Typeface.BOLD), 52f), 112f, y + 16f, textPaint(8f, typeColor, Typeface.BOLD))
        canvas.drawText(fitText(transaction.category, body, 125f), 171f, y + 16f, body)
        canvas.drawText(fitText(transaction.note.ifBlank { "—" }, body, 155f), 304f, y + 16f, body)
        val sign = if (transaction.type == "EXPENSE") "−" else "+"
        val amount = "$sign ${plainAmount(transaction.amountMinor)}"
        val amountPaint = textPaint(8.5f, typeColor, Typeface.BOLD)
        canvas.drawText(fitText(amount, amountPaint, 79f), 468f, y + 16f, amountPaint)
    }

    private fun drawFooter(canvas: Canvas, page: Int, totalPages: Int) {
        val divider = Paint().apply { color = COLOR_BORDER; strokeWidth = .8f }
        canvas.drawLine(42f, 788f, 553f, 788f, divider)
        canvas.drawText("Daily Ledger  •  PKR  •  Keep this statement for your records", 42f, 804f, textPaint(8f, COLOR_MUTED))
        val pageText = "Page $page of $totalPages"
        val paint = textPaint(8f, COLOR_MUTED)
        canvas.drawText(pageText, 553f - paint.measureText(pageText), 804f, paint)
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

    private fun String.isWithin(from: LocalDate?, through: LocalDate?): Boolean {
        if (from == null && through == null) return true
        val date = try {
            if (length == 7) YearMonth.parse(this).atDay(1) else LocalDate.parse(this)
        } catch (_: Exception) {
            return false
        }
        return (from == null || !date.isBefore(from)) && (through == null || !date.isAfter(through))
    }

    private fun TransactionEntity.isWithin(from: LocalDate?, through: LocalDate?) = date.isWithin(from, through)
    private fun esc(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val ROW_HEIGHT = 22f
    private const val COLOR_NAVY = 0xFF12324A.toInt()
    private const val COLOR_TEAL = 0xFF087F8C.toInt()
    private const val COLOR_INK = 0xFF263746.toInt()
    private const val COLOR_MUTED = 0xFF718096.toInt()
    private const val COLOR_BORDER = 0xFFD8E1E8.toInt()
    private const val COLOR_GREEN = 0xFF13805C.toInt()
    private const val COLOR_RED = 0xFFB83A45.toInt()
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy")
}
