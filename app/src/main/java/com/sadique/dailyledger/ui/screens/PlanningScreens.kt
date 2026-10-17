@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sadique.dailyledger.ai.LearnedCategoryRule
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.planning.*
import com.sadique.dailyledger.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun PlanningNotice() {
    Text("Budgets, reminder schedules and AI learning are encrypted on this device. Saved transactions use your existing Drive backup.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun BudgetScreen(owner: String, store: PlannerStore, transactions: List<TransactionEntity>) {
    val data by store.data.collectAsState()
    var edit by remember { mutableStateOf<CategoryBudget?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CategoryBudget?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val progress = PlannerMath.budgets(owner, data.budgets, transactions, YearMonth.now())
    LazyColumn(Modifier.fillMaxSize().testTag("budgets-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        enteringItem { Text("Category budgets", style = MaterialTheme.typography.headlineMedium); Text(YearMonth.now().toString()); PlanningNotice() }
        enteringItem { Button(onClick = { edit = null; adding = true }) { Text("Add budget") }; (message ?: store.error)?.let { Text(it) } }
        if (progress.isEmpty()) enteringItem { Text("Set a monthly category limit to see your spending progress.") }
        enteringItems(progress, key = { it.category }) { row ->
            LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 10.dp) {
                Text(row.category, style = MaterialTheme.typography.titleLarge)
                Text("Spent: ${visibleAmount(row.spent)}"); Text("Monthly limit: ${visibleAmount(row.limit)}")
                val percentUsed = if (row.limit > 0L) (row.spent.toDouble() / row.limit * 100.0).roundToInt() else 0
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        com.sadique.dailyledger.i18n.progressPercent(percentUsed),
                        Modifier.testTag("budget-progress-percent-${row.category}"),
                        translate = false, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        color = if (row.exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                LinearProgressIndicator(
                    progress = { row.fraction },
                    modifier = Modifier.fillMaxWidth().height(16.dp)
                        .testTag("budget-progress-track-${row.category}"),
                    color = if (row.exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(if (row.exceeded) "Budget limit reached" else if (row.nearLimit) "Approaching your budget limit" else "Within your budget", color = if (row.nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Row { TextButton(onClick = { edit = CategoryBudget(row.category, row.limit); adding = true }) { Text("Edit") }; TextButton(onClick = { pendingDelete = CategoryBudget(row.category, row.limit) }) { Text("Remove") } }
            }
        }
    }
    if (adding) {
        var category by remember(edit) { mutableStateOf(edit?.category ?: "") }
        var amount by remember(edit) { mutableStateOf(edit?.limitMinor?.let(::plainAmount) ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Monthly category budget") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryDropdown("EXPENSE", category, { category = it }, enabled = edit == null)
                OutlinedTextField(amount, { amount = it }, label = { Text("Monthly limit (Rs)") }, modifier = Modifier.testTag("budget-amount"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            runCatching { store.setBudget(category, requireNotNull(parseMinor(amount)) { "Enter a valid amount." }) }.onSuccess { adding = false; message = "Budget saved" }.onFailure { error = it.message }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
    }
    pendingDelete?.let { row -> ConfirmRemoval("Remove budget?", { pendingDelete = null }) {
        runCatching { store.removeBudget(row.category) }.onFailure { message = it.message }; pendingDelete = null
    } }
}

@Composable
fun BillsScreen(vm: MainViewModel) {
    val data by vm.planner.data.collectAsState()
    var edit by remember { mutableStateOf<RecurringBill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<RecurringBill?>(null) }
    var remove by remember { mutableStateOf<RecurringBill?>(null) }
    var skip by remember { mutableStateOf<RecurringBill?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize().testTag("bills-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        enteringItem { Text("Bills & reminders", style = MaterialTheme.typography.headlineMedium); Text("A reminder never saves or deducts money. Review and confirm each entry."); PlanningNotice() }
        enteringItem { Button(onClick = { edit = null; adding = true }) { Text("Add reminder") }; (error ?: vm.planner.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (data.bills.isEmpty()) enteringItem { Text("Add rent, internet, fees or another monthly entry.") }
        enteringItems(data.bills.sortedBy { it.nextDue() }, key = { it.id }) { bill ->
            LedgerCard(Modifier.fillMaxWidth()) {
                Text(bill.title, translate = false, style = MaterialTheme.typography.titleLarge)
                Text(bill.category); Text(visibleAmount(bill.amountMinor)); Text("Next due: ${bill.nextDue()}")
                Text(if (bill.nextDue() <= today) "Due for review" else "Upcoming")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { review = bill }, enabled = bill.enabled && bill.nextDue() <= today) { Text("Review entry") }
                    TextButton(onClick = { skip = bill }, enabled = bill.nextDue() <= today) { Text("Skip month") }
                }
                Row { TextButton(onClick = { edit = bill; adding = true }) { Text("Edit") }; TextButton(onClick = { remove = bill }) { Text("Remove") } }
            }
        }
    }
    if (adding) BillEditor(edit, { adding = false }) { bill ->
        vm.planner.saveBill(bill); com.sadique.dailyledger.planning.BillReminderWorker.schedule(vm.getApplication<android.app.Application>()); adding = false
    }
    review?.let { bill -> BillReview(bill, { review = null }, vm::saveRecurring) }
    remove?.let { bill -> ConfirmRemoval("Remove reminder?", { remove = null }) { runCatching { vm.planner.removeBill(bill.id) }.onFailure { error = it.message }; remove = null } }
    skip?.let { bill -> ConfirmRemoval("Skip this occurrence without saving an entry?", { skip = null }) { runCatching { vm.planner.markHandled(bill.id, bill.nextDue()) }.onFailure { error = it.message }; skip = null } }
}

@Composable
private fun BillEditor(existing: RecurringBill?, dismiss: () -> Unit, save: (RecurringBill) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "EXPENSE") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var amount by remember { mutableStateOf(existing?.amountMinor?.let(::plainAmount) ?: "") }
    var day by remember { mutableStateOf((existing?.day ?: LocalDate.now().dayOfMonth).toString()) }
    var start by remember { mutableStateOf(existing?.startMonth ?: YearMonth.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(.94f), title = { Text("Monthly reminder") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Reminder name") })
            Row { FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE"; category = "" }, label = { Text("Expense") }); FilterChip(selected = type == "INCOME", onClick = { type = "INCOME"; category = "" }, label = { Text("Income") }) }
            CategoryDropdown(type, category, { category = it })
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (Rs)") })
            OutlinedTextField(day, { day = it.take(2) }, label = { Text("Day of month (1–31)") })
            if (existing == null) OutlinedTextField(start, { start = it }, label = { Text("First month YYYY-MM") })
            Text("Short months use their last day. Overdue months stay pending until reviewed or skipped.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching {
        save(RecurringBill(existing?.id ?: UUID.randomUUID().toString(), title.trim(), type, category, requireNotNull(parseMinor(amount)) { "Enter a valid amount." }, day.toIntOrNull() ?: 0, start, existing?.handledThrough ?: ""))
    }.onFailure { error = it.message } }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun BillReview(bill: RecurringBill, dismiss: () -> Unit, save: suspend (RecurringBill, TransactionDraft) -> Unit) {
    var amount by remember { mutableStateOf(plainAmount(bill.amountMinor)) }
    var category by remember { mutableStateOf(bill.category) }
    var date by remember { mutableStateOf(bill.nextDue().toString()) }
    var note by remember { mutableStateOf(bill.title) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Review recurring entry") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(bill.type)
            CategoryDropdown(bill.type, category, { category = it }, enabled = !busy)
            OutlinedTextField(amount, { amount = it }, enabled = !busy, label = { Text("Amount (Rs)") })
            OutlinedTextField(date, { date = it }, enabled = !busy, label = { Text("Date YYYY-MM-DD") })
            OutlinedTextField(note, { note = it.take(500) }, enabled = !busy, label = { Text("Note") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        scope.launch { busy = true; try {
            save(bill, TransactionDraft(bill.type, requireNotNull(parseMinor(amount)) { "Enter a valid amount." }, category, note, date).validated()); dismiss()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { error = e.message } finally { busy = false } }
    }) { Text(if (busy) "Saving…" else "Confirm & save") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
fun LearningScreen(vm: MainViewModel) {
    val rules by vm.learnedCategories.collectAsState()
    val loadError by vm.learningError.collectAsState()
    var edit by remember { mutableStateOf<LearnedCategoryRule?>(null) }
    var remove by remember { mutableStateOf<LearnedCategoryRule?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("learning-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        enteringItem { Text("AI learning", style = MaterialTheme.typography.headlineMedium); Text("Review category mappings learned from your corrections and repeated saved items. Removing a rule also stops the same history mapping from reappearing."); PlanningNotice() }
        if (rules.isEmpty()) enteringItem { Text("No learned categories yet. Correct a category while reviewing AI entries to teach it.") }
        message?.let { enteringItem { Text(it) } }
        loadError?.let { enteringItem { Text(it, color = MaterialTheme.colorScheme.error) } }
        enteringItems(rules, key = { "${it.type}:${it.phrase}" }) { rule ->
            LedgerCard(Modifier.fillMaxWidth()) {
                Text(rule.phrase, translate = false, style = MaterialTheme.typography.titleMedium); Text(rule.type); Text(rule.category)
                Row { TextButton(onClick = { edit = rule }) { Text("Correct category") }; TextButton(onClick = { remove = rule }) { Text("Forget") } }
            }
        }
    }
    edit?.let { rule ->
        var category by remember(rule) { mutableStateOf(rule.category) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Correct category") }, text = { Column { Text(rule.phrase, translate = false); CategoryDropdown(rule.type, category, { category = it }) } },
            confirmButton = { TextButton(onClick = { message = vm.rememberCategory(rule.type, rule.phrase, category); if (message == null) edit = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
    }
    remove?.let { rule -> ConfirmRemoval("Forget this category mapping?", { remove = null }) { message = vm.forgetCategory(rule); remove = null } }
}

@Composable
private fun ConfirmRemoval(title: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text("Your saved ledger entries stay unchanged.") }, confirmButton = { TextButton(onClick = confirm) { Text("Confirm") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
