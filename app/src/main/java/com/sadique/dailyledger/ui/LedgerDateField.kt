package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Editable ISO date with the same input treatment and themed calendar in every entry form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Entry date",
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    enabled: Boolean = true,
    calendarTag: String = "date-calendar-$label",
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var calendarOpen by remember { mutableStateOf(false) }
    val invalid = !(optional && value.isBlank()) && !isValidDate(value)
    LedgerTextField(value, onValueChange, modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = true, isError = invalid,
        placeholder = { Text("YYYY-MM-DD", translate = false) },
        supportingText = { Text(if (invalid) "Use a valid YYYY-MM-DD date" else if (optional) "Optional • YYYY-MM-DD" else "YYYY-MM-DD") },
        trailingIcon = {
            IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); calendarOpen = true }, enabled = enabled, modifier = Modifier.testTag(calendarTag)) {
                Icon(Icons.Rounded.CalendarMonth, com.sadique.dailyledger.i18n.LocalStrings.current.text("Choose date from calendar"))
            }
        },
    )
    if (calendarOpen) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendarOpen = false },
            confirmButton = { TextButton(enabled = enabled && picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let {
                    onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                calendarOpen = false
            }) { Text("Apply date") } },
            dismissButton = { TextButton(modifier = Modifier.testTag("$calendarTag-cancel"), onClick = { calendarOpen = false }) { Text("Cancel") } },
        ) {
            DatePicker(picker, title = { Text(label, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                color = MaterialTheme.colorScheme.primary) }, colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                headlineContentColor = MaterialTheme.colorScheme.onSurface,
            ))
        }
    }
}
