@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerDateField

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
internal fun ReceiveDialog(committee: CommitteeEntity, balance: CommitteeBalance, dismiss: () -> Unit, turnLabel: String? = null, save: (Long, String, String, String) -> Unit) {
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
                LedgerDateField(date, { date = it }, label = "Entry date")
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
internal fun CommitteeDialog(dismiss: () -> Unit, save: (String, Long, Int, String, Int?, String, Int, String) -> Unit) {
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
                OutlinedTextField(payout, { payout = it }, label = { Text("Single payout turn (optional)") }, supportingText = { Text("For multiple turns, use Add member / turn.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
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
internal fun MethodDialog(title: String, dismiss: () -> Unit, impact: String = "", save: (String) -> Unit) {
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

@Composable
internal fun MarkMemberReceivedDialog(
    member: CommitteeMemberEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var date by remember(member.id) { mutableStateOf(LedgerRepository.today()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark ${member.name} received?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn #${member.turnNumber} • ${member.turnMonth}")
                LedgerDateField(date, { date = it }, label = "Received date")
            }
        },
        confirmButton = { Button(enabled = isValidDate(date), onClick = { onConfirm(date) }) { Text("Mark received") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DeleteCommitteeDialog(
    committee: CommitteeEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${committee.name}?") },
        text = { Text("This removes this kameti, its member turns, payment history and receiving history.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DeleteReceiptDialog(
    receipt: CommitteeReceiptEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this receiving?") },
        text = { Text("${money(receipt.amountMinor)} will be added back to the remaining receiving amount.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
