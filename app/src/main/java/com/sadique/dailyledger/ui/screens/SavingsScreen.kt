package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerDateField

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.sadique.dailyledger.ui.SummaryCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.sadique.dailyledger.ui.isValidDate
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor

@Composable
fun SavingsScreen(
    savings: List<SavingEntity>,
    onAdd: (String, Long, String, String) -> Unit,
    onDelete: (SavingEntity) -> Unit,
) {
    var add by remember { mutableStateOf(false) }
    val amountHidden = LocalDisplayPreferences.current?.privateAmounts == true
    val visibleMoney: (Long) -> String = { if (amountHidden) "Rs •••••" else money(it) }
    val direct = savings.filter { it.kind == "DIRECT" }.sumOf { it.amountMinor }
    val leftover = savings.filter { it.kind == "LEFTOVER" }.sumOf { it.amountMinor }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, null) } }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            enteringItem {
                Text("Savings", style = MaterialTheme.typography.headlineMedium)
                Text("Your savings stay separate from salary and kameti.")
                Spacer(Modifier.height(12.dp))
                SummaryCard("Total savings", direct + leftover, Icons.Outlined.AccountBalanceWallet, detail = "Direct: ${visibleMoney(direct)} • Leftover: ${visibleMoney(leftover)}", onClick = { add = true })
            }
            enteringItems(savings, key = { it.id }) { saving ->
                com.sadique.dailyledger.ui.LedgerRecordCard() {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (saving.kind == "LEFTOVER") "Leftover saving" else "Direct saving")
                            Text("${saving.date} • ${saving.note}", translate = false, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(visibleMoney(saving.amountMinor))
                        IconButton(onClick = { onDelete(saving) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                }
            }
        }
    }
    if (add) SavingDialog({ add = false }) { k, a, d, n -> onAdd(k, a, d, n); add = false }
}

@Composable
fun SavingDialog(dismiss: () -> Unit, onHistory: (() -> Unit)? = null, save: (String, Long, String, String) -> Unit) {
    var kind by remember { mutableStateOf("DIRECT") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LedgerRepository.today()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add saving") }, text = {
        Column {
            Row {
                FilterChip(kind == "DIRECT", { kind = "DIRECT" }, { Text("Direct") })
                Spacer(Modifier.width(8.dp))
                FilterChip(kind == "LEFTOVER", { kind = "LEFTOVER" }, { Text("Leftover") })
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            LedgerDateField(date, { date = it }, label = "Entry date")
            OutlinedTextField(note, { note = it }, label = { Text("Note") })
            onHistory?.let { action -> TextButton(onClick = action) { Text("View history") } }
        }
    }, confirmButton = { Button(enabled = isValidDate(date), onClick = { parseMinor(amount)?.takeIf { it > 0 }?.let { save(kind, it, date.trim(), note) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
