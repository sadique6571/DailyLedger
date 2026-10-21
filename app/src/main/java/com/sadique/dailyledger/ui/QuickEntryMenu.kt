package com.sadique.dailyledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    Row(Modifier.testTag("quick-entry-dial"),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(visible = expanded,
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))) {
            Surface(modifier = Modifier.widthIn(max = 220.dp).testTag("quick-entry-glass"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
                shadowElevation = 6.dp) {
                Column(Modifier.heightIn(max = 310.dp).verticalScroll(rememberScrollState())
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .25f), Color.Transparent)))
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    quickEntryActions.forEach { action ->
                        TextButton(onClick = { expanded = false; onSelect(action.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("quick-entry-${action.id}"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Icon(action.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(action.label, fontSize = 13.sp, maxLines = 1)
                            Spacer(Modifier.weight(1f))
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
