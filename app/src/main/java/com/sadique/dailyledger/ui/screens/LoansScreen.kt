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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.isValidDate
import com.sadique.dailyledger.ui.money
import com.sadique.dailyledger.ui.parseMinor
import com.sadique.dailyledger.ui.plainAmount
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
    var pickPayment by remember { mutableStateOf(false) }
    LaunchedEffect(requestPayment) { if (requestPayment) { pickPayment = true; onAddOpened() } }
    if (pickPayment) com.sadique.dailyledger.ui.EntryRecordPicker("Choose payment record",
        loans.filter { row -> row.principalMinor > payments.filter { it.loanId == row.id }.sumOf { it.amountMinor } }
            .map { row -> row.person to { pay = row; pickPayment = false } },
        { pickPayment = false })

    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, null) } }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            enteringItem { Text("Loans", style = MaterialTheme.typography.headlineMedium); Text("Borrowed and lent money, contacts and repayments in one place.") }
            enteringItems(loans, key = { it.id }) { loan ->
                var showHistory by remember(loan.id) { mutableStateOf(false) }
                val paid = payments.filter { it.loanId == loan.id }.sumOf { it.amountMinor }
                val left = (loan.principalMinor - paid).coerceAtLeast(0L)
                com.sadique.dailyledger.ui.LedgerRecordCard() {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("${loan.person} • ${com.sadique.dailyledger.i18n.LocalStrings.current.text(if (loan.direction == "BORROWED") "I borrowed" else "Borrowed from me")}", translate = false, style = MaterialTheme.typography.titleMedium)
                                Text("Outstanding ${visibleMoney(left)} / ${visibleMoney(loan.principalMinor)}")
                                loan.dueDate?.let { Text("Due $it • ${dueLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                                if (left == 0L || loan.closed) Text("Settled", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { onDelete(loan) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                        }
                        if (loan.phone.isNotBlank() || loan.whatsapp.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (loan.phone.isNotBlank()) { AssistChip(onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${loan.phone}"))) }, label = { Text("Call") }, leadingIcon = { Icon(Icons.Outlined.Call, null) }); AssistChip(onClick = { context.openContactAction(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${loan.phone}")).putExtra("sms_body", reminder(loan, left))) }, label = { Text("SMS") }, leadingIcon = { Icon(Icons.Outlined.Sms, null) }) }
                            val wa = loan.whatsapp.ifBlank { loan.phone }
                            if (wa.isNotBlank()) AssistChip(onClick = { val num=whatsappNumber(wa); val msg=URLEncoder.encode(reminder(loan,left), StandardCharsets.UTF_8.toString()); context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=$msg"))) }, label = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.Outlined.Chat, null) })
                        }
                        Button(onClick = { pay = loan }, enabled = left > 0, modifier = Modifier.fillMaxWidth()) { Text(if (loan.direction == "BORROWED") "Record my repayment" else "Record payment received") }
                        val allPayments = payments.filter { it.loanId == loan.id }
                        if (allPayments.size > 3) TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Show recent payments" else "Show all ${allPayments.size} payments") }
                        val history = if (showHistory) allPayments else allPayments.take(3)
                        history.forEach { payment ->
                            com.sadique.dailyledger.ui.LedgerHistoryCard() {
                                PaymentRowWithProofs(payment.id, "loan", repo) {
                                    Text("${payment.date} • ${visibleMoney(payment.amountMinor)} • ${payment.method}", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) LoanDialog({ add = false }) { d,p,a,due,n,phone,wa -> onAdd(d,p,a,due,n,phone,wa); add=false }
    pay?.let { loan ->
        val paid = payments.filter { it.loanId == loan.id }.sumOf { it.amountMinor }
        val remaining = (loan.principalMinor - paid).coerceAtLeast(0L)
        PaymentDialog(loan, remaining, { pay = null }) { a,date,n,method -> onPayment(loan.id,a,date,n,method); pay=null }
    }
}

private fun reminder(loan: LoanEntity, left: Long): String = if (loan.direction == "LENT") "Assalam o Alaikum ${loan.person}, Daily Ledger record ke mutabiq ${money(left)} loan balance pending hai. Kindly payment/update share kar dein." else "Assalam o Alaikum ${loan.person}, meri taraf ${money(left)} loan balance remaining hai. Payment ke hawale se rabta kar raha hoon."

@Composable private fun LoanDialog(dismiss:()->Unit, save:(String,String,Long,String?,String,String,String)->Unit){
    var dir by remember{mutableStateOf("BORROWED")}; var person by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var due by remember{mutableStateOf("")}; var note by remember{mutableStateOf("")}; var phone by remember{mutableStateOf("")}; var wa by remember{mutableStateOf("")}
    LedgerEntrySheet(onDismissRequest=dismiss,title={Text("New loan")},text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){Row{FilterChip(dir=="BORROWED",{dir="BORROWED"},{Text("I borrowed")});Spacer(Modifier.width(8.dp));FilterChip(dir=="LENT",{dir="LENT"},{Text("Borrowed from me")})};ContactPickerButton { pickedName, pickedPhone -> if (person.isBlank()) person = pickedName; phone = pickedPhone; if (wa.isBlank()) wa = pickedPhone };OutlinedTextField(person,{person=it},label={Text("Person")});OutlinedTextField(phone,{phone=it},label={Text("Phone (optional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone));OutlinedTextField(wa,{wa=it},label={Text("WhatsApp (optional)")},supportingText={Text("Leave blank to use phone number")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone));OutlinedTextField(amount,{amount=it},label={Text("Amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));LedgerDateField(due, { due = it }, label = "Due date (optional)", optional = true);OutlinedTextField(note,{note=it},singleLine=true,label={Text("Note")})}},confirmButton={Button(enabled=(due.isBlank()||isValidDate(due)) && (parseMinor(amount)?.let { it > 0 } == true),onClick={parseMinor(amount)?.takeIf{it>0}?.let{save(dir,person.ifBlank{"Unknown"},it,due.trim().ifBlank{null},note,phone,wa)}}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable private fun PaymentDialog(loan:LoanEntity,remaining:Long,dismiss:()->Unit,save:(Long,String,String,String)->Unit){
    var amount by remember(loan.id){mutableStateOf(plainAmount(remaining))};var date by remember{mutableStateOf(LedgerRepository.today())};var note by remember{mutableStateOf("")};var method by remember{mutableStateOf("Cash")}
    LedgerEntrySheet(onDismissRequest=dismiss,title={Text(if(loan.direction=="BORROWED")"Record repayment" else "Record amount received")},text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(amount,{amount=it},label={Text("Amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));LedgerDateField(date, { date = it }, label = "Entry date");Text("Payment method",style=MaterialTheme.typography.labelLarge);FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Cash","Bank","Easypaisa","JazzCash").forEach{FilterChip(method==it,{method=it},{Text(it)})}};OutlinedTextField(note,{note=it},singleLine=true,label={Text("Note")})}},confirmButton={Button(enabled=isValidDate(date) && (parseMinor(amount)?.let{it in 1..remaining}==true),onClick={parseMinor(amount)?.takeIf{it in 1..remaining}?.let{save(it,date.trim(),note,method)}}){Text("Record")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
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
