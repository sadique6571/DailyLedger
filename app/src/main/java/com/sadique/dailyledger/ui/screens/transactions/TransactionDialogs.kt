package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerDateField
import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField
import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount

@Composable
fun TransactionDialog(
    x: TransactionEntity?,
    dismiss: () -> Unit,
    save: (String, Long, String, String, String) -> Unit,
    initialType: String = "EXPENSE",
    initialCategory: String = "",
    initialNote: String = "",
    initialAmount: String = "",
    onHistory: ((String, String) -> Unit)? = null,
    onAdvanced: ((TransactionEntity) -> Unit)? = null,
) {
    var type by remember { mutableStateOf(x?.type ?: initialType) }
    var amount by remember { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: initialAmount) }
    var cat by remember { mutableStateOf(x?.category ?: initialCategory) }
    var note by remember { mutableStateOf(x?.note ?: initialNote) }
    var date by remember { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    var discardWarning by remember { mutableStateOf(false) }
    val originalAmount = x?.let { plainAmount(it.amountMinor) } ?: initialAmount
    val dirty = amount != originalAmount || type != (x?.type ?: initialType) || cat != (x?.category ?: initialCategory) || note != (x?.note ?: initialNote) || date != (x?.date ?: LedgerRepository.today())
    val safeDismiss = { if (dirty) discardWarning = true else dismiss() }
    if (discardWarning) AlertDialog(
        onDismissRequest = { discardWarning = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your edits have not been saved.") },
        confirmButton = { TextButton(onClick = { discardWarning = false; dismiss() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardWarning = false }) { Text("Keep editing") } },
    )
    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.97f).testTag("wide-transaction-entry"),
        title = { Text(if (x != null) "Edit transaction" else if (initialCategory == "Salary") "Add salary" else "Add transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(type == "EXPENSE", { type = "EXPENSE"; cat = "" }, { Text("Expense") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(type == "INCOME", { type = "INCOME"; cat = "" }, { Text("Income") })
                }
                if (type == "INCOME") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(cat.equals("Salary", true), { cat = "Salary" }, { Text("Salary") })
                        FilterChip(!cat.equals("Salary", true), { if (cat.equals("Salary", true)) cat = "Other income" }, { Text("Other income") })
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), isError = amount.isNotBlank() && (parseMinor(amount) ?: 0) <= 0, supportingText = { Text("Enter an amount greater than zero") })
                onAdvanced?.let { action -> TextButton(onClick = {
                    action((x ?: TransactionEntity("","",type,parseMinor(amount) ?: 0,cat,note,date,0,0)).copy(type=type,amountMinor=parseMinor(amount) ?: 0,category=cat,note=note,date=date))
                }) { Text("Quantity, account & privacy options") } }
                CategoryDropdown(type, cat, { cat = it })
                onHistory?.let { history -> TextButton(onClick = { history(type, cat) }) { Text("View history") } }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                Text("Quick notes", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Monthly", "Weekly", "Cash", "Online").forEach { preset ->
                        AssistChip(onClick = { note = if (note.isBlank()) preset else "$note • $preset" }, label = { Text(preset) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { date = LocalDate.now().toString() },
                        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Today") }
                    TextButton(onClick = { date = LocalDate.now().minusDays(1).toString() },
                        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Yesterday") }
                }
                LedgerDateField(date, { date = it }, label = if (type == "INCOME" && cat.equals("Salary", true)) "Salary date" else "Entry date", calendarTag = "transaction-calendar")
            }
        },
        confirmButton = {
            Button(
                enabled = isValidDate(date) && (parseMinor(amount) ?: 0) > 0,
                onClick = {
                    parseMinor(amount)?.takeIf { it > 0 }?.let { save(type, it, cat.ifBlank { if (type == "INCOME") "Other income" else "Other" }, note, date.trim()) }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = safeDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun TransactionDetailsDialog(
    detail: TransactionEntity,
    privateAmounts: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction details") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category: ${detail.category}")
            Text("Type: ${detail.type}")
            Text("Date: ${detail.date}")
            Text("Amount: ${if (privateAmounts) "Rs •••••" else money(detail.amountMinor)}")
            if (detail.note.isNotBlank()) Text("Note: ${detail.note}")
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(detail.category + " • " + detail.date + " • " + (if (privateAmounts) "Rs •••••" else money(detail.amountMinor)) + (if (detail.note.isNotBlank()) " • " + detail.note else "")))
                onCopy()
            }) { Text("Copy details") }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun DeleteTransactionDialog(
    target: TransactionEntity,
    privateAmounts: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete transaction?") },
        text = { Text("Delete ${target.category} (${if (privateAmounts) "Rs •••••" else money(target.amountMinor)}) from ${target.date}? You can undo the deletion from the message at the bottom.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DuplicateWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Possible duplicate") },
        text = { Text("A transaction with the same date, type, category and amount already exists. Save another entry anyway?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Review") } },
    )
}
