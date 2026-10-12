package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.sadique.dailyledger.ai.SpendingSnapshot
import com.sadique.dailyledger.ai.SpendingTip
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
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
    onAddSalary: () -> Unit, onAddExpense: () -> Unit, onOpenSavings: () -> Unit, onOpenKameti: () -> Unit,
    onAutoFill: (() -> Unit)? = null, snapshot: SpendingSnapshot? = null, aiTips: List<SpendingTip> = emptyList(),
    aiStatus: String = "", aiEnabled: Boolean = false, onEnableAi: () -> Unit = {},
    onAllTransactions: (() -> Unit)? = null,
    onStartNewMonth: () -> Unit = {}, onArchivePreviousMonth: ((Boolean) -> Unit) -> Unit = {},
) {
    val month = YearMonth.now()
    val summary = ledgerCycle?.let { ledgerCycleSummary(tx, cp, savings, it) }
        ?: monthlySalarySummary(tx, month.toString(), cp, savings)
    val expenseRows = tx.filter {
        it.type == "EXPENSE" && (ledgerCycle?.let { cycle -> it.createdAt >= cycle.startAt } ?: it.date.startsWith("$month-"))
    }
    val grouped = expenseRows.groupBy { it.category }.mapValues { it.value.sumOf { row -> row.amountMinor } }.entries.sortedByDescending { it.value }
    val frequentItems = expenseRows.groupBy { it.frequentPurchaseLabel().lowercase(Locale.ROOT) }
        .map { (_, purchases) -> purchases.first().frequentPurchaseLabel() to purchases.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
        .take(4)
    // All categories contribute to the chart total; smaller categories share the final slice.
    val categories = grouped.take(if (grouped.size > 5) 4 else 5).map { it.key to it.value } +
        if (grouped.size > 5) listOf("Others" to grouped.drop(4).sumOf { it.value }) else emptyList()
    var showBalance by remember { mutableStateOf(true) }
    var confirmNewMonth by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var showArchives by remember { mutableStateOf(false) }
    var archiveMessage by remember { mutableStateOf("") }
    var selectedFrequentCategory by remember { mutableStateOf<String?>(null) }
    val recent = tx.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.updatedAt }).take(4)
    val cycleStartLabel = ledgerCycle?.let {
        Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
    val availableForCycle = summary.salary + summary.otherIncome + summary.openingBalanceMinor
    val spentRatio = if (availableForCycle > 0) (summary.totalOutflow.toFloat() / availableForCycle).coerceIn(0f, 1f) else if (summary.totalOutflow > 0) 1f else 0f
    val spentPct = (spentRatio * 100).roundToInt()

    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("dashboard-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "greeting") {
                Box(Modifier.fillMaxWidth().heightIn(min = 103.dp)) {
                    LandscapeArtwork(Modifier.align(Alignment.BottomEnd).width(196.dp).height(112.dp))
                    Column(Modifier.padding(top = 6.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(when (LocalTime.now().hour) { in 5..11 -> "Good morning,"; in 12..16 -> "Good afternoon,"; else -> "Good evening," }, fontSize = 15.sp)
                        Text(userName.substringBefore(' ').ifBlank { "Friend" } + " 👋", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        if (weatherEnabled && weatherCity.isNotBlank()) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
                                Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = LedgerColors.Teal)
                                    Text(listOf(weatherCity, weatherTemperature, weatherCondition).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 255.dp))
                                }
                            }
                        } else Text(cycleStartLabel?.let { "Salary cycle from $it" } ?: month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "balance") {
                Surface(shape = RoundedCornerShape(23.dp), shadowElevation = 5.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CF9EE))) {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF087FAE), Color(0xFF004664), Color(0xFF006D75)))).padding(16.dp)) {
                        WalletArtwork(Modifier.align(Alignment.TopEnd).size(90.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Remaining balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { showBalance = !showBalance }, Modifier.size(30.dp)) {
                                    Icon(if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (showBalance) "Hide balance" else "Show balance", Modifier.size(18.dp), tint = Color(0xFFCDFBFF))
                                }
                            }
                            Text(if (showBalance) displayMoney(summary.remaining) else "Rs •••••", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.77f))
                            Text(if (summary.remaining < 0) "This salary cycle is over budget" else "Available in this salary cycle", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.fillMaxWidth().height(12.dp).background(Color.White.copy(alpha = .17f), CircleShape))
                                if (spentRatio > 0) Box(Modifier.fillMaxWidth(spentRatio).height(12.dp).background(Brush.verticalGradient(listOf(Color(0xFFCDFFFF), Color(0xFF00C7E6))), CircleShape))
                                Surface(Modifier.align(Alignment.CenterEnd), shape = CircleShape, color = Color.White) {
                                    Text("$spentPct%", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = LedgerColors.Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${displayMoney(summary.totalOutflow)} of ${displayMoney(availableForCycle.coerceAtLeast(0L))}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                                Text("${100 - spentPct}% left", color = Color.White, fontSize = 10.sp)
                            }
                            if (summary.openingBalanceMinor != 0L) {
                                Text("Balance carried forward: ${displayMoney(summary.openingBalanceMinor)}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                            }
                            if (summary.committeeContributions > 0 || summary.savingsContributions > 0) {
                                Text(
                                    "Expenses ${displayMoney(summary.expenses)} · Kameti ${displayMoney(summary.committeeContributions)} · Savings ${displayMoney(summary.savingsContributions)}",
                                    color = Color(0xFFC0FFEE), fontSize = 10.sp,
                                )
                            }
                            if (summary.otherIncome > 0) Text("Other income: ${displayMoney(summary.otherIncome)}", color = Color(0xFFCAF6FF), fontSize = 10.sp)
                        }
                    }
                }
            }
            item(key = "monthly-totals") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Salary", summary.salary, "This cycle", Icons.Rounded.ArrowUpward, LedgerColors.Green, Modifier.weight(1f).testTag("dashboard-add-salary"), onAddSalary)
                        DashboardStat("Other income", summary.otherIncome, "This cycle", Icons.Rounded.AddCircle, LedgerColors.Teal, Modifier.weight(1f), onAllTransactions ?: onAddSalary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Expense", summary.expenses, "This cycle", Icons.Rounded.ArrowDownward, LedgerColors.Red, Modifier.weight(1f), onAddExpense)
                        DashboardStat("Savings", savings.sumOf { it.amountMinor }, "Separate total", Icons.Rounded.AccountBalanceWallet, LedgerColors.Blue, Modifier.weight(1f), onOpenSavings)
                    }
                }
            }
            item(key = "salary-cycle-controls") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(
                        onClick = { confirmNewMonth = true },
                        enabled = ledgerCycle != null,
                        modifier = Modifier.fillMaxWidth().testTag("start-new-salary-month"),
                    ) {
                        Icon(Icons.Rounded.Autorenew, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Salary received • Start new month")
                    }
                    OutlinedButton(
                        onClick = { confirmArchive = true },
                        modifier = Modifier.fillMaxWidth().testTag("archive-last-month"),
                    ) {
                        Icon(Icons.Rounded.Archive, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Archive last month")
                    }
                    if (ledgerArchives.isNotEmpty()) {
                        TextButton(onClick = { showArchives = true }, modifier = Modifier.align(Alignment.End)) {
                            Text("View archives (${ledgerArchives.size})")
                        }
                    }
                    if (archiveMessage.isNotBlank()) Text(archiveMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            item(key = "spending") { SpendingDonutCard(categories, summary.expenses) }
            if (frequentItems.isNotEmpty()) item(key = "frequent-items") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Frequent purchases", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text("This cycle", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    frequentItems.forEachIndexed { index, (label, total) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFrequentCategory = label }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("frequent-item-$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label.ifBlank { "Other" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(displayMoney(total), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "recent") {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent transactions", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        onAllTransactions?.let { action -> TextButton(onClick = action, contentPadding = PaddingValues(start = 8.dp)) {
                            Text("See all", fontSize = 12.sp); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(17.dp))
                        } }
                    }
                    if (recent.isEmpty()) {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Text("Your ledger starts here", fontWeight = FontWeight.SemiBold)
                            Text("Add your salary or first expense to see your monthly picture.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onAddSalary) { Text("Add salary") }
                        }
                    } else recent.forEachIndexed { index, entry ->
                        val visual = categoryVisual(entry.category, entry.type)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowIcon(visual.icon, visual.color, size = 38.dp)
                            Column(Modifier.weight(1f)) {
                                Text(entry.note.ifBlank { entry.category }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text((if (entry.type == "INCOME") "+ " else "− ") + displayMoney(entry.amountMinor), fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, color = if (entry.type == "INCOME") LedgerColors.Green else LedgerColors.Red)
                        }
                        if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    }
                }
            }
            onAutoFill?.let { action -> item { GradientButton("AI Quick Add", action, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome) } }
            item(key = "separate-totals") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your money spaces", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Savings and kameti stay separate from salary.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("Savings total", savings.sumOf { it.amountMinor }, Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f), "All saved amounts", onOpenSavings)
                        SummaryCard("Kameti paid", cp.sumOf { it.amountMinor }, Icons.Rounded.Groups, Modifier.weight(1f), "Received: ${displayMoney(receipts.sumOf { it.amountMinor })}\nPending: ${displayMoney(committees.sumOf { committeeBalance(it, receipts).remaining })}", onOpenKameti)
                    }
                    committeeMembers.filter { it.isMe && !it.received }.mapNotNull { member ->
                        runCatching { YearMonth.parse(member.turnMonth) }.getOrNull()?.let { it to member }
                    }.filter { !it.first.isBefore(month) }.minByOrNull { it.first }?.let { (turn, _) ->
                        LedgerCard(Modifier.fillMaxWidth().clickable(onClick = onOpenKameti)) {
                            Text("Your kameti turn • $turn", fontWeight = FontWeight.SemiBold)
                            Text(if (turn == month) "Your receiving month is here" else "Upcoming personal turn", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (loans.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val outstanding: (String) -> Long = { direction -> loans.filter { it.direction == direction }.sumOf { loan ->
                            (loan.principalMinor - lp.filter { it.loanId == loan.id }.sumOf { it.amountMinor }).coerceAtLeast(0) } }
                        SummaryCard("I owe", outstanding("BORROWED"), Icons.Rounded.Handshake, Modifier.weight(1f))
                        SummaryCard("Owed to me", outstanding("LENT"), Icons.Rounded.AccountBalance, Modifier.weight(1f))
                    }
                }
            }
            snapshot?.let { data -> item(key = "insights") { InsightsCard(data, aiTips, aiStatus, aiEnabled, onEnableAi) } }
        }
    }
    if (confirmNewMonth) {
        AlertDialog(
            onDismissRequest = { confirmNewMonth = false },
            title = { Text("Start a new salary month?") },
            text = { Text("The current salary period will be archived. Its remaining balance (${displayMoney(summary.remaining)}) will carry forward; no money or transaction will be deleted. Start the new period before entering your next salary.") },
            confirmButton = {
                TextButton(onClick = { confirmNewMonth = false; onStartNewMonth() }) { Text("Archive & start") }
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
                            Text("Salary ${displayMoney(archive.salaryMinor)} · Other income ${displayMoney(archive.otherIncomeMinor)}", fontSize = 11.sp)
                            Text("Expenses ${displayMoney(archive.expensesMinor)} · Kameti ${displayMoney(archive.committeeMinor)} · Savings ${displayMoney(archive.savingsMinor)}", fontSize = 11.sp)
                            Text("Closing balance ${displayMoney(archive.closingBalanceMinor)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showArchives = false }) { Text("Close") } },
        )
    }
    selectedFrequentCategory?.let { category ->
        val purchases = expenseRows.filter { it.frequentPurchaseLabel().equals(category, ignoreCase = true) }
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
                    Text("Total this cycle: ${displayMoney(total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    purchases.forEachIndexed { index, purchase ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
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
                            Text(displayMoney(purchase.amountMinor), fontWeight = FontWeight.SemiBold)
                        }
                        if (index < purchases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    HorizontalDivider()
                    Text("Total: ${displayMoney(total)}", Modifier.align(Alignment.End), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { selectedFrequentCategory = null }) { Text("Close") } },
        )
    }
}

