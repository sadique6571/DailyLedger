package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-shot confetti burst (Round 3): plays the first time [active] becomes true
 * (loan cleared, kameti completed, savings goal reached). Particles are
 * deterministic per index so every burst looks the same cheerful way.
 */
@Composable
fun CelebrationBurst(active: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active && !played) {
            played = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(950))
        }
    }
    if (!played || progress.value >= 1f) return
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFFF59E0B),
        Color(0xFF16A34A),
        Color(0xFFEC4899),
    )
    val p = progress.value
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxDist = size.minDimension * 0.62f
        for (i in 0 until 22) {
            val angle = (i * 137.5 % 360.0) * (Math.PI / 180.0)
            val distFactor = 0.55f + ((i * 53) % 45) / 100f
            val dist = maxDist * distFactor * (1f - (1f - p) * (1f - p))
            val x = cx + (cos(angle) * dist).toFloat()
            val y = cy + (sin(angle) * dist).toFloat() + p * p * 34f
            val radius = (if (i % 3 == 0) 5.5f else 4f) * (1f - p * 0.5f)
            drawCircle(palette[i % palette.size].copy(alpha = (1f - p).coerceIn(0f, 1f)), radius, Offset(x, y))
        }
    }
}
