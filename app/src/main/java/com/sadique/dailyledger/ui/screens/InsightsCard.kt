package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface
import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ai.SpendingSnapshot
import com.sadique.dailyledger.ai.SpendingTip
import com.sadique.dailyledger.data.expenseEffect
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LocalDisplayPreferences

@Composable
fun InsightsCard(
    snapshot: SpendingSnapshot,
    cloudTips: List<SpendingTip>,
    status: String,
    enabled: Boolean,
    onEnable: () -> Unit,
    busy: Boolean = false,
    onRefresh: () -> Unit = {},
    onDismissTip: (SpendingTip) -> Unit = {},
    dismissedTipKeys: Set<String> = emptySet(),
) {
    Card(
        Modifier.fillMaxWidth().testTag("ai-insights-cards"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("Mini AI • Offline", style = MaterialTheme.typography.titleLarge)
            }
            Text("Recorded data only • through ${snapshot.through}", style = MaterialTheme.typography.labelMedium)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("Money score", if (snapshot.count > 0 && snapshot.summary.salary > 0) "${snapshot.healthScore}/100" else "—", Modifier.weight(1f))
                MetricChip("Saving rate", "${snapshot.savingRatePercent}%", Modifier.weight(1f))
                MetricChip("Possible save", com.sadique.dailyledger.ui.visibleAmount(snapshot.potentialSaving), Modifier.weight(1f))
            }

            Text("Budget estimate from recorded entries; not a credit score or guaranteed saving.", style = MaterialTheme.typography.bodySmall)

            snapshot.tips.filter { com.sadique.dailyledger.ai.LearningSnapshot.feedbackKey(it) !in dismissedTipKeys }.forEach { tip ->
                LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 4.dp) {
                    Text(tip.title, style = MaterialTheme.typography.titleSmall)
                    Text(if (LocalDisplayPreferences.current?.privateAmounts == true) "Amounts hidden in privacy mode" else tip.detail)
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Online AI", style = MaterialTheme.typography.titleMedium)
                if (enabled) OutlinedButton(onClick = onRefresh, enabled = !busy && snapshot.count > 0,
                    modifier = Modifier.testTag("online-ai-refresh")) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(if (busy) "Refreshing…" else "Refresh", Modifier.padding(start = 6.dp))
                }
            }
            if (!enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Offline Mini AI works without internet or API cost.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Cloud AI is optional for complex wording; the ledger and local suggestions do not depend on it.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onEnable) { Text("Enable optional Cloud AI") }
            } else {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status.ifBlank { "Tap Refresh to get online suggestions. Nothing is sent automatically." }, style = MaterialTheme.typography.bodySmall)
                cloudTips.filter { com.sadique.dailyledger.ai.LearningSnapshot.feedbackKey(it) !in dismissedTipKeys }.forEach { tip ->
                    LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 4.dp) {
                        Text(tip.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (LocalDisplayPreferences.current?.privateAmounts == true) "Amounts hidden in privacy mode" else tip.detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Bar chart of total expenses for the last six months, current month highlighted. */
@Composable
fun SpendingTrendCard(tx: List<com.sadique.dailyledger.data.TransactionEntity>) {
    val months = androidx.compose.runtime.remember(tx) {
        val now = java.time.YearMonth.now()
        (5 downTo 0).map { offset ->
            val m = now.minusMonths(offset.toLong())
            val total = tx.filter { it.type == "EXPENSE" && it.date.startsWith("$m-") }.sumOf { it.expenseEffect() }
            m to total
        }
    }
    if (months.all { it.second == 0L }) return
    val max = months.maxOf { it.second }.coerceAtLeast(1L)
    val currentMonth = java.time.YearMonth.now()
    Card(
        Modifier.fillMaxWidth().testTag("spending-trend-card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Spending trend", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                months.forEach { (m, total) ->
                    val barHeight = (96f * total.toFloat() / max.toFloat()).dp
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(if (total > 0) barHeight.coerceAtLeast(4.dp) else 2.dp)
                            .background(
                                if (m == currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .4f),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { (m, _) ->
                    Text(
                        m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m == currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Text("This month: ${com.sadique.dailyledger.ui.visibleAmount(months.last().second)}", style = MaterialTheme.typography.labelMedium)
        }
    }
}
