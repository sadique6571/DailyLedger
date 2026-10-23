package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Colored pill showing how close a due date is: red when overdue or due today,
 * warm when a week or less away, neutral otherwise. Renders nothing when the
 * date cannot be parsed. A little bell swings on urgent badges (Y).
 */
@Composable
fun DueDateBadge(dueDate: String, modifier: Modifier = Modifier) {
    val days = remember(dueDate) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dueDate)) }.getOrNull()
    } ?: return
    val label: String
    val background: Color
    val foreground: Color
    when {
        days < 0 -> {
            label = if (days == -1L) "1 day overdue" else "${-days} days overdue"
            background = MaterialTheme.colorScheme.errorContainer
            foreground = MaterialTheme.colorScheme.onErrorContainer
        }
        days == 0L -> {
            label = "Due today"
            background = MaterialTheme.colorScheme.errorContainer
            foreground = MaterialTheme.colorScheme.onErrorContainer
        }
        days <= 7 -> {
            label = if (days == 1L) "1 day left" else "$days days left"
            background = MaterialTheme.colorScheme.tertiaryContainer
            foreground = MaterialTheme.colorScheme.onTertiaryContainer
        }
        else -> {
            label = "$days days left"
            background = MaterialTheme.colorScheme.surfaceContainerHigh
            foreground = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val urgent = days <= 7
    val ambientOn = rememberAmbientAnimationsOn()
    val swing = remember { Animatable(0f) }
    LaunchedEffect(ambientOn, urgent) {
        if (ambientOn && urgent) while (true) {
            swing.animateTo(0f, keyframes {
                durationMillis = 1700
                0f at 0; 17f at 180; -13f at 380; 8f at 560; -5f at 730; 0f at 900; 0f at 1700
            })
        }
    }
    Surface(shape = RoundedCornerShape(999.dp), color = background, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (urgent) Icon(
                Icons.Rounded.Notifications, null,
                Modifier.size(11.dp).graphicsLayer {
                    rotationZ = swing.value
                    transformOrigin = TransformOrigin(0.5f, 0.15f)
                },
                tint = foreground,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = foreground)
        }
    }
}
