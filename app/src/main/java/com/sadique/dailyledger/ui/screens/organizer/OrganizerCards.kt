package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun OrganizerCircleCard(
    circle: OrganizerCircleEntity,
    roster: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
    payouts: List<OrganizerPayoutEntity>,
    selectedMonth: String,
    onDeleteCircle: (OrganizerCircleEntity) -> Unit,
    onAddMember: (OrganizerCircleEntity) -> Unit,
    onDrawMember: (OrganizerMemberEntity) -> Unit,
    onEditContact: (OrganizerMemberEntity) -> Unit,
    onDeleteMember: (OrganizerMemberEntity) -> Unit,
    onPayMember: (OrganizerMemberEntity, Boolean) -> Unit,
    onShareReminder: (String, String) -> Unit,
    repo: LedgerRepository? = null,
) {
    val context = LocalContext.current
    val expected = Math.multiplyExact(circle.fullMonthlyMinor, circle.totalSlots.toLong())
    val monthPaid = collections.filter { it.circleId == circle.id && it.month == selectedMonth }.sumOf { it.amountMinor }
    val fraction = (monthPaid.toDouble() / expected).toFloat().coerceIn(0f, 1f)
    val animatedFraction = com.sadique.dailyledger.ui.animatedProgress(fraction)
    Card(Modifier.fillMaxWidth(), accent = com.sadique.dailyledger.ui.ledgerCardAccent(circle.id), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(circle.name, Modifier.weight(1f), translate = false, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onDeleteCircle(circle) }) { Text("Delete") }
            }
            Text("${roster.sumOf { it.halfUnits } / 2.0} of ${circle.totalSlots} shares assigned • ${roster.map { it.name to it.phone }.distinct().size} member contacts",
                style = MaterialTheme.typography.bodySmall)
            Text("Full share: ${rowMoney(circle.fullMonthlyMinor)} / month • ${circle.totalSlots} turns")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Collected: ${rowMoney(monthPaid)}", style = MaterialTheme.typography.bodySmall)
                Text("${(animatedFraction * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { animatedFraction }, modifier = Modifier.fillMaxWidth().height(16.dp).testTag("organizer-collection-progress"))
            Text("Monthly target: ${rowMoney(expected)} • Remaining: ${rowMoney((expected-monthPaid).coerceAtLeast(0L))}")
            Button(onClick = { onAddMember(circle) }, modifier = Modifier.fillMaxWidth()) { Text("+ Add member / 0.5 or full share") }
            var showReminders by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showReminders = !showReminders }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showReminders) "Hide payment reminders" else "Pending payments & reminders")
            }
            if (showReminders) {
                val month = runCatching { YearMonth.parse(selectedMonth) }.getOrNull()
                val reminders = if (month == null) null else
                    runCatching { organizerReminderCandidates(circle, roster, collections, month, LocalDate.now()) }.getOrNull()
                if (reminders == null) {
                    Text("Cannot calculate reminders for this month.", color = MaterialTheme.colorScheme.error)
                } else if (reminders.isEmpty()) {
                    com.sadique.dailyledger.ui.LedgerHistoryCard { Text("All assigned members are fully paid for this month.") }
                } else reminders.forEach { candidate ->
                    com.sadique.dailyledger.ui.LedgerHistoryCard {
                        Text(candidate.name, translate = false, style = MaterialTheme.typography.titleSmall)
                        Text("${if (candidate.overdue) "Overdue" else "Pending"} • ${rowMoney(candidate.outstandingMinor)} remaining")
                        val contact = candidate.whatsapp.ifBlank { candidate.phone }
                        if (contact.isNotBlank()) {
                            TextButton(onClick = {
                                onShareReminder(contact, organizerReminderMessage(circle.name, month!!, candidate))
                            }) { Text("Review WhatsApp reminder") }
                        }
                    }
                }
            }
            var showCalendar by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showCalendar = !showCalendar }, modifier = Modifier.fillMaxWidth().testTag("kameti-payment-calendar-toggle")) {
                Text(if (showCalendar) "Hide payment calendar" else "Monthly payment calendar")
            }
            if (showCalendar) {
                val calendar = remember(circle, roster, collections) {
                    runCatching { organizerInstallmentCalendar(circle, roster, collections) }.getOrNull()
                }
                if (calendar == null) {
                    Text("Payment calendar could not be calculated. Please check member installments.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    calendar.forEach { entry ->
                        com.sadique.dailyledger.ui.LedgerHistoryCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.month.toString(), style = MaterialTheme.typography.titleSmall)
                                Text("${entry.paidMembers} paid • ${entry.partialMembers} partial • ${entry.unpaidMembers} unpaid",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Collected ${rowMoney(entry.collectedMinor)} / ${rowMoney(entry.expectedAssignedMinor)}",
                                style = MaterialTheme.typography.bodySmall)
                            val entryFraction = if (entry.expectedAssignedMinor == 0L) 0f
                                else (entry.collectedMinor.toDouble() / entry.expectedAssignedMinor).toFloat().coerceIn(0f, 1f)
                            val animatedEntry = com.sadique.dailyledger.ui.animatedProgress(entryFraction)
                            LinearProgressIndicator(progress = { animatedEntry }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        }
                    }
                }
            }

            var showOverview by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showOverview = !showOverview }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showOverview) "Hide money overview" else "Money overview & next turns")
            }
            if (showOverview) {
                val overview = remember(circle, roster, collections, payouts, selectedMonth) {
                    runCatching { organizerCircleHealth(circle, roster, collections, payouts, YearMonth.parse(selectedMonth)) }.getOrNull()
                }
                com.sadique.dailyledger.ui.LedgerHistoryCard {
                    if (overview == null) Text("Cannot calculate overview. Review financial records.", color = MaterialTheme.colorScheme.error)
                    else {
                        Text("Recorded collections: ${rowMoney(overview.recordedCollectionsMinor)}")
                        Text("Recorded payouts: ${rowMoney(overview.recordedPayoutsMinor)}")
                        Text("Recorded pooled balance: ${rowMoney(overview.pooledBalanceMinor)}")
                        Text("Members with installment outstanding: ${overview.membersWithOutstanding}")
                        Text("Recorded amounts are not bank-verified or a physical cash close.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                roster.filter { it.turnNumber > 0 }.sortedBy { it.turnNumber }.take(5).forEach { member ->
                    val days = organizerTurnCountdown(circle.startMonth, member.turnNumber, LocalDate.now())
                    Text("${member.name} • Turn #${member.turnNumber} • ${days ?: 0} days until month start",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            val circleReceipts = collections.filter { it.circleId == circle.id }
            var showReceipts by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showReceipts = !showReceipts }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showReceipts) "Hide payment receipts" else "Payment receipts")
            }
            if (showReceipts) {
                circleReceipts.sortedByDescending { it.createdAt }.take(50).forEach { payment ->
                    val payee = roster.firstOrNull { it.id == payment.memberId && it.ownerId == circle.ownerId }
                    if (payee != null) {
                        PaymentRowWithProofs(payment.id, "organizer", repo) {
                        com.sadique.dailyledger.ui.LedgerHistoryCard {
                            Text(payee.name, translate = false, style = MaterialTheme.typography.titleSmall)
                            Text("${organizerReceiptNumber(payment)} • ${payment.date}", style = MaterialTheme.typography.bodySmall)
                            Text("${rowMoney(payment.amountMinor)} • ${payment.method}")
                            TextButton(onClick = {
                                val receipt = organizerReceiptText(circle, payee, payment)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, receipt)
                                }
                                context.startActivity(Intent.createChooser(send, "Share Kameti receipt"))
                            }) { Text("Share receipt") }
                        }
                        }
                    }
                }
            }
            roster.sortedWith(compareBy<OrganizerMemberEntity> { it.turnNumber == 0 }.thenBy { it.turnNumber }).forEach { member ->
                val shareMonthly = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
                val collected = collections.filter { it.memberId == member.id && it.month == selectedMonth }.sumOf { it.amountMinor }
                val payoutExpected = Math.multiplyExact(shareMonthly, circle.totalSlots.toLong())
                val received = payouts.filter { it.memberId == member.id }.sumOf { it.amountMinor }
                val slotUnitCount = filledTurnUnits(roster, member.turnNumber)
                val pairedWith = roster.filter { it.id != member.id && it.turnNumber == member.turnNumber && member.turnNumber > 0 }
                HorizontalDivider()
                Text(member.name, translate = false, style = MaterialTheme.typography.titleMedium)
                Text("${shareLabel(member.halfUnits)} share • Monthly ${rowMoney(shareMonthly)} • Turn ${if(member.turnNumber == 0) "not drawn" else "#${member.turnNumber}"}")
                if (member.turnNumber > 0 && member.drawnAt > 0L) Text("Turn picked: ${member.drawMethod} • ${java.time.Instant.ofEpochMilli(member.drawnAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", style = MaterialTheme.typography.bodySmall)
                if (member.halfUnits == 1 && member.turnNumber > 0) {
                    Text(if (slotUnitCount == 2) "Paired: ${pairedWith.joinToString { it.name }}" else "Half share waiting for another half", style = MaterialTheme.typography.bodySmall)
                }
                Text("This month: ${rowMoney(collected)} / ${rowMoney(shareMonthly)} • Payout: ${rowMoney(received)} / ${rowMoney(payoutExpected)}", style = MaterialTheme.typography.bodySmall)
                val phone = member.phone
                val wa = member.whatsapp.ifBlank { phone }
                val reminderDue = rowMoney((shareMonthly - collected).coerceAtLeast(0L))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (phone.isNotBlank()) TextButton(onClick = {
                        context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }) { Text("Call") }
                    if (wa.isNotBlank()) TextButton(onClick = {
                        onShareReminder(wa, "Assalam o Alaikum ${member.name}, ${circle.name} ki $selectedMonth installment mein $reminderDue remaining hain.")
                    }) { Text("WhatsApp") }
                    if (phone.isNotBlank()) TextButton(onClick = {
                        val msg = "Assalam o Alaikum ${member.name}, ${circle.name} ki $selectedMonth Kameti installment ka reminder."
                        context.openContactAction(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", msg))
                    }) { Text("SMS") }
                    if (member.turnNumber == 0) TextButton(onClick = { onDrawMember(member) }) { Text("Pick number") }
                }
                TextButton(onClick = { onEditContact(member) }) { Text("Edit contact") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPayMember(member, false) }, enabled = collected < shareMonthly,
                        modifier = Modifier.weight(1f)) { Text("Collect") }
                    OutlinedButton(onClick = { onPayMember(member, true) },
                        enabled = member.turnNumber > 0 && slotUnitCount == 2 && received < payoutExpected,
                        modifier = Modifier.weight(1f)) { Text("Pay turn") }
                }
                if (collected == 0L && received == 0L) TextButton(onClick = { onDeleteMember(member) }) { Text("Remove member") }
            }
            if (roster.isNotEmpty()) Text("Turn slots: " + (1..circle.totalSlots).joinToString("  ") { n ->
                val units = filledTurnUnits(roster, n)
                "#$n ${if (units == 2) "✓" else if (units == 1) "½" else "–"}"
            }, style = MaterialTheme.typography.bodySmall)
        }
    }
}
