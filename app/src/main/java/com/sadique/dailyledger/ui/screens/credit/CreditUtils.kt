package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.data.CreditPurchaseEntity
import com.sadique.dailyledger.ui.money

internal fun creditReminder(credit: CreditPurchaseEntity, left: Long): String =
    "Assalam o Alaikum ${credit.creditor}, Daily Ledger record ke mutabiq ${money(left)} udhar balance remaining hai (${credit.item}). Payment/update ke hawale se rabta kar raha hoon."

internal fun creditDueLabel(date: String): String = runCatching {
    val due = java.time.LocalDate.parse(date)
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    when {
        days > 0 -> "$days days left"
        days == 0L -> "due today"
        else -> "${-days} days overdue"
    }
}.getOrDefault("date check")

internal fun creditWhatsappNumber(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}
