@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.CreditDraft
import com.sadique.dailyledger.data.CreditPaymentEntity
import com.sadique.dailyledger.data.CreditPurchaseEntity
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.ui.categoryVisual
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.LedgerBackdrop
import kotlinx.coroutines.launch
import com.sadique.dailyledger.ui.money
import java.util.UUID
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun CreditScreen(
    credits: List<CreditPurchaseEntity>,
    payments: List<CreditPaymentEntity>,
    repo: LedgerRepository? = null,
    onSave: suspend (List<CreditDraft>, String) -> Unit,
    onPayment: suspend (String, Long, String, String, String) -> Unit,
    onDelete: (CreditPurchaseEntity) -> Unit,
    requestPayment: Boolean = false, onPaymentOpened: () -> Unit = {},
    requestAdd: Boolean = false,
    onAddOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    var add by remember { mutableStateOf(false) }
    LaunchedEffect(requestAdd) { if (requestAdd) { add = true; onAddOpened() } }
    var aiAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CreditPurchaseEntity?>(null) }
    var pendingUndoDelete by remember { mutableStateOf<CreditPurchaseEntity?>(null) }
    val undoBar = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    val visibleCredits = credits.filter { it.id != pendingUndoDelete?.id }
    fun requestDelete(credit: CreditPurchaseEntity) {
        if (pendingUndoDelete == credit) return
        pendingUndoDelete = credit
        undoScope.launch {
            val result = undoBar.showSnackbar("Udhar record removed", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
            if (result != SnackbarResult.ActionPerformed) onDelete(credit)
            if (pendingUndoDelete == credit) pendingUndoDelete = null
        }
    }
    val manualBatchId = remember(add) { UUID.randomUUID().toString() }
    var pay by remember { mutableStateOf<CreditPurchaseEntity?>(null) }
    var pickPayment by remember { mutableStateOf(false) }
    LaunchedEffect(requestPayment) { if (requestPayment) { pickPayment = true; onPaymentOpened() } }
    if (pickPayment) com.sadique.dailyledger.ui.EntryRecordPicker("Choose payment record",
        credits.filter { row -> row.amountMinor > payments.filter { it.creditId == row.id }.sumOf { it.amountMinor } }
            .map { row -> row.creditor to { pay = row; pickPayment = false } },
        { pickPayment = false })

    val outstanding = credits.sumOf { c ->
        val paid = payments.filter { it.creditId == c.id }.sumOf { it.amountMinor }
        (c.amountMinor - paid).coerceAtLeast(0L)
    }

    LedgerBackdrop(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(undoBar) },
        floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, "Add udhar") } },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            enteringItem {
                Text("Udhar Saman", style = MaterialTheme.typography.headlineMedium)
                Text("Shop/person se udhar liya hua saman, categories, due dates aur payments alag record honge.")
                Spacer(Modifier.height(10.dp))
                LedgerCard(Modifier.fillMaxWidth().clickable { add = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                Icons.Rounded.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Total outstanding", style = MaterialTheme.typography.labelLarge)
                            Text(money(outstanding), style = MaterialTheme.typography.headlineSmall)
                        }
                        Button(onClick = { aiAdd = true }) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Udhar Add")
                        }
                    }
                }
            }

            if (visibleCredits.isEmpty()) {
                enteringItem { com.sadique.dailyledger.ui.LedgerEmptyState("No udhar records yet",
                    "Abhi koi udhar saman record nahi hai. Add button ya AI Udhar Add se entry karein.",
                    "Add udhar", Icons.Rounded.ShoppingCart, onAction = { add = true }) }
            }

            enteringItems(visibleCredits, key = { it.id }) { credit ->
                val history = payments.filter { it.creditId == credit.id }
                val paid = history.sumOf { it.amountMinor }
                val left = (credit.amountMinor - paid).coerceAtLeast(0L)
                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) { deleting = credit; false } else false
                    },
                )
                SwipeToDismissBox(
                    state = swipeState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, LedgerTokens.ShapeCard).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                ) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val accent = com.sadique.dailyledger.ui.ledgerCardAccent(credit.creditor)
                            Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        credit.creditor.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).map { it.first().uppercaseChar() }.joinToString(""),
                                        style = MaterialTheme.typography.titleSmall, color = accent, translate = false,
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(credit.item, translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("${credit.creditor} • ${com.sadique.dailyledger.i18n.LocalStrings.current.text(credit.category)}", translate = false, style = MaterialTheme.typography.bodyMedium)
                                Text("Bought ${credit.purchaseDate} • ${money(credit.amountMinor)}", style = MaterialTheme.typography.bodySmall)
                                credit.dueDate?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Due $it", style = MaterialTheme.typography.bodySmall)
                                        com.sadique.dailyledger.ui.DueDateBadge(it)
                                    }
                                }
                                Text("Paid ${money(paid)} • Remaining ${money(left)}")
                                if (left == 0L || credit.closed) Text("Paid in full", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = {
                                val personCredits = credits.filter { it.creditor.equals(credit.creditor, ignoreCase = true) }
                                val text = buildString {
                                    appendLine("Udhar statement — ${credit.creditor}")
                                    var totalLeft = 0L
                                    personCredits.forEach { c ->
                                        val p = payments.filter { it.creditId == c.id }.sumOf { it.amountMinor }
                                        val l = (c.amountMinor - p).coerceAtLeast(0L)
                                        totalLeft += l
                                        appendLine("• ${c.item} (${c.purchaseDate}) — ${money(c.amountMinor)} · Paid ${money(p)} · Remaining ${money(l)}")
                                    }
                                    appendLine("Total remaining: ${money(totalLeft)}")
                                    appendLine("— Daily Ledger")
                                }
                                com.sadique.dailyledger.ui.shareText(context, "Share udhar statement", text)
                            }) { Icon(Icons.Rounded.Share, "Share statement") }
                            IconButton(onClick = { deleting = credit }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                        }

                        if (credit.phone.isNotBlank() || credit.whatsapp.isNotBlank()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (credit.phone.isNotBlank()) {
                                    AssistChip(
                                        onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${credit.phone}"))) },
                                        label = { Text("Call") },
                                        leadingIcon = { Icon(Icons.Rounded.Call, null) },
                                    )
                                    AssistChip(
                                        onClick = {
                                            context.openContactAction(
                                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${credit.phone}"))
                                                    .putExtra("sms_body", creditReminder(credit, left))
                                            )
                                        },
                                        label = { Text("SMS") },
                                        leadingIcon = { Icon(Icons.Rounded.Sms, null) },
                                    )
                                }
                                val wa = credit.whatsapp.ifBlank { credit.phone }
                                if (wa.isNotBlank()) {
                                    AssistChip(
                                        onClick = {
                                            val num = creditWhatsappNumber(wa)
                                            val msg = URLEncoder.encode(creditReminder(credit, left), StandardCharsets.UTF_8.toString())
                                            context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=$msg")))
                                        },
                                        label = { Text("WhatsApp") },
                                        leadingIcon = { Icon(Icons.Rounded.Chat, null) },
                                    )
                                }
                            }
                        }

                        Button(onClick = { pay = credit }, enabled = left > 0L, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Record payment")
                        }
                        history.take(3).forEach { row ->
                            com.sadique.dailyledger.ui.LedgerHistoryCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(row.id)) {
                                PaymentRowWithProofs(row.id, "credit", repo) {
                                    Text("${row.date} • ${money(row.amountMinor)} • ${row.method}", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    }
    deleting?.let { credit ->
        DeleteCreditDialog(
            credit = credit,
            onDismiss = { deleting = null },
            onConfirm = { requestDelete(credit); deleting = null },
        )
    }
    if (add) {
        CreditEntryDialog(
            dismiss = { add = false },
            save = { creditor, item, amount, category, date, due, note, phone, wa ->
                onSave(listOf(CreditDraft(creditor, item, amount, category, date, due, note, phone, wa)), manualBatchId)
                add = false
            },
        )
    }

    if (aiAdd) {
        CreditAiDialog(
            dismiss = { aiAdd = false },
            save = { rows, batchId -> onSave(rows, batchId); aiAdd = false },
        )
    }

    pay?.let { credit ->
        val paid = payments.filter { it.creditId == credit.id }.sumOf { it.amountMinor }
        val remaining = (credit.amountMinor - paid).coerceAtLeast(0L)
        CreditPaymentDialog(
            remaining = remaining,
            dismiss = { pay = null },
            save = { amount, date, note, method ->
                onPayment(credit.id, amount, date, note, method)
                pay = null
            },
        )
    }
}
