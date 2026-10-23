@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerEntrySheet

import com.sadique.dailyledger.ui.LedgerDateField

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sadique.dailyledger.ai.CreditAiResult
import com.sadique.dailyledger.ai.OfflineCreditAi
import com.sadique.dailyledger.data.CreditDraft
import com.sadique.dailyledger.data.CreditPurchaseEntity
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.util.UUID

@Composable
internal fun CreditEntryDialog(
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
    LedgerEntrySheet(
        canDismiss = !busy, onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).testTag("udhar-manual-entry"),
        title = { Text(if (initial == null) "Add udhar saman" else "Review udhar entry") },
        text = {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth().testTag("udhar-amount"),
                    label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true)
                LedgerDateField(date, { date = it }, label = "Purchase date")
                LedgerDateField(due, { due = it }, label = "Due date (optional)", optional = true)
                OutlinedTextField(note, { note = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Note") })
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
            Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall,
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
    LedgerEntrySheet(scrollBody = false,
        canDismiss = !busy, onDismissRequest = { if (!busy) dismiss() },
        title = { Text("AI Udhar Add") },
        text = {
            LazyColumn(Modifier.testTag("credit-ai-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "input") {
                    Text("Example: 1 Oct rashan 5,000 Aslam Store se udhar, due 15 Oct. Works offline.")
                    OutlinedTextField(creditorHint, { creditorHint = it; result = null }, label = { Text("Shop/person hint") }, enabled = !busy)
                    OutlinedTextField(input, { input = it; result = null; error = "" }, minLines = 3,
                        label = { Text("Udhar saman likhein") }, enabled = !busy, modifier = Modifier.testTag("credit-ai-input"))
                    Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, onClick = {
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
            Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, onClick = {
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
internal fun CreditPaymentDialog(
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
    LedgerEntrySheet(
        canDismiss = !busy, onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { Text("Record udhar payment") },
        text = {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("Payment aapke current salary-cycle balance se minus hogi.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                LedgerDateField(date, { date = it }, label = "Entry date")
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Cash", "Bank", "Easypaisa", "JazzCash").forEach { option ->
                        FilterChip(selected = method == option, onClick = { method = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(note, { note = it }, singleLine = true, label = { Text("Note") })
            }
        },
        confirmButton = {
            val parsed = parseMinor(amount)
            Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall,
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

@Composable
internal fun DeleteCreditDialog(
    credit: CreditPurchaseEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    com.sadique.dailyledger.ui.PopAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete udhar record?") },
        text = { Text("${credit.item} and its payment history will be removed.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
