package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerEntrySheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

@Composable internal fun FinanceSelect(label:String,value:String,options:List<String>,onSelect:(String)->Unit,enabled:Boolean=true) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("$label: $value")}
        DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option)},onClick={onSelect(option);expanded=false})}}
    }
}
@Composable internal fun FinanceFormDialog(title:String,dismiss:()->Unit,save:suspend()->Unit,enabled:Boolean=true,button:String="Save",content:@Composable ColumnScope.(Boolean)->Unit) {
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    LedgerEntrySheet(onDismissRequest={if(!busy)dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false),modifier=Modifier,title={Text(title)},
        text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(10.dp)){
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)};content(busy)
        }},confirmButton={Button(enabled=enabled&&!busy,onClick={scope.launch{busy=true;error=null;try{save();dismiss()}catch(e:Exception){if(e is kotlin.coroutines.cancellation.CancellationException)throw e;error=e.message ?: "Could not save. Retry."}finally{busy=false}}}){Text(if(busy)"Saving…" else button)}},
        dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text("Cancel")}})
}
internal fun signedMinor(text:String):Long? = runCatching{BigDecimal(text.trim()).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact()}.getOrNull()

@Composable internal fun FinanceItemDialog(kind:FinanceKind,accounts:List<String>,balance:(String)->Long,dismiss:()->Unit,
    initial:FinanceItem?=null,onSave:suspend(FinanceItem,Long,Int)->Unit) {
    var title by remember{mutableStateOf(initial?.title.orEmpty())};var amount by remember{mutableStateOf(initial?.let{plainAmount(it.amount)}.orEmpty())}
    var date by remember{mutableStateOf(initial?.date ?: LocalDate.now().toString())};var category by remember{mutableStateOf(initial?.category ?: "Other")}
    var account by remember{mutableStateOf(initial?.account ?: "Cash")};var destination by remember{mutableStateOf(accounts.first{it!=account})}
    var note by remember{mutableStateOf(initial?.note.orEmpty())};var quantity by remember{mutableStateOf(initial?.quantity.orEmpty())}
    var unit by remember{mutableStateOf(initial?.unit.orEmpty())};var rate by remember{mutableStateOf(initial?.rate?.takeIf{it>0}?.let(::plainAmount).orEmpty())}
    var already by remember{mutableStateOf(initial?.let{plainAmount(it.settled)} ?: "0")};var advance by remember{mutableStateOf("0")};var count by remember{mutableStateOf("6")}
    var shortcutType by remember{mutableStateOf(initial?.reference?.takeIf{it in listOf("INCOME","EXPENSE")} ?: "EXPENSE")}
    val total=if(quantity.isNotBlank())runCatching{quantityAmount(quantity,parseMinor(rate) ?: 0)}.getOrNull() else if(kind in listOf(FinanceKind.ACCOUNT,FinanceKind.ADJUSTMENT))signedMinor(amount) else parseMinor(amount)
    val dialogTitle=when(kind){FinanceKind.ACCOUNT->"Account opening balance";FinanceKind.TRANSFER->"Transfer between accounts";FinanceKind.ADJUSTMENT->"Reconcile actual balance";FinanceKind.GOAL->"Saving goal";FinanceKind.INSTALLMENT->"Installment purchase";FinanceKind.REIMBURSEMENT->"Pay on someone's behalf";FinanceKind.SHOPPING->"Shopping item";FinanceKind.SHORTCUT->"Custom shortcut";FinanceKind.ALLOCATION->"Salary allocation";else->"Planned payment"}
    FinanceFormDialog(dialogTitle,dismiss,save={
        val raw=requireNotNull(total){"Enter a valid amount."}
        val adjusted=raw
        val item=FinanceItem(id=initial?.id ?: UUID.randomUUID().toString(),kind=kind,title=title.ifBlank{if(kind==FinanceKind.ACCOUNT)account else dialogTitle},amount=adjusted,date=date,category=category,account=account,
            reference=if(kind==FinanceKind.TRANSFER)destination else if(kind==FinanceKind.SHORTCUT)shortcutType else initial?.reference.orEmpty(),quantity=quantity,unit=unit,rate=if(quantity.isBlank())0 else requireNotNull(parseMinor(rate)),
            settled=if(kind==FinanceKind.GOAL)requireNotNull(parseMinor(already)){"Enter saved amount or zero."} else initial?.settled ?: 0,note=note,opening=if(kind==FinanceKind.GOAL)initial?.opening ?: requireNotNull(parseMinor(already)) else 0).validated()
        onSave(item,parseMinor(advance) ?: error("Enter an advance or zero."),count.toIntOrNull() ?: error("Enter installment count."))
    },enabled=total!=null && isValidDate(date) && (title.isNotBlank() || kind in listOf(FinanceKind.ADJUSTMENT,FinanceKind.TRANSFER))) { busy ->
        if(kind==FinanceKind.ACCOUNT)Text("Enter the balance before your tracked transactions. Use reconciliation to match today's balance.")
        LedgerTextField(title,{title=it},label={Text(if(kind==FinanceKind.ACCOUNT)"Account name" else "Name / purpose")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        if(kind!=FinanceKind.ACCOUNT)FinanceSelect("Account",account,accounts,{account=it;if(destination==it)destination=accounts.first{x->x!=it}},!busy)
        if(kind==FinanceKind.TRANSFER)FinanceSelect("To account",destination,accounts.filter{it!=account},{destination=it},!busy)
        if(kind==FinanceKind.ADJUSTMENT)Text("Tracked balance: ${money(balance(account))}. The actual difference is checked again when saving; it affects this account, never salary income or expenses.")
        LedgerTextField(amount,{amount=it},label={Text(if(kind==FinanceKind.ADJUSTMENT)"Actual balance PKR" else if(kind==FinanceKind.GOAL)"Target PKR" else if(kind==FinanceKind.INSTALLMENT)"Total price PKR" else "Amount PKR")},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        if(kind==FinanceKind.SHOPPING){
            LedgerTextField(quantity,{quantity=it},label={Text("Quantity (optional)")},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            LedgerTextField(unit,{unit=it},label={Text("Unit (litres, kg, pieces)")},enabled=!busy)
            LedgerTextField(rate,{rate=it},label={Text("Rate per unit PKR")},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            if(quantity.isNotBlank())Text("Calculated amount: ${total?.let(::money) ?: "Check quantity and rate"}")
        }
        if(kind==FinanceKind.SHORTCUT)FinanceSelect("Entry type",shortcutType,listOf("EXPENSE","INCOME"),{shortcutType=it},!busy)
        if(kind !in listOf(FinanceKind.ACCOUNT,FinanceKind.TRANSFER,FinanceKind.ADJUSTMENT,FinanceKind.GOAL))CategoryDropdown(if(kind==FinanceKind.SHORTCUT)shortcutType else "EXPENSE",category,{category=it},enabled=!busy)
        LedgerDateField(date,{date=it},label=when(kind){FinanceKind.GOAL->"Goal deadline";FinanceKind.INSTALLMENT->"First installment date";else->"Plan date"},enabled=!busy)
        if(kind==FinanceKind.GOAL)LedgerTextField(already,{already=it},readOnly=initial!=null,label={Text("Already saved toward goal PKR")},supportingText={Text("Existing savings only; this does not create another saving entry.")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
        if(kind==FinanceKind.INSTALLMENT){LedgerTextField(advance,{advance=it},label={Text("Advance PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy);LedgerTextField(count,{count=it},label={Text("Number of monthly installments")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=!busy)}
        LedgerTextField(note,{note=it},label={Text(if(kind in listOf(FinanceKind.TRANSFER,FinanceKind.ADJUSTMENT))"Reason (required)" else "Note")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    }
}

@Composable internal fun FinanceEntryEditor(owner:String,accounts:List<String>,initial:TransactionEntity?,template:TransactionEntity?=null,defaultPrivate:Boolean=false,dismiss:()->Unit,onSave:suspend(TransactionEntity)->Unit) {
    val context=LocalContext.current;val source=initial ?: template
    var type by remember{mutableStateOf(source?.type ?: "EXPENSE")};var amount by remember{mutableStateOf(source?.amountMinor?.takeIf{it>0}?.let(::plainAmount).orEmpty())}
    var category by remember{mutableStateOf(source?.category?.takeIf{it.isNotBlank()} ?: "Other")};var note by remember{mutableStateOf(source?.note.orEmpty())}
    var date by remember{mutableStateOf(source?.date ?: LocalDate.now().toString())};var account by remember{mutableStateOf(source?.account ?: "Cash")}
    var quantity by remember{mutableStateOf(source?.quantity.orEmpty())};var unit by remember{mutableStateOf(source?.unit.orEmpty())}
    var rate by remember{mutableStateOf(source?.unitPriceMinor?.takeIf{it>0}?.let(::plainAmount).orEmpty())};var hidden by remember{mutableStateOf(source?.privateEntry ?: defaultPrivate)}
    val ownerLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(hidden) {
        val protects=hidden
        if(protects)com.sadique.dailyledger.security.SensitiveEditors.enter()
        onDispose { if(protects)com.sadique.dailyledger.security.SensitiveEditors.leave() }
    }
    DisposableEffect(ownerLifecycle,hidden) {
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(hidden && event==androidx.lifecycle.Lifecycle.Event.ON_STOP)dismiss()}
        ownerLifecycle.lifecycle.addObserver(observer)
        onDispose{ownerLifecycle.lifecycle.removeObserver(observer)}
    }
    val total=if(quantity.isBlank())parseMinor(amount) else runCatching{quantityAmount(quantity,parseMinor(rate) ?: 0)}.getOrNull()
    FinanceFormDialog(if(initial==null)"New detailed entry" else "Edit entry details",dismiss,save={
        if(hidden)check(com.sadique.dailyledger.security.Biometrics.pick(context)!=null || context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure){"Set a phone PIN or fingerprint before creating private entries."}
        val now=System.currentTimeMillis()
        onSave(TransactionEntity(initial?.id ?: "$owner:${UUID.randomUUID()}",owner,type,requireNotNull(total),category,note,date,initial?.createdAt ?: now,now,account,quantity,unit,if(quantity.isBlank())0 else requireNotNull(parseMinor(rate)),initial?.refundOf.orEmpty(),hidden))
    },enabled=total?.let{it>0}==true && isValidDate(date)) { busy ->
        if(initial?.refundOf.isNullOrBlank())FinanceSelect("Entry type",type,listOf("EXPENSE","INCOME"),{type=it;if(it=="INCOME"){quantity="";unit="";rate=""}},!busy)
        FinanceSelect("Account",account,accounts,{account=it},!busy)
        LedgerTextField(amount,{amount=it},readOnly=quantity.isNotBlank(),label={Text("Amount PKR")},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        CategoryDropdown(type,category,{category=it},enabled=!busy && initial?.refundOf.isNullOrBlank())
        LedgerTextField(note,{note=it},label={Text("Item / note")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        if(type=="EXPENSE" && initial?.refundOf.isNullOrBlank()){
            LedgerTextField(quantity,{quantity=it},label={Text("Quantity (optional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
            LedgerTextField(unit,{unit=it},label={Text("Unit (litres, kg, pieces)")},enabled=!busy)
            LedgerTextField(rate,{rate=it},label={Text("Rate per unit PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
            if(quantity.isNotBlank())Text("Calculated amount: ${total?.let(::money) ?: "Check quantity and rate"}")
        }
        LedgerDateField(date,{date=it},enabled=!busy)
        Row { Checkbox(hidden,{hidden=it},enabled=!busy);Text("Private entry") }
        Text("Private entries need phone screen lock. They remain part of financial totals and Drive app-data JSON backup (not end-to-end encrypted).")
    }
}
@Composable internal fun AmountDateDialog(title:String,max:Long,accounts:List<String>,dismiss:()->Unit,onSave:suspend(Long,String,String)->Unit) {
    var amount by remember{mutableStateOf(plainAmount(max))};var date by remember{mutableStateOf(LocalDate.now().toString())};var account by remember{mutableStateOf(accounts.first())}
    val parsed=parseMinor(amount)
    FinanceFormDialog(title,dismiss,save={onSave(requireNotNull(parsed),date,account)},enabled=parsed?.let{it in 1..max}==true && isValidDate(date)) {busy->
        Text("Available: ${money(max)}")
        LedgerTextField(amount,{amount=it},label={Text("Amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
        FinanceSelect("Account",account,accounts,{account=it},!busy);LedgerDateField(date,{date=it},enabled=!busy)
    }
}
private data class SplitRow(val id:String=UUID.randomUUID().toString(),val category:String="Other",val amount:String="",val note:String="")
@Composable internal fun SplitBillDialog(accounts:List<String>,dismiss:()->Unit,onSave:suspend(List<TransactionDraft>,Long,String,String)->Unit) {
    var total by remember{mutableStateOf("")};var rows by remember{mutableStateOf(listOf(SplitRow(),SplitRow()))};var date by remember{mutableStateOf(LocalDate.now().toString())};var account by remember{mutableStateOf("Cash")}
    val batch=remember{UUID.randomUUID().toString()};val totalMinor=parseMinor(total);val sum=rows.sumOf{parseMinor(it.amount) ?: 0}
    FinanceFormDialog("Split a bill",dismiss,save={onSave(rows.map{TransactionDraft("EXPENSE",requireNotNull(parseMinor(it.amount)),it.category,it.note,date)},requireNotNull(totalMinor),account,batch)},
        enabled=totalMinor?.let{it>0 && it==sum}==true && rows.all{(parseMinor(it.amount) ?: 0)>0} && isValidDate(date)) {busy->
        LedgerTextField(total,{total=it},label={Text("Bill total PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
        FinanceSelect("Account",account,accounts,{account=it},!busy);LedgerDateField(date,{date=it},enabled=!busy)
        rows.forEach { row -> key(row.id){LedgerCard(contentSpacing=8.dp){
            CategoryDropdown("EXPENSE",row.category,{c->rows=rows.map{if(it.id==row.id)it.copy(category=c) else it}},enabled=!busy)
            LedgerTextField(row.amount,{a->rows=rows.map{if(it.id==row.id)it.copy(amount=a) else it}},label={Text("Split amount PKR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy)
            LedgerTextField(row.note,{n->rows=rows.map{if(it.id==row.id)it.copy(note=n) else it}},label={Text("Item / note")},enabled=!busy)
            if(rows.size>2)TextButton(onClick={rows=rows.filterNot{it.id==row.id}},enabled=!busy){Text("Remove row")}
        }} }
        if(rows.size<50)OutlinedButton(onClick={rows=rows+SplitRow()},enabled=!busy){Text("Add row")}
        Text("Split total: ${money(sum)} • Difference: ${money((totalMinor ?: 0)-sum)}")
    }
}
