package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.ui.*

@Composable
fun DashboardOrderEditor(display: DisplayPreferences) {
    val latestOrder by rememberUpdatedState(display.dashboardOrder)
    fun move(id: String, offset: Int) {
        val values = latestOrder.toMutableList(); val from = values.indexOf(id); val to = from + offset
        if (from >= 0 && to in values.indices) { values.add(to, values.removeAt(from)); display.updateDashboardOrder(values) }
    }
    Text("Dashboard order", style = MaterialTheme.typography.titleMedium)
    Text("Hold and drag a handle, or use the up/down buttons.", style = MaterialTheme.typography.bodySmall)
    display.dashboardOrder.forEach { id -> key(id) {
        var height by remember { mutableStateOf(1f) }
        var distance by remember { mutableStateOf(0f) }
        Row(Modifier.fillMaxWidth().onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }.testTag("dashboard-order-$id"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragHandle, "Drag ${dashboardSectionLabel(id)}", Modifier.size(48.dp).pointerInput(id) {
                detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }, onDragEnd = { distance = 0f }, onDragCancel = { distance = 0f }) { change, drag ->
                    change.consume(); distance += drag.y
                    if (distance > height * .7f) { move(id, 1); distance = 0f }
                    else if (distance < -height * .7f) { move(id, -1); distance = 0f }
                }
            })
            Text(dashboardSectionLabel(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { move(id, -1) }, enabled = display.dashboardOrder.first() != id) { Icon(Icons.Rounded.ArrowUpward, "Move up ${dashboardSectionLabel(id)}") }
            IconButton(onClick = { move(id, 1) }, enabled = display.dashboardOrder.last() != id) { Icon(Icons.Rounded.ArrowDownward, "Move down ${dashboardSectionLabel(id)}") }
        }
    } }
    TextButton(onClick = display::resetDashboardOrder) { Text("Reset card order") }
}
