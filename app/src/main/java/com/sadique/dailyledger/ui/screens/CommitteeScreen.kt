@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun CommitteeScreen(
    committees: List<CommitteeEntity>,
    payments: List<CommitteePaymentEntity>,
    receipts: List<CommitteeReceiptEntity>,
    members: List<CommitteeMemberEntity>,
    organizerCircles: List<OrganizerCircleEntity> = emptyList(),
    organizerMembers: List<OrganizerMemberEntity> = emptyList(),
    organizerCollections: List<OrganizerCollectionEntity> = emptyList(),
    organizerPayouts: List<OrganizerPayoutEntity> = emptyList(),
    onAddCircle: (String, Long, Int, String) -> Unit = { _, _, _, _ -> },
    onAddOrganizerMembers: suspend (String, List<OrganizerMemberDraft>, String) -> Unit = { _, _, _ -> error("Organizer saving is unavailable.") },
    onAssignOrganizerTurn: (String, Int, String) -> Unit = { _, _, _ -> },
    onUpdateOrganizerContact: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOrganizerCollect: (String, String, Long, String, String) -> Unit = { _, _, _, _, _ -> },
    onOrganizerPayout: (String, Long, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteOrganizerMember: (String) -> Unit = {},
    onDeleteOrganizerCircle: (String) -> Unit = {},

    onAdd: (String, Long, Int, String, Int?, String, Int, String) -> Unit,
    onPaid: (CommitteeEntity, Int, String, String) -> Unit,
    onReceive: (String, Long, String, String, String) -> Unit,
    onDeleteReceipt: (CommitteeReceiptEntity) -> Unit,
    onAddMembers: suspend (String, List<PersonalTurnDraft>, String) -> Unit,
    onMemberReceived: (CommitteeMemberEntity, String) -> Unit,
    onDeleteMember: (CommitteeMemberEntity) -> Unit,
    onDelete: (CommitteeEntity) -> Unit,
    onCalendar: () -> Unit = {},
    onPersonalTurnReceive: (String, Long, String, String, String) -> Unit = { _, _, _, _, _ -> },
) {
    val context = LocalContext.current
    var organizerMode by remember { mutableStateOf(false) }
    if (organizerMode) {
        OrganizerKametiScreen(
            circles = organizerCircles, members = organizerMembers,
            collections = organizerCollections, payouts = organizerPayouts,
            onBack = { organizerMode = false },
            onAddCircle = onAddCircle, onAddMembers = onAddOrganizerMembers,
            onAssignTurn = onAssignOrganizerTurn, onUpdateContact = onUpdateOrganizerContact, onCollect = onOrganizerCollect,
            onPayout = onOrganizerPayout, onDeleteMember = onDeleteOrganizerMember,
            onDeleteCircle = onDeleteOrganizerCircle,
        )
        return
    }
    var add by remember { mutableStateOf(false) }
    var receivingId by remember { mutableStateOf<String?>(null) }
    var deleteCommittee by remember { mutableStateOf<CommitteeEntity?>(null) }
    var deleteReceipt by remember { mutableStateOf<CommitteeReceiptEntity?>(null) }
    var paying by remember { mutableStateOf<Pair<CommitteeEntity, Int>?>(null) }
    var addMemberTo by remember { mutableStateOf<CommitteeEntity?>(null) }
    var markMemberReceived by remember { mutableStateOf<CommitteeMemberEntity?>(null) }
    var receivePersonalMember by remember { mutableStateOf<CommitteeMemberEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }) { Icon(Icons.Outlined.Add, "New kameti") }
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            enteringItem {
                Text("Kameti", style = MaterialTheme.typography.headlineMedium)
                Text("Apni installments, receiving, members ke turns aur contacts ek jagah track karein.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCalendar) { Text("Kameti calendar") }
                    FilledTonalButton(onClick = { organizerMode = true }) { Text("Collection / Organizer") }
                }
            }
            enteringItem {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("Total paid", payments.sumOf { it.amountMinor }, Icons.Outlined.Payments, Modifier.weight(1f))
                    SummaryCard("Total received", receipts.sumOf { it.amountMinor }, Icons.Outlined.AccountBalanceWallet, Modifier.weight(1f))
                }
            }
            if (committees.isEmpty()) {
                enteringItem { Text("Kameti add karein. Start month aur installments se app automatically har turn ka month calculate karegi.") }
            }
            enteringItems(committees, key = { it.id }) { committee ->
                val ps = payments.filter { it.committeeId == committee.id }
                val rs = receipts.filter { it.committeeId == committee.id }
                val ms = members.filter { it.committeeId == committee.id }.sortedBy { it.turnNumber }
                val balance = committeeBalance(committee, rs)
                val next = (1..committee.totalInstallments).firstOrNull { n -> ps.none { it.installmentNumber == n } }
                val nextMonth = next?.let { scheduledMonth(committee, it) }
                var history by remember(committee.id) { mutableStateOf(false) }
                var memberList by remember(committee.id) { mutableStateOf(true) }

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(committee.name, translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("${committee.sharesHalfUnits / 2.0} kametis • ${ps.size}/${committee.totalInstallments} installments paid", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { deleteCommittee = committee }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${committee.name}") }
                        }

                        Text("Monthly payment: ${visibleMoney(committee.monthlyContribution())}", style = MaterialTheme.typography.titleSmall)
                        val paidCount = ps.map { it.installmentNumber }.distinct().size
                        val paidFraction = (paidCount.toFloat() / committee.totalInstallments.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Installment progress", style = MaterialTheme.typography.bodySmall)
                            Text("$paidCount / ${committee.totalInstallments} • ${(paidFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { paidFraction },
                            modifier = Modifier.fillMaxWidth().height(12.dp).testTag("kameti-installment-progress"),
                        )
                        nextMonth?.let { month ->
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("Next installment #$next • $month", style = MaterialTheme.typography.labelLarge)
                                    Text(monthCountdown(month), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        HorizontalDivider()
                        BalanceRow("Expected receiving", balance.expected)
                        BalanceRow("Received", balance.received)
                        BalanceRow("Remaining", balance.remaining)
                        LinearProgressIndicator(
                            progress = { if (balance.expected > 0) (balance.received.toFloat() / balance.expected).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(balance.status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                        }

                        val myStructuredTurns = ms.filter { it.isMe }
                        if (myStructuredTurns.isNotEmpty()) {
                            myStructuredTurns.forEach { member ->
                                Text("My kameti turn: #${member.turnNumber} • ${member.turnMonth} • ${if (member.shareHalfUnits == 1) "0.5" else "1"} share", style = MaterialTheme.typography.titleSmall)
                                Text(monthCountdown(member.turnMonth), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            committee.payoutInstallment?.let { turn ->
                                val month = scheduledMonth(committee, turn)
                                Text("My kameti turn: #$turn • $month", style = MaterialTheme.typography.titleSmall)
                                Text(monthCountdown(month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (committee.organizerPhone.isNotBlank()) {
                            val reminder = committeeOrganizerMessage(committee, next, nextMonth)
                            ContactActionRow(
                                phone = committee.organizerPhone,
                                whatsapp = committee.organizerPhone,
                                message = reminder,
                                callLabel = "Call organizer",
                            )
                        }

                        if (committee.note.isNotBlank()) Text(committee.note, translate = false, style = MaterialTheme.typography.bodySmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { next?.let { paying = committee to it } },
                                enabled = next != null,
                                modifier = Modifier.weight(1f),
                            ) { Text(next?.let { "Pay #$it" } ?: "All paid") }
                            Button(
                                onClick = { receivingId = committee.id },
                                enabled = balance.remaining > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (balance.complete) "Received" else "Receive amount") }
                        }

                        FilledTonalButton(onClick = { addMemberTo = committee }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add member / turn")
                        }

                        if (ms.isNotEmpty() || committee.memberSchedule.isNotBlank()) {
                            TextButton(onClick = { memberList = !memberList }) {
                                Icon(if (memberList) Icons.Outlined.ExpandLess else Icons.Outlined.Groups, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (memberList) "Hide member turns" else "Show member turns")
                            }
                        }

                        if (memberList) {
                            if (ms.isNotEmpty()) {
                                Text("Member turns", style = MaterialTheme.typography.titleSmall)
                                ms.forEach { member ->
                                    MemberTurnCard(
                                        member = member,
                                        onMarkReceived = { if (member.isMe) receivePersonalMember = member else markMemberReceived = member },
                                        onDelete = { onDeleteMember(member) },
                                    )
                                }
                            } else if (committee.memberSchedule.isNotBlank()) {
                                Text("Legacy member turns", style = MaterialTheme.typography.titleSmall)
                                committee.memberSchedule.lineSequence().filter { it.isNotBlank() }.take(24).forEach { line ->
                                    val parts = line.split("|").map { it.trim() }
                                    com.sadique.dailyledger.ui.LedgerHistoryCard { Text("${parts.getOrNull(2).orEmpty()} • ${parts.getOrNull(0).orEmpty()}${parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
                                }
                                Text("Naye member records ke liye ‘Add member / turn’ use karein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (ps.isNotEmpty() || rs.isNotEmpty()) {
                            TextButton(onClick = { history = !history }) {
                                Icon(if (history) Icons.Outlined.ExpandLess else Icons.Outlined.History, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (history) "Hide history" else "Receiving & payment history")
                            }
                        }
                        if (history) {
                            if (rs.isNotEmpty()) Text("Receiving history", style = MaterialTheme.typography.titleSmall)
                            rs.sortedWith(compareByDescending<CommitteeReceiptEntity> { it.date }.thenByDescending { it.createdAt }).forEach { receipt ->
                                com.sadique.dailyledger.ui.LedgerHistoryCard() {
                                  Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(visibleMoney(receipt.amountMinor), style = MaterialTheme.typography.titleSmall)
                                        Text("${receipt.date ?: "Date not recorded"} • ${receipt.method}", style = MaterialTheme.typography.bodySmall)
                                        if (receipt.note.isNotBlank()) Text(receipt.note, translate = false, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { deleteReceipt = receipt }) { Icon(Icons.Outlined.DeleteOutline, "Delete receiving") }
                                  }
                                }
                            }
                            if (ps.isNotEmpty()) Text("Installments paid", style = MaterialTheme.typography.titleSmall)
                            ps.sortedByDescending { it.installmentNumber }.forEach {
                                com.sadique.dailyledger.ui.LedgerHistoryCard() {
                                    Text("#${it.installmentNumber} • ${it.month} • ${visibleMoney(it.amountMinor)} • ${it.method}" + if (it.chargesSalaryBalance()) "" else " • Historical record", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        CommitteeDialog({ add = false }) { n, a, t, s, payout, note, shares, phone ->
            onAdd(n, a, t, s, payout, note, shares, phone)
            add = false
        }
    }

    committees.firstOrNull { it.id == receivingId }?.let { committee ->
        ReceiveDialog(committee, committeeBalance(committee, receipts), { receivingId = null }) { amount, date, note, method ->
            onReceive(committee.id, amount, date, note, method)
            receivingId = null
        }
    }

    paying?.let { (committee, installment) ->
        val month = scheduledMonth(committee, installment)
        MethodDialog("Pay installment #$installment • $month", { paying = null },
            impact = if (month == YearMonth.now().toString()) "${visibleMoney(committee.monthlyContribution())} will be deducted from your current balance."
                else "Historical / other-month record: Paid count and total will increase. Your current balance will not change.") { method ->
            onPaid(committee, installment, month, method)
            paying = null
        }
    }

    addMemberTo?.let { committee ->
        PersonalMemberBatchDialog(committee, members.filter { it.committeeId == committee.id }, { addMemberTo = null }) { requestId, entries ->
            onAddMembers(committee.id, entries, requestId)
        }
    }

    receivePersonalMember?.let { member ->
        val committee = committees.firstOrNull { it.id == member.committeeId }
        if (committee != null) {
            val perMonth = committee.monthlyAmountMinor * (member.shareHalfUnits / 2) +
                (if (member.shareHalfUnits == 1) committee.monthlyAmountMinor / 2 else 0L)
            val expected = Math.multiplyExact(perMonth, committee.totalInstallments.toLong())
            val receivedSoFar = receipts.filter { it.memberId == member.id }.sumOf { it.amountMinor }
            val totalRemaining = committeeBalance(committee, receipts).remaining
            val allowed = minOf((expected - receivedSoFar).coerceAtLeast(0L), totalRemaining)
            ReceiveDialog(
                committee, CommitteeBalance(allowed, 0L),
                { receivePersonalMember = null },
                turnLabel = "My turn #${member.turnNumber} • ${if (member.shareHalfUnits == 1) "0.5" else "1"} share",
            ) { amount, date, note, method ->
                onPersonalTurnReceive(member.id, amount, date, note, method)
                receivePersonalMember = null
            }
        }
    }

    markMemberReceived?.let { member ->
        var date by remember(member.id) { mutableStateOf(LedgerRepository.today()) }
        AlertDialog(
            onDismissRequest = { markMemberReceived = null },
            title = { Text("Mark ${member.name} received?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Turn #${member.turnNumber} • ${member.turnMonth}")
                    OutlinedTextField(date, { date = it }, label = { Text("Received date YYYY-MM-DD") }, isError = !isValidDate(date), singleLine = true)
                }
            },
            confirmButton = { Button(enabled = isValidDate(date), onClick = { onMemberReceived(member, date); markMemberReceived = null }) { Text("Mark received") } },
            dismissButton = { TextButton(onClick = { markMemberReceived = null }) { Text("Cancel") } },
        )
    }

    deleteCommittee?.let { committee ->
        AlertDialog(
            onDismissRequest = { deleteCommittee = null },
            title = { Text("Delete ${committee.name}?") },
            text = { Text("This removes this kameti, its member turns, payment history and receiving history.") },
            confirmButton = { TextButton(onClick = { onDelete(committee); deleteCommittee = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCommittee = null }) { Text("Cancel") } },
        )
    }

    deleteReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { deleteReceipt = null },
            title = { Text("Delete this receiving?") },
            text = { Text("${money(receipt.amountMinor)} will be added back to the remaining receiving amount.") },
            confirmButton = { TextButton(onClick = { onDeleteReceipt(receipt); deleteReceipt = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteReceipt = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemberTurnCard(member: CommitteeMemberEntity, onMarkReceived: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${member.turnNumber} • ${member.turnMonth} • ${member.name} • ${if (member.shareHalfUnits == 1) "0.5" else "1"} share", translate = false, style = MaterialTheme.typography.titleSmall)
                    Text(monthCountdown(member.turnMonth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (member.isMe) AssistChip(onClick = {}, enabled = false, label = { Text("Me") })
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete member") }
            }
            if (member.phone.isNotBlank() || member.whatsapp.isNotBlank()) {
                ContactActionRow(
                    phone = member.phone,
                    whatsapp = member.whatsapp.ifBlank { member.phone },
                    message = "Assalam o Alaikum ${member.name}, aapki kameti turn ${member.turnMonth} (#${member.turnNumber}) record hai. Daily Ledger reminder.",
                )
            }
            if (member.received) {
                Text("Received${member.receivedDate?.let { " • $it" } ?: ""}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            } else {
                OutlinedButton(onClick = onMarkReceived) { Text(if (member.isMe) "Receive my turn" else "Mark turn received") }
            }
            if (member.note.isNotBlank()) Text(member.note, translate = false, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContactActionRow(phone: String, whatsapp: String, message: String, callLabel: String = "Call") {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (phone.isNotBlank()) {
            AssistChip(
                onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
                label = { Text(callLabel) },
                leadingIcon = { Icon(Icons.Outlined.Call, null) },
            )
            AssistChip(
                onClick = { context.openContactAction(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", message)) },
                label = { Text("SMS") },
                leadingIcon = { Icon(Icons.Outlined.Sms, null) },
            )
        }
        val wa = whatsappNumber(whatsapp)
        if (wa.isNotBlank()) {
            AssistChip(
                onClick = {
                    val msg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
                    context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=$msg")))
                },
                label = { Text("WhatsApp") },
                leadingIcon = { Icon(Icons.Outlined.Chat, null) },
            )
        }
    }
}

private fun scheduledMonth(committee: CommitteeEntity, installment: Int): String =
    YearMonth.parse(committee.startMonth).plusMonths((installment - 1).toLong()).toString()

private fun monthCountdown(monthText: String, today: LocalDate = LocalDate.now()): String {
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

private fun committeeOrganizerMessage(committee: CommitteeEntity, next: Int?, month: String?): String {
    return if (next != null && month != null) {
        "Assalam o Alaikum, ${committee.name} ki installment #$next ($month) ${money(committee.monthlyContribution())} hai. Payment ke hawale se rabta kar raha hoon."
    } else {
        "Assalam o Alaikum, ${committee.name} kameti ke record ke hawale se rabta kar raha hoon."
    }
}

@Composable
private fun BalanceRow(label: String, amount: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(visibleMoney(amount), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ReceiveDialog(committee: CommitteeEntity, balance: CommitteeBalance, dismiss: () -> Unit, turnLabel: String? = null, save: (Long, String, String, String) -> Unit) {
    var amount by remember(committee.id) { mutableStateOf(plainAmount(minOf(balance.remaining, committee.expectedPayout() / committee.shares))) }
    var date by remember { mutableStateOf(LedgerRepository.today()) }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    val parsed = parseMinor(amount)
    val validAmount = parsed != null && parsed > 0 && parsed <= balance.remaining
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(turnLabel?.let { "Receive • $it" } ?: "Receive • ${committee.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remaining: ${visibleMoney(balance.remaining)}")
                OutlinedTextField(amount, { amount = it }, label = { Text("Received amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = !validAmount)
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, isError = !isValidDate(date), singleLine = true)
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Cash", "Bank", "Easypaisa", "JazzCash", "Other").forEach { FilterChip(method == it, { method = it }, { Text(it) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = { Button(enabled = validAmount && isValidDate(date), onClick = { parsed?.let { save(it, date.trim(), note, method) } }) { Text("Save receiving") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommitteeDialog(dismiss: () -> Unit, save: (String, Long, Int, String, Int?, String, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("12") }
    var shares by remember { mutableStateOf("1") }
    var start by remember { mutableStateOf(LedgerRepository.thisMonth()) }
    var payout by remember { mutableStateOf("") }
    var organizerPhone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val a = parseMinor(amount)
    val t = total.toIntOrNull()
    val s = parseHalfShareUnits(shares)
    val p = payout.toIntOrNull()
    val combined = runCatching { if (s != null && a != null && (s % 2 == 0 || a % 2L == 0L)) Math.addExact(Math.multiplyExact(a, (s / 2).toLong()), if (s % 2 == 1) a / 2L else 0L) else null }.getOrNull()
    val expected = runCatching { Math.multiplyExact(combined ?: 0L, (t ?: 0).toLong()) }.getOrNull()
    val valid = a != null && a > 0 && t != null && t > 0 && s != null && s > 0 && s <= t * 2 && expected != null && expected > 0 && isValidMonth(start) && (payout.isBlank() || (p != null && p in 1..t))
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("New kameti") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name / place") })
                OutlinedTextField(shares, { shares = it }, label = { Text("My total shares (0.5, 1, 1.5, 2…)") }, supportingText = { Text("For 1.5: create a full turn and a half turn separately after saving.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Monthly PKR per kameti") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(total, { total = it }, label = { Text("Total monthly installments") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (valid) {
                    Text("Monthly total: ${visibleMoney(combined!!)}", style = MaterialTheme.typography.bodySmall)
                    Text("Total expected receiving: ${visibleMoney(expected!!)}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(start, { start = it }, label = { Text("Start month YYYY-MM") }, isError = !isValidMonth(start), singleLine = true)
                OutlinedTextField(payout, { payout = it }, label = { Text("Legacy single turn (optional; use Add member / turn for multiple)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (p != null && t != null && p in 1..t && isValidMonth(start)) Text("My month: ${YearMonth.parse(start).plusMonths((p - 1).toLong())}", style = MaterialTheme.typography.bodySmall)
                ContactPickerButton { _, pickedPhone -> organizerPhone = pickedPhone }
                OutlinedTextField(organizerPhone, { organizerPhone = it }, label = { Text("Organizer phone / WhatsApp (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { save(name.ifBlank { "Kameti" }, a!!, t!!, start.trim(), p, note, s!!, organizerPhone) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun PersonalMemberBatchDialog(
    committee: CommitteeEntity,
    currentMembers: List<CommitteeMemberEntity>,
    dismiss: () -> Unit,
    save: suspend (String, List<PersonalTurnDraft>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val requestId = remember { java.util.UUID.randomUUID().toString() }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val queued = remember(committee.id) { mutableStateListOf<PersonalTurnDraft>() }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var turn by remember { mutableStateOf("") }
    var isMe by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var halfUnits by remember { mutableIntStateOf(2) }
    val turnNumber = turn.toIntOrNull()
    val turnMonth = turnNumber?.takeIf { it in 1..committee.totalInstallments }?.let { scheduledMonth(committee, it) }
    val sameTurnUnits = currentMembers.filter { it.turnNumber == turnNumber }.sumOf { it.shareHalfUnits } +
        queued.filter { it.turn == turnNumber }.sumOf { it.halfUnits }
    val myUsedUnits = currentMembers.filter { it.isMe }.sumOf { it.shareHalfUnits } +
        queued.filter { it.isMe }.sumOf { it.halfUnits }
    val duplicate = sameTurnUnits + halfUnits > 2
    val canAddMyTurn = myUsedUnits + halfUnits <= committee.sharesHalfUnits
    val valid = turnMonth != null && !duplicate && (!isMe || canAddMyTurn) &&
        (isMe || name.isNotBlank())
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text("Add all members / my turns") },
        text = {
            if (saving) Text("Saving all entries…")
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kameti-save-error")) }
                Text("Use a separate row for every turn. If you own 2 shares, add two full turns; for 3 shares, add three. Every turn displays its calculated month.")
                Text("My shares assigned: ${myUsedUnits / 2.0} / ${committee.sharesHalfUnits / 2.0}")
                queued.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${entry.name} • ${if (entry.isMe) "My" else "Member"} • ${if (entry.halfUnits == 1) "0.5" else "1"} share • #${entry.turn} • ${entry.month}",
                            Modifier.weight(1f), translate = false, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { queued.removeAt(index) }) { Text("Remove") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("This is my turn", Modifier.weight(1f))
                    Switch(isMe, onCheckedChange = { isMe = it; if (it && name.isBlank()) name = "Me" })
                }
                ContactPickerButton { pickedName, pickedPhone ->
                    if (!isMe) name = pickedName
                    phone = pickedPhone
                    if (whatsapp.isBlank()) whatsapp = pickedPhone
                }
                OutlinedTextField(name, { name = it }, label = { Text(if (isMe) "Your name" else "Member name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(halfUnits == 1, { halfUnits = 1 }, label = { Text("Half 0.5") })
                    FilterChip(halfUnits == 2, { halfUnits = 2 }, label = { Text("Full 1") })
                }
                OutlinedTextField(turn, { turn = it }, label = { Text("Turn number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    isError = turn.isNotBlank() && (turnMonth == null || duplicate))
                turnMonth?.let { Text("Receiving month: $it", style = MaterialTheme.typography.titleSmall) }
                if (duplicate) Text("This turn already has a full share (or two half shares).", color = MaterialTheme.colorScheme.error)
                if (isMe && !canAddMyTurn) Text("Your declared shares are already allocated; increase shares in a new Kameti if needed.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
                OutlinedButton(enabled = valid, onClick = {
                    queued.add(PersonalTurnDraft(name.ifBlank { "Me" }.trim(), phone.trim(), whatsapp.trim(), turnNumber!!,
                        turnMonth!!, isMe, note.trim(), halfUnits))
                    name = if (isMe) "Me" else ""
                    phone = ""; whatsapp = ""; turn = ""; note = ""
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Add another turn / member") }
                Text("Review all rows before saving.")
            }
        },
        confirmButton = {
            Button(enabled = queued.isNotEmpty() && !saving, modifier = Modifier.testTag("kameti-save-all"), onClick = {
                if (!saving) {
                    val snapshot = queued.toList()
                    saving = true; saveError = null
                    scope.launch {
                        try { save(requestId, snapshot); dismiss() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { saveError = e.message ?: "Could not save. Your list is still here. Please retry." }
                        finally { saving = false }
                    }
                }
            }) {
                Text("Save all (${queued.size})")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MethodDialog(title: String, dismiss: () -> Unit, impact: String = "", save: (String) -> Unit) {
    var method by remember { mutableStateOf("Cash") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (impact.isNotBlank()) Text(impact, style = MaterialTheme.typography.bodyMedium)
                Text("Payment method")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Cash", "Bank", "Easypaisa", "JazzCash", "Other").forEach { FilterChip(method == it, { method = it }, { Text(it) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { save(method) }) { Text("Record payment") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}


private fun whatsappNumber(raw: String): String {
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
private fun visibleMoney(amount: Long): String = if (LocalDisplayPreferences.current?.privateAmounts == true) "Rs •••••" else money(amount)