private fun TransactionEntity.frequentPurchaseLabel(): String =
    note.trim().ifBlank { category.trim().ifBlank { "Other" } }

@Composable
private fun DashboardStat(title: String, amount: Long, detail: String, icon: ImageVector, color: Color, modifier: Modifier, action: () -> Unit) {
    val dark = ledgerIsDark()
    Surface(modifier.clickable(onClick = action), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (dark) .08f else 1f))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(color.copy(alpha = if (dark) .16f else .12f), Color.Transparent))).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GlowIcon(icon, color, size = 27.dp)
                Text(title, fontSize = 11.sp, maxLines = 1)
            }
            Text(displayMoney(amount), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SpendingDonutCard(categories: List<Pair<String, Long>>, total: Long) {
    val palette = listOf(LedgerColors.Green, LedgerColors.Blue, LedgerColors.Amber, LedgerColors.Purple, Color(0xFF8194B2))
    LedgerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spending by category", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("This cycle", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(125.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 20.dp.toPx(); val inset = width / 2
                    drawArc(Color(0xFFE6F0F7), 0f, 360f, false, Offset(inset,inset), Size(size.width-width,size.height-width), style = Stroke(width))
                    var start = -90f
                    categories.forEachIndexed { i, (_, value) ->
                        val sweep = if (total > 0) 360f * value / total else 0f
                        drawArc(palette[i % palette.size], start, (sweep - 1.2f).coerceAtLeast(0f), false,
                            Offset(inset,inset), Size(size.width-width,size.height-width), style = Stroke(width))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayMoney(total), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Spent", fontSize = 11.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (categories.isEmpty()) Text("Add an expense to see where your money goes.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                categories.forEachIndexed { i, (name,value) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(9.dp).background(palette[i % palette.size], CircleShape))
                        Text(name, Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (total > 0) (value * 100.0 / total).roundToInt() else 0}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
