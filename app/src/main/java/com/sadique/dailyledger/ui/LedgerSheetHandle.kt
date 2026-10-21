package com.sadique.dailyledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A deliberate handle gesture changes size; scrolling the form never does. */
@Composable
internal fun LedgerSheetHandle(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val latestChange by rememberUpdatedState(onExpandedChange)
    val threshold = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(36.dp).testTag("entry-sheet-handle")
        .pointerInput(expanded, threshold) {
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onDragEnd = {
                    if (distance < -threshold) latestChange(true)
                    else if (distance > threshold) latestChange(false)
                },
                onVerticalDrag = { change, delta -> change.consume(); distance += delta },
            )
        }.clickable(role = Role.Button, onClickLabel = if (expanded) "Reduce form" else "Expand form") {
            latestChange(!expanded)
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(38.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f), CircleShape))
    }
}
