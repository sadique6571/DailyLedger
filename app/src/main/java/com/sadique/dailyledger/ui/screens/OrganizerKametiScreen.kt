package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.YearMonth

private fun shareLabel(units: Int) = if (units == 1) "0.5" else "1"
private fun organizerWhatsApp(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}
@Composable private fun rowMoney(amount: Long) = visibleAmount(amount)

@Composable
fun OrganizerKametiScreen(
    circles: List<OrganizerCircleEntity>,
    members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
    payouts: List<OrganizerPayoutEntity>,
    onBack: () -> Unit,
    onAddCircle: (String, Long, Int, String) -> Unit,
    onAddMember: (String, String, String, String, Int, Int) -> Unit,
    onAssignTurn: (String, Int) -> Unit,
    onCollect: (String, String, Long, String, String) -> Unit,
    onPayout: (String, Long, String, String) -> Unit,
    onDeleteMember: (String) -> Unit,
    onDeleteCircle: (String) -> Unit,
) {
    var addCircle by remember { mutableStateOf(false) }
    var memberFor by remember { mutableStateOf<OrganizerCircleEntity?>(null) }
    var payMember by remember { mutableStateOf<Pair<OrganizerMemberEntity, Boolean>?>(null) }
    var drawMember by remember { mutableStateOf<OrganizerMemberEntity?>(null) }
    var deleteMember by remember { mutableStateOf<OrganizerMemberEntity?>(null) }
    var deleteCircle by remember { mutableStateOf<OrganizerCircleEntity?>(null) }
    var shareMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().toString()) }
    val context = LocalContext.current

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { addCircle = true }) { Text("+ New collection") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("organizer-kameti-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = onBack) { Text("← My Kametis") }
                Text("My collection / Organizer", style = MaterialTheme.typography.headlineSmall)
                Text("Members' collected amounts are pooled funds, not your personal income or expense.")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { selectedMonth = YearMonth.parse(selectedMonth).minusMonths(1).toString() }) { Text("‹") }
                    Text(selectedMonth, Modifier.testTag("collection-selected-month"), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { selectedMonth = YearMonth.parse(selectedMonth).plusMonths(1).toString() }) { Text("›") }
                }
            }
            if (circles.isEmpty()) item {
                Text("Add an organizer Kameti to start collecting from members, pairing half shares, and drawing turn numbers.")
            }
            items(circles, key = { it.id }) { circle ->
                val roster = members.filter { it.circleId == circle.id }
                val expected = Math.multiplyExact(circle.fullMonthlyMinor, circle.totalSlots.toLong())
                val monthPaid = collections.filter { it.circleId == circle.id && it.month == selectedMonth }.sumOf { it.amountMinor }
                val fraction = (monthPaid.toDouble() / expected).toFloat().coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(circle.name, Modifier.weight(1f), translate = false, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { deleteCircle = circle }) { Text("Delete") }
                        }
                        Text("${roster.sumOf { it.halfUnits } / 2.0} of ${circle.totalSlots} shares assigned • ${roster.map { it.name to it.phone }.distinct().size} member contacts",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Full share: ${rowMoney(circle.fullMonthlyMinor)} / month • ${circle.totalSlots} turns")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Collected: ${rowMoney(monthPaid)}", style = MaterialTheme.typography.bodySmall)
                            Text("${(fraction * 100).toInt()}%", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(16.dp).testTag("organizer-collection-progress"))
                        Text("Monthly target: ${rowMoney(expected)} • Remaining: ${rowMoney((expected-monthPaid).coerceAtLeast(0L))}")
                        Button(onClick = { memberFor = circle }, modifier = Modifier.fillMaxWidth()) { Text("+ Add member / 0.5 or full share") }
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
                                    shareMessage = wa to "Assalam o Alaikum ${member.name}, ${circle.name} ki $selectedMonth installment mein $reminderDue remaining hain."
                                }) { Text("WhatsApp") }
                                if (member.turnNumber == 0) TextButton(onClick = { drawMember = member }) { Text("Pick number") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { payMember = member to false }, enabled = collected < shareMonthly,
                                    modifier = Modifier.weight(1f)) { Text("Collect") }
                                OutlinedButton(onClick = { payMember = member to true },
                                    enabled = member.turnNumber > 0 && slotUnitCount == 2 && received < payoutExpected,
                                    modifier = Modifier.weight(1f)) { Text("Pay turn") }
                            }
                            if (collected == 0L && received == 0L) TextButton(onClick = { deleteMember = member }) { Text("Remove member") }
                        }
                        if (roster.isNotEmpty()) Text("Turn slots: " + (1..circle.totalSlots).joinToString("  ") { n ->
                            val units = filledTurnUnits(roster, n)
                            "#$n ${if (units == 2) "✓" else if (units == 1) "½" else "–"}"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (addCircle) OrganizerCircleDialog({ addCircle = false }) { name, amount, slots, start ->
        onAddCircle(name, amount, slots, start); addCircle = false
    }
    memberFor?.let { circle ->
        OrganizerMemberDialog(circle, members.filter { it.circleId == circle.id }, { memberFor = null }) { name, phone, wa, units, turn ->
            onAddMember(circle.id, name, phone, wa, units, turn); memberFor = null
        }
    }
    drawMember?.let { member ->
        val circle = circles.firstOrNull { it.id == member.circleId }
        if (circle != null) OrganizerDrawDialog(member, circle,
            members.filter { it.circleId == circle.id }, { drawMember = null }) { n ->
            onAssignTurn(member.id, n); drawMember = null
        }
    }
    payMember?.let { (member, payout) ->
        val circle = circles.firstOrNull { it.id == member.circleId }
        if (circle != null) OrganizerPaymentDialog(member, circle, payout, selectedMonth,
            if (payout) payouts.filter { it.memberId == member.id }.sumOf { it.amountMinor }
            else collections.filter { it.memberId == member.id && it.month == selectedMonth }.sumOf { it.amountMinor },
            { payMember = null }) { amount, month, date, method ->
            if (payout) onPayout(member.id, amount, date, method) else onCollect(member.id, month, amount, date, method)
            payMember = null
        }
    }
    deleteMember?.let { member ->
        AlertDialog(onDismissRequest = { deleteMember = null }, title = { Text("Remove member?") },
            text = { Text("Only members without payment history can be removed.") },
            confirmButton = { Button(onClick = { onDeleteMember(member.id); deleteMember = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleteMember = null }) { Text("Cancel") } })
    }
    deleteCircle?.let { circle ->
        AlertDialog(onDismissRequest = { deleteCircle = null }, title = { Text("Delete collection Kameti?") },
            text = { Text("Only circles without any recorded collection or payout can be deleted.") },
            confirmButton = { Button(onClick = { onDeleteCircle(circle.id); deleteCircle = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCircle = null }) { Text("Cancel") } })
    }
    shareMessage?.let { (phone, message) ->
        AlertDialog(onDismissRequest = { shareMessage = null }, title = { Text("Review WhatsApp reminder") },
            text = { Text(message, translate = false) },
            confirmButton = { Button(onClick = {
                context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${organizerWhatsApp(phone)}?text=${URLEncoder.encode(message, "UTF-8")}")))
                shareMessage = null
            }) { Text("Open WhatsApp") } },
            dismissButton = { TextButton(onClick = { shareMessage = null }) { Text("Cancel") } })
    }
}

@Composable
private fun OrganizerCircleDialog(dismiss: () -> Unit, save: (String, Long, Int, String) -> Unit) {
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
            OutlinedTextField(slots, { slots = it }, label = { Text("Total full-share turns (e.g. 20)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(start, { start = it }, label = { Text("Start YYYY-MM") })
            if (valid) Text("Monthly target: ${rowMoney(Math.multiplyExact(minor!!, n!!.toLong()))}")
        }},
        confirmButton = { Button(enabled = valid, onClick = { save(name, minor!!, n!!, start) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun OrganizerMemberDialog(circle: OrganizerCircleEntity, roster: List<OrganizerMemberEntity>,
    dismiss: () -> Unit, save: (String, String, String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf("") }
    var units by remember { mutableIntStateOf(2) }
    var turn by remember { mutableStateOf("0") }
    val n = turn.toIntOrNull()
    val slots = if (n == null) emptyList() else availableDraws(roster,
        OrganizerMemberEntity("", "", circle.id, name, halfUnits = units, turnNumber = 0, createdAt = 0), circle.totalSlots)
    val valid = name.isNotBlank() && roster.sumOf { it.halfUnits } + units <= circle.totalSlots * 2 &&
        (n == 0 || (n != null && n in slots))
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add member share") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("For 1.5 shares, add one full and one half ownership row for the same contact.")
            ContactPickerButton { picked, number -> name = picked; phone = number; if (wa.isBlank()) wa = number }
            OutlinedTextField(name, { name = it }, label = { Text("Name") })
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(wa, { wa = it }, label = { Text("WhatsApp (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(units == 1, onClick = { units = 1 }, label = { Text("0.5 share") })
                FilterChip(units == 2, onClick = { units = 2 }, label = { Text("1 full") })
            }
            OutlinedTextField(turn, { turn = it }, label = { Text("Turn number (0 = pick later)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (n != 0 && n != null && n in 1..circle.totalSlots) Text("Slot occupancy: ${filledTurnUnits(roster, n)}/2 half units")
            if (roster.sumOf { it.halfUnits } + units > circle.totalSlots * 2) Text("No free shares", color = MaterialTheme.colorScheme.error)
        }},
        confirmButton = { Button(enabled = valid, onClick = { save(name, phone, wa, units, n!!) }) { Text("Save member") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun OrganizerDrawDialog(member: OrganizerMemberEntity, circle: OrganizerCircleEntity,
    roster: List<OrganizerMemberEntity>, dismiss: () -> Unit, save: (Int) -> Unit) {
    val choices = availableDraws(roster, member, circle.totalSlots)
    var proposed by remember(member.id) { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Turn / parchi number") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${member.name} • ${shareLabel(member.halfUnits)} share", translate = false)
            Text("Random draws only from slots with free capacity. A half can share a number with another half.")
            Button(onClick = { proposed = choices.randomOrNull() ?: 0 }, enabled = choices.isNotEmpty()) { Text("Random pick") }
            Text("Or manually choose:")
            choices.forEach { n ->
                FilterChip(selected = proposed == n, onClick = { proposed = n },
                    label = { Text("#$n • ${if (filledTurnUnits(roster, n) == 1) "Pair with half" else "Free"}") })
            }
            if (proposed > 0) Text("Preview: #$proposed — ${YearMonth.parse(circle.startMonth).plusMonths((proposed-1).toLong())}",
                fontWeight = FontWeight.Bold)
        }},
        confirmButton = { Button(enabled = proposed in choices, onClick = { save(proposed) }) { Text("Confirm number") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun OrganizerPaymentDialog(member: OrganizerMemberEntity, circle: OrganizerCircleEntity,
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
            OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Cash","Bank","Easypaisa","JazzCash").forEach { choice ->
                    FilterChip(method == choice, { method = choice }, label = { Text(choice) })
                }
            }
            if (payout) Text("This is a pooled payout, not a personal salary expense.")
        }},
        confirmButton = { Button(enabled = valid, onClick = { save(minor!!, month, date, method) }) { Text("Record") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
