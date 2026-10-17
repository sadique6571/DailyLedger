package com.sadique.dailyledger.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/** Account-scoped local planning preference; selecting a day never creates a salary entry. */
class SalaryScheduleStore(context: Context, ownerId: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val prefs = context.applicationContext.getSharedPreferences("salary_schedule_$key", Context.MODE_PRIVATE)
    var day: Int? by mutableStateOf(prefs.getInt("day", 0).takeIf { it in 1..31 })
        private set

    fun updateDay(value: Int?) {
        require(value == null || value in 1..31)
        prefs.edit().putInt("day", value ?: 0).apply()
        day = value
    }
}
