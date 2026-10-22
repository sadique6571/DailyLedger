package com.sadique.dailyledger.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import java.time.LocalDate
import java.time.YearMonth

/** Compact amount for a calendar cell: 850 -> "850", 12_500 -> "12.5k" (input is minor units). */
private fun calendarAmount(minor: Long): String {
    val rupees = minor / 100.0
    return when {
        rupees >= 1000 -> {
            val k = rupees / 1000
            if (k % 1.0 == 0.0) "${k.toInt()}k" else "${(k * 10).toInt() / 10.0}k"
        }
        rupees % 1.0 == 0.0 -> rupees.toInt().toString()
        else -> rupees.toString()
    }
}

/**
 * Month calendar for Transactions (Round 2, #2): each day cell shows that day's total
 * expense (compact). Tapping a day hands it back so the list can show that day's entries.
 * Switching months slides the grid (Round 2 animations, #6).
 */
@Composable
fun MonthCalendarView(items: List<TransactionEntity>, onDayClick: (LocalDate) -> Unit) {
    val hidden = LocalDisplayPreferences.current?.privateAmounts == true
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = remember(monthText) { runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.now()) }
    val today = LocalDate.now()

    com.sadique.dailyledger.ui.LedgerRaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                Text(
                    "${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${month.year}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                    Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            AnimatedContent(targetState = monthText, transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(250)) { if (forward) it else -it } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it else it } + fadeOut(tween(200)))
            }, label = "calendar-month") { targetText ->
                val targetMonth = runCatching { YearMonth.parse(targetText) }.getOrDefault(month)
                CalendarWeeks(items = items, month = targetMonth, today = today, hidden = hidden, onDayClick = onDayClick)
            }
            Text("Tap a date to see that day's entries. Red number = total expense, green dot = income.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarWeeks(items: List<TransactionEntity>, month: YearMonth, today: LocalDate, hidden: Boolean, onDayClick: (LocalDate) -> Unit) {
    val monthText = month.toString()
    val byDay = remember(items, monthText) {
        items.filter { it.date.startsWith(monthText) }.groupBy { it.date }
    }
    val first = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val startOffset = first.dayOfWeek.value % 7 // Sunday-first grid
    val cells = startOffset + daysInMonth
    val weeks = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (dow in 0..6) {
                    val dayNum = week * 7 + dow - startOffset + 1
                    if (dayNum !in 1..daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.92f))
                    } else {
                        val date = month.atDay(dayNum)
                        val dayEntries = byDay[date.toString()].orEmpty()
                        val expense = dayEntries.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
                        val hasIncome = dayEntries.any { it.type == "INCOME" }
                        val isToday = date == today
                        Column(
                            Modifier.weight(1f).aspectRatio(0.92f)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .then(if (isToday) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
                                .clickable { onDayClick(date) }
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(dayNum.toString(), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isToday || dayEntries.isNotEmpty()) FontWeight.Bold else FontWeight.Normal)
                            if (hidden) {
                                if (dayEntries.isNotEmpty()) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                            } else if (expense > 0) {
                                Text(calendarAmount(expense), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                            }
                            if (hasIncome) Box(Modifier.size(5.dp).background(androidx.compose.ui.graphics.Color(0xFF16A34A), RoundedCornerShape(50)))
                        }
                    }
                }
            }
        }
    }
}
