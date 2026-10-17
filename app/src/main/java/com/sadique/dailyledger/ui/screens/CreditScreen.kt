@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sadique.dailyledger.ai.CreditAiResult
import com.sadique.dailyledger.ai.OfflineCreditAi
import com.sadique.dailyledger.data.CreditDraft
import com.sadique.dailyledger.data.CreditPaymentEntity
import com.sadique.dailyledger.data.CreditPurchaseEntity
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.categoryVisual
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerBackdrop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.util.UUID
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun CreditScreen(
    credits: List<CreditPurchaseEntity>,
    payments: List<CreditPaymentEntity>,
    onSave: suspend (List<CreditDraft>, String) -> Unit,
    onPayment: suspend (String, Long, String, String, String) -> Unit,
    onDelete: (CreditPurchaseEntity) -> Unit,
    requestAdd: Boolean = false,
    onAddOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    var add by remember { mutableStateOf(false) }
    LaunchedEffect(requestAdd) { if (requestAdd) { add = true; onAddOpened() } }
    var aiAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CreditPurchaseEntity?>(null) }
    val manualBatchId = remember(add) { UUID.randomUUID().toString() }
    var pay by remember { mutableStateOf<CreditPurchaseEntity?>(null) }
    val outstanding = credits.sumOf { c ->
        val paid = payments.filter { it.creditId == c.id }.sumOf { it.amountMinor }
        (c.amountMinor - paid).coerceAtLeast(0L)
    }

    LedgerBackdrop(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                                Icons.Outlined.ShoppingCart,
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
                            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Udhar Add")
                        }
                    }
                }
            }

            if (credits.isEmpty()) {
                enteringItem { Text("Abhi koi udhar saman record nahi hai. Add button ya AI Udhar Add se entry karein.") }
            }

            enteringItems(credits, key = { it.id }) { credit ->
                val history = payments.filter { it.creditId == credit.id }
                val paid = history.sumOf { it.amountMinor }
                val left = (credit.amountMinor - paid).coerceAtLeast(0L)
                LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val tint = categoryVisual(credit.category).color
                            Surface(shape = CircleShape, color = tint.copy(alpha = 0.11f)) {
                                Icon(
                                    categoryVisual(credit.category).icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                    tint = tint,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(credit.item, translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("${credit.creditor} • ${com.sadique.dailyledger.i18n.LocalStrings.current.text(credit.category)}", translate = false, style = MaterialTheme.typography.bodyMedium)
                                Text("Bought ${credit.purchaseDate} • ${money(credit.amountMinor)}", style = MaterialTheme.typography.bodySmall)
                                credit.dueDate?.let { Text("Due $it • ${creditDueLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                                Text("Paid ${money(paid)} • Remaining ${money(left)}")
                                if (left == 0L || credit.closed) Text("Paid in full", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { deleting = credit }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                        }

                        if (credit.phone.isNotBlank() || credit.whatsapp.isNotBlank()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (credit.phone.isNotBlank()) {
                                    AssistChip(
                                        onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${credit.phone}"))) },
                                        label = { Text("Call") },
                                        leadingIcon = { Icon(Icons.Outlined.Call, null) },
                                    )
                                    AssistChip(
                                        onClick = {
                                            context.openContactAction(
                                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${credit.phone}"))
                                                    .putExtra("sms_body", creditReminder(credit, left))
                                            )
                                        },
                                        label = { Text("SMS") },
                                        leadingIcon = { Icon(Icons.Outlined.Sms, null) },
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
                                        leadingIcon = { Icon(Icons.Outlined.Chat, null) },
                                    )
                                }
                            }
                        }

                        Button(onClick = { pay = credit }, enabled = left > 0L, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Record payment")
                        }
                        history.take(3).forEach { row ->
                            com.sadique.dailyledger.ui.LedgerHistoryCard() {
                                Text("${row.date} • ${money(row.amountMinor)} • ${row.method}", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    }
    deleting?.let { credit ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete udhar record?") },
            text = { Text("${credit.item} and its payment history will be removed.") },
            confirmButton = { TextButton(onClick = { onDelete(credit); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
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

@Composable
private fun CreditEntryDialog(
    initial: CreditDraft? = null,
    dismiss: () -> Unit,
    save: suspend (String, String, Long, String, String, String?, String, String, String) -> Unit,
) {
    var creditor by remember { mutableStateOf(initial?.creditor.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amountMinor?.let(::plainAmount).orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var date by remember { mutableStateOf(initial?.purchaseDate ?: LedgerRepository.today()) }
    var due by remember { mutableStateOf(initial?.dueDate.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var wa by remember { mutableStateOf(initial?.whatsapp.orEmpty()) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).testTag("udhar-manual-entry"),
        title = { Text(if (initial == null) "Add udhar saman" else "Review udhar entry") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                ContactPickerButton { pickedName, pickedPhone ->
                    if (creditor.isBlank()) creditor = pickedName
                    phone = pickedPhone
                    if (wa.isBlank()) wa = pickedPhone
                }
                OutlinedTextField(creditor, { creditor = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Shop / person") }, singleLine = true)
                CategoryDropdown("EXPENSE", category, { category = it }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("udhar-category"))
                OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true)
                OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Purchase date YYYY-MM-DD") }, isError = !isValidDate(date), singleLine = true)
                OutlinedTextField(due, { due = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Due date (optional) YYYY-MM-DD") },
                    isError = due.isNotBlank() && !isValidDate(due), singleLine = true)
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note") })
                Text("Contact (optional)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true)
                OutlinedTextField(wa, { wa = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true)
            }
        },
        confirmButton = {
            val parsed = parseMinor(amount)
            Button(
                enabled = !busy && parsed?.let { it > 0L } == true && category.isNotBlank() && isValidDate(date) && (due.isBlank() || isValidDate(due)),
                onClick = {
                    val value = parsed ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            // No separate item field: manual Udhar uses the selected category as its item.
                            // Preserve meaningful AI draft item names when their category was not changed.
                            val resolvedItem = initial?.item?.takeIf {
                                it.isNotBlank() && category == initial.category
                            } ?: category.trim()
                            save(creditor, resolvedItem, value, category, date.trim(),
                                due.trim().ifBlank { null }, note, phone, wa)
                        }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Could not save. Try again." }
                        finally { busy = false }
                    }
                },
            ) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } },
    )


}

@Composable
internal fun CreditAiDialog(dismiss: () -> Unit, save: suspend (List<CreditDraft>, String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var creditorHint by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CreditAiResult?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var batchId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text("AI Udhar Add") },
        text = {
            LazyColumn(Modifier.heightIn(max = 500.dp).testTag("credit-ai-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "input") {
                    Text("Example: 1 Oct rashan 5,000 Aslam Store se udhar, due 15 Oct. Works offline.")
                    OutlinedTextField(creditorHint, { creditorHint = it; result = null }, label = { Text("Shop/person hint") }, enabled = !busy)
                    OutlinedTextField(input, { input = it; result = null; error = "" }, minLines = 3,
                        label = { Text("Udhar saman likhein") }, enabled = !busy, modifier = Modifier.testTag("credit-ai-input"))
                    Button(onClick = {
                        error = ""
                        result = try { OfflineCreditAi.drafts(input, creditorHint) }
                            catch (e: Exception) { error = e.message ?: "Check the dates and amount."; null }
                        batchId = UUID.randomUUID().toString()
                        if (result == null && error.isBlank()) error = "Write an item and amount for every entry (maximum 10). Nothing was saved."
                    }, enabled = !busy && input.isNotBlank(), modifier = Modifier.testTag("credit-ai-prepare")) { Text("Prepare entries") }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    result?.let { Text(it.message) }
                }
                result?.entries?.forEachIndexed { index, draft -> item(key = "credit-$index") {
                    LedgerCard {
                        Text("${draft.item} • ${money(draft.amountMinor)}", style = MaterialTheme.typography.titleSmall)
                        Text("${draft.creditor} • ${com.sadique.dailyledger.i18n.LocalStrings.current.text(draft.category)}", translate = false)
                        Text("Purchase ${draft.purchaseDate}")
                        draft.dueDate?.let { Text("Due $it") }
                        Row {
                            TextButton(onClick = { editing = index }, enabled = !busy, modifier = Modifier.testTag("credit-edit-$index")) { Text("Edit") }
                            TextButton(onClick = { result = result?.copy(entries = result!!.entries.filterIndexed { i, _ -> i != index }) }, enabled = !busy) { Text("Remove") }
                        }
                    }
                } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val drafts = result?.entries.orEmpty()
                busy = true
                scope.launch {
                    try { save(drafts, batchId) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not save. Try again." }
                    finally { busy = false }
                }
            }, enabled = !busy && result?.entries?.isNotEmpty() == true, modifier = Modifier.testTag("credit-ai-save")) {
                Text(if (busy) "Saving…" else "Save ${result?.entries?.size ?: 0} entries")
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } },
    )
    editing?.let { index -> result?.entries?.getOrNull(index)?.let { draft ->
        CreditEntryDialog(initial = draft, dismiss = { editing = null }, save = { creditor, item, amount, category, date, due, note, phone, wa ->
            val updated = CreditDraft(creditor, item, amount, category, date, due, note, phone, wa).validated()
            result = result?.copy(entries = result!!.entries.mapIndexed { i, row -> if (i == index) updated else row })
            editing = null
        })
    } }
}

@Composable
private fun CreditPaymentDialog(
    remaining: Long,
    dismiss: () -> Unit,
    save: suspend (Long, String, String, String) -> Unit,
) {
    var amount by remember { mutableStateOf(plainAmount(remaining)) }
    var date by remember { mutableStateOf(LedgerRepository.today()) }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { Text("Record udhar payment") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("Payment aapke current salary-cycle balance se minus hogi.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, isError = !isValidDate(date))
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Cash", "Bank", "Easypaisa", "JazzCash").forEach { option ->
                        FilterChip(selected = method == option, onClick = { method = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            val parsed = parseMinor(amount)
            Button(
                enabled = !busy && isValidDate(date) && parsed?.let { it in 1..remaining } == true,
                onClick = {
                    val value = parsed?.takeIf { it in 1..remaining } ?: return@Button
                    busy = true
                    scope.launch {
                        try { save(value, date.trim(), note, method) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Could not save payment." }
                        finally { busy = false }
                    }
                },
            ) { Text(if (busy) "Saving…" else "Record") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } },
    )
}

private fun creditReminder(credit: CreditPurchaseEntity, left: Long): String =
    "Assalam o Alaikum ${credit.creditor}, Daily Ledger record ke mutabiq ${money(left)} udhar balance remaining hai (${credit.item}). Payment/update ke hawale se rabta kar raha hoon."

private fun creditDueLabel(date: String): String = runCatching {
    val due = java.time.LocalDate.parse(date)
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    when {
        days > 0 -> "$days days left"
        days == 0L -> "due today"
        else -> "${-days} days overdue"
    }
}.getOrDefault("date check")

private fun creditWhatsappNumber(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}
