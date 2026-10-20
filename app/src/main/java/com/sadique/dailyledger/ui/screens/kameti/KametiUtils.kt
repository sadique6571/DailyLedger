package com.sadique.dailyledger.ui.screens

import androidx.compose.runtime.Composable
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import com.sadique.dailyledger.ui.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal fun scheduledMonth(committee: CommitteeEntity, installment: Int): String =
    YearMonth.parse(committee.startMonth).plusMonths((installment - 1).toLong()).toString()

internal fun monthCountdown(monthText: String, today: LocalDate = LocalDate.now()): String {
    val month = runCatching { YearMonth.parse(monthText) }.getOrNull() ?: return monthText
    val start = month.atDay(1)
    val end = month.atEndOfMonth()
    return when {
        today.isBefore(start) -> {
            val days = ChronoUnit.DAYS.between(today, start)
            "$days days remaining"
        }
        !today.isAfter(end) -> {
            val days = ChronoUnit.DAYS.between(today, end)
            "Turn month is here • $days days left"
        }
        else -> "Turn month has passed"
    }
}

internal fun committeeOrganizerMessage(committee: CommitteeEntity, next: Int?, month: String?): String {
    return if (next != null && month != null) {
        "Assalam o Alaikum, ${committee.name} ki installment #$next ($month) ${money(committee.monthlyContribution())} hai. Payment ke hawale se rabta kar raha hoon."
    } else {
        "Assalam o Alaikum, ${committee.name} kameti ke record ke hawale se rabta kar raha hoon."
    }
}

internal fun kametiWhatsappNumber(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}

/** Presentation-only masking. Never changes payment calculations or outgoing messages. */
@Composable
internal fun visibleMoney(amount: Long): String = if (LocalDisplayPreferences.current?.privateAmounts == true) "Rs •••••" else money(amount)
