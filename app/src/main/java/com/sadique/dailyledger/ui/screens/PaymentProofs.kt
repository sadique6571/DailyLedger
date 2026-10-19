package com.sadique.dailyledger.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Visibility
import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.PaymentProofEntity
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Attach / view / delete payment proof images for a single payment record.
 *
 * [kind] is one of "loan", "committee", "credit", "organizer" and must match
 * the payment table the [paymentId] belongs to.
 */
@Composable
fun PaymentProofSection(
    paymentId: String,
    kind: String,
    repo: LedgerRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val proofs by repo.observePaymentProofs(paymentId).collectAsState(initial = emptyList())
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PaymentProofEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy=true
            try {
            runCatching {
                val mime = context.contentResolver.getType(uri)
                require(mime == null || mime.startsWith("image/") || mime == "application/pdf") {
                    "Only photos and PDFs can be attached."
                }
                repo.addPaymentProof(paymentId, kind, uri, mime, context)
                error = null
            }.onFailure { if(it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "Could not attach the proof." }
            } finally { busy=false }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Payment proof", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("(${proofs.size})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Attach", fontSize = 12.sp)
            }
        }
        if(busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Photos or PDF • 10 MB per file • 20 MB per account", style=MaterialTheme.typography.labelSmall)
        proofs.forEach { proof ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Receipt, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(proofLabel(proof), fontSize = 13.sp)
                    Text(proofMeta(proof), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { runCatching { viewProof(context, repo, proof) }.onFailure { error=it.message ?: "Could not open proof." } } }) {
                    Icon(Icons.Rounded.Visibility, "View proof", Modifier.size(20.dp))
                }
                IconButton(onClick = { pendingDelete = proof }) {
                    Icon(Icons.Rounded.Delete, "Delete proof", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
    }

    pendingDelete?.let { proof ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this proof?") },
            text = { Text("The attached file will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { runCatching { repo.deletePaymentProof(proof, context) }.onFailure { error=it.message ?: "Could not delete proof." } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun proofLabel(proof: PaymentProofEntity): String {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(proof.createdAt))
    return "Receipt · $date"
}

private fun proofMeta(proof: PaymentProofEntity): String {
    val kb = proof.sizeBytes / 1024
    val size = if (kb < 1024) "$kb KB" else "${kb / 1024} MB"
    return "$size · ${proof.mimeType.ifBlank { "image" }}"
}

private suspend fun viewProof(context: android.content.Context, repo: LedgerRepository, proof: PaymentProofEntity) {
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { proofCacheFile(context.applicationContext, proof) }
    if (!file.isFile) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, proof.mimeType.ifBlank { "image/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

/**
 * A payment history row that expands to reveal [PaymentProofSection].
 * Drop-in wrapper for existing payment rows.
 */
@Composable
fun PaymentRowWithProofs(
    paymentId: String,
    kind: String,
    repo: LedgerRepository?,
    rowContent: @Composable () -> Unit,
) {
    // Without a repository (e.g. UI tests) the row renders plainly with no proof actions.
    if (repo == null) {
        Box(Modifier.fillMaxWidth()) { rowContent() }
        return
    }
    var expanded by remember(paymentId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(
                    onClick = { expanded = !expanded },
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { rowContent() }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Rounded.Visibility else Icons.Rounded.AttachFile,
                    if (expanded) "Hide proofs" else "Payment proofs",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            PaymentProofSection(paymentId, kind, repo)
        }
    }
}
