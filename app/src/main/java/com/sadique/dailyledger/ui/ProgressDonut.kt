package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text

/**
 * Animated progress ring with a center label (Round 2, user request).
 * Used for the savings goal, udhar paid share, kameti and loan progress.
 */
@Composable
fun ProgressDonut(
    progress: Float,
    centerLabel: String = "",
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 96.dp,
    strokeWidth: Dp = 12.dp,
    subLabel: String? = null,
    centerPercent: Int? = null,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, tween(700), label = "progress-donut")
    val track = MaterialTheme.colorScheme.surfaceVariant
    // A soft glint travels around the ring continuously (off where animations are disabled).
    val ambientOn = rememberAmbientAnimationsOn()
    val glintAnim = remember { Animatable(0f) }
    LaunchedEffect(ambientOn) {
        if (ambientOn) while (true) {
            glintAnim.animateTo(360f, tween(6_500, easing = LinearEasing))
            glintAnim.snapTo(0f)
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = strokeWidth.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            drawArc(Color.White.copy(alpha = 0.30f), glintAnim.value - 90f, 34f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (centerPercent != null) "${(animated * 100).toInt()}%" else centerLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subLabel != null) Text(subLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
