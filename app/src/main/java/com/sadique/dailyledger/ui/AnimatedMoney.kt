package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Money amount that counts up/down to its value when it changes (Round 2, #6).
 * [format] decides how a value is displayed, so privacy formatting stays with the caller.
 * Animation extras (2026-10-22): the number rolls like an odometer while it counts (C),
 * and flashes green when the value rises or soft red when it falls (V).
 */
@Composable
fun AnimatedMoneyText(
    amount: Long,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
) {
    var displayed by remember { mutableLongStateOf(0L) }
    val progress = remember { Animatable(1f) }
    var prevAmount by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var flashUp by remember { mutableStateOf(true) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(amount) {
        val from = displayed
        val isFirst = prevAmount == Long.MIN_VALUE
        if (!isFirst && amount != prevAmount) {
            flashUp = amount > prevAmount
            launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(850))
            }
        }
        prevAmount = amount
        if (from == amount) { displayed = amount; return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650)) {
            displayed = from + ((amount - from) * value).toLong()
        }
        displayed = amount
    }
    val baseColor = if (color == Color.Unspecified) LocalContentColor.current else color
    val flashColor = if (flashUp) Color(0xFF2FBF71) else Color(0xFFE5534B)
    val shownColor = if (flash.value > 0f) lerp(flashColor, baseColor, 1f - flash.value * 0.55f) else color
    Text(format(displayed), modifier = modifier.graphicsLayer {
        // Odometer-style roll: a small vertical churn while the digits settle.
        translationY = sin(progress.value * PI.toFloat()) * -4.5f
    }, color = shownColor, style = style, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
