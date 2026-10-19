package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ai.DailySpendingGuide
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.LedgerCard
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalarySettingsCard(store: SalaryScheduleStore, transactions: List<TransactionEntity>) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val plan = store.calendar
    val guide = DailySpendingGuide.calculate(transactions, SalarySummary(0, 0, 0), today,
        store.day, plan.expected, plan.lastRecorded)
    var choosing by remember { mutableStateOf<String?>(null) }
    SectionCard("Salary calendar", "Choose dates here; the Home screen keeps your countdown.") {
        Text("Select the salary date for this month. The same date next month is suggested automatically. Change the next expected date anytime for a 20-day or 40-day gap.", style = MaterialTheme.typography.bodySmall)
        SalaryCalendarField("Salary date", plan.reference ?: guide.lastSalaryDate, "salary-reference-date") { error = null; choosing = "REFERENCE" }
        SalaryCalendarField("Next expected salary", guide.expectedSalaryDate, "salary-expected-date") { error = null; choosing = "EXPECTED" }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Dates are estimates only. Record received salary separately. Shorter months use their last available day.", style = MaterialTheme.typography.bodySmall)
        if (plan.expected != null || store.day != null) TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            error = null
            try { withContext(Dispatchers.IO) { store.clearCalendar() } }
            catch (e: Exception) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; error = e.message }
            finally { busy = false }
        } },
            modifier = Modifier.testTag("salary-calendar-auto")) { Text("Estimate from last salary") }
    }
    choosing?.let { kind ->
        val initial = if (kind == "REFERENCE") plan.reference ?: guide.lastSalaryDate ?: today
            else guide.expectedSalaryDate ?: today.plusMonths(1)
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { choosing = null },
            confirmButton = { TextButton(enabled = !busy && picker.selectedDateMillis != null, onClick = {
                scope.launch { busy = true; try {
                picker.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    withContext(Dispatchers.IO) {
                        if (kind == "REFERENCE") store.selectReference(date, guide.lastSalaryDate)
                        else store.selectExpected(date, guide.lastSalaryDate)
                    }
                }
                choosing = null
                } catch (e: Exception) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; error = e.message } finally { busy = false } }
            }) { Text(if (busy) "Saving…" else "Save") } },
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        ) {
            DatePicker(picker, modifier = Modifier.testTag("salary-calendar"),
                title = { Text(if (kind == "REFERENCE") "Select salary date" else "Select next expected salary date",
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    color = MaterialTheme.colorScheme.primary) },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun SalaryCalendarField(label: String, date: LocalDate?, tag: String, open: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = open)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(date?.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())) ?: "Choose date",
                    translate = date == null, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
