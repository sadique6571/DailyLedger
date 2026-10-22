@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerEntrySheet

import com.sadique.dailyledger.ui.LedgerDateField

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun LoansScreen(
    loans: List<LoanEntity>, payments: List<LoanPaymentEntity>,
    repo: LedgerRepository? = null,
    onAdd: (String, String, Long, String?, String, String, String) -> Unit,
    onPayment: (String, Long, String, String, String) -> Unit,
    onDelete: (LoanEntity) -> Unit,
    requestAdd: Boolean = false, onAddOpened: () -> Unit = {},
    requestPayment: Boolean = false,
) {
    val context = LocalContext.current
    val amountHidden = LocalDisplayPreferences.current?.privateAmounts == true
    val visibleMoney: (Long) -> String = { if (amountHidden) "Rs •••••" else money(it) }
    var add by remember { mutableStateOf(false) }
    LaunchedEffect(requestAdd) { if (requestAdd) { add = true; onAddOpened() } }
    var pay by remember { mutableStateOf<LoanEntity?>(null) }
    // PAID stamp (J): slams onto the loan card right after a payment is recorded.
    var stampLoanId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stampLoanId) { if (stampLoanId != null) { kotlinx.coroutines.delay(1500); stampLoanId = null } }
    var pendingUndoDelete by remember { mutableStateOf<LoanEntity?>(null) }
    val undoBar = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    var loanQuery by remember { mutableStateOf("") }
    val visibleLoans = loans.filter { it.id != pendingUndoDelete?.id }
        .filter { loanQuery.isBlank() || it.person.contains(loanQuery, true) || it.note.contains(loanQuery, true) || it.phone.contains(loanQuery) }
    fun requestDelete(loan: LoanEntity) {
        if (pendingUndoDelete == loan) return
        pendingUndoDelete = loan
        undoScope.launch {
            val result = undoBar.showSnackbar("Loan removed", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
            if (result != SnackbarResult.ActionPerformed) onDelete(loan)
            if (pendingUndoDelete == loan) pendingUndoDelete = null
        }
    }
    var pickPayment by remember { mutableStateOf(false) }
        LaunchedEffect(requestPayment) { if (requestPayment) { pickPayment = true; onAddOpened() } }
    if (pickPayment) com.sadique.dailyledger.ui.EntryRecordPicker("Choose payment record",
        loans.filter { row -> row.principalMinor > payments.filter { it.loanId == row.id }.sumOf { it.amountMinor } }
            .map { row -> row.person to { pay = row; pickPayment = false } },
        { pickPayment = false })

    Scaffold(snackbarHost = { SnackbarHost(undoBar) { data -> com.sadique.dailyledger.ui.UndoCountdownSnackbar(data) } }, floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, "Add loan") } }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            enteringItem { Text("Loans", style = MaterialTheme.typography.headlineMedium); Text("Borrowed and lent money, contacts and repayments in one place.") }
            enteringItem {
                OutlinedTextField(loanQuery, { loanQuery = it }, placeholder = { Text("Search loans by name") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
            if (loans.isNotEmpty() && visibleLoans.isEmpty()) enteringItem {
                Text("No loans match your search.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            enteringItem {
                val borrowedLeft = visibleLoans.filter { it.direction == "BORROWED" && !it.closed }.sumOf { l -> (l.principalMinor - payments.filter { it.loanId == l.id }.sumOf { it.amountMinor }).coerceAtLeast(0L) }
                val lentLeft = visibleLoans.filter { it.direction == "LENT" && !it.closed }.sumOf { l -> (l.principalMinor - payments.filter { it.loanId == l.id }.sumOf { it.amountMinor }).coerceAtLeast(0L) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.sadique.dailyledger.ui.SummaryCard("Total dena hai", borrowedLeft, Icons.Rounded.Payments, Modifier.weight(1f))
                    com.sadique.dailyledger.ui.SummaryCard("Total lena hai", lentLeft, Icons.Rounded.Handshake, Modifier.weight(1f))
                }
            }
            enteringItem {
                val openLoans = visibleLoans.filter { !it.closed }
                val principal = openLoans.sumOf { it.principalMinor }
                if (principal > 0) {
                    val paidAll = openLoans.sumOf { l -> payments.filter { it.loanId == l.id }.sumOf { it.amountMinor } }
                    com.sadique.dailyledger.ui.LedgerRaisedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            com.sadique.dailyledger.ui.ProgressDonut(
                                progress = (paidAll.toFloat() / principal.toFloat()).coerceIn(0f, 1f),
                                centerPercent = (paidAll * 100 / principal).toInt(),
                                size = 76.dp, strokeWidth = 10.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Overall loan progress", style = MaterialTheme.typography.titleMedium)
                                Text("Ada ho chuka: ${visibleMoney(paidAll)} / ${visibleMoney(principal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (visibleLoans.isEmpty()) enteringItem {
                com.sadique.dailyledger.ui.LedgerEmptyState("No loans yet", "Track money borrowed or lent and record repayments here.",
                    "Add loan", Icons.Rounded.Handshake, onAction = { add = true })
            }
            enteringItems(visibleLoans, key = { it.id }) { loan ->
                var showHistory by remember(loan.id) { mutableStateOf(false) }
                val paid = payments.filter { it.loanId == loan.id }.sumOf { it.amountMinor }
                val left = (loan.principalMinor - paid).coerceAtLeast(0L)
                val loanSwipe = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { requestDelete(loan); false } else false })
                Box {
                SwipeToDismissBox(state = loanSwipe, enableDismissFromStartToEnd = false, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Rounded.Delete, null, Modifier.graphicsLayer { val s = 0.6f + 0.5f * loanSwipe.progress.coerceIn(0f, 1f); scaleX = s; scaleY = s }, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }) {
                com.sadique.dailyledger.ui.LedgerRecordCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(loan.id)) {
                    Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("${loan.person} • ${com.sadique.dailyledger.i18n.LocalStrings.current.text(if (loan.direction == "BORROWED") "I borrowed" else "Borrowed from me")}", translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("Outstanding ${visibleMoney(left)} / ${visibleMoney(loan.principalMinor)}")
                                loan.dueDate?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Due $it", style = MaterialTheme.typography.bodySmall)
                                        com.sadique.dailyledger.ui.DueDateBadge(it)
                                    }
                                }
                                if (left == 0L || loan.closed) Text("Settled", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { requestDelete(loan) }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                        }
                        if (loan.phone.isNotBlank() || loan.whatsapp.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (loan.phone.isNotBlank()) { AssistChip(onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${loan.phone}"))) }, label = { Text("Call") }, leadingIcon = { Icon(Icons.Rounded.Call, null) }); AssistChip(onClick = { context.openContactAction(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${loan.phone}")).putExtra("sms_body", reminder(loan, left))) }, label = { Text("SMS") }, leadingIcon = { Icon(Icons.Rounded.Sms, null) }) }
                            val wa = loan.whatsapp.ifBlank { loan.phone }
                            if (wa.isNotBlank()) AssistChip(onClick = { val num=whatsappNumber(wa); val msg=URLEncoder.encode(reminder(loan,left), StandardCharsets.UTF_8.toString()); context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=$msg"))) }, label = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.Rounded.Chat, null) })
                        }
                        Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, onClick = { pay = loan }, enabled = left > 0, modifier = Modifier.fillMaxWidth()) { Text(if (loan.direction == "BORROWED") "Record my repayment" else "Record payment received") }
                        val allPayments = payments.filter { it.loanId == loan.id }
                        if (allPayments.size > 3) TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Show recent payments" else "Show all ${allPayments.size} payments") }
                        allPayments.take(3).forEach { payment -> LoanPaymentRow(payment, repo, visibleMoney) }
                        androidx.compose.animation.AnimatedVisibility(visible = showHistory && allPayments.size > 3,
                            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)),
                            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allPayments.drop(3).forEach { payment -> LoanPaymentRow(payment, repo, visibleMoney) }
                            }
                        }
                    }
                }
                }
                com.sadique.dailyledger.ui.CelebrationBurst(active = left <= 0L && loan.principalMinor > 0, modifier = Modifier.matchParentSize())
                com.sadique.dailyledger.ui.PaidStamp(visible = stampLoanId == loan.id, modifier = Modifier.matchParentSize())
                }
            }
        }
    }
    if (add) LoanDialog({ add = false }) { d,p,a,due,n,phone,wa -> onAdd(d,p,a,due,n,phone,wa); add=false }
    pay?.let { loan ->
        val paid = payments.filter { it.loanId == loan.id }.sumOf { it.amountMinor }
        val remaining = (loan.principalMinor - paid).coerceAtLeast(0L)
        PaymentDialog(loan, remaining, { pay = null }) { a,date,n,method -> onPayment(loan.id,a,date,n,method); stampLoanId = loan.id; pay=null }
    }
}

