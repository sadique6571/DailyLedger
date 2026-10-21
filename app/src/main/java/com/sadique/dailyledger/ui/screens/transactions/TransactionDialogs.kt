package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerDateField
import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField
import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler
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
import com.sadique.dailyledger.ui.parseAmountExpression
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
    saveAsync: (suspend (String, Long, String, String, String) -> Unit)? = null,
) {
    var type by rememberSaveable { mutableStateOf(x?.type ?: initialType) }
    var amount by rememberSaveable { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: initialAmount) }
    var cat by rememberSaveable { mutableStateOf(x?.category ?: initialCategory) }
    var note by rememberSaveable { mutableStateOf(x?.note ?: initialNote) }
    var date by rememberSaveable { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    var discardWarning by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val saveScope = rememberCoroutineScope()
    val compactForm = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.4f
    val sidePadding = if (compactForm) 12.dp else 20.dp
    val originalAmount = x?.let { plainAmount(it.amountMinor) } ?: initialAmount
    val dirty = amount != originalAmount || type != (x?.type ?: initialType) || cat != (x?.category ?: initialCategory) || note != (x?.note ?: initialNote) || date != (x?.date ?: LedgerRepository.today())
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val safeDismiss = { if (!busy) { if (dirty) discardWarning = true else dismiss() } }
    val latestKeyboardVisible = rememberUpdatedState(keyboardVisible)
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
    val latestBusy = rememberUpdatedState(busy)
    val confirmSheetChange: (SheetValue) -> Boolean = remember {
        { target ->
            if (target == SheetValue.Hidden && latestBusy.value) {
                false
            } else if (target == SheetValue.Hidden && latestKeyboardVisible.value) {
                keyboard?.hide()
                false
            } else if (target == SheetValue.Hidden && latestDirty.value) {
                discardWarning = true
                false
            } else true
        }
    }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true, confirmValueChange = confirmSheetChange,
    )
    ModalBottomSheet(
        onDismissRequest = { if (keyboardVisible) keyboard?.hide() else safeDismiss() },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !keyboardVisible),
        sheetState = sheetState,
        // Fullscreen body scrolling must never hand a boundary fling to the sheet.
        // The dedicated handle still controls expand/reduce and dismissal.
        sheetGesturesEnabled = !fullScreen && !keyboardVisible && !busy,
        dragHandle = { com.sadique.dailyledger.ui.LedgerSheetHandle(fullScreen, { fullScreen = it }) {
            if (keyboardVisible) keyboard?.hide() else safeDismiss()
        } },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("transaction-bottom-sheet"),
    ) {
        BackHandler(enabled = keyboardVisible) { keyboard?.hide() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(if (fullScreen) .99f else .86f).imePadding()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f), Color.Transparent)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .testTag("wide-transaction-entry")) {
            Text(if (x != null) "Edit transaction" else if (initialCategory == "Salary") "Add salary" else "Add transaction",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = sidePadding, vertical = 4.dp))
            Column(Modifier.weight(1f).testTag("transaction-entry-body").verticalScroll(rememberScrollState())
                .padding(horizontal = sidePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(type == "EXPENSE", { type = "EXPENSE"; cat = "" }, { Text("Expense") }, enabled = !busy)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(type == "INCOME", { type = "INCOME"; cat = "" }, { Text("Income") }, enabled = !busy)
                }
                if (type == "INCOME") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(cat.equals("Salary", true), { cat = "Salary" }, { Text("Salary") }, enabled = !busy)
                        FilterChip(!cat.equals("Salary", true), { if (cat.equals("Salary", true)) cat = "Other income" }, { Text("Other income") }, enabled = !busy)
                    }
                }
                OutlinedTextField(amount, { amount = it }, enabled = !busy, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), isError = amount.isNotBlank() && (parseAmountExpression(amount) ?: 0) <= 0, supportingText = {
                    val exprTotal = if (amount.contains('+') || amount.contains('-')) parseAmountExpression(amount) else null
                    Text(if (exprTotal != null && exprTotal > 0) "= ${money(exprTotal)}" else "Enter an amount greater than zero")
                })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("100", "500", "1000", "5000").forEach { preset ->
                        AssistChip(enabled = !busy, onClick = { amount = preset }, label = { Text("Rs $preset") })
                    }
                }
                onAdvanced?.let { action -> TextButton(enabled = !busy, onClick = {
                    action((x ?: TransactionEntity("","",type,parseAmountExpression(amount) ?: 0,cat,note,date,0,0)).copy(type=type,amountMinor=parseAmountExpression(amount) ?: 0,category=cat,note=note,date=date))
                }) { Text("Quantity, account & privacy options") } }
                CategoryDropdown(type, cat, { cat = it }, inlineResults = true, enabled = !busy)
                OutlinedTextField(note, { note = it }, enabled = !busy, singleLine = true,
                    label = { Text("Note") }, modifier = Modifier.fillMaxWidth().testTag("transaction-note"))
                LedgerDateField(date, { date = it }, enabled = !busy, label = if (type == "INCOME" && cat.equals("Salary", true)) "Salary date" else "Entry date", calendarTag = "transaction-calendar")
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = sidePadding).testTag("transaction-save-error")) }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            com.sadique.dailyledger.ui.LedgerFormActions(
                modifier = Modifier.padding(horizontal = sidePadding, vertical = if (compactForm) 8.dp else 12.dp),
                dismissButton = { TextButton(onClick = safeDismiss, enabled = !busy) { Text("Cancel") } },
                confirmButton = { Button(
                    modifier = Modifier.heightIn(min = 48.dp).testTag("transaction-save"),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !busy && isValidDate(date) && (parseAmountExpression(amount) ?: 0) > 0 && cat.isNotBlank(),
                    onClick = {
                        if (!busy) parseAmountExpression(amount)?.takeIf { it > 0 && cat.isNotBlank() }?.let { parsed ->
                            val async = saveAsync
                            if (async == null) save(type, parsed, cat, note, date.trim())
                            else {
                                // Lock synchronously, before another tap can enqueue a save.
                                busy = true
                                saveError = null
                                val draft = listOf(type, cat, note, date.trim())
                                keyboard?.hide()
                                saveScope.launch {
                                    try { async(draft[0], parsed, draft[1], draft[2], draft[3]) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { saveError = e.message ?: "Could not save. Please try again." }
                                    finally { busy = false }
                                }
                            }
                        }
                    },
                ) { Text(if (busy) "Saving…" else "Save") } },
            )
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
