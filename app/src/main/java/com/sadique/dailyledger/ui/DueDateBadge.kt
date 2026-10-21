package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Colored pill showing how close a due date is: red when overdue or due today,
 * warm when a week or less away, neutral otherwise. Renders nothing when the
 * date cannot be parsed.
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
    Surface(shape = RoundedCornerShape(999.dp), color = background, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = foreground, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
