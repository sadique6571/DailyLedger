package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.data.expenseEffect

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ai.DailySpendingGuide
import com.sadique.dailyledger.ai.SpendingSnapshot
import com.sadique.dailyledger.ai.SpendingTip
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    tx: List<TransactionEntity>, loans: List<LoanEntity>, lp: List<LoanPaymentEntity>,
    committees: List<CommitteeEntity>, cp: List<CommitteePaymentEntity>, receipts: List<CommitteeReceiptEntity>,
    committeeMembers: List<CommitteeMemberEntity> = emptyList(), savings: List<SavingEntity>, userName: String,
    weatherEnabled: Boolean, weatherCity: String, weatherTemperature: String, weatherCondition: String,
    ledgerCycle: LedgerCycleEntity? = null, ledgerArchives: List<LedgerArchiveEntity> = emptyList(),
    onAddSalary: () -> Unit, onAddSalaryWithAmount: (String) -> Unit = { onAddSalary() }, onAddExpense: () -> Unit, onOpenSavings: () -> Unit, onOpenKameti: () -> Unit,
    onAutoFill: (() -> Unit)? = null, snapshot: SpendingSnapshot? = null, aiTips: List<SpendingTip> = emptyList(),
    aiStatus: String = "", aiEnabled: Boolean = false, onEnableAi: () -> Unit = {},
    aiBusy: Boolean = false, onRefreshAi: () -> Unit = {},
    onAllTransactions: (() -> Unit)? = null,
    onExpenseHistory: () -> Unit = { onAllTransactions?.invoke() },
    onAddOtherIncome: () -> Unit = onAddSalary,
    onAddCategoryExpense: (String, String) -> Unit = { _, _ -> onAddExpense() },
    onOpenInsights: () -> Unit = {}, onOpenLoans: () -> Unit = {}, onOpenUdhar: () -> Unit = {},
    udharOutstanding: Long = 0L,
    creditPayments: List<CreditPaymentEntity> = emptyList(),
    onStartNewMonth: () -> Unit = {}, onArchivePreviousMonth: ((Boolean) -> Unit) -> Unit = {},
    salaryDay: Int? = null, plannedSalaryDate: LocalDate? = null, salaryRecordedWhenPlanned: LocalDate? = null,
    planningNotices: List<String> = emptyList(),
    onOpenPlanning: () -> Unit = {},
    onDismissTip: (SpendingTip) -> Unit = {}, dismissedTipKeys: Set<String> = emptySet(),
    lastSync: Long = 0L, driveEnabled: Boolean = false,
) {
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) { value = LocalDate.now(); delay(30_000) }
    }
    val month = YearMonth.from(today)
    val summary = remember(tx, cp, savings, ledgerCycle, creditPayments, month) { ledgerCycle?.let { ledgerCycleSummary(tx, cp, savings, it, creditPayments = creditPayments) }
        ?: monthlySalarySummary(tx, month.toString(), cp, savings, creditPayments) }
    val expenseRows = remember(tx, ledgerCycle, month) { tx.filter {
        it.type == "EXPENSE" && !it.privateEntry && (ledgerCycle?.let { cycle -> it.createdAt >= cycle.startAt } ?: it.date.startsWith("$month-"))
    }
    }
    val grouped = remember(expenseRows) { expenseRows.groupBy { it.category }.mapValues { it.value.sumOf { row -> row.expenseEffect() } }.entries.filter { it.value > 0 }.sortedByDescending { it.value } }
    val currentMonthExpenses = remember(tx, month) { tx.filter { it.type == "EXPENSE" && it.date.startsWith("$month-") } }
    val prevMonth = month.minusMonths(1)
    val prevMonthExpensesTotal = remember(tx, prevMonth) { tx.filter { it.type == "EXPENSE" && it.date.startsWith("$prevMonth-") }.sumOf { it.expenseEffect() } }
    val curMonthExpensesTotal = remember(currentMonthExpenses) { currentMonthExpenses.sumOf { it.expenseEffect() } }
    val frequentItems = remember(currentMonthExpenses) { currentMonthExpenses.filter { !it.privateEntry && it.refundOf.isBlank() }.groupBy { it.frequentPurchaseLabel().lowercase(Locale.ROOT) }
        .map { (_, purchases) -> purchases.first().frequentPurchaseLabel() to purchases.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
        .take(4) }
    val categories = grouped.map { it.key to it.value }
    var balanceDetails by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(true) }
    var salaryOptionsOpen by remember { mutableStateOf(false) }
    var selectedFrequentCategory by remember { mutableStateOf<String?>(null) }
    var frequentExpanded by rememberSaveable { mutableStateOf(true) }
    val recent = tx.filterNot { it.privateEntry }.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.updatedAt }).take(4)
    val cycleStartLabel = ledgerCycle?.let {
        Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
    val availableForCycle = summary.salary + summary.otherIncome + summary.openingBalanceMinor
    val spentRatio = if (availableForCycle > 0) (summary.totalOutflow.toFloat() / availableForCycle).coerceIn(0f, 1f) else if (summary.totalOutflow > 0) 1f else 0f
    val spentPct = (spentRatio * 100).roundToInt()
    val guide = remember(tx, summary, today, salaryDay, plannedSalaryDate, salaryRecordedWhenPlanned) { DailySpendingGuide.calculate(tx, summary, today, salaryDay, plannedSalaryDate, salaryRecordedWhenPlanned) }
    val display = LocalDisplayPreferences.current!!
    val context = androidx.compose.ui.platform.LocalContext.current
    val visibleMoney: (Long) -> String = { if (showBalance && !display.privateAmounts) displayMoney(it) else "Rs •••••" }

    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("dashboard-list"), state = rememberLazyListState(), contentPadding = PaddingValues(start = if (display.compact) 10.dp else 16.dp, end = if (display.compact) 10.dp else 16.dp, top = 0.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(if (display.compact) 10.dp else 12.dp)) {
            val sections = DashboardSections()
            with(sections) {
            enteringItem(key = "greeting", index = 0) {
                DashboardGreetingSection(
                    userName = userName, month = month, cycleStartLabel = cycleStartLabel,
                    weatherEnabled = weatherEnabled, weatherCity = weatherCity,
                    weatherTemperature = weatherTemperature, weatherCondition = weatherCondition,
                )
            }
            enteringItem(key = "balance", index = 1) {
                DashboardBalanceCard(
                    summary = summary, guide = guide,
                    availableForCycle = availableForCycle, spentRatio = spentRatio, spentPct = spentPct,
                    showBalance = showBalance, onToggleBalanceVisibility = { showBalance = !showBalance },
                    onOpenDetails = { balanceDetails = true },
                    salaryDay = salaryDay, plannedSalaryDate = plannedSalaryDate, money = visibleMoney,
                )
            }
            enteringItem(key = "backup-status", index = 2) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (driveEnabled && lastSync > 0) Icons.Rounded.CloudSync else Icons.Rounded.Cloud, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            !driveEnabled -> "Google Drive backup is off"
                            lastSync > 0 -> "Last backup: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSync))}"
                            else -> "Not yet backed up"
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            enteringItem(key = "monthly-totals", index = 2) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space10)) {
                    DashboardStat("Salary", summary.salary, "This cycle", Icons.Rounded.ArrowUpward, LedgerColors.Green, Modifier.weight(1f).fillMaxHeight().testTag("dashboard-add-salary"), onAddSalary)
                    DashboardStat("Expense", summary.expenses, "This cycle", Icons.Rounded.ArrowDownward, LedgerColors.Red, Modifier.weight(1f).fillMaxHeight().testTag("dashboard-add-expense"), onAddExpense)
                }
                if (prevMonthExpensesTotal > 0) {
                    val deltaPct = ((curMonthExpensesTotal - prevMonthExpensesTotal) * 100.0 / prevMonthExpensesTotal).roundToInt()
                    Text(
                        when {
                            deltaPct > 0 -> "Expenses ${visibleMoney(curMonthExpensesTotal)} this month — $deltaPct% more than last month"
                            deltaPct < 0 -> "Expenses ${visibleMoney(curMonthExpensesTotal)} this month — ${-deltaPct}% less than last month"
                            else -> "Expenses ${visibleMoney(curMonthExpensesTotal)} this month — same as last month"
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                TextButton(onClick = {
                    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                    val text = buildString {
                        appendLine("Daily Ledger — $monthName ${month.year} summary")
                        appendLine("Income: ${visibleMoney(summary.salary + summary.otherIncome)}")
                        appendLine("Expenses: ${visibleMoney(summary.expenses)}")
                        appendLine("Savings: ${visibleMoney(summary.savingsContributions)}")
                        appendLine("Remaining: ${visibleMoney(summary.remaining)}")
                    }
                    com.sadique.dailyledger.ui.shareText(context, "Share monthly summary", text)
                }, modifier = Modifier.testTag("share-monthly-summary")) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share summary")
                }
                }
            }

            enteringItem(key = "salary-cycle-controls", index = 3) {
                OutlinedButton(
                    onClick = { salaryOptionsOpen = true }, modifier = Modifier.fillMaxWidth().testTag("start-new-salary-month"),
                    shape = LedgerTokens.ShapeCard,
                ) { Text("Salary Received & Cycle Options") }

            }
            if (planningNotices.isNotEmpty()) enteringItem(key = "budget-notice", index = 4) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Text("Budgets & reminders", style = MaterialTheme.typography.titleMedium)
                    planningNotices.take(3).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onOpenPlanning) { Text("Review") }
                }
            }
            if (true) enteringItem(key = "spending", index = 4) { SpendingDonutCard(categories, categories.sumOf { it.second }, onExpenseHistory) }
            if (display.showFrequent && frequentItems.isNotEmpty()) enteringItem(key = "frequent-items", index = 5) {
                DashboardFrequentSection(
                    items = frequentItems, expanded = frequentExpanded,
                    onToggleExpanded = { frequentExpanded = !frequentExpanded },
                    money = visibleMoney, onSelectCategory = { selectedFrequentCategory = it },
                )
            }
            if (true) enteringItem(key = "recent", index = 6) {
                DashboardRecentSection(
                    recent = recent, money = visibleMoney,
                    onAllTransactions = onAllTransactions, onAddSalary = onAddSalary,
                )
            }
            enteringItem(key = "insights-shortcut", index = 7) { GradientButton("Spending insights", onOpenInsights, Modifier.fillMaxWidth(), icon = Icons.Rounded.BarChart) }
            if (true) enteringItem(key = "separate-totals", index = 8) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardStat("Other income", summary.otherIncome, "This cycle", Icons.Rounded.AddCircle, LedgerColors.Teal, Modifier.fillMaxWidth(), onAddOtherIncome)
                DashboardMoneySpaces(
                    savings = savings, cp = cp, receipts = receipts, committees = committees,
                    committeeMembers = committeeMembers, loans = loans, lp = lp, month = month,
                    savingsFirst = display.savingsFirst, money = visibleMoney,
                    onOpenSavings = onOpenSavings, onOpenKameti = onOpenKameti, onOpenLoans = onOpenLoans,
                )
                }
            }
            enteringItem(key = "udhar-shortcut", index = 9) {
                SummaryCard("Udhar Saman", udharOutstanding, Icons.Rounded.ShoppingCart, detail = "Outstanding goods on credit • Tap to add", onClick = onOpenUdhar)
            }
            snapshot?.takeIf { true }?.let { data -> enteringItem(key = "insights", index = 10) { InsightsCard(data, aiTips, aiStatus, aiEnabled, onEnableAi, aiBusy, onRefreshAi, onDismissTip, dismissedTipKeys) } }
            }
            sections.render(this, DisplayPreferences.DEFAULT_ORDER)
        }
    }
    if (balanceDetails) BalanceDetailsDialog(
        summary = summary, money = visibleMoney,
        onDismiss = { balanceDetails = false },
        onViewHistory = { balanceDetails = false; onAllTransactions?.invoke() },
    )
    DashboardSalaryDialogs(
        dialogOpen = salaryOptionsOpen, onCloseDialog = { salaryOptionsOpen = false },
        ledgerCycle = ledgerCycle, ledgerArchives = ledgerArchives, summary = summary, month = month,
        money = visibleMoney, onAddSalaryWithAmount = onAddSalaryWithAmount,
        onStartNewMonth = onStartNewMonth, onArchivePreviousMonth = onArchivePreviousMonth,
    )
    selectedFrequentCategory?.let { category ->
        FrequentCategoryDialog(
            category = category, transactions = currentMonthExpenses,
            money = visibleMoney, onDismiss = { selectedFrequentCategory = null },
        )
    }
}
