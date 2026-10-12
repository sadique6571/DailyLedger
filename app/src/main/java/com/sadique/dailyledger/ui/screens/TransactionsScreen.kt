package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
) {
    var show by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TransactionEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    val filtered = items.filter {
        (filter == "ALL" || it.type == filter) &&
            (query.isBlank() || it.category.contains(query, true) || it.note.contains(query, true))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = null; show = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Transactions", style = MaterialTheme.typography.headlineMedium)
                onAutoFill?.let { action -> FilledTonalButton(onClick = action) { Text("AI Auto Fill") } }
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "EXPENSE", "INCOME").forEach {
                        FilterChip(filter == it, { filter = it }, { Text(it.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
            }
            items(filtered, key = { it.id }) { x ->
                Card(onClick = { edit = x; show = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${x.category} • ${x.type}")
                            Text(x.date, style = MaterialTheme.typography.bodySmall)
                            if (x.note.isNotBlank()) {
                                Text(x.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text(money(if (x.type == "EXPENSE") -x.amountMinor else x.amountMinor))
                            IconButton(onClick = { onDelete(x) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                        }
                    }
                }
            }
        }
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
) {
    var type by remember { mutableStateOf(x?.type ?: initialType) }
    var amount by remember { mutableStateOf(x?.let { plainAmount(it.amountMinor) } ?: "") }
    var cat by remember { mutableStateOf(x?.category ?: initialCategory) }
    var note by remember { mutableStateOf(x?.note ?: "") }
    var date by remember { mutableStateOf(x?.date ?: LedgerRepository.today()) }
    var chooseCategory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val categoryCount = remember(type) { CategoryCatalog.load(context).count { it.type == type } }
    if (chooseCategory) CategoryPicker(type, { cat = it; chooseCategory = false }, { chooseCategory = false })
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (x != null) "Edit transaction" else if (initialCategory == "Salary") "Add salary" else "Add transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(type == "EXPENSE", { type = "EXPENSE" }, { Text("Expense") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(type == "INCOME", { type = "INCOME" }, { Text("Income") })
                }
                if (type == "INCOME") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(cat.equals("Salary", true), { cat = "Salary" }, { Text("Salary") })
                        FilterChip(!cat.equals("Salary", true), { if (cat.equals("Salary", true)) cat = "Other" }, { Text("Other income") })
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(cat, { cat = it.take(60) }, label = { Text("Category / custom category") })
                TextButton(onClick = { chooseCategory = true }) { Text("Choose from $categoryCount categories") }
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, isError = !isValidDate(date), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = isValidDate(date) && (parseMinor(amount) ?: 0) > 0,
                onClick = {
                    parseMinor(amount)?.takeIf { it > 0 }?.let { save(type, it, cat.ifBlank { "Other" }, note, date.trim()) }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
