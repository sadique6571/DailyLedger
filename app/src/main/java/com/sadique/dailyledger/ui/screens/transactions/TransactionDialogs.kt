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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import java.time.LocalDate
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount

@OptIn(ExperimentalMaterial3Api::class)
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
    var type by rememberSaveable { mutableStateOf(x?.type ?: initialType) }
    var amount by rememberSaveable { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: initialAmount) }
    var cat by rememberSaveable { mutableStateOf(x?.category ?: initialCategory) }
    var note by rememberSaveable { mutableStateOf(x?.note ?: initialNote) }
    var date by rememberSaveable { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    var discardWarning by rememberSaveable { mutableStateOf(false) }
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
    // Sheet state keys include confirmValueChange. Keep its identity stable while
    // reading current draft state; a new callback on each keystroke recreates the
    // sheet at Hidden and replays its opening animation.
    val latestDirty = rememberUpdatedState(dirty)
    val confirmSheetChange: (SheetValue) -> Boolean = remember {
        { target ->
            if (target == SheetValue.Hidden && latestDirty.value) {
                discardWarning = true
                false
            } else true
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true, confirmValueChange = confirmSheetChange,
    )
    ModalBottomSheet(
        onDismissRequest = safeDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("transaction-bottom-sheet"),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.82f).imePadding()
            .testTag("wide-transaction-entry")) {
            Text(if (x != null) "Edit transaction" else if (initialCategory == "Salary") "Add salary" else "Add transaction",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("100", "500", "1000", "5000").forEach { preset ->
                        AssistChip(onClick = { amount = preset }, label = { Text("Rs $preset") })
                    }
                }
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
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = safeDismiss) { Text("Cancel") }
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("transaction-save"),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isValidDate(date) && (parseMinor(amount) ?: 0) > 0 && cat.isNotBlank(),
                    onClick = {
                        parseMinor(amount)?.takeIf { it > 0 && cat.isNotBlank() }?.let { save(type, it, cat, note, date.trim()) }
                    },
                ) { Text("Save") }
            }
        }
    }

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
