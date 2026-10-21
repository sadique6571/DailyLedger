package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.sadique.dailyledger.i18n.LocalizedText as Text

/**
 * Money amount that counts up/down to its value when it changes (Round 2, #6).
 * [format] decides how a value is displayed, so privacy formatting stays with the caller.
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
    LaunchedEffect(amount) {
        val from = displayed
        if (from == amount) { displayed = amount; return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650)) {
            displayed = from + ((amount - from) * value).toLong()
        }
        displayed = amount
    }
    Text(format(displayed), modifier = modifier, color = color, style = style, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
