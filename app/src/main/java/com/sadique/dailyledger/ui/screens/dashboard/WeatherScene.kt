package com.sadique.dailyledger.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ui.rememberAmbientAnimationsOn
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Sky scenes for the weather box: the picture follows the live condition. */
internal enum class WeatherSceneKind { CLEAR, PARTLY, CLOUDY, RAIN, STORM, SNOW, FOG }

internal fun weatherSceneKind(condition: String): WeatherSceneKind = when {
    condition.contains("storm", true) || condition.contains("hail", true) -> WeatherSceneKind.STORM
    condition.contains("snow", true) -> WeatherSceneKind.SNOW
    condition.contains("rain", true) || condition.contains("drizzle", true) ||
        condition.contains("shower", true) -> WeatherSceneKind.RAIN
    condition.contains("fog", true) -> WeatherSceneKind.FOG
    condition.contains("partly", true) -> WeatherSceneKind.PARTLY
    condition.contains("cloud", true) -> WeatherSceneKind.CLOUDY
    condition.contains("mostly", true) -> WeatherSceneKind.PARTLY
    else -> WeatherSceneKind.CLEAR
}

/** 0 = new moon, 0.5 = full moon. Good enough for a sky picture. */
private fun moonPhaseFraction(nowMillis: Long): Float {
    val days = nowMillis / 86_400_000.0
    val synodic = 29.53058867
    var age = (days - 10962.76) % synodic // reference new moon: 6 Jan 2000
    if (age < 0) age += synodic
    return (age / synodic).toFloat()
}

/**
 * Animated sky inside the weather box: a realistic sun by day and a real-phase
 * moon by night, with clouds, rain, storm lightning, snow or drifting fog to
 * match the reported condition. One 60-second master clock drives everything
 * with whole cycles per minute, so the loop restart is invisible. All motion
 * stops when the system animator scale is off (as in UI tests).
 */
