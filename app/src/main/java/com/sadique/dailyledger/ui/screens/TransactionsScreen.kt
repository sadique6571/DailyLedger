package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import java.time.LocalDate
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.plainAmount
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    var undoPendingId by remember { mutableStateOf<String?>(null) }
    val undoBar = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var duplicateWarning by remember { mutableStateOf(false) }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    val listState = rememberLazyListState()
    val display = LocalDisplayPreferences.current!!
    val dateContext = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val favoritesStore = remember(dateContext) { dateContext.applicationContext.getSharedPreferences("transaction_favorites_v2", android.content.Context.MODE_PRIVATE) }
    var favoriteIds by remember { mutableStateOf(favoritesStore.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    fun toggleFavorite(id: String) {
        val key = id.toString()
        favoriteIds = if (key in favoriteIds) favoriteIds - key else favoriteIds + key
        favoritesStore.edit().putStringSet("ids", favoriteIds).apply()
    }
    var dateRange by rememberSaveable { mutableStateOf("ALL") }
    var selectedDay by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf("NEWEST") }
    var expandedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var compactRows by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickTemplate by remember { mutableStateOf<TransactionEntity?>(null) }
    val frequentCategories = remember(items) { items.groupingBy { it.type to it.category }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key } }
    val today = LocalDate.now()
    val searchStrings = com.sadique.dailyledger.i18n.LocalStrings.current
    val filtered = items.filter {
        it.id != undoPendingId &&
        (!favoritesOnly || it.id in favoriteIds) &&
        (filter == "ALL" || it.type == filter) &&
            (query.isBlank() || it.category.contains(query, true) || searchStrings.text(it.category).contains(query, true) || it.note.contains(query, true) || it.date.contains(query, true) || plainAmount(it.amountMinor).contains(query)) &&
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
        snackbarHost = { SnackbarHost(undoBar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = null; quickTemplate = null; show = true }) { Icon(Icons.Default.Add, "Add transaction") }
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
                val favorites = items.filter { it.id.toString() in favoriteIds }.take(8)
                if (favorites.isNotEmpty()) {
                    Text("Favorite transactions • tap to repeat", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        favorites.forEach { favorite ->
                            AssistChip(onClick = { edit = null; quickTemplate = favorite; show = true }, label = { Text(favorite.category) }, leadingIcon = { Icon(Icons.Filled.Star, null) })
                        }
                    }
                }
                if (frequentCategories.isNotEmpty()) {
                    Text("Frequently used categories", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        frequentCategories.forEach { (type, category) ->
                            AssistChip(onClick = { edit = null; quickTemplate = items.firstOrNull { it.type == type && it.category == category }; show = true }, label = { Text(category) })
                        }
                    }
                }
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
                    TextButton(onClick = { query = ""; filter = "ALL"; dateRange = "ALL"; selectedDay = ""; sortOrder = "NEWEST"; favoritesOnly = false }) { Text("Clear all filters") }
                    TextButton(onClick = { favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) "Show all entries" else "Favorites only") }
                    TextButton(onClick = { compactRows = !compactRows }) { Text(if (compactRows) "Comfortable rows" else "Compact rows") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "EXPENSE", "INCOME").forEach {
                        FilterChip(filter == it, { filter = it }, { Text(it.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { com.sadique.dailyledger.ui.LedgerRecordCard { Text(if (items.isEmpty()) "No transactions yet. Tap + to add your first entry." else "No matching transactions. Try another search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            sorted.forEachIndexed { index, x ->
                if (sortOrder in setOf("NEWEST", "OLDEST") && (index == 0 || sorted[index - 1].date != x.date)) {
                    enteringItem(key = "transaction-date-${x.date}") {
                        Text(
                            when (x.date) {
                                today.toString() -> "Today"
                                today.minusDays(1).toString() -> "Yesterday"
                                else -> x.date
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                }
                enteringItem(key = x.id) {
                Card(onClick = { quickTemplate = null; edit = x; show = true }, modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(if (compactRows) 10.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(x.category, style = MaterialTheme.typography.titleMedium)
                             Text(x.type.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall,
                                 color = if (x.type == "INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text(x.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showDetails = x }, contentPadding = PaddingValues(0.dp)) { Text("Details") }
                            if (x.note.isNotBlank()) {
                                Text(x.note, translate = false, modifier = Modifier.clickable { expandedNoteId = if (expandedNoteId == x.id) null else x.id }, maxLines = if (expandedNoteId == x.id) Int.MAX_VALUE else 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            IconButton(onClick = { toggleFavorite(x.id) }) {
                                Icon(if (x.id.toString() in favoriteIds) Icons.Filled.Star else Icons.Outlined.StarBorder, if (x.id.toString() in favoriteIds) "Remove favorite" else "Add favorite")
                            }
                            Text(if (display.privateAmounts) "Rs •••••" else money(if (x.type == "EXPENSE") -x.amountMinor else x.amountMinor))
                            IconButton(onClick = { pendingDelete = x }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${x.category} transaction") }
                        }
                    }
                }
                }
            }
        }
    }
    showDetails?.let { detail ->
        AlertDialog(
            onDismissRequest = { showDetails = null },
            title = { Text("Transaction details") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category: ${detail.category}")
                Text("Type: ${detail.type}")
                Text("Date: ${detail.date}")
                Text("Amount: ${if (display.privateAmounts) "Rs •••••" else money(detail.amountMinor)}")
                if (detail.note.isNotBlank()) Text("Note: ${detail.note}")
                TextButton(onClick = { clipboard.setText(AnnotatedString(detail.category + " • " + detail.date + " • " + (if (display.privateAmounts) "Rs •••••" else money(detail.amountMinor)) + (if (detail.note.isNotBlank()) " • " + detail.note else ""))) }) { Text("Copy details") }
            } },
            confirmButton = { TextButton(onClick = { showDetails = null }) { Text("Close") } },
        )
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("Delete ${target.category} (${if (display.privateAmounts) "Rs •••••" else money(target.amountMinor)}) from ${target.date}? You can undo the deletion from the message at the bottom.") },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                undoPendingId = target.id
                undoScope.launch {
                    val result = undoBar.showSnackbar("Transaction removed", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
                    undoPendingId = null
                    if (result != SnackbarResult.ActionPerformed) onDelete(target)
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    if (duplicateWarning) AlertDialog(
        onDismissRequest = { duplicateWarning = false; pendingSave = null },
        title = { Text("Possible duplicate") },
        text = { Text("A transaction with the same date, type, category and amount already exists. Save another entry anyway?") },
        confirmButton = { TextButton(onClick = { val saveAction = pendingSave; duplicateWarning = false; pendingSave = null; saveAction?.invoke() }) { Text("Save anyway") } },
        dismissButton = { TextButton(onClick = { duplicateWarning = false; pendingSave = null }) { Text("Review") } },
    )
    if (show) {
        key(edit?.id, quickTemplate?.id) {
        TransactionDialog(edit, { show = false }, save = { type, amount, cat, note, date ->
            val action = { onSave(type, amount, cat, note, date, edit); show = false; Unit }
            val duplicate = items.any { it.id != edit?.id && it.type == type && it.amountMinor == amount && it.category.equals(cat, true) && it.date == date }
            if (duplicate) { pendingSave = action; duplicateWarning = true } else action()
        }, initialType = quickTemplate?.type ?: "EXPENSE", initialCategory = quickTemplate?.category ?: "", initialAmount = quickTemplate?.let { plainAmount(it.amountMinor) } ?: "")
        }
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
    initialAmount: String = "",
    onHistory: ((String, String) -> Unit)? = null,
) {
    var type by remember { mutableStateOf(x?.type ?: initialType) }
    var amount by remember { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: initialAmount) }
    var cat by remember { mutableStateOf(x?.category ?: initialCategory) }
    var note by remember { mutableStateOf(x?.note ?: initialNote) }
    var date by remember { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    var discardWarning by remember { mutableStateOf(false) }
    val formContext = LocalContext.current
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
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val selected = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(formContext, { _, year, month, day ->
                                date = LocalDate.of(year, month + 1, day).toString()
                            }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                        },
                        modifier = Modifier.size(40.dp).testTag("transaction-calendar"),
                    ) { Icon(Icons.Default.DateRange, contentDescription = "Choose date from calendar") }
                }
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
        dismissButton = { TextButton(onClick = safeDismiss) { Text("Cancel") } },
    )
}
