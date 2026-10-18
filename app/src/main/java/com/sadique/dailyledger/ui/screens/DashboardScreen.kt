package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.sadique.dailyledger.ai.DailySpendingGuide
import com.sadique.dailyledger.ai.SpendingSnapshot
import com.sadique.dailyledger.ai.SpendingTip
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
) {
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) { value = LocalDate.now(); delay(30_000) }
    }
    val month = YearMonth.from(today)
    val summary = remember(tx, cp, savings, ledgerCycle, creditPayments, month) { ledgerCycle?.let { ledgerCycleSummary(tx, cp, savings, it, creditPayments = creditPayments) }
        ?: monthlySalarySummary(tx, month.toString(), cp, savings, creditPayments) }
    val expenseRows = remember(tx, ledgerCycle, month) { tx.filter {
        it.type == "EXPENSE" && (ledgerCycle?.let { cycle -> it.createdAt >= cycle.startAt } ?: it.date.startsWith("$month-"))
    }
    }
    val grouped = remember(expenseRows) { expenseRows.groupBy { it.category }.mapValues { it.value.sumOf { row -> row.amountMinor } }.entries.sortedByDescending { it.value } }
    val currentMonthExpenses = remember(tx, month) { tx.filter { it.type == "EXPENSE" && it.date.startsWith("$month-") } }
    val frequentItems = remember(currentMonthExpenses) { currentMonthExpenses.groupBy { it.frequentPurchaseLabel().lowercase(Locale.ROOT) }
        .map { (_, purchases) -> purchases.first().frequentPurchaseLabel() to purchases.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
        .take(4) }
    val categories = grouped.map { it.key to it.value }
    var balanceDetails by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(true) }
    var salaryOptionsOpen by remember { mutableStateOf(false) }
    var confirmNewMonth by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var salaryAmountInput by remember { mutableStateOf("") }
    var receiveSalarySelected by remember { mutableStateOf(true) }
    var archivePreviousSelected by remember { mutableStateOf(false) }
    var showArchives by remember { mutableStateOf(false) }
    var archiveMessage by remember { mutableStateOf("") }
    var selectedFrequentCategory by remember { mutableStateOf<String?>(null) }
    var frequentExpanded by rememberSaveable { mutableStateOf(true) }
    val recent = tx.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.updatedAt }).take(4)
    val cycleStartLabel = ledgerCycle?.let {
        Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
    val availableForCycle = summary.salary + summary.otherIncome + summary.openingBalanceMinor
    val spentRatio = if (availableForCycle > 0) (summary.totalOutflow.toFloat() / availableForCycle).coerceIn(0f, 1f) else if (summary.totalOutflow > 0) 1f else 0f
    val spentPct = (spentRatio * 100).roundToInt()
    val guide = remember(tx, summary, today, salaryDay, plannedSalaryDate, salaryRecordedWhenPlanned) { DailySpendingGuide.calculate(tx, summary, today, salaryDay, plannedSalaryDate, salaryRecordedWhenPlanned) }
    val display = LocalDisplayPreferences.current!!
    val visibleMoney: (Long) -> String = { if (showBalance && !display.privateAmounts) displayMoney(it) else "Rs •••••" }

    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("dashboard-list"), state = rememberLazyListState(), contentPadding = PaddingValues(start = if (display.compact) 10.dp else 16.dp, end = if (display.compact) 10.dp else 16.dp, top = 0.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(if (display.compact) 12.dp else 16.dp)) {
            val sections = DashboardSections()
            with(sections) {
            enteringItem(key = "greeting", index = 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LedgerColors.Teal.copy(alpha = .25f)),
                ) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(LedgerColors.Teal.copy(alpha = .16f), LedgerColors.Blue.copy(alpha = .06f))), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp)
                ) {
                    val compact = maxWidth < 370.dp
                    LandscapeArtwork(Modifier.align(Alignment.BottomEnd).width(if (compact) 112.dp else 196.dp).height(112.dp))
                    Column(Modifier.padding(top = 6.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(when (LocalTime.now().hour) { in 5..11 -> "🌅 Good morning,"; in 12..16 -> "☀️ Good afternoon,"; in 17..20 -> "🌆 Good evening,"; else -> "🌙 Good night," }, fontSize = 15.sp)
                        Text(translate = false, text = "👋 " + userName.substringBefore(' ').ifBlank { "Friend" }, fontSize = if (compact) 23.sp else 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(if (compact) .65f else .85f))
                        if (weatherEnabled && weatherCity.isNotBlank()) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
                                Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(when { weatherCondition.contains("rain", true) -> "🌧️"; weatherCondition.contains("cloud", true) -> "☁️"; weatherCondition.contains("storm", true) -> "⛈️"; weatherCondition.contains("snow", true) -> "❄️"; LocalTime.now().hour !in 6..18 -> "🌙"; else -> "☀️" }, fontSize = 20.sp)
                                    Text(listOf(weatherCity, weatherTemperature, weatherCondition).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 270.dp))
                                }
                            }
                        } else Text(cycleStartLabel?.let { "Salary cycle from $it" } ?: month.format(DateTimeFormatter.ofPattern("MMMM yyyy", if (com.sadique.dailyledger.i18n.LocalStrings.current.urdu) Locale("ur", "PK") else Locale.ENGLISH)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            enteringItem(key = "balance", index = 1) {
                Surface(modifier = Modifier.fillMaxWidth().clickable { balanceDetails = true }.testTag("balance-details"), shape = RoundedCornerShape(20.dp), shadowElevation = 5.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CF9EE))) {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF087FAE), Color(0xFF004664), Color(0xFF006D75)))).padding(16.dp)) {
                        WalletArtwork(Modifier.align(Alignment.TopEnd).size(90.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Remaining balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { showBalance = !showBalance }, Modifier.size(30.dp)) {
                                    Icon(if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (showBalance) "Hide balance" else "Show balance", Modifier.size(18.dp), tint = Color(0xFFCDFBFF))
                                }
                            }
                            Text(visibleMoney(summary.remaining), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.77f))
                            Text(if (summary.remaining < 0) "This salary cycle is over budget" else "Available in this salary cycle", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                            // Keep the percentage outside the track: an overlaid chip clips in
                            // Urdu RTL and with large system text on narrower devices.
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Spent", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Surface(shape = CircleShape, color = Color.White) {
                                    Text(
                                        com.sadique.dailyledger.i18n.progressPercent(spentPct),
                                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            .testTag("balance-progress-percent"),
                                        translate = false, color = LedgerColors.Navy,
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                        maxLines = 1, softWrap = false,
                                    )
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(18.dp)
                                    .clip(CircleShape).background(Color.White.copy(alpha = .25f))
                                    .testTag("balance-progress-track"),
                            ) {
                                if (spentRatio > 0f) Box(
                                    Modifier.fillMaxWidth(spentRatio).fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(Color(0xFFCDFFFF), Color(0xFF00C7E6))), CircleShape),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "${visibleMoney(summary.totalOutflow)} of ${visibleMoney(availableForCycle.coerceAtLeast(0L))}",
                                    Modifier.weight(1f), color = Color(0xFFC0FFEE),
                                    fontSize = 10.sp, maxLines = 2,
                                )
                                Text("${100 - spentPct}% left", color = Color.White, fontSize = 11.sp, maxLines = 2)
                            }
                            if (summary.openingBalanceMinor != 0L) {
                                Text("Balance carried forward: ${visibleMoney(summary.openingBalanceMinor)}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                            }
                            if (summary.committeeContributions > 0 || summary.savingsContributions > 0) {
                                Text("Expenses ${visibleMoney(summary.expenses)} · Kameti ${visibleMoney(summary.committeeContributions)} · Savings ${visibleMoney(summary.savingsContributions)}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                            }
                            if (summary.udharPayments > 0) Text("Udhar paid this cycle: ${visibleMoney(summary.udharPayments)}", color = Color(0xFFC0FFEE), fontSize = 11.sp)
                            if (summary.otherIncome > 0) Text("Other income: ${visibleMoney(summary.otherIncome)}", color = Color(0xFFCAF6FF), fontSize = 10.sp)
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = .2f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = Color(0xFFB4FFF1))
                                Text("Daily spending guide · Offline AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                            Text("Daily allowance", color = Color(0xFFCAF6FF), fontSize = 11.sp)
                            Text(
                                guide.dailyAllowanceMinor?.let { "Around ${visibleMoney(it)} / day until salary" }
                                    ?: if (guide.lastSalaryDate == null) "Record your salary date to see a daily estimate."
                                    else "Confirm your next salary before planning more spending.",
                                color = Color.White, fontSize = 13.sp, modifier = Modifier.testTag("daily-spending-guide"),
                            )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Actual daily average", color = Color(0xFFCAF6FF), fontSize = 11.sp)
                                Text(guide.actualDailyExpenseMinor?.let { "${visibleMoney(it)} / day" }
                                    ?: "Record salary to see average", color = Color.White, fontSize = 13.sp,
                                    modifier = Modifier.testTag("actual-daily-average"))
                                guide.elapsedSalaryDays?.let { Text("Over $it days since salary", color = Color(0xFFCAF6FF), fontSize = 10.sp) }
                            }
                            }
                            guide.expectedSalaryDate?.let { expected ->
                                val days = guide.daysUntilSalary!!
                                val countdown = when {
                                    days > 0 -> "$days day${if (days == 1L) "" else "s"} to expected salary"
                                    days == 0L -> "Salary expected today"
                                    else -> "Salary expected ${-days} day${if (days == -1L) "" else "s"} ago"
                                }
                                Text("$countdown · ${expected.format(DateTimeFormatter.ofPattern("dd MMM yyyy", if (com.sadique.dailyledger.i18n.LocalStrings.current.urdu) Locale("ur", "PK") else Locale.ENGLISH))}",
                                    color = Color(0xFFCAF6FF), fontSize = 11.sp, modifier = Modifier.testTag("salary-countdown"))
                                Text(if (salaryDay == null && plannedSalaryDate == null) "Estimate from your last salary. Reserve upcoming bills first." else "Based on your selected salary day. Reserve upcoming bills first.", color = Color(0xFFCAF6FF), fontSize = 10.sp)
                            }
                            if (guide.lowBalance) {
                                Surface(color = Color(0xFFFFE5E8), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("low-balance-notice")) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(21.dp), tint = Color(0xFFAA1739))
                                        Text(if (summary.remaining <= 0) "Balance used up. Review essentials before further spending."
                                            else "Low remaining balance · 10% or less left. Keep essentials first.",
                                            color = Color(0xFF8E1231), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            enteringItem(key = "monthly-totals", index = 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Salary", summary.salary, "This cycle", Icons.Rounded.ArrowUpward, LedgerColors.Green, Modifier.weight(1f).fillMaxHeight().testTag("dashboard-add-salary"), onAddSalary)
                        DashboardStat("Other income", summary.otherIncome, "This cycle", Icons.Rounded.AddCircle, LedgerColors.Teal, Modifier.weight(1f).fillMaxHeight(), onAddOtherIncome)
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Expense", summary.expenses, "This cycle", Icons.Rounded.ArrowDownward, LedgerColors.Red, Modifier.weight(1f).fillMaxHeight(), onAddExpense)
                        DashboardStat("Savings", savings.sumOf { it.amountMinor }, "Separate total", Icons.Rounded.AccountBalanceWallet, LedgerColors.Blue, Modifier.weight(1f).fillMaxHeight(), onOpenSavings)
                    }
                }
            }
            enteringItem(key = "salary-cycle-controls", index = 3) {
                GradientButton(
                    "Salary Received & Cycle Options",
                    onClick = { salaryOptionsOpen = true },
                    modifier = Modifier.fillMaxWidth().testTag("start-new-salary-month"),
                    icon = Icons.Rounded.EventAvailable,
                )
            }
            if (planningNotices.isNotEmpty()) enteringItem(key = "budget-notice", index = 4) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Text("Budgets & reminders", style = MaterialTheme.typography.titleMedium)
                    planningNotices.take(3).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onOpenPlanning) { Text("Review") }
                }
            }
            if (true) enteringItem(key = "spending", index = 4) { SpendingDonutCard(categories, summary.expenses, onExpenseHistory, snapshot?.tips?.firstOrNull { com.sadique.dailyledger.ai.LearningSnapshot.feedbackKey(it) !in dismissedTipKeys }) }
            if (display.showFrequent && frequentItems.isNotEmpty()) enteringItem(key = "frequent-items", index = 5) {
                LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 12.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Frequent purchases", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { frequentExpanded = !frequentExpanded }) {
                            Text(if (frequentExpanded) "Collapse" else "Expand")
                        }
                    }
                    if (frequentExpanded) frequentItems.forEachIndexed { index, (label, total) ->
                        val accent = listOf(LedgerColors.Teal, LedgerColors.Amber, LedgerColors.Blue, LedgerColors.Purple)[index % 4]
                        val visual = categoryVisual(label, "EXPENSE")
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).testTag("frequent-item-$index")
                                .clickable { selectedFrequentCategory = label },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .32f)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(accent.copy(alpha = .17f), accent.copy(alpha = .04f))), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GlowIcon(visual.icon, accent, size = 35.dp)
                                Text(label.ifBlank { "Other" }, modifier = Modifier.weight(1f), fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(visibleMoney(total), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Rounded.ChevronRight, "View $label history", Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (true) enteringItem(key = "recent", index = 6) {
                LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 12.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent transactions", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        onAllTransactions?.let { action ->
                            TextButton(onClick = action, contentPadding = PaddingValues(start = 8.dp)) {
                                Text("See all", fontSize = 12.sp)
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(17.dp))
                            }
                        }
                    }
                    if (recent.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Your ledger starts here", fontWeight = FontWeight.SemiBold)
                            Text("Add your salary or first expense to see your monthly picture.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onAddSalary) { Text("Add salary") }
                        }
                    } else recent.forEachIndexed { index, entry ->
                        val visual = categoryVisual(entry.category, entry.type)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).testTag("recent-transaction-$index")
                                .clickable { onAllTransactions?.invoke() },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, visual.color.copy(alpha = .30f)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(visual.color.copy(alpha = .13f), visual.color.copy(alpha = .03f))), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                GlowIcon(visual.icon, visual.color, size = 35.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(if (entry.note.isBlank()) com.sadique.dailyledger.i18n.LocalStrings.current.text(entry.category) else entry.note, translate = false, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text((if (entry.type == "INCOME") "+ " else "− ") + visibleMoney(entry.amountMinor),
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    color = if (entry.type == "INCOME") LedgerColors.Green else LedgerColors.Red)
                            }
                        }
                    }
                }
            }
            enteringItem(key = "insights-shortcut", index = 7) { GradientButton("Spending insights", onOpenInsights, Modifier.fillMaxWidth(), icon = Icons.Rounded.BarChart) }
            if (true) enteringItem(key = "separate-totals", index = 8) {
                LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 14.dp) {
                    Text("Your money spaces", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Savings and kameti stay separate from salary.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneySpacePair(
                        first = { cardModifier ->
                            if (display.savingsFirst) {
                                SummaryCard("Savings total", savings.sumOf { it.amountMinor },
                                    Icons.Rounded.AccountBalanceWallet, cardModifier, "All saved amounts", onOpenSavings, compact = true)
                            } else {
                                SummaryCard("Kameti paid", cp.sumOf { it.amountMinor },
                                    Icons.Rounded.Groups, cardModifier,
                                    "Received: ${visibleMoney(receipts.sumOf { it.amountMinor })}\nPending: ${visibleMoney(committees.sumOf { committeeBalance(it, receipts).remaining })}",
                                    onOpenKameti, compact = true)
                            }
                        },
                        second = { cardModifier ->
                            if (display.savingsFirst) {
                                SummaryCard("Kameti paid", cp.sumOf { it.amountMinor },
                                    Icons.Rounded.Groups, cardModifier,
                                    "Received: ${visibleMoney(receipts.sumOf { it.amountMinor })}\nPending: ${visibleMoney(committees.sumOf { committeeBalance(it, receipts).remaining })}",
                                    onOpenKameti, compact = true)
                            } else {
                                SummaryCard("Savings total", savings.sumOf { it.amountMinor },
                                    Icons.Rounded.AccountBalanceWallet, cardModifier, "All saved amounts", onOpenSavings, compact = true)
                            }
                        },
                    )
                    committeeMembers.filter { it.isMe && !it.received }.mapNotNull { member ->
                        runCatching { YearMonth.parse(member.turnMonth) }.getOrNull()?.let { it to member }
                    }.filter { !it.first.isBefore(month) }.minByOrNull { it.first }?.let { (turn, _) ->
                        val shape = RoundedCornerShape(20.dp)
                        val accent = MaterialTheme.colorScheme.tertiary
                        Surface(
                            modifier = Modifier.fillMaxWidth().testTag("kameti-turn-card")
                                .clickable(onClick = onOpenKameti),
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .38f)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clip(shape)
                                    .background(
                                        Brush.horizontalGradient(listOf(
                                            accent.copy(alpha = .13f),
                                            MaterialTheme.colorScheme.surface,
                                        )),
                                        shape = shape,
                                    ).padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                GlowIcon(Icons.Rounded.EventAvailable, accent, size = 30.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Your kameti turn • $turn", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        if (turn == month) "Your receiving month is here" else "Upcoming personal turn",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = accent)
                            }
                        }
                    }
                    if (loans.isNotEmpty()) {
                        val outstanding: (String) -> Long = { direction ->
                            loans.filter { it.direction == direction }.sumOf { loan ->
                                (loan.principalMinor - lp.filter { it.loanId == loan.id }.sumOf { it.amountMinor })
                                    .coerceAtLeast(0)
                            }
                        }
                        MoneySpacePair(
                            first = { cardModifier ->
                                SummaryCard("I owe", outstanding("BORROWED"), Icons.Rounded.Handshake,
                                    cardModifier, onClick = onOpenLoans, compact = true)
                            },
                            second = { cardModifier ->
                                SummaryCard("Owed to me", outstanding("LENT"), Icons.Rounded.AccountBalance,
                                    cardModifier, onClick = onOpenLoans, compact = true)
                            },
                        )
                    }
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
    if (balanceDetails) AlertDialog(
        onDismissRequest = { balanceDetails = false }, title = { Text("Balance breakdown") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Carried forward" to summary.openingBalanceMinor, "Salary" to summary.salary, "Other income" to summary.otherIncome,
                "Expenses" to -summary.expenses, "Kameti charged this cycle" to -summary.committeeContributions,
                "Savings set aside" to -summary.savingsContributions, "Udhar payments" to -summary.udharPayments, "Remaining balance" to summary.remaining).forEach { (label, amount) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, Modifier.weight(1f)); Text(visibleMoney(amount), fontWeight = FontWeight.SemiBold)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { balanceDetails = false; onAllTransactions?.invoke() }) { Text("View history") } },
        dismissButton = { TextButton(onClick = { balanceDetails = false }) { Text("Close") } },
    )
    if (salaryOptionsOpen) {
        AlertDialog(
            onDismissRequest = { salaryOptionsOpen = false },
            modifier = Modifier.testTag("salary-actions-dialog"),
            title = { Text("Salary Received & Cycle Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        salaryOptionsOpen = false
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
                        salaryOptionsOpen = false
                        when {
                            archivePreviousSelected -> confirmArchive = true
                            receiveSalarySelected && ledgerCycle != null -> confirmNewMonth = true
                            receiveSalarySelected -> onAddSalaryWithAmount(salaryAmountInput)
                        }
                    },
                    modifier = Modifier.testTag("salary-options-continue"),
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { salaryOptionsOpen = false }) { Text("Cancel") } },
        )
    }
    if (confirmNewMonth) {
        AlertDialog(
            onDismissRequest = { confirmNewMonth = false },
            title = { Text("Start a new salary month?") },
            text = { Text("The current salary period will be archived. Its remaining balance (${visibleMoney(summary.remaining)}) will carry forward; no money or transaction will be deleted. Start the new period before entering your next salary.") },
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
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ledgerArchives.forEach { archive ->
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Text(archive.label, fontWeight = FontWeight.SemiBold)
                            Text("Salary ${visibleMoney(archive.salaryMinor)} · Other income ${visibleMoney(archive.otherIncomeMinor)}", fontSize = 11.sp)
                            Text("Expenses ${visibleMoney(archive.expensesMinor)} · Kameti ${visibleMoney(archive.committeeMinor)} · Savings ${visibleMoney(archive.savingsMinor)}", fontSize = 11.sp)
                            Text("Closing balance ${visibleMoney(archive.closingBalanceMinor)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showArchives = false }) { Text("Close") } },
        )
    }
    selectedFrequentCategory?.let { category ->
        val purchases = currentMonthExpenses.filter { it.frequentPurchaseLabel().equals(category, ignoreCase = true) }
            .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
        val total = purchases.sumOf { it.amountMinor }
        AlertDialog(
            onDismissRequest = { selectedFrequentCategory = null },
            title = { Text(category.ifBlank { "Other" }) },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Current month · ${purchases.size} entries", style = MaterialTheme.typography.bodySmall)
                    Text("Total this month: ${visibleMoney(total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    purchases.forEachIndexed { index, purchase ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("frequent-history-entry-${purchase.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            Text(visibleMoney(purchase.amountMinor), fontWeight = FontWeight.SemiBold)
                        }
                        if (index < purchases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    HorizontalDivider()
                    Text("Total: ${visibleMoney(total)}", Modifier.align(Alignment.End), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { selectedFrequentCategory = null }) { Text("Close") } },
        )
    }
}

/** Responsive compact tiles. Stack only on very narrow screens; keep 14dp gaps. */
@Composable
private fun MoneySpacePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 275.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                first(Modifier.fillMaxWidth().heightIn(min = 126.dp).testTag("money-space-first"))
                second(Modifier.fillMaxWidth().heightIn(min = 126.dp).testTag("money-space-second"))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                first(Modifier.weight(1f).heightIn(min = 134.dp).testTag("money-space-first"))
                second(Modifier.weight(1f).heightIn(min = 134.dp).testTag("money-space-second"))
            }
        }
    }
}

