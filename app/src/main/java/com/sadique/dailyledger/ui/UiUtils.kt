package com.sadique.dailyledger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

fun money(minor: Long): String = displayMoney(minor)

/** Parses user text such as "1,250.50" into integer paisa. Returns null for invalid input. */
fun parseMinor(text: String): Long? = runCatching {
    text.replace(",", "").trim().toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}.getOrNull()

/** Paisa -> plain decimal string (never scientific notation), e.g. 1250050 -> "12500.50". */
fun plainAmount(minor: Long): String = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2).toPlainString()

fun isValidDate(text: String): Boolean = runCatching { LocalDate.parse(text.trim()) }.isSuccess
fun isValidMonth(text: String): Boolean = runCatching { YearMonth.parse(text.trim()) }.isSuccess

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f),
    highlightColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-shift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(0f, 0f),
        end = Offset(1000f * offset, 1000f),
    )
    Box(modifier = modifier.clip(shape).background(brush))
}
