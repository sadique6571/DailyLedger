package com.sadique.dailyledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
    QuickEntryAction("aichat", "AI Chat", Icons.Rounded.Chat),
    QuickEntryAction("expense", "Expense", Icons.Rounded.ShoppingCart),
    QuickEntryAction("salary", "Salary", Icons.Rounded.Payments),
    QuickEntryAction("kameti", "Kameti", Icons.Rounded.Groups),
    QuickEntryAction("credit", "Udhar", Icons.Rounded.Storefront),
)

/** The global shortcut belongs to Home; destination-specific Add buttons stay on their screens. */
@Composable
internal fun DashboardEntryDial(
    tab: Int,
    expandedState: MutableState<Boolean>,
    onBoundsChanged: (Rect) -> Unit = {},
    onSelect: (String) -> Unit,
) {
    LaunchedEffect(tab) { if (tab != NavigationHistory.HOME) expandedState.value = false }
    if (tab == NavigationHistory.HOME) QuickEntryDial(expandedState, onBoundsChanged, onSelect)
}

/** Five labelled actions retract into the stationary plus/minus toggle. */
@Composable
internal fun QuickEntryDial(
    expandedState: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
    onBoundsChanged: (Rect) -> Unit = {},
    onSelect: (String) -> Unit,
) {
    var expanded by expandedState
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(280), label = "entry-toggle")
    val ink = MaterialTheme.colorScheme.onPrimary
    BackHandler(enabled = expanded) { expanded = false }
    Row(Modifier.onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }.testTag("quick-entry-dial"),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(visible = expanded,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100, delayMillis = 420))) {
            Surface(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 128.dp, max = 164.dp).testTag("quick-entry-glass"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
                shadowElevation = 6.dp) {
                Column(Modifier.heightIn(max = 310.dp).verticalScroll(rememberScrollState())
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .25f), Color.Transparent)))
                    .padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    quickEntryActions.forEachIndexed { index, action ->
                        val interactions = remember { MutableInteractionSource() }
                        val pressed by interactions.collectIsPressedAsState()
                        val iconScale by animateFloatAsState(if (pressed) .86f else 1f, tween(100), label = "entry-press-${action.id}")
                        TextButton(onClick = { expanded = false; onSelect(action.id) },
                            enabled = expanded,
                            interactionSource = interactions,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .animateEnterExit(
                                    enter = slideInHorizontally(tween(220, delayMillis = index * 60)) { it } +
                                        fadeIn(tween(180, delayMillis = index * 60)),
                                    exit = slideOutHorizontally(tween(180, delayMillis = (quickEntryActions.lastIndex - index) * 60)) { it } +
                                        fadeOut(tween(140, delayMillis = (quickEntryActions.lastIndex - index) * 60)))
                                .testTag("quick-entry-${action.id}"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Icon(action.icon, null, Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(action.label, fontSize = 13.sp, maxLines = 1)
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
            Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = 180f * progress }) {
                val stroke = 2.5.dp.toPx()
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
