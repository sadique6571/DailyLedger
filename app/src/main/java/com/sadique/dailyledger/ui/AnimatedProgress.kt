package com.sadique.dailyledger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Smoothly animated version of a progress fraction (Round 2 follow-up):
 * feed the result into a progress bar and its percentage label so both move together.
 */
@Composable
fun animatedProgress(target: Float): Float {
    val animated by animateFloatAsState(target, tween(800), label = "progress")
    return animated
}
