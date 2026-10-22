package com.sadique.dailyledger.ui.screens

import androidx.compose.ui.draw.clip

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    // Slice tap selection (H) and donut/bar view switch (X).
    var selectedSlice by remember { mutableStateOf<Int?>(null) }
    var barMode by rememberSaveable { mutableStateOf(false) }
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
    // Smooth height when the legend expands/collapses; the toggle chevron turns with it.
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "chart-chevron")
    // Travelling shine inside the ring — same recipe as the progress-bar shine
    // (the user's pick for the donut too; the still sheen arc is gone).
    val ringShine = remember { Animatable(0f) }
    val shineCtx = androidx.compose.ui.platform.LocalContext.current
    val shineAmbientOn = remember { android.provider.Settings.Global.getFloat(shineCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    if (shineAmbientOn) LaunchedEffect(Unit) {
        while (true) {
            ringShine.animateTo(360f, tween(6500, easing = androidx.compose.animation.core.LinearEasing))
            ringShine.snapTo(0f)
            kotlinx.coroutines.delay(600)
        }
    }
    // Grow-in for the bar view: bars and their fill rise when the view opens.
    val barGrow = remember { Animatable(0f) }
    LaunchedEffect(barMode) {
        if (barMode) {
            barGrow.snapTo(0f)
            if (shineAmbientOn) barGrow.animateTo(1f, tween(650)) else barGrow.snapTo(1f)
        }
    }
    LedgerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spending by category", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("This cycle", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            if (categories.isNotEmpty()) IconButton(onClick = { barMode = !barMode }, modifier = Modifier.testTag("chart-view-toggle")) {
                Icon(if (barMode) Icons.Rounded.DonutLarge else Icons.Rounded.BarChart, if (barMode) "Donut view" else "Bar view")
            }
        }
        Spacer(Modifier.height(15.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            AnimatedContent(targetState = barMode, transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), 0.97f)).togetherWith(fadeOut(tween(160)))
            }, label = "chart-mode") { bars ->
            if (bars) {
                // Bar view (X): same categories as animated horizontal bars.
                Column(Modifier.fillMaxWidth().testTag("chart-bars"), verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space6)) {
                    val maxV = (categories.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                    if (categories.isEmpty()) Text("Add an expense to see where your money goes.", fontSize = 12.sp)
                    categories.forEachIndexed { i, (name, value) ->
                        val tint = colorFor(i)
                        val frac by animateFloatAsState(value.toFloat() / maxV.toFloat(), tween(600), label = "cat-bar")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(if (privacyEnabled) "Rs •••••" else displayMoney(value), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(track)) {
                                Box(Modifier.fillMaxWidth(frac * barGrow.value).fillMaxHeight().background(tint, CircleShape))
                            }
                        }
                    }
                }
            } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(if (compact) 180.dp else 200.dp).testTag("chart-total"), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().pointerInput(categories, total) {
                            detectTapGestures { tap ->
                                val cxx = size.width / 2f; val cyy = size.height / 2f
                                val dx = tap.x - cxx; val dy = tap.y - cyy
                                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                val outer = minOf(size.width, size.height).toFloat() / 2f
                                if (dist > outer - 30.dp.toPx() && dist < outer + 4.dp.toPx()) {
                                    var ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                    if (ang < 0f) ang += 360f
                                    var acc2 = 0f; var hit: Int? = null
                                    categories.forEachIndexed { idx, (_, v) ->
                                        val sw = if (total > 0) 360f * v / total else 0f
                                        if (ang >= acc2 && ang < acc2 + sw) hit = idx
                                        acc2 += sw
                                    }
                                    selectedSlice = if (selectedSlice == hit) null else hit
                                }
                            }
                        }) {
                            val width = 26.dp.toPx()
                            val inset = width / 2
                            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width))
                            var start = -90f
                            var budget = 360f * sweepAnim.value
                            categories.forEachIndexed { index, (_, value) ->
                                val sweep = if (total > 0) 360f * value / total else 0f
                                val drawn = minOf(sweep, budget).coerceAtLeast(0f)
                                if (drawn > 0f) {
                                    val selOff = if (index == selectedSlice) {
                                        val midRad = Math.toRadians((start + sweep / 2 - 90).toDouble())
                                        Offset((5.dp.toPx() * kotlin.math.cos(midRad)).toFloat(), (5.dp.toPx() * kotlin.math.sin(midRad)).toFloat())
                                    } else Offset.Zero
                                    drawArc(colorFor(index), start, (drawn - minOf(1.2f, drawn * .15f)).coerceAtLeast(0f), false,
                                        Offset(inset + selOff.x, inset + selOff.y), Size(size.width - width, size.height - width), style = Stroke(width))
                                }
                                start += sweep
                                budget -= drawn
                            }
                            // Travelling shine inside the ring, like the progress bar's.
                            if (shineAmbientOn) {
                                withTransform({ rotate(ringShine.value, Offset(size.width / 2f, size.height / 2f)) }) {
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent, Color.Transparent),
                                            Offset(size.width / 2f, size.height / 2f),
                                        ),
                                        0f, 360f, false, Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width),
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp)) {
                            com.sadique.dailyledger.ui.AnimatedMoneyText(total, { if (privacyEnabled) "Rs •••••" else displayMoney(it) }, style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            Text("Total spent", fontSize = 11.sp)
                        }
                    }
                Column(Modifier.fillMaxWidth().animateContentSize(tween(320)).testTag("chart-legend"), verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space6)) {
                    if (categories.isEmpty()) Text("Add an expense to see where your money goes.", fontSize = 12.sp)
                    (if (expanded) categories else categories.take(3)).forEachIndexed { i, (name, value) ->
                        val tint = colorFor(i)
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .testTag("chart-category-$name"),
                            shape = LedgerTokens.ShapeCard,
                            color = scheme.surface,
                            border = androidx.compose.foundation.BorderStroke(if (i == selectedSlice) 2.dp else 1.dp, tint.copy(alpha = if (i == selectedSlice) .95f else .29f)),
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
                selectedSlice?.let { sel -> categories.getOrNull(sel)?.let { (selName, selValue) ->
                    Text("$selName — ${if (privacyEnabled) "Rs •••••" else displayMoney(selValue)} • ${if (total > 0) (selValue * 100.0 / total).roundToInt() else 0}% of spending",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorFor(sel),
                        modifier = Modifier.testTag("chart-selected-detail"))
                } }
            }
            }
        }
        }
        if (categories.size > 3) TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().testTag("chart-toggle-categories"),
        ) {
            Text(if (expanded) "Show fewer categories" else "Show all categories")
            Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = chevronRotation })
        }
        TextButton(onClick = onHistory) { Text("View expense history") }
    }
}
