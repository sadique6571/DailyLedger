package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerEntrySheet

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.parseAmountExpression
import com.sadique.dailyledger.ui.plainAmount

@Composable
fun SavingsScreen(
    savings: List<SavingEntity>,
    onAdd: (String, Long, String, String) -> Unit,
    onDelete: (SavingEntity) -> Unit,
) {
    var add by remember { mutableStateOf(false) }
    var goalEditor by remember { mutableStateOf(false) }
    val display = LocalDisplayPreferences.current!!
    val amountHidden = LocalDisplayPreferences.current?.privateAmounts == true
    val visibleMoney: (Long) -> String = { if (amountHidden) "Rs •••••" else money(it) }
    val direct = savings.filter { it.kind == "DIRECT" }.sumOf { it.amountMinor }
    val leftover = savings.filter { it.kind == "LEFTOVER" }.sumOf { it.amountMinor }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, "Add saving") } }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            enteringItem {
                Text("Savings", style = MaterialTheme.typography.headlineMedium)
                Text("Your savings stay separate from salary and kameti.")
                Spacer(Modifier.height(12.dp))
                SummaryCard("Total savings", direct + leftover, Icons.Outlined.AccountBalanceWallet, detail = "Direct: ${visibleMoney(direct)} • Leftover: ${visibleMoney(leftover)}", onClick = { add = true })
            }
            enteringItem {
                val goal = display.savingsGoalMinor
                val total = direct + leftover
                com.sadique.dailyledger.ui.LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 6.dp) {
                    if (goal > 0) {
                        val pct = if (goal > 0) (total * 100 / goal).coerceIn(0, 100) else 0
                        Text(if (display.savingsGoalLabel.isNotBlank()) "Savings goal — ${display.savingsGoalLabel}" else "Savings goal", translate = false, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { (total.toFloat() / goal.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().testTag("savings-goal-progress"))
                        Text("${visibleMoney(total)} of ${visibleMoney(goal)} • $pct%", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { goalEditor = true }) { Text("Edit goal") }
                            TextButton(onClick = { display.updateSavingsGoal(0L, "") }) { Text("Remove") }
                        }
                    } else {
                        Text("Savings goal", style = MaterialTheme.typography.titleMedium)
                        Text("Set a target — for example Bike or Emergency fund — and watch your progress here.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { goalEditor = true }) { Text("Set a goal") }
                    }
                }
            }
            if (savings.isEmpty()) enteringItem {
                com.sadique.dailyledger.ui.LedgerEmptyState("No savings yet", "Keep direct and leftover savings separate from daily spending.",
                    "Add saving", Icons.Outlined.AccountBalanceWallet, onAction = { add = true })
            }
            enteringItems(savings, key = { it.id }) { saving ->
                com.sadique.dailyledger.ui.LedgerRecordCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(saving.id)) {
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
    if (goalEditor) {
        var label by remember { mutableStateOf(display.savingsGoalLabel) }
        var amount by remember { mutableStateOf(if (display.savingsGoalMinor > 0) plainAmount(display.savingsGoalMinor) else "") }
        AlertDialog(
            onDismissRequest = { goalEditor = false },
            title = { Text("Savings goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Goal name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(amount, { amount = it }, label = { Text("Target amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { display.updateSavingsGoal(parseAmountExpression(amount) ?: 0L, label.trim()); goalEditor = false }, enabled = (parseAmountExpression(amount) ?: 0L) > 0L) { Text("Save") } },
            dismissButton = { TextButton(onClick = { goalEditor = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SavingDialog(dismiss: () -> Unit, onHistory: (() -> Unit)? = null, save: (String, Long, String, String) -> Unit) {
    var kind by remember { mutableStateOf("DIRECT") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LedgerRepository.today()) }
    var note by remember { mutableStateOf("") }
    LedgerEntrySheet(onDismissRequest = dismiss, title = { Text("Add saving") }, text = {
        Column {
            Row {
                FilterChip(kind == "DIRECT", { kind = "DIRECT" }, { Text("Direct") })
                Spacer(Modifier.width(8.dp))
                FilterChip(kind == "LEFTOVER", { kind = "LEFTOVER" }, { Text("Leftover") })
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            LedgerDateField(date, { date = it }, label = "Entry date")
            OutlinedTextField(note, { note = it }, singleLine = true, label = { Text("Note") })
            onHistory?.let { action -> TextButton(onClick = action) { Text("View history") } }
        }
    }, confirmButton = { Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, enabled = isValidDate(date), onClick = { parseMinor(amount)?.takeIf { it > 0 }?.let { save(kind, it, date.trim(), note) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
