package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.CardEntrance
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerColors
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.rememberCategoryVisual

internal fun TransactionEntity.frequentPurchaseLabel(): String =
    note.trim().ifBlank { category.trim().ifBlank { "Other" } }

/** Frequent-purchase shortcuts for the current month. */
@Composable
internal fun DashboardFrequentSection(
    items: List<Pair<String, Long>>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    money: (Long) -> String,
    onSelectCategory: (String) -> Unit,
    entranceBaseDelayMs: Int = 0,
) {
    LedgerCard(Modifier.fillMaxWidth(), contentSpacing = LedgerTokens.Space12) {
        val freqChevron by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "frequent-chevron")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Frequent purchases", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "Collapse" else "Expand")
                Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = freqChevron })
            }
        }
        AnimatedVisibility(visible = expanded,
            enter = expandVertically(tween(320)) + fadeIn(tween(320)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(260))) {
        Column(verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space16)) {
        items.forEachIndexed { index, (label, total) ->
            val accent = listOf(LedgerColors.Teal, LedgerColors.Amber, LedgerColors.Blue, LedgerColors.Purple)[index % 4]
            val visual = rememberCategoryVisual(label, "EXPENSE")
            CardEntrance(index = index, delayMs = entranceBaseDelayMs + LedgerTokens.EntranceDurationMs, modifier = Modifier.fillMaxWidth()) {
            LedgerCard(
                accent = com.sadique.dailyledger.ui.ledgerCardAccent("frequent:$label"),
                modifier = Modifier.fillMaxWidth().testTag("frequent-item-$index")
                    .clickable { onSelectCategory(label) },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space8),
                ) {
                    GlowIcon(visual.icon, accent, size = 35.dp)
                    Text(label.ifBlank { "Other" }, modifier = Modifier.weight(1f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(money(total), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, "View $label history", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }
        }
        }
    }
}

/** Latest transactions with a shortcut to the full history. */
@Composable
internal fun DashboardRecentSection(
    recent: List<TransactionEntity>,
    money: (Long) -> String,
    onAllTransactions: (() -> Unit)?,
    onAddSalary: () -> Unit,
    entranceBaseDelayMs: Int = 0,
) {
    LedgerCard(Modifier.fillMaxWidth(), contentSpacing = LedgerTokens.Space16) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent transactions", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            onAllTransactions?.let { action ->
                TextButton(onClick = action, contentPadding = PaddingValues(start = LedgerTokens.Space8)) {
                    Text("See all", fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(17.dp))
                }
            }
        }
        if (recent.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space4)) {
                Text("Your ledger starts here", fontWeight = FontWeight.SemiBold)
                Text("Add your salary or first expense to see your monthly picture.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onAddSalary) { Text("Add salary") }
            }
        } else recent.forEachIndexed { index, entry ->
            val visual = rememberCategoryVisual(entry.category, entry.type)
            CardEntrance(index = index, delayMs = entranceBaseDelayMs + LedgerTokens.EntranceDurationMs, modifier = Modifier.fillMaxWidth()) {
            LedgerCard(
                accent = com.sadique.dailyledger.ui.ledgerCardAccent("transaction:${entry.id}"),
                modifier = Modifier.fillMaxWidth().testTag("recent-transaction-$index")
                    .clickable { onAllTransactions?.invoke() },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    GlowIcon(visual.icon, visual.color, size = 35.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (entry.note.isBlank()) com.sadique.dailyledger.i18n.LocalStrings.current.text(entry.category) else entry.note, translate = false, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if ((entry.type == "INCOME" || entry.refundOf.isNotBlank())) "+ " else "− ") + money(entry.amountMinor),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if ((entry.type == "INCOME" || entry.refundOf.isNotBlank())) LedgerColors.Green else LedgerColors.Red)
                }
            }
            }
        }
    }
}
