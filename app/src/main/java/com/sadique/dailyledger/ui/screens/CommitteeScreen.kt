@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface
import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun CommitteeScreen(
    committees: List<CommitteeEntity>,
    payments: List<CommitteePaymentEntity>,
    receipts: List<CommitteeReceiptEntity>,
    members: List<CommitteeMemberEntity>,
    repo: LedgerRepository? = null,
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
    entryRequest: String? = null, onEntryOpened: () -> Unit = {},
    onPersonalTurnReceive: (String, Long, String, String, String) -> Unit = { _, _, _, _, _ -> },
) {
    val context = LocalContext.current
    var organizerMode by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }
    var pickEntry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entryRequest) {
        when (entryRequest) {
            "kameti" -> { organizerMode = false; add = true; onEntryOpened() }
            "collection" -> { organizerMode = true; addCollection = true; onEntryOpened() }
            "kameti-paid", "kameti-receive", "kameti-member" -> { organizerMode = false; pickEntry = entryRequest; onEntryOpened() }
        }
    }
    if (organizerMode) {
        OrganizerKametiScreen(
            repo = repo, circles = organizerCircles, members = organizerMembers,
            requestAdd = addCollection, onAddOpened = { addCollection = false },
            collections = organizerCollections, payouts = organizerPayouts,
            onBack = { organizerMode = false },
            onAddCircle = onAddCircle, onAddMembers = onAddOrganizerMembers,
            onAssignTurn = onAssignOrganizerTurn, onUpdateContact = onUpdateOrganizerContact, onCollect = onOrganizerCollect,
            onPayout = onOrganizerPayout, onDeleteMember = onDeleteOrganizerMember,
            onDeleteCircle = onDeleteOrganizerCircle,
        )
        return
    }
    var receivingId by remember { mutableStateOf<String?>(null) }
    var deleteCommittee by remember { mutableStateOf<CommitteeEntity?>(null) }
    var pendingUndoDelete by remember { mutableStateOf<CommitteeEntity?>(null) }
    val undoBar = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    val visibleCommittees = committees.filter { it.id != pendingUndoDelete?.id }
    fun requestDeleteCommittee(committee: CommitteeEntity) {
        if (pendingUndoDelete == committee) return
        pendingUndoDelete = committee
        undoScope.launch {
            val result = undoBar.showSnackbar("Kameti removed", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
            if (result != SnackbarResult.ActionPerformed) onDelete(committee)
            if (pendingUndoDelete == committee) pendingUndoDelete = null
        }
    }
    var deleteReceipt by remember { mutableStateOf<CommitteeReceiptEntity?>(null) }
    var paying by remember { mutableStateOf<Pair<CommitteeEntity, Int>?>(null) }
    var addMemberTo by remember { mutableStateOf<CommitteeEntity?>(null) }
    var markMemberReceived by remember { mutableStateOf<CommitteeMemberEntity?>(null) }
    var receivePersonalMember by remember { mutableStateOf<CommitteeMemberEntity?>(null) }
    pickEntry?.let { action ->
        val choices: List<Pair<String, () -> Unit>> = when (action) {
            "kameti-paid" -> committees.flatMap { c ->
                (1..c.totalInstallments).filter { n -> payments.none { it.committeeId == c.id && it.installmentNumber == n } }
                    .map { n -> "${c.name} • #$n • ${scheduledMonth(c, n)}" to { paying = c to n; pickEntry = null } }
            }
            "kameti-receive" -> committees.filter { committeeBalance(it, receipts).remaining > 0 }
                .map { c -> c.name to { receivingId = c.id; pickEntry = null } }
            else -> committees.map { c -> c.name to { addMemberTo = c; pickEntry = null } }
        }
        com.sadique.dailyledger.ui.EntryRecordPicker("Choose kameti", choices, { pickEntry = null })
    }


    Scaffold(
        snackbarHost = { SnackbarHost(undoBar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }) { Icon(Icons.Rounded.Add, "New kameti") }
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
                    SummaryCard("Total paid", payments.sumOf { it.amountMinor }, Icons.Rounded.Payments, Modifier.weight(1f))
                    SummaryCard("Total received", receipts.sumOf { it.amountMinor }, Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f))
                }
            }
            enteringItem {
                val remainingToPay = visibleCommittees.filter { it.active }.sumOf { c -> (c.expectedPayout() - payments.filter { it.committeeId == c.id }.sumOf { it.amountMinor }).coerceAtLeast(0L) }
                val remainingToReceive = visibleCommittees.sumOf { committeeBalance(it, receipts).remaining }
                SummaryCard("Remaining to pay", remainingToPay, Icons.Rounded.Payments, Modifier.fillMaxWidth(),
                    detail = "Dena baqi: ${visibleMoney(remainingToPay)} • Milna baqi: ${visibleMoney(remainingToReceive)}")
            }
            enteringItem {
                val totalExpected = visibleCommittees.filter { it.active }.sumOf { it.expectedPayout() }
                val totalPaidK = payments.sumOf { it.amountMinor }
                if (totalExpected > 0) {
                    com.sadique.dailyledger.ui.LedgerRaisedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            com.sadique.dailyledger.ui.ProgressDonut(
                                progress = (totalPaidK.toFloat() / totalExpected.toFloat()).coerceIn(0f, 1f),
                                centerPercent = (totalPaidK * 100 / totalExpected).toInt(),
                                size = 76.dp, strokeWidth = 10.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Kameti progress", style = MaterialTheme.typography.titleMedium)
                                Text("Ada: ${visibleMoney(totalPaidK)} / Kul: ${visibleMoney(totalExpected)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (visibleCommittees.isEmpty()) {
                enteringItem { com.sadique.dailyledger.ui.LedgerEmptyState("No kameti yet",
                    "Kameti add karein. Start month aur installments se app automatically har turn ka month calculate karegi.",
                    "New kameti", Icons.Rounded.Groups, onAction = { add = true }) }
            }
            enteringItems(visibleCommittees, key = { it.id }) { committee ->
                val ps = payments.filter { it.committeeId == committee.id }
                val rs = receipts.filter { it.committeeId == committee.id }
                val ms = members.filter { it.committeeId == committee.id }.sortedBy { it.turnNumber }
                val balance = committeeBalance(committee, rs)
                val next = (1..committee.totalInstallments).firstOrNull { n -> ps.none { it.installmentNumber == n } }
                val nextMonth = next?.let { scheduledMonth(committee, it) }
                var history by remember(committee.id) { mutableStateOf(false) }
                var memberList by remember(committee.id) { mutableStateOf(true) }

                val committeeSwipe = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { deleteCommittee = committee; false } else false })
                Box {
                SwipeToDismissBox(state = committeeSwipe, enableDismissFromStartToEnd = false, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Rounded.Delete, null, Modifier.graphicsLayer { val s = 0.6f + 0.5f * committeeSwipe.progress.coerceIn(0f, 1f); scaleX = s; scaleY = s }, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }) {
                Card(
                    Modifier.fillMaxWidth(),
                    accent = com.sadique.dailyledger.ui.ledgerCardAccent(committee.id), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(committee.name, translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("${committee.sharesHalfUnits / 2.0} kametis • ${ps.size}/${committee.totalInstallments} installments paid", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { deleteCommittee = committee }) { Icon(Icons.Rounded.DeleteOutline, "Delete ${committee.name}") }
                        }

                        Text("Monthly payment: ${visibleMoney(committee.monthlyContribution())}", style = MaterialTheme.typography.titleSmall)
                        val paidCount = ps.map { it.installmentNumber }.distinct().size
                        val paidFraction = (paidCount.toFloat() / committee.totalInstallments.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val animatedPaid = com.sadique.dailyledger.ui.animatedProgress(paidFraction)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Installment progress", style = MaterialTheme.typography.bodySmall)
                            Text("$paidCount / ${committee.totalInstallments} • ${(animatedPaid * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { animatedPaid },
                            modifier = Modifier.fillMaxWidth().height(12.dp).testTag("kameti-installment-progress"),
                        )
                        nextMonth?.let { month ->
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("Next installment #$next • $month", style = MaterialTheme.typography.labelLarge)
                                    val badgeDate = runCatching {
                                        val ym = java.time.YearMonth.parse(month)
                                        if (ym.isAfter(java.time.YearMonth.now())) ym.atDay(1) else ym.atEndOfMonth()
                                    }.getOrNull()
                                    badgeDate?.let { date -> com.sadique.dailyledger.ui.DueDateBadge(date.toString()) }
                                }
                            }
                        }

                        HorizontalDivider()
                        BalanceRow("Expected receiving", balance.expected)
                        BalanceRow("Received", balance.received)
                        BalanceRow("Remaining", balance.remaining)
                        val receivedFraction = if (balance.expected > 0) (balance.received.toFloat() / balance.expected).coerceIn(0f, 1f) else 0f
                        val animatedReceived = com.sadique.dailyledger.ui.animatedProgress(receivedFraction)
                        LinearProgressIndicator(
                            progress = { animatedReceived },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
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
                            Icon(Icons.Rounded.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add member / turn")
                        }

                        if (ms.isNotEmpty() || committee.memberSchedule.isNotBlank()) {
                            TextButton(onClick = { memberList = !memberList }) {
                                Icon(if (memberList) Icons.Rounded.ExpandLess else Icons.Rounded.Groups, null)
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
                                Icon(if (history) Icons.Rounded.ExpandLess else Icons.Rounded.History, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (history) "Hide history" else "Receiving & payment history")
                            }
                        }
                        if (history) {
                            if (rs.isNotEmpty()) Text("Receiving history", style = MaterialTheme.typography.titleSmall)
                            rs.sortedWith(compareByDescending<CommitteeReceiptEntity> { it.date }.thenByDescending { it.createdAt }).forEach { receipt ->
                                com.sadique.dailyledger.ui.LedgerHistoryCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(receipt.id)) {
                                  Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(visibleMoney(receipt.amountMinor), style = MaterialTheme.typography.titleSmall)
                                        Text("${receipt.date ?: "Date not recorded"} • ${receipt.method}", style = MaterialTheme.typography.bodySmall)
                                        if (receipt.note.isNotBlank()) Text(receipt.note, translate = false, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { deleteReceipt = receipt }) { Icon(Icons.Rounded.DeleteOutline, "Delete receiving") }
                                  }
                                }
                            }
                            if (ps.isNotEmpty()) Text("Installments paid", style = MaterialTheme.typography.titleSmall)
                            ps.sortedByDescending { it.installmentNumber }.forEach {
                                com.sadique.dailyledger.ui.LedgerHistoryCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(it.id)) {
                                    PaymentRowWithProofs(it.id, "committee", repo) {
                                        Text("#${it.installmentNumber} • ${it.month} • ${visibleMoney(it.amountMinor)} • ${it.method}" + if (it.chargesSalaryBalance()) "" else " • Historical record", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                }
                com.sadique.dailyledger.ui.CelebrationBurst(active = committee.totalInstallments > 0 && ps.map { it.installmentNumber }.distinct().size >= committee.totalInstallments, modifier = Modifier.matchParentSize())
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
        MarkMemberReceivedDialog(member, { markMemberReceived = null }) { date ->
            onMemberReceived(member, date)
            markMemberReceived = null
        }
    }

    deleteCommittee?.let { committee ->
        DeleteCommitteeDialog(committee, { deleteCommittee = null }) {
            requestDeleteCommittee(committee)
            deleteCommittee = null
        }
    }

    deleteReceipt?.let { receipt ->
        DeleteReceiptDialog(receipt, { deleteReceipt = null }) {
            onDeleteReceipt(receipt)
            deleteReceipt = null
        }
    }
}
