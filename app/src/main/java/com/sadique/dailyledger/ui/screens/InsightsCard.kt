package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface
import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.sadique.dailyledger.ui.CardEntrance
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
                val animScore = androidx.compose.animation.core.animateIntAsState(snapshot.healthScore, androidx.compose.animation.core.tween(800), label = "money-score")
                val animRate = androidx.compose.animation.core.animateIntAsState(snapshot.savingRatePercent, androidx.compose.animation.core.tween(800), label = "saving-rate")
                // Inner small boxes slide in one-by-one AFTER the big card itself
                // (delayMs sequencing) — otherwise their slide hides inside the
                // parent card's own entrance and is never seen.
                CardEntrance(index = 0, delayMs = 500, modifier = Modifier.weight(1f)) {
                    MetricChip("Money score", if (snapshot.count > 0 && snapshot.summary.salary > 0) "${animScore.value}/100" else "—", Modifier.fillMaxWidth())
                }
                CardEntrance(index = 1, delayMs = 500, modifier = Modifier.weight(1f)) {
                    MetricChip("Saving rate", "${animRate.value}%", Modifier.fillMaxWidth())
                }
                CardEntrance(index = 2, delayMs = 500, modifier = Modifier.weight(1f)) {
                    MetricMoneyChip("Possible save", snapshot.potentialSaving, Modifier.fillMaxWidth())
                }
            }

            Text("Budget estimate from recorded entries; not a credit score or guaranteed saving.", style = MaterialTheme.typography.bodySmall)

            snapshot.tips.filter { com.sadique.dailyledger.ai.LearningSnapshot.feedbackKey(it) !in dismissedTipKeys }.forEachIndexed { tipIndex, tip ->
                CardEntrance(index = tipIndex, delayMs = 700) {
                LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 4.dp) {
                    Text(tip.title, style = MaterialTheme.typography.titleSmall)
                    Text(if (LocalDisplayPreferences.current?.privateAmounts == true) "Amounts hidden in privacy mode" else tip.detail)
                }
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
                cloudTips.filter { com.sadique.dailyledger.ai.LearningSnapshot.feedbackKey(it) !in dismissedTipKeys }.forEachIndexed { tipIndex, tip ->
                    CardEntrance(index = tipIndex, delayMs = 800) {
                    LedgerCard(Modifier.fillMaxWidth(), contentSpacing = 4.dp) {
                        Text(tip.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (LocalDisplayPreferences.current?.privateAmounts == true) "Amounts hidden in privacy mode" else tip.detail)
                    }
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

@Composable
private fun MetricMoneyChip(label: String, amount: Long, modifier: Modifier = Modifier) {
    val hiddenAmounts = LocalDisplayPreferences.current?.privateAmounts == true
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            com.sadique.dailyledger.ui.AnimatedMoneyText(amount, { if (hiddenAmounts) "Rs •••••" else com.sadique.dailyledger.ui.money(it) }, style = MaterialTheme.typography.titleSmall)
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
            // Trend line (R): draws itself left-to-right over the bars.
            val lineReveal = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(months) { lineReveal.snapTo(0f); lineReveal.animateTo(1f, androidx.compose.animation.core.tween(850)) }
            val lineColor = MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxWidth().height(96.dp)) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                months.forEachIndexed { barIndex, (m, total) ->
                    val barHeight = (96f * total.toFloat() / max.toFloat()).dp
                    val barStarted = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { barStarted.value = true }
                    val animatedBarHeight = androidx.compose.animation.core.animateDpAsState(
                        if (barStarted.value) barHeight else 0.dp,
                        androidx.compose.animation.core.tween(650, delayMillis = barIndex * 80), label = "trend-bar",
                    ).value
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(if (total > 0) animatedBarHeight.coerceAtLeast(4.dp) else 2.dp)
                            .background(
                                if (m == currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .4f),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val gapPx = 8.dp.toPx()
                val slot = (size.width + gapPx) / months.size
                val pts = months.mapIndexed { i, (_, total) ->
                    val barH = if (total > 0) (size.height * total.toFloat() / max.toFloat()).coerceAtLeast(4.dp.toPx()) else 2.dp.toPx()
                    androidx.compose.ui.geometry.Offset(slot * i + (slot - gapPx) / 2f, size.height - barH)
                }
                // Draw only up to the reveal cutoff (interpolated on the crossing segment).
                val cutoffX = size.width * lineReveal.value
                val visiblePts = pts.filter { it.x <= cutoffX }
                if (visiblePts.size > 1) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        visiblePts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                        val nextPt = pts.getOrNull(visiblePts.size)
                        val lastPt = visiblePts.last()
                        if (nextPt != null && nextPt.x > lastPt.x) {
                            val t = (cutoffX - lastPt.x) / (nextPt.x - lastPt.x)
                            lineTo(cutoffX, lastPt.y + (nextPt.y - lastPt.y) * t)
                        }
                    }
                    drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                visiblePts.forEach { drawCircle(lineColor, 3.dp.toPx(), it) }
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
