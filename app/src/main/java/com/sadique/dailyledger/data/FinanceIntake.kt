package com.sadique.dailyledger.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.sadique.dailyledger.ui.parseMinor

/** All sources produce reviewable drafts only; pasted SMS text and receipt photos are never stored wholesale. */
object FinanceIntake {
    fun receipt(text:String,date:String):List<TransactionDraft> {
        LocalDate.parse(date); require(text.length<=20000)
        val line=Regex("^(.+?)\\s+(?:Rs\\.?\\s*|PKR\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*$",RegexOption.IGNORE_CASE)
        val totals=Regex("^(grand\\s*total|total|subtotal|sub total|balance|change|cash|tendered|card|receipt|invoice|phone|tel|tax no|ntn|gst no|discount)\\b",RegexOption.IGNORE_CASE)
        return text.lines().mapNotNull { raw ->
            val s=raw.trim();if(totals.containsMatchIn(s))return@mapNotNull null
            val match=line.matchEntire(s) ?: return@mapNotNull null
            val name=match.groupValues[1].trim().take(100)
            if(name.none { it.isLetter() }) return@mapNotNull null
            val amount=parseMinor(match.groupValues[2].replace(",",""))?.takeIf { it in 1..1_000_000_000_000L } ?: return@mapNotNull null
            TransactionDraft("EXPENSE",amount,"Other",name,date)
        }.also { require(it.size<=50) { "Review large receipts in sections of up to 50 items." }; require(it.isNotEmpty()) { "No clear item amounts found. Correct the text or enter items manually." } }
    }
    fun sms(text:String, fallbackDate:String, type:String):TransactionDraft {
        require(text.length in 1..5000 && type in listOf("INCOME","EXPENSE"));LocalDate.parse(fallbackDate)
        require(Regex("debit|credit|paid|payment|withdraw|received|sent|purchase|transfer",RegexOption.IGNORE_CASE).containsMatchIn(text)) { "Paste a payment message, not an OTP or balance-only message." }
        val amount=Regex("(?:PKR|Rs\\.?|Rupees)\\s*[:.]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",RegexOption.IGNORE_CASE).find(text)
            ?: error("No clear PKR amount found. Enter the amount manually.")
        val value=requireNotNull(parseMinor(amount.groupValues[1].replace(",","")));require(value>0)
        val iso=Regex("\\b(20[0-9]{2}-[0-9]{2}-[0-9]{2})\\b").find(text)?.groupValues?.get(1)
        val numeric=Regex("\\b([0-9]{2}/[0-9]{2}/20[0-9]{2})\\b").find(text)?.groupValues?.get(1)
        val date=when { iso!=null -> LocalDate.parse(iso).toString();numeric!=null -> LocalDate.parse(numeric,DateTimeFormatter.ofPattern("dd/MM/uuuu",Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString();else -> fallbackDate }
        // Deliberately do not retain account/card identifiers, OTPs or the complete message in notes.
        return TransactionDraft(type,value,if(type=="INCOME")"Other income" else "Other","Bank message • reviewed entry",date)
    }
}
