package com.sadique.dailyledger.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.security.MessageDigest
import java.time.LocalDate

data class SalaryCalendarPlan(val reference: LocalDate?, val expected: LocalDate?, val lastRecorded: LocalDate?)

/** Account-scoped local planning preference; selecting a day never creates a salary entry. */
class SalaryScheduleStore(private val context: Context, private val ownerId: String, scope: kotlinx.coroutines.CoroutineScope? = null) {
    private val repo = LedgerRepository(AppDatabase.get(context), ownerId)
    private val initial = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { runCatching { repo.profileSnapshot() }.getOrDefault(ProfileBackupData()) }
    private val key = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val prefs = context.applicationContext.getSharedPreferences("salary_schedule_$key", Context.MODE_PRIVATE)
    var day: Int? by mutableStateOf(initial.salaryDay)
        private set

    private fun readDate(key: String) = prefs.getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var calendar by mutableStateOf(SalaryCalendarPlan(initial.reference?.let(LocalDate::parse), initial.expected?.let(LocalDate::parse), initial.lastRecorded?.let(LocalDate::parse)))
        private set

    fun selectReference(date: LocalDate, lastRecorded: LocalDate?) = saveCalendar(SalaryCalendarPlan(date, date.plusMonths(1), lastRecorded))
    fun selectExpected(date: LocalDate, lastRecorded: LocalDate?) = saveCalendar(calendar.copy(expected = date, lastRecorded = lastRecorded))
    fun clearCalendar() = saveCalendar(SalaryCalendarPlan(null, null, null))

    init { scope?.launch(kotlinx.coroutines.Dispatchers.IO) { repo.profileBackup.collect { data ->
        day = data.salaryDay
        calendar = SalaryCalendarPlan(data.reference?.let(LocalDate::parse), data.expected?.let(LocalDate::parse), data.lastRecorded?.let(LocalDate::parse))
    } } }
    private fun save(change: (ProfileBackupData) -> ProfileBackupData) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val next = com.sadique.dailyledger.sync.BackupLock.mutex.withLock {
            SettingsStore(context).markDrivePending(ownerId)
            repo.updateProfile(change)
        }
        runCatching { com.sadique.dailyledger.sync.SyncScheduler.syncNow(context, ownerId) }
        next
    }
    private fun saveCalendar(value: SalaryCalendarPlan) {
        save { it.copy(salaryDay = null, reference = value.reference?.toString(), expected = value.expected?.toString(), lastRecorded = value.lastRecorded?.toString()) }
        day = null; calendar = value
    }
    fun updateDay(value: Int?) {
        require(value == null || value in 1..31)
        save { it.copy(salaryDay = value) }; day = value
    }
}
