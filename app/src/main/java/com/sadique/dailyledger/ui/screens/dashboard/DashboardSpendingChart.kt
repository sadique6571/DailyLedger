package com.sadique.dailyledger.ui.screens

import androidx.compose.ui.draw.clip

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerColors
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import com.sadique.dailyledger.ui.displayMoney
import kotlin.math.roundToInt

/** Donut chart of spending by category with an expandable legend. */
@Composable
internal fun SpendingDonutCard(categories: List<Pair<String, Long>>, total: Long, onHistory: () -> Unit) {
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
    // Sweep-in animation (Round 2): segments fill one after another whenever the data changes.
    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(categories, total) {
        sweepAnim.snapTo(0f)
        sweepAnim.animateTo(1f, tween(900))
    }
    LedgerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spending by category", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("This cycle", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(15.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(if (compact) 180.dp else 200.dp).testTag("chart-total"), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val width = 26.dp.toPx()
                            val inset = width / 2
                            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width))
                            var start = -90f
                            var budget = 360f * sweepAnim.value
                            categories.forEachIndexed { index, (_, value) ->
                                val sweep = if (total > 0) 360f * value / total else 0f
                                val drawn = minOf(sweep, budget).coerceAtLeast(0f)
                                if (drawn > 0f) {
                                    drawArc(colorFor(index), start, (drawn - minOf(1.2f, drawn * .15f)).coerceAtLeast(0f), false,
                                        Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width))
                                }
                                start += sweep
                                budget -= drawn
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp)) {
                            com.sadique.dailyledger.ui.AnimatedMoneyText(total, { if (privacyEnabled) "Rs •••••" else displayMoney(it) }, style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            Text("Total spent", fontSize = 11.sp)
                        }
                    }
                Column(Modifier.fillMaxWidth().testTag("chart-legend"), verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space6)) {
                    if (categories.isEmpty()) Text("Add an expense to see where your money goes.", fontSize = 12.sp)
                    (if (expanded) categories else categories.take(3)).forEachIndexed { i, (name, value) ->
                        val tint = colorFor(i)
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .testTag("chart-category-$name"),
                            shape = LedgerTokens.ShapeCard,
                            color = scheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .29f)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clip(LedgerTokens.ShapeCard)
                                    .background(
                                        Brush.horizontalGradient(listOf(tint.copy(alpha = .13f), tint.copy(alpha = .03f))),
                                        LedgerTokens.ShapeCard,
                                    )
                                    .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space6),
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