private fun TransactionEntity.frequentPurchaseLabel(): String =
    note.trim().ifBlank { category.trim().ifBlank { "Other" } }

@Composable
private fun DashboardStat(title: String, amount: Long, detail: String, icon: ImageVector, color: Color, modifier: Modifier, action: () -> Unit) {
    val dark = ledgerIsDark()
    val hidden = LocalDisplayPreferences.current!!.privateAmounts
    Surface(modifier.clickable(onClick = action), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (dark) .08f else 1f))) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(color.copy(alpha = if (dark) .16f else .12f), MaterialTheme.colorScheme.surface)), RoundedCornerShape(20.dp)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GlowIcon(icon, color, size = 27.dp)
                Text(title, fontSize = 11.sp, maxLines = 1)
            }
            Text(if (hidden) "Rs •••••" else displayMoney(amount), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
internal fun SpendingDonutCard(categories: List<Pair<String, Long>>, total: Long, onHistory: () -> Unit, tip: SpendingTip? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val privacyEnabled = LocalDisplayPreferences.current?.privateAmounts == true
    val scheme = MaterialTheme.colorScheme
    // Keep neighboring slices distinguishable; a four-color theme palette caused repeated hues.
    val categoryTones = listOf(
        LedgerColors.Teal, LedgerColors.Amber, LedgerColors.Blue, LedgerColors.Green,
        LedgerColors.Purple, LedgerColors.Red, Color(0xFFDE639D), Color(0xFF6683B7),
    )
    val colorFor: (Int) -> Color = { index ->
        androidx.compose.ui.graphics.lerp(
            categoryTones[index % categoryTones.size],
            scheme.primary, 0.10f,
        )
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    LedgerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spending by category", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("This cycle", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(15.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)) {
            Column(Modifier.width(if (compact) 103.dp else 122.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(if (compact) 103.dp else 122.dp).testTag("chart-total"), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 20.dp.toPx()
                    val inset = width / 2
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width))
                    var start = -90f
                    categories.forEachIndexed { index, (_, value) ->
                        val sweep = if (total > 0) 360f * value / total else 0f
                        drawArc(colorFor(index), start, (sweep - minOf(1.2f, sweep * .15f)).coerceAtLeast(0f), false,
                            Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
                    Text(if (privacyEnabled) "Rs •••••" else displayMoney(total), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("Total spent", fontSize = 11.sp)
                }
            }
            if (tip != null && categories.size >= 3 && !privacyEnabled) {
                Text(tip.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tip.detail, fontSize = 10.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant)
            }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (categories.isEmpty()) Text("Add an expense to see where your money goes.", fontSize = 12.sp)
                (if (expanded) categories else categories.take(3)).forEachIndexed { i, (name, value) ->
                    val tint = colorFor(i)
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("chart-category-$name"),
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .29f)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(tint.copy(alpha = .13f), tint.copy(alpha = .03f))),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(9.dp).background(tint, CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (privacyEnabled) "Rs •••••" else displayMoney(value), fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text("${if (total > 0) (value * 100.0 / total).roundToInt() else 0}%", fontSize = 10.sp)
                        }
                    }
                }
            }
            }
        }
        if (categories.size > 3) TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().testTag("chart-toggle-categories"),
        ) {
            Text(if (expanded) "Show fewer categories" else "Show all categories")
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        TextButton(onClick = onHistory) { Text("View expense history") }
    }
}
