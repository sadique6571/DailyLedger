@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerDateField

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
internal fun OrganizerCircleDialog(dismiss: () -> Unit, save: (String, Long, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("5000") }
    var slots by remember { mutableStateOf("20") }
    var start by remember { mutableStateOf(YearMonth.now().toString()) }
    val minor = parseMinor(amount)
    val n = slots.toIntOrNull()
    val valid = name.isNotBlank() && minor != null && minor > 0 && minor % 2L == 0L && n != null && n in 2..120 && runCatching { YearMonth.parse(start) }.isSuccess
    AlertDialog(onDismissRequest = dismiss, title = { Text("Create collection Kameti") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Kameti / place name") })
            OutlinedTextField(amount, { amount = it }, label = { Text("Monthly PKR per full share") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(slots, { slots = it }, label = { Text("Full-share turns") }, supportingText = { Text("For example, 20 turns.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(start, { start = it }, label = { Text("Start YYYY-MM") })
            if (valid) Text("Monthly target: ${rowMoney(Math.multiplyExact(minor!!, n!!.toLong()))}")
        }},
        confirmButton = { Button(enabled = valid, onClick = { save(name, minor!!, n!!, start) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun OrganizerMemberDialog(
    circle: OrganizerCircleEntity,
    roster: List<OrganizerMemberEntity>,
    dismiss: () -> Unit,
    save: suspend (String, List<OrganizerMemberDraft>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val requestId = remember { java.util.UUID.randomUUID().toString() }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf("") }
    var units by remember { mutableIntStateOf(2) }
    var turn by remember { mutableStateOf("0") }
    val queued = remember(circle.id) { mutableStateListOf<OrganizerMemberDraft>() }
    val virtualRoster = roster + queued.mapIndexed { index, entry ->
        OrganizerMemberEntity("draft-$index", "", circle.id, entry.name,
            halfUnits = entry.units, turnNumber = entry.turn, createdAt = 0)
    }
    val parsedTurn = turn.toIntOrNull()
    val candidate = OrganizerMemberEntity("candidate", "", circle.id, name,
        halfUnits = units, turnNumber = 0, createdAt = 0)
    val available = availableDraws(virtualRoster, candidate, circle.totalSlots)
    val currentValid = name.isNotBlank() &&
        virtualRoster.sumOf { it.halfUnits } + units <= circle.totalSlots * 2 &&
        parsedTurn != null && (parsedTurn == 0 || parsedTurn in available)
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text("Add all members / turns") },
        text = {
            if (saving) Text("Saving all entries…")
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kameti-save-error")) }
                Text("Add each member and each share as a separate row. The same person can have 2 or 3 turns in different months.")
                Text("Full monthly: ${rowMoney(circle.fullMonthlyMinor)} • Half monthly: ${rowMoney(circle.fullMonthlyMinor / 2)}")
                queued.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${entry.name} • ${shareLabel(entry.units)} share • ${rowMoney(halfShareMoney(circle.fullMonthlyMinor, entry.units))} • ${if (entry.turn == 0) "Parchi later" else "Turn #${entry.turn}"}",
                            Modifier.weight(1f), translate = false, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { queued.removeAt(index) }) { Text("Remove") }
                    }
                }
                Text("Shares filled: ${virtualRoster.sumOf { it.halfUnits } / 2.0} / ${circle.totalSlots}")
                ContactPickerButton { picked, number ->
                    name = picked
                    phone = number
                    if (wa.isBlank()) wa = number
                }
                OutlinedTextField(name, { name = it }, label = { Text("Member name") })
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(wa, { wa = it }, label = { Text("WhatsApp (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(units == 1, onClick = { units = 1 }, label = { Text("0.5 share") })
                    FilterChip(units == 2, onClick = { units = 2 }, label = { Text("1 full share") })
                }
                OutlinedTextField(turn, { turn = it }, label = { Text("Turn number (0 = pick later)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsedTurn == null || (parsedTurn != 0 && parsedTurn !in available))
                if (parsedTurn != null && parsedTurn > 0) {
                    val turnMonth = runCatching { YearMonth.parse(circle.startMonth).plusMonths((parsedTurn - 1).toLong()) }.getOrNull()
                    Text("Turn month: ${turnMonth ?: "invalid"}")
                }
                if (!currentValid && name.isNotBlank()) Text("Check share capacity or turn occupancy.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(enabled = currentValid, onClick = {
                    queued.add(OrganizerMemberDraft(name.trim(), phone.trim(), wa.trim(), units, parsedTurn!!))
                    name = ""; phone = ""; wa = ""; turn = "0"; units = 2
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Add another member / turn") }
                Text("Review the full list, then Save all. Nothing is saved until Save all.")
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
internal fun OrganizerDrawDialog(member: OrganizerMemberEntity, circle: OrganizerCircleEntity,
    roster: List<OrganizerMemberEntity>, dismiss: () -> Unit, save: (Int, String) -> Unit) {
    val choices = availableDraws(roster, member, circle.totalSlots)
    var proposed by remember(member.id) { mutableIntStateOf(0) }
    var method by remember(member.id) { mutableStateOf("MANUAL") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Turn / parchi number") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${member.name} • ${shareLabel(member.halfUnits)} share", translate = false)
            Text("Random draws only from slots with free capacity. A half can share a number with another half.")
            Button(onClick = { proposed = choices.randomOrNull() ?: 0; method = "RANDOM" }, enabled = choices.isNotEmpty()) { Text("Random pick") }
            Text("Or manually choose:")
            choices.forEach { n ->
                FilterChip(selected = proposed == n, onClick = { proposed = n; method = "MANUAL" },
                    label = { Text("#$n • ${if (filledTurnUnits(roster, n) == 1) "Pair with half" else "Free"}") })
            }
            if (proposed > 0) Text("Preview: #$proposed — ${YearMonth.parse(circle.startMonth).plusMonths((proposed-1).toLong())}",
                fontWeight = FontWeight.Bold)
        }},
        confirmButton = { Button(enabled = proposed in choices, onClick = { save(proposed, method) }) { Text("Confirm number") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun OrganizerPaymentDialog(member: OrganizerMemberEntity, circle: OrganizerCircleEntity,
    payout: Boolean, month: String, already: Long, dismiss: () -> Unit,
    save: (Long, String, String, String) -> Unit) {
    val full = halfShareMoney(circle.fullMonthlyMinor, member.halfUnits)
    val expected = if (payout) Math.multiplyExact(full, circle.totalSlots.toLong()) else full
    val remaining = (expected - already).coerceAtLeast(0L)
    var amount by remember(member.id, payout) { mutableStateOf(plainAmount(remaining)) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var method by remember { mutableStateOf("Cash") }
    val minor = parseMinor(amount)
    val valid = minor != null && minor in 1..remaining && runCatching { LocalDate.parse(date) }.isSuccess
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (payout) "Pay member's Kameti turn" else "Collect member installment") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(member.name, translate = false)
            Text(if (payout) "Turn #${member.turnNumber} • Member payout due ${rowMoney(remaining)}" else "$month • Due ${rowMoney(remaining)}")
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount PKR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            LedgerDateField(date, { date = it }, label = "Entry date")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Cash","Bank","Easypaisa","JazzCash").forEach { choice ->
                    FilterChip(method == choice, { method = choice }, label = { Text(choice) })
                }
            }
            if (payout) Text("This is a pooled payout, not a personal salary expense.")
        }},
        confirmButton = { Button(enabled = valid, onClick = { save(minor!!, month, date, method) }) { Text("Record") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun OrganizerContactDialog(member: OrganizerMemberEntity, dismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember(member.id) { mutableStateOf(member.name) }
    var phone by remember(member.id) { mutableStateOf(member.phone) }
    var wa by remember(member.id) { mutableStateOf(member.whatsapp) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit member contact") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactPickerButton { picked, number -> name = picked; phone = number; if (wa.isBlank()) wa = number }
            OutlinedTextField(name, { name = it }, label = { Text("Name") })
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(wa, { wa = it }, label = { Text("WhatsApp (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }},
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { save(name, phone, wa) }) { Text("Save contact") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun DeleteOrganizerMemberDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Remove member?") },
        text = { Text("Only members without payment history can be removed.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun DeleteOrganizerCircleDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete collection Kameti?") },
        text = { Text("Only circles without any recorded collection or payout can be deleted.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun ShareReminderDialog(phone: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Review WhatsApp reminder") },
        text = { Text(message, translate = false) },
        confirmButton = { Button(onClick = {
            context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${organizerWhatsApp(phone)}?text=${URLEncoder.encode(message, "UTF-8")}")))
            onConfirm()
        }) { Text("Open WhatsApp") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
