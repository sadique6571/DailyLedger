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

@Composable internal fun FinanceSelect(label:String,value:String,options:List<String>,onSelect:(String)->Unit,enabled:Boolean=true) {
    var expanded by remember { mutableStateOf(false) }
    val inline = LocalEntrySheet.current
    Column {
        OutlinedButton(onClick={expanded=!expanded},enabled=enabled,modifier=Modifier.fillMaxWidth(),
            shape=LedgerTokens.ShapeSmall,
            colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f)),
            border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.55f))){Text("$label: $value")}
        if (inline && expanded) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        TextButton(onClick = { onSelect(option); expanded=false }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                    }
                }
            }
        } else if (!inline) {
            DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option)},onClick={onSelect(option);expanded=false})}}
        }
    }
}
@Composable internal fun FinanceFormDialog(title:String,dismiss:()->Unit,save:suspend()->Unit,enabled:Boolean=true,button:String="Save",content:@Composable ColumnScope.(Boolean)->Unit) {
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    LedgerEntrySheet(canDismiss = !busy, onDismissRequest={if(!busy)dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false),modifier=Modifier,title={Text(title)},
        text={Column(Modifier,verticalArrangement=Arrangement.spacedBy(10.dp)){
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)};content(busy)
        }},confirmButton={Button(shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeSmall, enabled=enabled&&!busy,onClick={if(!busy){busy=true;error=null;scope.launch{try{save();dismiss()}catch(e:Exception){if(e is kotlin.coroutines.cancellation.CancellationException)throw e;error=e.message ?: "Could not save. Retry."}finally{busy=false}}}}){Text(if(busy)"Saving…" else button)}},
        dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text("Cancel")}})
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
        LedgerTextField(note,{note=it},singleLine=true,label={Text("Item / note")},enabled=!busy,modifier=Modifier.fillMaxWidth())
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
