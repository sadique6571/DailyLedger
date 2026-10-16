package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.planning.*
import com.sadique.dailyledger.ui.*
import com.sadique.dailyledger.i18n.LocalStrings
import java.time.YearMonth

@Composable
fun KametiCalendarScreen(owner: String, committees: List<CommitteeEntity>, payments: List<CommitteePaymentEntity>, receipts: List<CommitteeReceiptEntity>, members: List<CommitteeMemberEntity>) {
    var selected by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(selected)
    val report = remember(owner, month, committees, payments, receipts, members) { ReportQueries.calendar(owner, month, committees, payments, receipts, members) }
    LazyColumn(Modifier.fillMaxSize().testTag("kameti-calendar"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Kameti calendar", style = MaterialTheme.typography.headlineMedium); MonthSelector(month, { selected = it.toString() }) }
        item { Text("Installments follow their scheduled month. Receiving uses its recorded date. This calendar does not change your balance.") }
        if (report.installments.isEmpty()) item { Text("No installments scheduled for this month.") }
        items(report.installments, key = { it.committee.id }) { row ->
            LedgerCard(Modifier.fillMaxWidth()) {
                Text(row.committee.name, style = MaterialTheme.typography.titleLarge)
                Text("Installment #${row.number}")
                Text(if (row.paid >= row.expected) "Paid" else "Pending", color = if (row.paid >= row.expected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text("Paid: ${visibleMoney(row.paid)}"); Text("Expected: ${visibleMoney(row.expected)}")
                if (row.personalTurn) Text("Your receiving turn is expected this month", style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Text("Recorded receiving", style = MaterialTheme.typography.titleLarge) }
        if (report.receipts.isEmpty()) item { Text("No receiving recorded for this month.") }
        items(report.receipts, key = { it.id }) { row -> LedgerCard(Modifier.fillMaxWidth()) {
            Text(committees.firstOrNull { it.id == row.committeeId }?.name.orEmpty()); Text(row.date.orEmpty()); Text(visibleMoney(row.amountMinor)); if (row.note.isNotBlank()) Text(row.note, translate = false)
        } }
        if (receipts.any { it.ownerId == owner && it.date == null }) item { Text("Undated older receiving remains in Kameti history and All records exports.") }
    }
}

@Composable
fun MonthlyComparisonScreen(owner: String, transactions: List<TransactionEntity>, savings: List<SavingEntity>) {
    var first by rememberSaveable { mutableStateOf(YearMonth.now().minusMonths(1).toString()) }
    var second by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val a = ReportQueries.month(owner, YearMonth.parse(first), transactions, savings)
    val b = ReportQueries.month(owner, YearMonth.parse(second), transactions, savings)
    val categories = (a.categories.keys + b.categories.keys).sortedByDescending { (a.categories[it] ?: 0) + (b.categories[it] ?: 0) }
    LazyColumn(Modifier.fillMaxSize().testTag("monthly-comparison"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Monthly comparison", style = MaterialTheme.typography.headlineMedium)
            MonthSelector(YearMonth.parse(first), { first = it.toString() }); MonthSelector(YearMonth.parse(second), { second = it.toString() })
            Text("Calendar-month records, including any future-dated entries. These totals are not your active salary-cycle balance.", style = MaterialTheme.typography.bodySmall)
        }
        item { ComparisonRow("Income", a.income, b.income, first, second); ComparisonRow("Expenses", a.expenses, b.expenses, first, second); ComparisonRow("Savings", a.savings, b.savings, first, second) }
        if (categories.isEmpty()) item { Text("No expense records in the selected months.") }
        items(categories, key = { it }) { category -> ComparisonRow(category, a.categories[category] ?: 0, b.categories[category] ?: 0, first, second) }
    }
}
@Composable private fun ComparisonRow(title: String, a: Long, b: Long, first: String, second: String) {
    LedgerCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("$first: ${visibleMoney(a)}"); Text("$second: ${visibleMoney(b)}")
        Text("Difference: ${visibleMoney(b - a)}")
    }
}
@Composable private fun MonthSelector(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { change(month.minusMonths(1)) }) { Text("Previous") }
        Text(month.toString(), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { change(month.plusMonths(1)) }) { Text("Next") }
    }
}

private data class SearchRecord(val key: String, val ledger: String, val route: Int, val title: String, val date: String, val amount: Long, val note: String)
@Composable
fun GlobalSearchScreen(vm: MainViewModel, open: (Int) -> Unit) {
    val tx by vm.transactions.collectAsState(); val loans by vm.loans.collectAsState(); val loanPayments by vm.loanPayments.collectAsState()
    val savings by vm.savings.collectAsState(); val committees by vm.committees.collectAsState(); val cp by vm.committeePayments.collectAsState(); val cr by vm.committeeReceipts.collectAsState()
    val credits by vm.creditPurchases.collectAsState(); val creditPayments by vm.creditPayments.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<SearchRecord?>(null) }
    val strings = LocalStrings.current
    val rows = remember(tx, loans, loanPayments, savings, committees, cp, cr, credits, creditPayments) { buildList {
        tx.forEach { add(SearchRecord("tx:${it.id}", "Transactions", 1, it.category, it.date, it.amountMinor, it.note)) }
        loans.forEach { add(SearchRecord("loan:${it.id}", "Loans", 2, it.person, it.dueDate.orEmpty(), it.principalMinor, it.note)) }
        loanPayments.forEach { add(SearchRecord("lp:${it.id}", "Loan payments", 2, loans.find { l -> l.id == it.loanId }?.person.orEmpty(), it.date, it.amountMinor, it.note)) }
        savings.forEach { add(SearchRecord("saving:${it.id}", "Savings", 4, it.kind, it.date, it.amountMinor, it.note)) }
        committees.forEach { add(SearchRecord("kameti:${it.id}", "Kameti", 3, it.name, it.startMonth, it.monthlyContribution(), it.note)) }
        cp.forEach { add(SearchRecord("cp:${it.id}", "Kameti payments", 3, committees.find { c -> c.id == it.committeeId }?.name.orEmpty(), it.month, it.amountMinor, "Installment #${it.installmentNumber}")) }
        cr.forEach { add(SearchRecord("cr:${it.id}", "Kameti receiving", 3, committees.find { c -> c.id == it.committeeId }?.name.orEmpty(), it.date.orEmpty(), it.amountMinor, it.note)) }
        credits.forEach { add(SearchRecord("credit:${it.id}", "Udhar", 7, "${it.creditor} · ${it.category}", it.purchaseDate, it.amountMinor, it.note)) }
        creditPayments.forEach { add(SearchRecord("credit-payment:${it.id}", "Udhar payments", 7, credits.find { c -> c.id == it.creditId }?.creditor.orEmpty(), it.date, it.amountMinor, it.note)) }
    } }
    val matches = if (query.isBlank()) emptyList() else rows.filter { row ->
        val words = query.trim().split(Regex("\\s+"))
        val text = "${row.ledger} ${strings.text(row.ledger)} ${row.title} ${strings.text(row.title)} ${row.date} ${plainAmount(row.amount)} ${row.note}"
        words.all { text.contains(it, true) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("all-ledgers-search"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Search all ledgers", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(query, { query = it.take(100) }, label = { Text("Name, category, date or amount") }, modifier = Modifier.fillMaxWidth().testTag("global-search-input"), singleLine = true)
            Text(if (query.isBlank()) "Search income, expenses, savings, loans, kameti and udhar." else "${matches.size} matching records")
            if (matches.size > 200) Text("Showing the first 200 results. Refine your search to see fewer matches.")
        }
        items(matches.sortedByDescending { it.date }.take(200), key = { it.key }) { row ->
            LedgerCard(Modifier.fillMaxWidth().clickable { selected = row }) {
                Text(if (row.ledger in listOf("Transactions", "Savings")) strings.text(row.title) else row.title, translate = false, style = MaterialTheme.typography.titleMedium); Text(row.ledger); Text(row.date); Text(visibleMoney(row.amount))
            }
        }
    }
    selected?.let { row -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(if (row.ledger in listOf("Transactions", "Savings")) strings.text(row.title) else row.title, translate = false) }, text = {
        Column { Text(row.ledger); Text(row.date); Text(visibleMoney(row.amount)); if (row.note.isNotBlank()) Text(row.note, translate = false) }
    }, confirmButton = { TextButton(onClick = { selected = null; open(row.route) }) { Text("Open ledger") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } }) }
}
