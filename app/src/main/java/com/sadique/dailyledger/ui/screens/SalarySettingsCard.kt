package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.sadique.dailyledger.ai.DailySpendingGuide
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.LedgerTextField
import com.sadique.dailyledger.i18n.LocalizedText as Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalarySettingsCard(store: SalaryScheduleStore, transactions: List<TransactionEntity>) {
    val today = LocalDate.now()
    val plan = store.calendar
    val guide = DailySpendingGuide.calculate(transactions, SalarySummary(0, 0, 0), today,
        store.day, plan.expected, plan.lastRecorded)
    var choosing by remember { mutableStateOf<String?>(null) }
    SectionCard("Salary calendar", "Choose dates here; the Home screen keeps your countdown.") {
        Text("Select the salary date for this month. The same date next month is suggested automatically. Change the next expected date anytime for a 20-day or 40-day gap.", style = MaterialTheme.typography.bodySmall)
        SalaryCalendarField("Salary date", plan.reference ?: guide.lastSalaryDate, "salary-reference-date") { choosing = "REFERENCE" }
        SalaryCalendarField("Next expected salary", guide.expectedSalaryDate, "salary-expected-date") { choosing = "EXPECTED" }
        Text("Dates are estimates only. Record received salary separately. Shorter months use their last available day.", style = MaterialTheme.typography.bodySmall)
        if (plan.expected != null || store.day != null) TextButton(onClick = store::clearCalendar,
            modifier = Modifier.testTag("salary-calendar-auto")) { Text("Estimate from last salary") }
    }
    choosing?.let { kind ->
        val initial = if (kind == "REFERENCE") plan.reference ?: guide.lastSalaryDate ?: today
            else guide.expectedSalaryDate ?: today.plusMonths(1)
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { choosing = null },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (kind == "REFERENCE") store.selectReference(date, guide.lastSalaryDate)
                    else store.selectExpected(date, guide.lastSalaryDate)
                }
                choosing = null
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        ) { DatePicker(picker, modifier = Modifier.testTag("salary-calendar")) }
    }
}

@Composable
private fun SalaryCalendarField(label: String, date: LocalDate?, tag: String, open: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        LedgerTextField(date?.toString().orEmpty(), {}, readOnly = true,
            label = { Text(label) }, placeholder = { Text("Choose date") },
            trailingIcon = { IconButton(onClick = open) { Icon(Icons.Rounded.CalendarMonth, label) } },
            modifier = Modifier.fillMaxWidth())
        // The complete field is a calendar target; typing an unvalidated date is unnecessary.
        androidx.compose.material3.TextButton(onClick = open,
            modifier = Modifier.matchParentSize().semantics { contentDescription = label }.testTag(tag)) { }
    }
}