@Composable
private fun LoanPaymentRow(payment: LoanPaymentEntity, repo: LedgerRepository?, visibleMoney: (Long) -> String) {
    com.sadique.dailyledger.ui.LedgerHistoryCard(accent = com.sadique.dailyledger.ui.ledgerCardAccent(payment.id)) {
        PaymentRowWithProofs(payment.id, "loan", repo) {
            Text("${payment.date} • ${visibleMoney(payment.amountMinor)} • ${payment.method}", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun reminder(loan: LoanEntity, left: Long): String = if (loan.direction == "LENT") "Assalam o Alaikum ${loan.person}, Daily Ledger record ke mutabiq ${money(left)} loan balance pending hai. Kindly payment/update share kar dein." else "Assalam o Alaikum ${loan.person}, meri taraf ${money(left)} loan balance remaining hai. Payment ke hawale se rabta kar raha hoon."

@Composable private fun LoanDialog(dismiss:()->Unit, save:(String,String,Long,String?,String,String,String)->Unit){
    var dir by remember{mutableStateOf("BORROWED")}; var person by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var due by remember{mutableStateOf("")}; var note by remember{mutableStateOf("")}; var phone by remember{mutableStateOf("")}; var wa by remember{mutableStateOf("")}
    LedgerEntrySheet(onDismissRequest=dismiss,title={Text("New loan")},text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){Row{FilterChip(dir=="BORROWED",{dir="BORROWED"},{Text("I borrowed")});Spacer(Modifier.width(8.dp));FilterChip(dir=="LENT",{dir="LENT"},{Text("Borrowed from me")})};ContactPickerButton { pickedName, pickedPhone -> if (person.isBlank()) person = pickedName; phone = pickedPhone; if (wa.isBlank()) wa = pickedPhone };OutlinedTextField(person,{person=it},label={Text("Person")});OutlinedTextField(phone,{phone=it},label={Text("Phone (optional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone));OutlinedTextField(wa,{wa=it},label={Text("WhatsApp (optional)")},supportingText={Text("Leave blank to use phone number")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone));OutlinedTextField(amount,{amount=it},label={Text("Amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));LedgerDateField(due, { due = it }, label = "Due date (optional)", optional = true);OutlinedTextField(note,{note=it},singleLine=true,label={Text("Note")})}},confirmButton={Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, enabled=(due.isBlank()||isValidDate(due)) && (parseMinor(amount)?.let { it > 0 } == true),onClick={parseMinor(amount)?.takeIf{it>0}?.let{save(dir,person.ifBlank{"Unknown"},it,due.trim().ifBlank{null},note,phone,wa)}}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable private fun PaymentDialog(loan:LoanEntity,remaining:Long,dismiss:()->Unit,save:(Long,String,String,String)->Unit){
    var amount by remember(loan.id){mutableStateOf(plainAmount(remaining))};var date by remember{mutableStateOf(LedgerRepository.today())};var note by remember{mutableStateOf("")};var method by remember{mutableStateOf("Cash")}
    LedgerEntrySheet(onDismissRequest=dismiss,title={Text(if(loan.direction=="BORROWED")"Record repayment" else "Record amount received")},text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(amount,{amount=it},label={Text("Amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));LedgerDateField(date, { date = it }, label = "Entry date");Text("Payment method",style=MaterialTheme.typography.labelLarge);FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Cash","Bank","Easypaisa","JazzCash").forEach{FilterChip(method==it,{method=it},{Text(it)})}};OutlinedTextField(note,{note=it},singleLine=true,label={Text("Note")})}},confirmButton={Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, enabled=isValidDate(date) && (parseMinor(amount)?.let{it in 1..remaining}==true),onClick={parseMinor(amount)?.takeIf{it in 1..remaining}?.let{save(it,date.trim(),note,method)}}){Text("Record")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}


private fun dueLabel(date: String): String = runCatching {
    val due = java.time.LocalDate.parse(date)
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    when {
        days > 0 -> "$days days left"
        days == 0L -> "due today"
        else -> "${-days} days overdue"
    }
}.getOrDefault("date check")


private fun whatsappNumber(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0092") -> digits.removePrefix("00")
        digits.startsWith("92") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "92" + digits.drop(1)
        else -> digits
    }
}
