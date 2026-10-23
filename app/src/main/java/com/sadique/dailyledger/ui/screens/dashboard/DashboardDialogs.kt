package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerEntrySheet

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.data.LedgerArchiveEntity
import com.sadique.dailyledger.data.LedgerCycleEntity
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.SalarySummary
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerTokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Balance breakdown dialog opened from the hero balance card. */
@Composable
internal fun BalanceDetailsDialog(
    summary: SalarySummary,
    money: (Long) -> String,
    onDismiss: () -> Unit,
    onViewHistory: () -> Unit,
) {
    // Details grow out of the card (N): a small scale-in when the dialog opens.
    val pop = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0.86f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.tween(190)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Balance breakdown") },
        text = {
            Column(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value }, verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space10)) {
                listOf("Carried forward" to summary.openingBalanceMinor, "Salary" to summary.salary, "Other income" to summary.otherIncome,
                    "Expenses" to -summary.expenses, "Kameti charged this cycle" to -summary.committeeContributions,
                    "Savings set aside" to -summary.savingsContributions, "Udhar payments" to -summary.udharPayments, "Remaining balance" to summary.remaining).forEach { (label, amount) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f)); Text(money(amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onViewHistory) { Text("View history") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Salary-cycle dialogs: salary options, new-month confirm, archive confirm, archives list. */
@Composable
internal fun DashboardSalaryDialogs(
    dialogOpen: Boolean,
    onCloseDialog: () -> Unit,
    ledgerCycle: LedgerCycleEntity?,
    ledgerArchives: List<LedgerArchiveEntity>,
    summary: SalarySummary,
    month: YearMonth,
    money: (Long) -> String,
    onAddSalaryWithAmount: (String) -> Unit,
    onStartNewMonth: () -> Unit,
    onArchivePreviousMonth: ((Boolean) -> Unit) -> Unit,
) {
    var salaryAmountInput by remember { mutableStateOf("") }
    var receiveSalarySelected by remember { mutableStateOf(true) }
    var archivePreviousSelected by remember { mutableStateOf(false) }
    var showArchives by remember { mutableStateOf(false) }
    var archiveMessage by remember { mutableStateOf("") }
    var confirmNewMonth by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    if (dialogOpen) {
        LedgerEntrySheet(
            onDismissRequest = onCloseDialog,
            modifier = Modifier.testTag("salary-actions-dialog"),
            title = { Text("Salary Received & Cycle Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = receiveSalarySelected,
                            onCheckedChange = { receiveSalarySelected = it },
                            modifier = Modifier.testTag("salary-received-option"),
                        )
                        Text("Salary entry", modifier = Modifier.weight(1f))
                    }
                    if (receiveSalarySelected) OutlinedTextField(
                        value = salaryAmountInput,
                        onValueChange = { salaryAmountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Salary received (Rs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = archivePreviousSelected,
                            onCheckedChange = { archivePreviousSelected = it },
                            modifier = Modifier.testTag("archive-previous-option"),
                        )
                        Text("Archive last month", modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Salary is reviewed in the normal entry form. Starting a new salary cycle asks for confirmation.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ledgerArchives.isNotEmpty()) TextButton(onClick = {
                        onCloseDialog()
                        showArchives = true
                    }) { Text("View archives (${ledgerArchives.size})") }
                    if (archiveMessage.isNotBlank()) Text(
                        archiveMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = receiveSalarySelected || archivePreviousSelected,
                    onClick = {
                        onCloseDialog()
                        when {
                            archivePreviousSelected -> confirmArchive = true
                            receiveSalarySelected && ledgerCycle != null -> confirmNewMonth = true
                            receiveSalarySelected -> onAddSalaryWithAmount(salaryAmountInput)
                        }
                    },
                    modifier = Modifier.testTag("salary-options-continue"),
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = onCloseDialog) { Text("Cancel") } },
        )
    }
    if (confirmNewMonth) {
        AlertDialog(
            onDismissRequest = { confirmNewMonth = false },
            title = { Text("Start a new salary month?") },
            text = { Text("The current salary period will be archived. Its remaining balance (${money(summary.remaining)}) will carry forward; no money or transaction will be deleted. Start the new period before entering your next salary.") },
            confirmButton = {
                TextButton(onClick = { confirmNewMonth = false; onStartNewMonth(); if (receiveSalarySelected) onAddSalaryWithAmount(salaryAmountInput) }) { Text("Archive & start") }
            },
            dismissButton = { TextButton(onClick = { confirmNewMonth = false }) { Text("Cancel") } },
        )
    }
    if (confirmArchive) {
        val previousMonth = month.minusMonths(1)
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive $previousMonth?") },
            text = { Text("Save a summary of last calendar month's salary, other income, expenses, kameti and savings. Original transactions remain in your ledger. This will not change your active salary month or balance.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    onArchivePreviousMonth { archived ->
                        archiveMessage = if (archived) "$previousMonth archived. Original entries were kept." else "$previousMonth was already archived."
                        if (receiveSalarySelected) { if (ledgerCycle != null) confirmNewMonth = true else onAddSalaryWithAmount(salaryAmountInput) }
                    }
                }) { Text("Archive month") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }
    if (showArchives) {
        AlertDialog(
            onDismissRequest = { showArchives = false },
            title = { Text("Archived periods") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space10)) {
                    ledgerArchives.forEach { archive ->
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Text(archive.label, fontWeight = FontWeight.SemiBold)
                            Text("Salary ${money(archive.salaryMinor)} · Other income ${money(archive.otherIncomeMinor)}", fontSize = 11.sp)
                            Text("Expenses ${money(archive.expensesMinor)} · Kameti ${money(archive.committeeMinor)} · Savings ${money(archive.savingsMinor)}", fontSize = 11.sp)
                            Text("Closing balance ${money(archive.closingBalanceMinor)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showArchives = false }) { Text("Close") } },
        )
    }
}

/** Purchase history for one frequent-purchase category in the current month. */
@Composable
internal fun FrequentCategoryDialog(
    category: String,
    transactions: List<TransactionEntity>,
    money: (Long) -> String,
    onDismiss: () -> Unit,
) {
    val purchases = transactions.filter { !it.privateEntry && it.refundOf.isBlank() && it.frequentPurchaseLabel().equals(category, ignoreCase = true) }
        .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
    val total = purchases.sumOf { it.amountMinor }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(category.ifBlank { "Other" }) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space8),
            ) {
                Text("Current month · ${purchases.size} entries", style = MaterialTheme.typography.bodySmall)
                Text("Total this month: ${money(total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                purchases.forEachIndexed { index, purchase ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("frequent-history-entry-${purchase.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space12),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                purchase.note.ifBlank { purchase.category.ifBlank { "Purchase" } },
                                fontWeight = FontWeight.Medium,
                            )
                            val displayDate = runCatching {
                                java.time.LocalDate.parse(purchase.date)
                                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
                            }.getOrDefault(purchase.date)
                            Text(displayDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(purchase.amountMinor), fontWeight = FontWeight.SemiBold)
                    }
                    if (index < purchases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                HorizontalDivider()
                Text("Total: ${money(total)}", Modifier.align(Alignment.End), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
