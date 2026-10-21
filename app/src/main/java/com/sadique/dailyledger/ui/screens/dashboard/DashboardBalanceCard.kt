package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ai.DailySpendingGuide
import com.sadique.dailyledger.data.SalarySummary
import com.sadique.dailyledger.ui.LedgerColors
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.WalletArtwork
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hero balance card: remaining balance, spent progress, daily guide and salary countdown. */
@Composable
internal fun DashboardBalanceCard(
    summary: SalarySummary,
    guide: DailySpendingGuide,
    availableForCycle: Long,
    spentRatio: Float,
    spentPct: Int,
    showBalance: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onOpenDetails: () -> Unit,
    salaryDay: Int?,
    plannedSalaryDate: LocalDate?,
    money: (Long) -> String,
) {
    val shape = LedgerTokens.ShapeCard
    Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenDetails() }.testTag("balance-details"), shape = shape, shadowElevation = LedgerTokens.Elevation3, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CF9EE))) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF087FAE), Color(0xFF004664), Color(0xFF006D75)))).padding(14.dp)) {
            WalletArtwork(Modifier.align(Alignment.TopEnd).size(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remaining balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = onToggleBalanceVisibility, Modifier.size(30.dp)) {
                        Icon(if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (showBalance) "Hide balance" else "Show balance", Modifier.size(18.dp), tint = Color(0xFFCDFBFF))
                    }
                }
                com.sadique.dailyledger.ui.AnimatedMoneyText(summary.remaining, money, color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(.77f))
                if (summary.remaining < 0) Text("This salary cycle is over budget", color = Color.White, fontSize = 11.sp)
                // Keep the percentage outside the track: an overlaid chip clips in
                // Urdu RTL and with large system text on narrower devices.
                Row(
                    Modifier.fillMaxWidth().padding(top = LedgerTokens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Spent", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Surface(shape = CircleShape, color = Color.White) {
                        Text(
                            com.sadique.dailyledger.i18n.progressPercent(spentPct),
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space8),
                ) {
                    Text(
                        "${money(summary.totalOutflow)} of ${money(availableForCycle.coerceAtLeast(0L))}",
                        Modifier.weight(1f), color = Color(0xFFC0FFEE),
                        fontSize = 10.sp, maxLines = 2,
                    )
                    Text("${100 - spentPct}% left", color = Color.White, fontSize = 11.sp, maxLines = 2)
                }
                if (summary.openingBalanceMinor != 0L) {
                    Text("Balance carried forward: ${money(summary.openingBalanceMinor)}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                }
                if (summary.committeeContributions > 0 || summary.savingsContributions > 0) {
                    Text("Expenses ${money(summary.expenses)} · Kameti ${money(summary.committeeContributions)} · Savings ${money(summary.savingsContributions)}", color = Color(0xFFC0FFEE), fontSize = 10.sp)
                }
                if (summary.udharPayments > 0) Text("Udhar paid this cycle: ${money(summary.udharPayments)}", color = Color(0xFFC0FFEE), fontSize = 11.sp)
                if (summary.otherIncome > 0) Text("Other income: ${money(summary.otherIncome)}", color = Color(0xFFCAF6FF), fontSize = 10.sp)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = .2f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space6)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = Color(0xFFB4FFF1))
                    Text("Daily spending guide · Offline AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space12)) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily allowance", color = Color(0xFFCAF6FF), fontSize = 11.sp)
                        Text(
                            guide.dailyAllowanceMinor?.let { "Around ${money(it)} / day until salary" }
                                ?: if (guide.lastSalaryDate == null) "Record your salary date to see a daily estimate."
                                else "Confirm your next salary before planning more spending.",
                            color = Color.White, fontSize = 11.sp, modifier = Modifier.testTag("daily-spending-guide"),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Actual daily average", color = Color(0xFFCAF6FF), fontSize = 11.sp)
                        Text(guide.actualDailyExpenseMinor?.let { "${money(it)} / day" }
                            ?: "Record salary to see average", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.testTag("actual-daily-average"))
                        guide.elapsedSalaryDays?.let { Text("Over $it days since salary", color = Color(0xFFCAF6FF), fontSize = 9.sp) }
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

                }
                if (guide.lowBalance) {
                    Surface(color = Color(0xFFFFE5E8), shape = LedgerTokens.ShapeSmall, modifier = Modifier.fillMaxWidth().padding(top = LedgerTokens.Space4).testTag("low-balance-notice")) {
                        Row(Modifier.padding(LedgerTokens.Space10), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space4)) {
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