@Composable
internal fun WeatherScene(condition: String, isDay: Boolean, modifier: Modifier = Modifier) {
    val kind = remember(condition) { weatherSceneKind(condition) }
    val ambientOn = rememberAmbientAnimationsOn()
    val clock = remember { Animatable(0f) }
    LaunchedEffect(ambientOn) {
        if (ambientOn) while (true) {
            clock.animateTo(60f, tween(60_000, easing = LinearEasing))
            clock.snapTo(0f)
        }
    }
    val phase = remember { moonPhaseFraction(System.currentTimeMillis()) }
    // Fixed star field (night scenes).
    val stars = remember {
        val rnd = kotlin.random.Random(20261022)
        List(16) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.75f, rnd.nextFloat()) }
    }
    val sky = when (kind) {
        WeatherSceneKind.STORM -> if (isDay) listOf(Color(0xFF46525F), Color(0xFF77828D))
            else listOf(Color(0xFF141A24), Color(0xFF2C3644))
        WeatherSceneKind.FOG -> if (isDay) listOf(Color(0xFF8E9A9B), Color(0xFFC9C9BC))
            else listOf(Color(0xFF1D2126), Color(0xFF383E44))
        WeatherSceneKind.RAIN, WeatherSceneKind.SNOW -> if (isDay) listOf(Color(0xFF5B7E9C), Color(0xFFA8C3D6))
            else listOf(Color(0xFF0D1524), Color(0xFF27354A))
        WeatherSceneKind.CLOUDY -> if (isDay) listOf(Color(0xFF6E97B5), Color(0xFFC2D5E2))
            else listOf(Color(0xFF0C1226), Color(0xFF2E3A55))
        else -> if (isDay) listOf(Color(0xFF2F7FC4), Color(0xFFB8E2F5))
            else listOf(Color(0xFF070D29), Color(0xFF27335E))
    }
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) {
        val w = size.width
        val h = size.height
        val t = clock.value // seconds, 0..60
        drawRect(Brush.verticalGradient(sky))

        fun cloud(cx: Float, cy: Float, scale: Float, color: Color) {
            val r = 9.dp.toPx() * scale
            drawCircle(color, r * 0.72f, Offset(cx - r * 0.95f, cy + r * 0.28f))
            drawCircle(color, r * 0.95f, Offset(cx - r * 0.25f, cy - r * 0.22f))
            drawCircle(color, r * 0.78f, Offset(cx + r * 0.55f, cy + r * 0.10f))
            drawCircle(color, r * 0.60f, Offset(cx + r * 1.15f, cy + r * 0.36f))
            drawRoundRect(color, Offset(cx - r * 1.62f, cy + r * 0.18f),
                Size(r * 3.3f, r * 0.72f), CornerRadius(r * 0.36f, r * 0.36f))
        }
        fun drift(periodSec: Float, offset: Float): Float {
            val f = ((t / periodSec + offset) % 1.35f + 1.35f) % 1.35f
            return -0.18f + f // fraction of width, enters left, leaves right
        }

        // Twinkling stars on clear/partly night skies.
        if (!isDay && (kind == WeatherSceneKind.CLEAR || kind == WeatherSceneKind.PARTLY)) {
            stars.forEachIndexed { i, (sx, sy, seed) ->
                val tw = 0.35f + 0.65f * abs(sin(t * 1.05f + seed * 12f + i))
                drawCircle(Color.White.copy(alpha = 0.75f * tw), (1.0f + seed) * 1.1.dp.toPx() * 0.62f,
                    Offset(sx * w, sy * h))
            }
        }

        val sunX = w * 0.30f
        val sunY = h * 0.46f
        val sunR = 10.5.dp.toPx()
        fun drawSun(dim: Float = 1f) {
            val pulse = 1f + 0.05f * sin(t * 2.094f) // 3 s pulse, 20 cycles/min
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFC93C).copy(alpha = 0.55f * dim), Color.Transparent),
                    center = Offset(sunX, sunY), radius = sunR * 2.3f * pulse,
                ), sunR * 2.3f * pulse, Offset(sunX, sunY),
            )
            val rot = t * 6f // one slow turn per minute
            for (i in 0 until 8) {
                val ang = Math.toRadians((rot + i * 45f).toDouble())
                val inner = sunR * 1.22f
                val outer = sunR * (1.55f + 0.18f * sin(t * 2.094f + i * 1.7f))
                drawLine(Color(0xFFFFB300).copy(alpha = 0.85f * dim),
                    Offset(sunX + (inner * cos(ang)).toFloat(), sunY + (inner * sin(ang)).toFloat()),
                    Offset(sunX + (outer * cos(ang)).toFloat(), sunY + (outer * sin(ang)).toFloat()),
                    strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFFA000)),
                    center = Offset(sunX - sunR * 0.25f, sunY - sunR * 0.25f), radius = sunR * 1.5f,
                ), sunR, Offset(sunX, sunY),
            )
        }
        fun drawMoon(dim: Float = 1f) {
            val mx = w * 0.30f
            val my = h * 0.44f
            val r = 10.dp.toPx()
            drawCircle(Color(0xFFDCE9F7).copy(alpha = 0.28f * dim), r * 1.7f, Offset(mx, my))
            val moonPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(mx - r, my - r, mx + r, my + r)) }
            clipPath(moonPath) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFE3EAF1), Color(0xFFB9C6D2)),
                        center = Offset(mx - r * 0.3f, my - r * 0.3f), radius = r * 1.6f,
                    ), r, Offset(mx, my),
                )
                // Craters.
                val crater = Color(0xFF8FA3B5).copy(alpha = 0.28f * dim)
                drawCircle(crater, r * 0.20f, Offset(mx - r * 0.32f, my - r * 0.18f))
                drawCircle(crater, r * 0.14f, Offset(mx + r * 0.28f, my + r * 0.30f))
                drawCircle(crater, r * 0.11f, Offset(mx + r * 0.05f, my - r * 0.48f))
                drawCircle(crater, r * 0.09f, Offset(mx - r * 0.05f, my + r * 0.52f))
                // Phase shadow: dark side + terminator ellipse.
                val dark = Color(0xFF0A1122).copy(alpha = 0.90f * dim)
                val lit = Color(0xFFE9EFF5).copy(alpha = dim)
                val p = phase
                val rx = r * abs(cos(2 * Math.PI * p).toFloat())
                if (p < 0.5f) { // waxing: light grows from the right
                    drawArc(dark, 90f, 180f, true, Offset(mx - r, my - r), Size(2 * r, 2 * r))
                    if (p < 0.25f) drawOval(dark, Offset(mx - rx, my - r), Size(2 * rx, 2 * r))
                    else drawOval(lit, Offset(mx - rx, my - r), Size(2 * rx, 2 * r))
                } else { // waning: light shrinks from the right
                    drawArc(dark, 270f, 180f, true, Offset(mx - r, my - r), Size(2 * r, 2 * r))
                    if (p < 0.75f) drawOval(lit, Offset(mx - rx, my - r), Size(2 * rx, 2 * r))
                    else drawOval(dark, Offset(mx - rx, my - r), Size(2 * rx, 2 * r))
                }
            }
        }

        val cloudLight = if (isDay) Color.White.copy(alpha = 0.95f) else Color(0xFF9FB2C8).copy(alpha = 0.9f)
        val cloudDark = if (isDay) Color(0xFF5C6B78) else Color(0xFF39434F)
        when (kind) {
            WeatherSceneKind.CLEAR -> if (isDay) drawSun() else drawMoon()
            WeatherSceneKind.PARTLY -> {
                if (isDay) drawSun() else drawMoon()
                cloud(drift(30f, 0.15f) * w, h * 0.62f, 0.95f, cloudLight)
            }
            WeatherSceneKind.CLOUDY -> {
                cloud(drift(40f, 0.55f) * w, h * 0.30f, 1.05f, cloudLight.copy(alpha = 0.75f))
                cloud(drift(24f, 0.05f) * w, h * 0.58f, 1.25f, cloudLight)
            }
            WeatherSceneKind.FOG -> {
                if (isDay) drawSun(0.30f) else drawMoon(0.30f)
                for (i in 0..2) {
                    val fy = h * (0.34f + i * 0.22f)
                    val fx = sin(t * 0.42f + i * 2.1f) * w * 0.05f
                    drawRoundRect(Color.White.copy(alpha = 0.34f), Offset(w * 0.06f + fx, fy),
                        Size(w * 0.82f, 5.5.dp.toPx()), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                }
            }
            WeatherSceneKind.RAIN, WeatherSceneKind.STORM, WeatherSceneKind.SNOW -> {
                val heavy = kind == WeatherSceneKind.STORM
                cloud(drift(48f, 0.35f) * w, h * 0.26f, 1.15f, if (heavy) cloudDark else cloudLight.copy(alpha = 0.8f))
                cloud(w * 0.52f, h * 0.34f, 1.35f, if (heavy) cloudDark else cloudLight)
                if (kind == WeatherSceneKind.RAIN || heavy) {
                    val drops = if (heavy) 16 else 12
                    for (i in 0 until drops) {
                        val fx = 0.05f + (i * 0.79f) % 0.9f
                        val fy = ((t / 0.9f + i * 0.37f) % 1f)
                        val x = fx * w
                        val y = (fy * 1.25f - 0.15f) * h
                        drawLine(Color(0xFFBFE0FA).copy(alpha = 0.85f),
                            Offset(x, y), Offset(x - 2.dp.toPx(), y + 6.5.dp.toPx()),
                            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                if (kind == WeatherSceneKind.SNOW) {
                    for (i in 0 until 13) {
                        val fx = 0.04f + (i * 0.61f) % 0.92f
                        val fy = ((t / 3.6f + i * 0.29f) % 1f)
                        val sway = sin(t * 1.4f + i * 1.9f) * 3.dp.toPx()
                        drawCircle(Color.White.copy(alpha = 0.92f), 1.6.dp.toPx(),
                            Offset(fx * w + sway, (fy * 1.2f - 0.1f) * h))
                    }
                }
                if (heavy) {
                    val cyc = t % 6f
                    if (cyc < 0.10f || (cyc > 3.2f && cyc < 3.30f)) {
                        drawRect(Color.White.copy(alpha = 0.16f))
                        val bx = w * 0.56f
                        val bolt = Path().apply {
                            moveTo(bx, h * 0.44f)
                            lineTo(bx - w * 0.055f, h * 0.70f)
                            lineTo(bx - w * 0.005f, h * 0.68f)
                            lineTo(bx - w * 0.045f, h * 0.94f)
                            lineTo(bx + w * 0.05f, h * 0.63f)
                            lineTo(bx + w * 0.002f, h * 0.65f)
                            close()
                        }
                        drawPath(bolt, Color(0xFFFFEB3B))
                    }
                }
            }
        }
    }
}
