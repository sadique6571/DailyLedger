@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text

@Composable
internal fun SalaryDayDialog(day: Int?, dismiss: () -> Unit, save: (Int?) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(day ?: 0) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Set salary day") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose your monthly salary day")
                Text("For shorter months, the last available day is used. This changes the estimate only; record received salary separately.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..31).forEach { value ->
                        FilterChip(selected == value, { selected = value },
                            label = { Text(value.toString(), translate = false) }, modifier = Modifier.testTag("salary-day-$value"))
                    }
                }
                FilterChip(selected == 0, { selected = 0 }, label = { Text("Estimate from last salary") }, modifier = Modifier.testTag("salary-day-auto"))
            }
        },
        confirmButton = { Button(onClick = { save(selected.takeIf { it != 0 }) }, modifier = Modifier.testTag("save-salary-day")) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
