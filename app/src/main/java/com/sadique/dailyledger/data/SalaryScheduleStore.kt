package com.sadique.dailyledger.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.time.LocalDate

data class SalaryCalendarPlan(val reference: LocalDate?, val expected: LocalDate?, val lastRecorded: LocalDate?)

/** Account-scoped local planning preference; selecting a day never creates a salary entry. */
class SalaryScheduleStore(context: Context, ownerId: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val prefs = context.applicationContext.getSharedPreferences("salary_schedule_$key", Context.MODE_PRIVATE)
    var day: Int? by mutableStateOf(prefs.getInt("day", 0).takeIf { it in 1..31 })
        private set

    private fun readDate(key: String) = prefs.getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var calendar by mutableStateOf(SalaryCalendarPlan(readDate("reference"), readDate("expected"), readDate("lastRecorded")))
        private set

    fun selectReference(date: LocalDate, lastRecorded: LocalDate?) = saveCalendar(SalaryCalendarPlan(date, date.plusMonths(1), lastRecorded))
    fun selectExpected(date: LocalDate, lastRecorded: LocalDate?) = saveCalendar(calendar.copy(expected = date, lastRecorded = lastRecorded))
    fun clearCalendar() = saveCalendar(SalaryCalendarPlan(null, null, null))

    private fun saveCalendar(value: SalaryCalendarPlan) {
        prefs.edit().putString("reference", value.reference?.toString()).putString("expected", value.expected?.toString())
            .putString("lastRecorded", value.lastRecorded?.toString()).remove("day").apply()
        day = null
        calendar = value
    }

    fun updateDay(value: Int?) {
        require(value == null || value in 1..31)
        prefs.edit().putInt("day", value ?: 0).apply()
        day = value
    }
}
