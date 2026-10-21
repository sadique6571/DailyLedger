package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable
fun OrganizerKametiScreen(
    circles: List<OrganizerCircleEntity>,
    members: List<OrganizerMemberEntity>,
    collections: List<OrganizerCollectionEntity>,
    payouts: List<OrganizerPayoutEntity>,
    onBack: () -> Unit,
    onAddCircle: (String, Long, Int, String) -> Unit,
    requestAdd: Boolean = false, onAddOpened: () -> Unit = {},
    onAddMembers: suspend (String, List<OrganizerMemberDraft>, String) -> Unit,
    onAssignTurn: (String, Int, String) -> Unit,
    onUpdateContact: (String, String, String, String) -> Unit,
    onCollect: (String, String, Long, String, String) -> Unit,
    onPayout: (String, Long, String, String) -> Unit,
    onDeleteMember: (String) -> Unit,
    onDeleteCircle: (String) -> Unit,
    repo: LedgerRepository? = null,
) {
    var addCircle by remember { mutableStateOf(false) }
    LaunchedEffect(requestAdd) { if (requestAdd) { addCircle = true; onAddOpened() } }
    var memberFor by remember { mutableStateOf<OrganizerCircleEntity?>(null) }
    var payMember by remember { mutableStateOf<Pair<OrganizerMemberEntity, Boolean>?>(null) }
    var drawMember by remember { mutableStateOf<OrganizerMemberEntity?>(null) }
    var editContact by remember { mutableStateOf<OrganizerMemberEntity?>(null) }
    var deleteMember by remember { mutableStateOf<OrganizerMemberEntity?>(null) }
    var deleteCircle by remember { mutableStateOf<OrganizerCircleEntity?>(null) }
    var shareMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().toString()) }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { addCircle = true }) { Text("+ New collection") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(bottom = 88.dp).testTag("organizer-kameti-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
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
                OrganizerCircleCard(
                    repo = repo, circle = circle,
                    roster = members.filter { it.circleId == circle.id },
                    collections = collections,
                    payouts = payouts,
                    selectedMonth = selectedMonth,
                    onDeleteCircle = { deleteCircle = it },
                    onAddMember = { memberFor = it },
                    onDrawMember = { drawMember = it },
                    onEditContact = { editContact = it },
                    onDeleteMember = { deleteMember = it },
                    onPayMember = { member, payout -> payMember = member to payout },
                    onShareReminder = { phone, message -> shareMessage = phone to message },
                )
            }
        }
    }
    if (addCircle) OrganizerCircleDialog({ addCircle = false }) { name, amount, slots, start ->
        onAddCircle(name, amount, slots, start); addCircle = false
    }
    memberFor?.let { circle ->
        OrganizerMemberDialog(circle, members.filter { it.circleId == circle.id }, { memberFor = null }) { requestId, drafts ->
            onAddMembers(circle.id, drafts, requestId)
        }
    }
    drawMember?.let { member ->
        val circle = circles.firstOrNull { it.id == member.circleId }
        if (circle != null) OrganizerDrawDialog(member, circle,
            members.filter { it.circleId == circle.id }, { drawMember = null }) { n, method ->
            onAssignTurn(member.id, n, method); drawMember = null
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
    editContact?.let { member ->
        OrganizerContactDialog(member, { editContact = null }) { name, phone, whatsapp ->
            onUpdateContact(member.id, name, phone, whatsapp); editContact = null
        }
    }
    deleteMember?.let { member ->
        DeleteOrganizerMemberDialog(onDismiss = { deleteMember = null },
            onConfirm = { onDeleteMember(member.id); deleteMember = null })
    }
    deleteCircle?.let { circle ->
        DeleteOrganizerCircleDialog(onDismiss = { deleteCircle = null },
            onConfirm = { onDeleteCircle(circle.id); deleteCircle = null })
    }
    shareMessage?.let { (phone, message) ->
        ShareReminderDialog(phone, message, onDismiss = { shareMessage = null },
            onConfirm = { shareMessage = null })
    }
}
