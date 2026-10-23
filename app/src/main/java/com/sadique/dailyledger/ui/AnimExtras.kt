package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One-shot celebration shown when a new entry lands (A + D): a gold coin drops
 * in with a bounce, then a green ring draws itself with a tick inside.
 * Render it conditionally; it plays once on appearance and then rests.
 */
@Composable
fun EntrySavedCelebration(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1_000)) }
    val green = Color(0xFF1B9E57)
    val gold = Color(0xFFF5B921)
    Canvas(modifier) {
        val p = progress.value
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringR = size.minDimension * 0.34f
        // Coin: falls from above with a small bounce, fades as the ring takes over.
        if (p < 0.62f) {
            val fall = (p / 0.45f).coerceIn(0f, 1f)
            val bounce = if (fall < 1f) 1f else 1f - 0.10f * sin((p - 0.45f) / 0.17f * PI.toFloat()).coerceIn(0f, 1f)
            val coinY = cy - ringR * 2.1f + (ringR * 2.1f) * fall * bounce
            val coinAlpha = if (p < 0.45f) 1f else 1f - (p - 0.45f) / 0.17f
            val coinR = ringR * 0.52f
            drawCircle(gold.copy(alpha = 0.30f * coinAlpha), coinR * 1.35f, Offset(cx, coinY))
            drawCircle(gold.copy(alpha = coinAlpha), coinR, Offset(cx, coinY))
            drawCircle(Color(0xFFFFD964).copy(alpha = coinAlpha), coinR * 0.78f, Offset(cx, coinY))
            drawCircle(Color(0xFF9A6A00).copy(alpha = coinAlpha), coinR * 0.16f, Offset(cx, coinY))
        }
        // Ring + tick draw in after the coin lands.
        if (p > 0.42f) {
            val rp = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
            drawCircle(green.copy(alpha = 0.14f), ringR * 1.18f, Offset(cx, cy))
            drawArc(green, -90f, 360f * rp.coerceAtMost(0.999f), false,
                Offset(cx - ringR, cy - ringR),
                androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (rp > 0.45f) {
                val tp = ((rp - 0.45f) / 0.55f).coerceIn(0f, 1f)
                val tick = Path().apply {
                    moveTo(cx - ringR * 0.52f, cy + ringR * 0.02f)
                    lineTo(cx - ringR * 0.16f, cy + ringR * 0.40f)
                    lineTo(cx + ringR * 0.58f, cy - ringR * 0.42f)
                }
                val partial = Path()
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(tick, false) }
                measure.getSegment(0f, measure.length * tp, partial, true)
                drawPath(partial, green, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * Rotated "PAID" stamp that slams onto a card after a payment is recorded (J).
 * Plays its slam once whenever [visible] turns true.
 */
@Composable
fun PaidStamp(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val scale = remember { Animatable(2.1f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(90))
        scale.animateTo(1f, keyframes {
            durationMillis = 300
            2.1f at 0
            0.92f at 210
            1.04f at 255
            1f at 300
        })
    }
    val green = Color(0xFF1B9E57)
    Box(modifier.graphicsLayer {
        scaleX = scale.value; scaleY = scale.value
        rotationZ = -14f; this.alpha = alpha.value
    }, contentAlignment = Alignment.Center) {
        Text("PAID ✓", color = green, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.border(3.dp, green, RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp))
    }
}

/** Loading placeholder with a soft light sweep (B). Sweep follows the ambient gate. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val ambientOn = rememberAmbientAnimationsOn()
    val sweep = remember { Animatable(-0.6f) }
    LaunchedEffect(ambientOn) {
        if (ambientOn) while (true) {
            sweep.animateTo(1.6f, tween(1_300, easing = LinearEasing))
            sweep.snapTo(-0.6f)
        }
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))) {
        val wPx = constraints.maxWidth.toFloat()
        Box(Modifier.size(maxWidth, maxHeight).graphicsLayer { translationX = sweep.value * wPx }
            .background(Brush.horizontalGradient(listOf(
                Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent))))
    }
}

/** Horizontal shake whenever [trigger] changes to a new non-zero value (P). */
@Composable
fun Modifier.animatedShake(trigger: Int): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            anim.snapTo(0f)
            anim.animateTo(0f, keyframes {
                durationMillis = 400
                0f at 0; -11f at 60; 11f at 130; -7f at 200; 7f at 270; -3f at 330; 0f at 400
            })
        }
    }
    return this.then(Modifier.offset { IntOffset(anim.value.roundToInt(), 0) })
}

/**
 * Snackbar with a shrinking ring that shows how much of the Undo window
 * is left (Q). Duration mirrors the snackbar's own duration.
 */
@Composable
fun UndoCountdownSnackbar(data: SnackbarData) {
    val durationMs = when (data.visuals.duration) {
        SnackbarDuration.Short -> 4_000L
        SnackbarDuration.Long -> 10_000L
        SnackbarDuration.Indefinite -> 0L
    }
    val ring = remember(data) { Animatable(1f) }
    LaunchedEffect(data) {
        if (durationMs > 0) {
            ring.snapTo(1f)
            ring.animateTo(0f, tween(durationMs.toInt(), easing = LinearEasing))
        }
    }
    Snackbar(
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) { Text(label) }
            }
        },
        dismissAction = {
            if (data.visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) { Icon(Icons.Rounded.Close, null) }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (durationMs > 0) {
                Canvas(Modifier.size(18.dp)) {
                    val w = 2.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.30f), 0f, 360f, false,
                        Offset(w / 2, w / 2),
                        androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        style = Stroke(w))
                    drawArc(Color.White, -90f, 360f * ring.value, false,
                        Offset(w / 2, w / 2),
                        androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round))
                }
                Box(Modifier.size(8.dp))
            }
            Text(data.visuals.message)
        }
    }
}

/** Helper for callers that tilt cards (F): rotation from a touch point. */
fun tiltAngles(fractionX: Float, fractionY: Float, maxDegrees: Float = 9f): Pair<Float, Float> {
    val ry = ((fractionX - 0.5f) * 2f * maxDegrees).coerceIn(-maxDegrees, maxDegrees)
    val rx = ((0.5f - fractionY) * 2f * maxDegrees).coerceIn(-maxDegrees, maxDegrees)
    return rx to ry
}
