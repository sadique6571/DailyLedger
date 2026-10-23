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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch
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
    guideCollapsed: Boolean = false,
) {
    val shape = LedgerTokens.ShapeCard
    Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenDetails() }.testTag("balance-details"), shape = shape, shadowElevation = LedgerTokens.Elevation3, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CF9EE))) {
        val livingAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
        val shineAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(-0.45f) }
        val barShineAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(-0.35f) }
                val appCtx = androidx.compose.ui.platform.LocalContext.current
        val ambientAnimationsOn = androidx.compose.runtime.remember { android.provider.Settings.Global.getFloat(appCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
        if (ambientAnimationsOn) androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                livingAnim.animateTo(1f, androidx.compose.animation.core.tween(4200))
                livingAnim.animateTo(0f, androidx.compose.animation.core.tween(4200))
            }
        }
        if (ambientAnimationsOn) androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                shineAnim.animateTo(1.45f, androidx.compose.animation.core.tween(2600, delayMillis = 900))
                shineAnim.snapTo(-0.45f)
            }
        }
        if (ambientAnimationsOn) androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                barShineAnim.animateTo(1.35f, androidx.compose.animation.core.tween(2100, easing = androidx.compose.animation.core.LinearEasing))
                barShineAnim.snapTo(-0.35f)
                kotlinx.coroutines.delay(350)
            }
        }
        // Touch tilt (F): the card leans toward the finger and springs back on release.
        val tiltX = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
        val tiltY = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
        val tiltScope = androidx.compose.runtime.rememberCoroutineScope()
        // Zero balance (Z): the card's colors go quiet when nothing is left.
        val zeroBalance = summary.remaining <= 0L
        val gradColors = if (zeroBalance) listOf(
            androidx.compose.ui.graphics.lerp(Color(0xFF66727D), Color(0xFF79858F), livingAnim.value),
            androidx.compose.ui.graphics.lerp(Color(0xFF3E4852), Color(0xFF4C5761), livingAnim.value),
            androidx.compose.ui.graphics.lerp(Color(0xFF5B646B), Color(0xFF6D767E), livingAnim.value),
        ) else listOf(
            androidx.compose.ui.graphics.lerp(Color(0xFF087FAE), Color(0xFF13A7C9), livingAnim.value),
            androidx.compose.ui.graphics.lerp(Color(0xFF004664), Color(0xFF0B6182), livingAnim.value),
            androidx.compose.ui.graphics.lerp(Color(0xFF006D75), Color(0xFF00A48F), livingAnim.value),
        )
        Box(Modifier.fillMaxWidth()
            .graphicsLayer { rotationX = tiltX.value; rotationY = tiltY.value; cameraDistance = 13f * density }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Release,
                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> tiltScope.launch {
                                tiltX.animateTo(0f, androidx.compose.animation.core.tween(350))
                                tiltY.animateTo(0f, androidx.compose.animation.core.tween(350))
                            }
                            else -> if (change.pressed && size.width > 0 && size.height > 0) {
                                val fx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val fy = (change.position.y / size.height).coerceIn(0f, 1f)
                                val angles = com.sadique.dailyledger.ui.tiltAngles(fx, fy)
                                tiltScope.launch {
                                    tiltX.animateTo(angles.first, androidx.compose.animation.core.tween(120))
                                    tiltY.animateTo(angles.second, androidx.compose.animation.core.tween(120))
                                }
                            }
                        }
                    }
                }
            }
            .background(Brush.linearGradient(gradColors)).padding(14.dp)) {
            WalletArtwork(Modifier.align(Alignment.TopEnd).size(48.dp).graphicsLayer { alpha = if (zeroBalance) 0.45f else 1f })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val animatedRatio = com.sadique.dailyledger.ui.animatedProgress(spentRatio)
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
                if (summary.remaining == 0L) Text("Balance khatam — jeb khali hai", color = Color.White, fontSize = 11.sp)
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
                            com.sadique.dailyledger.i18n.progressPercent((animatedRatio * 100).toInt()),
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
                    val fillWidthPx = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                    if (animatedRatio > 0f) Box(
                        Modifier.fillMaxWidth(animatedRatio.coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFFCDFFFF), Color(0xFF00C7E6))))
                            .onSizeChanged { fillWidthPx.value = it.width.toFloat() },
                    ) {
                        // Windows-copy style: the shine sweeps only across the filled part of the bar.
                        if (ambientAnimationsOn && fillWidthPx.value > 0f) Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.32f)
                                .graphicsLayer { translationX = barShineAnim.value * fillWidthPx.value }
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent))),
                        )
                    }
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
                // O (re-applied, test-safe): the spending guide folds away as the
                // dashboard scrolls, giving the list room. Height animation — NOT
                // AnimatedVisibility — so the guide nodes stay in the semantics
                // tree (BalanceGuideScreenTest asserts their text while scrolled;
                // AnimatedVisibility removed them in run #436 and broke it).
                val guideFullPx = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val guideAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
                val guidePrimed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(guideCollapsed, guideFullPx.intValue) {
                    if (guideFullPx.intValue > 0) {
                        if (!guidePrimed.value) { guideAnim.snapTo(guideFullPx.intValue.toFloat()); guidePrimed.value = true }
                        else guideAnim.animateTo(if (guideCollapsed) 0f else guideFullPx.intValue.toFloat(),
                            androidx.compose.animation.core.tween(260))
                    }
                }
                val guideDensity = androidx.compose.ui.platform.LocalDensity.current
                Box(
                    Modifier
                        .then(if (guidePrimed.value) Modifier.height(with(guideDensity) { guideAnim.value.toDp() }) else Modifier)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                        .graphicsLayer { alpha = if (guideFullPx.intValue > 0) (guideAnim.value / guideFullPx.intValue).coerceIn(0f, 1f) else 1f }
                ) {
                Column(Modifier.wrapContentHeight(unbounded = true).onSizeChanged { if (it.height > 0) guideFullPx.intValue = it.height },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }
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
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(130.dp)
                    .graphicsLayer { translationX = shineAnim.value * 1100f; rotationZ = 16f }
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent))))
            }
        }
    }
}
