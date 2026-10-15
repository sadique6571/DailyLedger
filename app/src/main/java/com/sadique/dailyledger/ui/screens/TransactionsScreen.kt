package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import java.time.LocalDate
import android.app.DatePickerDialog
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.plainAmount
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.CategoryCatalog
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor

@Composable
fun TransactionsScreen(
    items: List<TransactionEntity>,
    onSave: (String, Long, String, String, String, TransactionEntity?) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onAutoFill: (() -> Unit)? = null,
    initialQuery: String = "",
    initialFilter: String = "ALL",
) {
    var show by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    val listState = rememberLazyListState()
    val display = LocalDisplayPreferences.current!!
    val dateContext = LocalContext.current
    var dateRange by rememberSaveable { mutableStateOf("ALL") }
    var selectedDay by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf("NEWEST") }
    var expandedNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()
    val filtered = items.filter {
        (filter == "ALL" || it.type == filter) &&
            (query.isBlank() || it.category.contains(query, true) || it.note.contains(query, true)) &&
            when (dateRange) {
                "TODAY" -> it.date == today.toString()
                "WEEK" -> runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today) } == true
                "MONTH" -> it.date.startsWith(today.toString().take(7))
                "DAY" -> it.date == selectedDay
                else -> true
            }
    }

    val sorted = when (sortOrder) {
        "OLDEST" -> filtered.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
        "AMOUNT_HIGH" -> filtered.sortedByDescending { it.amountMinor }
        "AMOUNT_LOW" -> filtered.sortedBy { it.amountMinor }
        else -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = null; show = true }) { Icon(Icons.Default.Add, "Add transaction") }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(start = if (display.compact) 10.dp else 16.dp, end = if (display.compact) 10.dp else 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            enteringItem {
                Text("Transactions", style = MaterialTheme.typography.headlineMedium)
                onAutoFill?.let { action -> FilledTonalButton(onClick = action) { Text("AI Auto Fill") } }
                OutlinedTextField(query, { query = it }, label = { Text("Search categories or notes") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All dates", "TODAY" to "Today", "WEEK" to "Last 7 days", "MONTH" to "This month").forEach { (value, label) ->
                        FilterChip(selected = dateRange == value, onClick = { dateRange = value }, label = { Text(label) })
                    }
                    FilterChip(selected = dateRange == "DAY", onClick = {
                        val date = runCatching { LocalDate.parse(selectedDay) }.getOrDefault(today)
                        DatePickerDialog(dateContext, { _, year, month, day ->
                            selectedDay = LocalDate.of(year, month + 1, day).toString()
                            dateRange = "DAY"
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, label = { Text(if (dateRange == "DAY") selectedDay else "Pick date") })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NEWEST" to "Newest", "OLDEST" to "Oldest", "AMOUNT_HIGH" to "Highest amount", "AMOUNT_LOW" to "Lowest amount").forEach { (value, label) ->
                        FilterChip(selected = sortOrder == value, onClick = { sortOrder = value }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "EXPENSE", "INCOME").forEach {
                        FilterChip(filter == it, { filter = it }, { Text(it.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text(if (items.isEmpty()) "No transactions yet. Tap + to add your first entry." else "No matching transactions. Try another search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp)) }
            }
            enteringItems(sorted, key = { it.id }) { x ->
                Card(onClick = { edit = x; show = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${x.category} • ${x.type}")
                            Text(x.date, style = MaterialTheme.typography.bodySmall)
                            if (x.note.isNotBlank()) {
                                Text(x.note, modifier = Modifier.clickable { expandedNoteId = if (expandedNoteId == x.id) null else x.id }, maxLines = if (expandedNoteId == x.id) Int.MAX_VALUE else 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text(if (display.privateAmounts) "Rs •••••" else money(if (x.type == "EXPENSE") -x.amountMinor else x.amountMinor))
                            IconButton(onClick = { pendingDelete = x }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${x.category} transaction") }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("Delete ${target.category} (${money(target.amountMinor)}) from ${target.date}? This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(target) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    if (show) {
        TransactionDialog(edit, { show = false }, save = { type, amount, cat, note, date ->
            onSave(type, amount, cat, note, date, edit)
            show = false
        })
    }
}

@Composable
fun TransactionDialog(
    x: TransactionEntity?,
    dismiss: () -> Unit,
    save: (String, Long, String, String, String) -> Unit,
    initialType: String = "EXPENSE",
    initialCategory: String = "",
    initialNote: String = "",
    onHistory: ((String, String) -> Unit)? = null,
) {
    var type by remember { mutableStateOf(x?.type ?: initialType) }
    var amount by remember { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: "") }
    var cat by remember { mutableStateOf(x?.category ?: initialCategory) }
    var note by remember { mutableStateOf(x?.note ?: initialNote) }
    var date by remember { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    AlertDialog(
        onDismissRequest = dismiss,
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 500, 1000).forEach { value ->
                        OutlinedButton(onClick = { amount = value.toString() }) { Text("Rs $value") }
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), isError = amount.isNotBlank() && (parseMinor(amount) ?: 0) <= 0, supportingText = { Text("Enter an amount greater than zero") })
                CategoryDropdown(type, cat, { cat = it })
                onHistory?.let { history -> TextButton(onClick = { history(type, cat) }) { Text("View history") } }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, isError = !isValidDate(date), singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("Use a valid YYYY-MM-DD date") })
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
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
