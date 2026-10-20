package com.sadique.dailyledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.i18n.LocalizedText as Text

internal data class QuickEntryAction(val id: String, val label: String, val icon: ImageVector)
internal val quickEntryActions = listOf(
    QuickEntryAction("ai", "AI", Icons.Rounded.AutoAwesome),
    QuickEntryAction("expense", "Expense", Icons.Rounded.ShoppingCart),
    QuickEntryAction("salary", "Salary", Icons.Rounded.Payments),
    QuickEntryAction("kameti", "Kameti", Icons.Rounded.Groups),
    QuickEntryAction("credit", "Udhar", Icons.Rounded.Storefront),
)

/** Five labelled actions retract into the stationary plus/minus toggle. */
@Composable
internal fun QuickEntryDial(onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(280), label = "entry-toggle")
    val ink = MaterialTheme.colorScheme.onPrimary
    BackHandler(enabled = expanded) { expanded = false }
    Row(Modifier.fillMaxWidth().widthIn(max = 440.dp).testTag("quick-entry-dial"),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(visible = expanded,
                enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(180))) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    quickEntryActions.forEach { action ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(onClick = { expanded = false; onSelect(action.id) },
                                modifier = Modifier.size(43.dp).testTag("quick-entry-${action.id}"),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                                Icon(action.icon, action.label, Modifier.size(23.dp))
                            }
                            Text(action.label, fontSize = 10.sp, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { expanded = !expanded },
            modifier = Modifier.size(58.dp).testTag("floating-add-expense").semantics {
                contentDescription = if (expanded) "Close entry menu" else "Add entry"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }, containerColor = MaterialTheme.colorScheme.primary, contentColor = ink) {
            Canvas(Modifier.size(29.dp).graphicsLayer { rotationZ = 180f * progress }) {
                val stroke = 3.dp.toPx()
                drawLine(ink, Offset(0f,size.height/2), Offset(size.width,size.height/2), stroke, StrokeCap.Round)
                drawLine(ink.copy(alpha = 1f-progress), Offset(size.width/2,0f), Offset(size.width/2,size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun EntryRecordPicker(title: String, choices: List<Pair<String, () -> Unit>>, dismiss: () -> Unit) {
    LedgerEntrySheet(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (choices.isEmpty()) Text("No eligible records. Add a record first.")
            choices.forEach { (label, select) ->
                FilledTonalButton(onClick = select, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
