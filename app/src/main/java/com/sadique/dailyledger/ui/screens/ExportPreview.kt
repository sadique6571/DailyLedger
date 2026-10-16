package com.sadique.dailyledger.ui.screens

import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sadique.dailyledger.export.ExportManager
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportPreview(bytes: ByteArray, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    val strings = com.sadique.dailyledger.i18n.LocalStrings.current
    val file by produceState<File?>(null, bytes) {
        try { value = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
            File.createTempFile("DailyLedger-", ".pdf", directory).apply { writeBytes(bytes) }
        } } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { previewError = true; message = "Could not prepare the PDF preview. Try again." }
    }
    val bitmap by produceState<Bitmap?>(null, file, page) {
        val source = file ?: return@produceState
        value = null
        try { val result = withContext(Dispatchers.IO) {
            PdfRenderer(ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                require(renderer.pageCount > 0)
                renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { p ->
                    val width = 1000; val height = (p.height.toLong() * width / p.width).toInt()
                    renderer.pageCount to Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }
        }; count = result.first; value = result.second
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { previewError = true; message = "Could not prepare the PDF preview. Try again." }
    }
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, securePolicy = androidx.compose.ui.window.SecureFlagPolicy.Inherit)) {
        Surface(Modifier.fillMaxSize().testTag("pdf-preview")) {
            Column(Modifier.safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = dismiss, enabled = !busy) { Text("Close") }
                    Text("PDF preview", style = MaterialTheme.typography.titleLarge)
                    Text("${page + 1} / $count")
                }
                bitmap?.let { Image(it.asImageBitmap(), strings.text("PDF page ${page + 1}"), Modifier.weight(1f).fillMaxWidth()) } ?: Box(Modifier.weight(1f)) { if (!previewError) CircularProgressIndicator() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous") }
                    TextButton(enabled = page + 1 < count, onClick = { page++ }) { Text("Next") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !busy && file != null, onClick = {
                        scope.launch { busy = true; try { ExportManager.savePreparedPdf(context, bytes); message = "PDF saved to Downloads" }
                        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                        catch (e: Exception) { message = e.message } finally { busy = false } }
                    }, modifier = Modifier.weight(1f)) { Text("Save PDF") }
                    OutlinedButton(enabled = !busy && file != null, onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", requireNotNull(file))
                            val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("PDF", uri) }
                            context.startActivity(Intent.createChooser(intent, strings.text("Share PDF")))
                        }.onFailure { message = "Could not open sharing. Save the PDF and share it from Downloads." }
                    }, modifier = Modifier.weight(1f)) { Text("Share") }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
