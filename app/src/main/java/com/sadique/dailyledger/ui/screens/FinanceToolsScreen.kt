package com.sadique.dailyledger.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import com.sadique.dailyledger.security.Biometrics
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONObject

@Composable
fun FinanceToolsScreen(vm:MainViewModel) {
    val context=LocalContext.current
    val profile by vm.repo.profileBackup.collectAsState(initial=ProfileBackupData())
    val tx by vm.transactions.collectAsState(); val savings by vm.savings.collectAsState()
    val committees by vm.committees.collectAsState();val payments by vm.committeePayments.collectAsState()
    val receipts by vm.committeeReceipts.collectAsState();val credits by vm.creditPurchases.collectAsState()
    val creditPayments by vm.creditPayments.collectAsState();val loans by vm.loans.collectAsState();val loanPayments by vm.loanPayments.collectAsState()
    val cycle by vm.ledgerCycle.collectAsState()
    val workspace=profile.workspace;val today=LocalDate.now()
    val summary=cycle?.let { ledgerCycleSummary(tx,payments,savings,it,creditPayments=creditPayments) }
        ?: monthlySalarySummary(tx,YearMonth.from(today).toString(),payments,savings,creditPayments)
    val guide=com.sadique.dailyledger.ai.DailySpendingGuide.calculate(tx,summary,today,vm.salarySchedule.day,vm.salarySchedule.calendar.expected,vm.salarySchedule.calendar.lastRecorded)
    val start=vm.salarySchedule.calendar.reference?.takeIf{it<=today} ?: guide.lastSalaryDate ?: today.withDayOfMonth(1)
    val next=guide.expectedSalaryDate ?: today.plusMonths(1)
    var page by rememberSaveable { mutableStateOf("Money tools") }
    var addKind by remember { mutableStateOf<FinanceKind?>(null) }
    var editPlan by remember { mutableStateOf<FinanceItem?>(null) }
    var paying by remember { mutableStateOf(emptySet<String>()) }
    var showEntry by remember { mutableStateOf(false) };var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var refund by remember { mutableStateOf<TransactionEntity?>(null) };var split by remember { mutableStateOf(false) }
    var vault by remember { mutableStateOf(false) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) };var selected by remember { mutableStateOf(emptySet<String>()) }
    var settle by remember { mutableStateOf<FinanceItem?>(null) };var goal by remember { mutableStateOf<FinanceItem?>(null) }
    var whatIf by rememberSaveable { mutableStateOf("") }; var query by rememberSaveable { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    suspend fun save(action:suspend LedgerRepository.()->Unit) { vm.saveChange(action);success="Saved";error=null }
    fun run(action:suspend LedgerRepository.()->Unit) { if(!busy)scope.launch { busy=true;error=null;try{save(action)}catch(e:Exception){if(e is kotlin.coroutines.cancellation.CancellationException)throw e;error=e.message}finally{busy=false} } }
    fun accountBalance(account:String):Long {
        var amount=FinanceMath.accountBalance(account,workspace,tx)
        fun method(m:String)=if(m in workspace.accounts)m else "Cash"
        if(account=="Cash") {
            amount-=savings.sumOf { it.amountMinor }
            amount+=workspace.items.filter{it.kind==FinanceKind.GOAL_CONTRIBUTION && it.account!="Cash"}.sumOf{it.amount}
            loans.forEach { amount+=if(it.direction=="BORROWED")it.principalMinor else -it.principalMinor }
        }
        if(account!="Cash")amount-=workspace.items.filter{it.kind==FinanceKind.GOAL_CONTRIBUTION && it.account==account}.sumOf{it.amount}
        loanPayments.filter { method(it.method)==account }.forEach { p -> amount+=if(loans.find { it.id==p.loanId }?.direction=="LENT")p.amountMinor else -p.amountMinor }
        amount-=payments.filter { method(it.method)==account && it.chargesSalaryBalance() }.sumOf { it.amountMinor }
        amount+=receipts.filter { method(it.method)==account }.sumOf { it.amountMinor }
        amount-=creditPayments.filter { method(it.method)==account }.sumOf { it.amountMinor }
        return amount
    }
    val privateVisible=vault && page=="Private entries"
    val masked=LocalDisplayPreferences.current?.privateAmounts==true
    fun moneyText(n:Long)=if(masked)"Rs •••••" else money(n)
    val life=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _, event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) vault=false }
        life.lifecycle.addObserver(observer);onDispose { life.lifecycle.removeObserver(observer) }
    }
    BackHandler(page!="Money tools") { page="Money tools";vault=false;selected=emptySet() }
    val credentialUnlock=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK){vault=true;page="Private entries"} else error="Private entries remain locked."
    }
    fun openPrivate() {
        val activity=context as? FragmentActivity
        val auth=Biometrics.pick(context)
        if(activity==null) { error="Open this screen in the app to unlock private entries.";return }
        if(auth==null) {
            val keyguard=context.getSystemService(android.app.KeyguardManager::class.java)
            val intent=if(keyguard.isDeviceSecure)keyguard.createConfirmDeviceCredentialIntent("Private entries","Unlock with your phone screen lock") else null
            if(intent!=null)credentialUnlock.launch(intent) else error="Set a phone PIN or fingerprint before using private entries."
            return
        }
        val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(context),object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){vault=true;page="Private entries"}
            override fun onAuthenticationError(code:Int,message:CharSequence){error=message.toString()}
        })
        val builder=BiometricPrompt.PromptInfo.Builder().setTitle("Private entries").setSubtitle("Unlock with your phone screen lock").setAllowedAuthenticators(auth)
        if(auth and androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL==0)builder.setNegativeButtonText("Cancel")
        prompt.authenticate(builder.build())
    }
    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.testTag("finance-tools-list")) {
            item {
                if(page!="Money tools") TextButton(onClick={page="Money tools";vault=false;selected=emptySet()}){Text("Back to Money tools")}
                Text(page,style=MaterialTheme.typography.headlineSmall)
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) };success?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if(page=="Money tools") {
                item { LedgerCard { Text("Plan without changing your balance. Review every payment before saving.") } }
                items(listOf("Salary planning","Spending forecast","Rates & comparisons","Shopping list","Accounts & transfers","Entries & refunds","Split a bill","Reimbursements","Installments","Saving goals","Receipt, SMS & voice","Monthly checklist","Custom shortcuts","Edit history","Private entries","Widget privacy")) { name ->
                    LedgerCard(Modifier.fillMaxWidth().clickable {
                        success=null;error=null;selected=emptySet()
                        when(name){"Private entries"->openPrivate();"Split a bill"->split=true;else->page=name}
                    }) { Text(name,style=MaterialTheme.typography.titleMedium) }
                }
            }
            when(page) {
                "Salary planning" -> {
                    item { LedgerCard { Text("Remaining balance: ${moneyText(summary.remaining)}");Text("Allocations reserve a plan only; paying a planned item creates an expense once.")
                        val allocated=workspace.items.filter { it.kind==FinanceKind.ALLOCATION && it.date>=start.toString() && it.date<=next.toString() }.sumOf { it.amount }
                        Text("Allocated: ${moneyText(allocated)} • Unallocated: ${moneyText(summary.remaining-allocated)}")
                        if(allocated>summary.remaining)Text("Your allocations exceed the remaining balance. Adjust the plan before paying.",color=MaterialTheme.colorScheme.error)
                        Button(onClick={addKind=FinanceKind.ALLOCATION},enabled=!busy){Text("Add allocation")}
                        OutlinedButton(onClick={addKind=FinanceKind.PLANNED},enabled=!busy){Text("Plan a payment")}
                    } }
                    val plans=workspace.items.filter { it.kind in listOf(FinanceKind.ALLOCATION,FinanceKind.PLANNED) }.sortedBy { it.date }
                    items(plans,key={it.id}) { row -> FinanceItemCard(row,masked) {
                        if(row.kind==FinanceKind.PLANNED && !row.done) Button(onClick={paying=setOf(row.id)},enabled=!busy){Text("Review & pay")}
                        if(row.transactionId.isBlank())TextButton(onClick={editPlan=row;addKind=row.kind},enabled=!busy){Text("Edit plan")}
                        if(row.transactionId.isBlank())TextButton(onClick={run { finance.deleteItem(row.id) }},enabled=!busy){Text("Delete plan")}
                    } }
                }
                "Spending forecast" -> item {
                    val forecast=FinanceMath.forecast(summary.remaining,tx,start,next,today)
                    LedgerCard(contentSpacing=10.dp) {
                        Text("Salary date: $next • ${forecast.days} days to cover")
                        if(next<=today)Text("Expected salary is due or overdue. Update the date before relying on this forecast.",color=MaterialTheme.colorScheme.error)
                        Text("Recent daily spending: ${moneyText(forecast.daily)}")
                        Text("At this pace: ${forecast.daysCovered?.toString() ?: "No spending rate yet"} days covered")
                        Text("Estimated shortfall: ${moneyText(forecast.shortfall)}",color=if(forecast.shortfall>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text("Estimate uses recorded net expenses since $start, including days without purchases. It is not a promise about future spending.")
                        LedgerTextField(whatIf,{whatIf=it},label={Text("What-if purchase (PKR)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                        parseMinor(whatIf)?.takeIf{it>0}?.let { cost -> Text("After this purchase: ${moneyText(summary.remaining-cost)} • Daily allowance: ${moneyText((summary.remaining-cost).coerceAtLeast(0)/forecast.days)}") }
                        Text("What-if calculations never save an entry.")
                    }
                }
                "Rates & comparisons" -> {
                    val previous=tx.filter { it.isSalary() && it.date<start.toString() }.maxOfOrNull { it.date }?.let(LocalDate::parse) ?: start.minusMonths(1)
                    val days=(ChronoUnit.DAYS.between(start,today)+1).coerceAtLeast(1)
                    val previousEnd=minOf(previous.plusDays(days-1),start.minusDays(1))
                    val currentRows=tx.filter { it.date in start.toString()..today.toString() }
                    val previousRows=tx.filter { it.date in previous.toString()..previousEnd.toString() }
                    item { LedgerCard {
                        Text("Current: $start to $today (${days} days)")
                        Text("Previous: $previous to $previousEnd (${ChronoUnit.DAYS.between(previous,previousEnd)+1} days)")
                        Text("Net spending: ${moneyText(currentRows.sumOf { it.expenseEffect() })} • Previous: ${moneyText(previousRows.sumOf { it.expenseEffect() })}")
                        Text("Rate comparisons require quantity, unit and rate on at least two matching item entries.")
                    } }
                    item { Text("Why spending changed this month",style=MaterialTheme.typography.titleMedium);Text("Same elapsed calendar days, matching item and unit, weighted purchase rates. Refunds are excluded from quantity/rate attribution because returned quantities are unknown.") }
                    items(monthlyRateComparisons(tx)) { c -> LedgerCard { Text(c.name,translate=false);Text("Net purchase change: ${moneyText(c.totalChange)} • Quantity effect: ${moneyText(c.quantityEffect)} • Rate effect: ${moneyText(c.rateEffect)}") } }
                    item { Text("Latest purchase rates",style=MaterialTheme.typography.titleMedium) }
                    val comparisons=priceComparisons(tx)
                    if(comparisons.isEmpty())item{Text("Add quantity and rate using Entries & refunds → New entry.")}
                    items(comparisons) { c -> LedgerCard(contentSpacing=6.dp) {
                        Text(c.name,style=MaterialTheme.typography.titleMedium)
                        Text("Unit rate: ${moneyText(c.oldRate)} → ${moneyText(c.newRate)}")
                        Text("Spending change: ${moneyText(c.totalChange)} • Quantity effect: ${moneyText(c.quantityEffect)} • Rate effect: ${moneyText(c.rateEffect)}")
                        Text("Compared with the previous matching purchase and unit; this is not a market price estimate.")
                    } }
                }
                "Shopping list" -> {
                    item { Button(onClick={addKind=FinanceKind.SHOPPING},enabled=!busy){Text("Add shopping item")}
                        Text("Tick purchased items, then save the reviewed expenses together. Quantity × rate calculates the amount.")
                        if(selected.isNotEmpty())Button(onClick={run { finance.payItems(selected,today.toString());selected=emptySet() }},enabled=!busy){Text("Save ${selected.size} purchased items")}
                    }
                    items(workspace.items.filter { it.kind==FinanceKind.SHOPPING }.sortedBy { it.done },key={it.id}) { row -> FinanceItemCard(row,masked) {
                        if(!row.done)Row { Checkbox(row.id in selected,{checked->selected=if(checked)selected+row.id else selected-row.id},enabled=!busy);Text("Purchased")
                            TextButton(onClick={run { finance.deleteItem(row.id) }},enabled=!busy){Text("Delete")}}
                    } }
                }
                "Accounts & transfers" -> {
                    item { Text("Tracked balances use recorded entries and movements. Older entries, savings and loan principal default to Cash; payment methods select bank/wallet where available. Reconcile to your actual balance before relying on these totals.")
                        Button(onClick={addKind=FinanceKind.ACCOUNT},enabled=!busy){Text("Add account / opening balance")}
                        OutlinedButton(onClick={addKind=FinanceKind.TRANSFER},enabled=!busy){Text("Transfer between accounts")}
                        OutlinedButton(onClick={addKind=FinanceKind.ADJUSTMENT},enabled=!busy){Text("Reconcile actual balance")}
                    }
                    items(workspace.accounts) { account -> LedgerCard { Text(account,style=MaterialTheme.typography.titleMedium);Text(moneyText(accountBalance(account)))
                        val opening=workspace.items.find{it.kind==FinanceKind.ACCOUNT && it.title==account}
                        if(opening!=null)TextButton(onClick={editPlan=opening;addKind=FinanceKind.ACCOUNT},enabled=!busy){Text("Edit opening balance")}
                    } }
                    items(workspace.items.filter { it.kind in listOf(FinanceKind.TRANSFER,FinanceKind.ADJUSTMENT) }.sortedByDescending { it.date },key={it.id}) { row -> FinanceItemCard(row,masked){} }
                }
                "Entries & refunds", "Private entries" -> {
                    item {
                        if(page=="Private entries" && !vault) { Text("Private entries are locked.");Button(onClick=::openPrivate){Text("Unlock") } }
                        else { Button(onClick={editing=null;showEntry=true},enabled=!busy){Text("New entry")}
                            LedgerTextField(query,{query=it},label={Text("Search entries")},modifier=Modifier.fillMaxWidth())
                            Text("Private individual entries are hidden from normal lists, AI and exports. Financial totals still include them. Private records are included in Drive app-data JSON; this backup is not end-to-end encrypted.")
                        }
                    }
                    val visible=tx.filter { it.privateEntry==privateVisible && (page!="Private entries" || vault) && (query.isBlank() || it.note.contains(query,true) || it.category.contains(query,true)) }.sortedWith(compareByDescending<TransactionEntity>{it.date}.thenByDescending{it.createdAt})
                    items(visible,key={it.id}) { row -> LedgerCard(contentSpacing=6.dp) {
                        Text(row.note.ifBlank { row.category },translate=false,style=MaterialTheme.typography.titleMedium)
                        Text("${row.date} • ${row.category} • ${row.account}")
                        Text("${if(row.refundOf.isNotBlank()) "Refund • " else ""}${moneyText(row.amountMinor)}")
                        if(row.quantity.isNotBlank())Text("${row.quantity} ${row.unit} × ${moneyText(row.unitPriceMinor)}")
                        Row { TextButton(onClick={editing=row;showEntry=true},enabled=!busy){Text("Edit")}
                            if(row.type=="EXPENSE" && row.refundOf.isBlank())TextButton(onClick={refund=row},enabled=!busy){Text("Refund")}
                            TextButton(onClick={run { deleteTransaction(row) }},enabled=!busy){Text("Delete")}
                        }
                    } }
                }
                "Reimbursements" -> {
                    item { Button(onClick={addKind=FinanceKind.REIMBURSEMENT},enabled=!busy){Text("Pay on someone's behalf")};Text("Creates an expense and a claim. Record returned money against the claim; it is tracked as reimbursement income.") }
                    items(workspace.items.filter { it.kind==FinanceKind.REIMBURSEMENT },key={it.id}) { row -> FinanceItemCard(row,masked) {
                        Text("Received: ${moneyText(row.settled)} • Outstanding: ${moneyText(row.amount-row.settled)}")
                        if(!row.done)Button(onClick={settle=row},enabled=!busy){Text("Record returned money")}
                    } }
                }
                "Installments" -> {
                    item{Button(onClick={addKind=FinanceKind.INSTALLMENT},enabled=!busy){Text("Add installment purchase")};Text("Only the advance and installments you mark paid become expenses; the full purchase is not deducted twice.")}
                    items(workspace.items.filter { it.kind==FinanceKind.INSTALLMENT }.sortedBy{it.date},key={it.id}) { row -> FinanceItemCard(row,masked) {
                        if(!row.done)Button(onClick={paying=setOf(row.id)},enabled=!busy){Text("Review & pay")}
                    } }
                }
                "Saving goals" -> {
                    item{Button(onClick={addKind=FinanceKind.GOAL},enabled=!busy){Text("Add saving goal")}}
                    items(workspace.items.filter { it.kind==FinanceKind.GOAL },key={it.id}) { row -> FinanceItemCard(row,masked) {
                        val perSalary=runCatching { FinanceMath.goalPerSalary(row.amount,row.settled,LocalDate.parse(row.date),next) }.getOrNull()
                        Text("Saved toward goal: ${moneyText(row.settled)}")
                        Text(if(perSalary==null) "Deadline is before the next salary or has passed; review the plan." else "Needed each salary: ${moneyText(perSalary)}")
                        perSalary?.let { Text(if(it<=summary.remaining.coerceAtLeast(0))"Within current remaining balance; future salaries may differ." else "Above current remaining balance; extend the deadline or lower the target.") }
                        TextButton(onClick={editPlan=row;addKind=FinanceKind.GOAL},enabled=!busy){Text("Edit goal")}
                        if(row.settled<row.amount)Button(onClick={goal=row},enabled=!busy){Text("Record saved contribution")}
                    } }
                }
                "Receipt, SMS & voice" -> item { FinanceIntakePanel(tx.filterNot{it.privateEntry},workspace.accounts,vm.ownerId) { rows,account,batch -> save { finance.saveReviewed(rows,account,batch) } } }
                "Monthly checklist" -> {
                    val month=YearMonth.from(today).toString()
                    val labels=(listOf("Salary","Rent","Bills","Kameti")+profile.planner.bills.map{it.title}).distinct()
                    item{Text("Your review checklist for $month. A tick confirms you checked the record; it does not create a payment.")}
                    items(labels) { title -> val key="$month:$title";LedgerCard { Row { Checkbox(key in workspace.checked,{checked->run{finance.check(key,checked)}},enabled=!busy);Text(title) }
                        if(title=="Salary")Text(if(tx.any{it.isSalary() && it.date.startsWith(month)})"Salary record found" else "No salary record this month")
                        if(title=="Kameti")Text("Paid installments this month: ${payments.count{it.month==month}}")
                    } }
                }
                "Custom shortcuts" -> {
                    item{Button(onClick={addKind=FinanceKind.SHORTCUT},enabled=!busy){Text("Add shortcut")};Text("Shortcuts open an editable entry. Nothing is saved until you confirm.")}
                    items(workspace.items.filter{it.kind==FinanceKind.SHORTCUT},key={it.id}) { row -> FinanceItemCard(row,masked) {
                        Button(onClick={editing=TransactionEntity("",vm.ownerId,row.reference.ifBlank{"EXPENSE"},row.amount,row.category,row.title,today.toString(),0,0,row.account);showEntry=true},enabled=!busy){Text("Use shortcut")}
                        TextButton(onClick={run{finance.deleteItem(row.id)}},enabled=!busy){Text("Delete")}
                    } }
                }
                "Edit history" -> {
                    item{Text("History records entry creation, edits and deletions. Private revisions require the Private entries unlock.");Button(onClick=::openPrivate){Text("Open private entries")}}
                    val publicHistory=workspace.revisions.filter { r ->
                        fun hidden(s:String)=s.isNotBlank() && JSONObject(s).optBoolean("private")
                        !hidden(r.before) && !hidden(r.after)
                    }.sortedByDescending { it.at }
                    items(publicHistory,key={it.id}) { r -> RevisionCard(r,masked) }
                }
                "Widget privacy" -> item { LedgerCard(contentSpacing=8.dp) {
                    Text("Add the DailyLedger widget from your phone's home-screen widget picker. Tap New expense to open a review form.")
                    Row { Checkbox(workspace.widgetBalance,{show->run{updateProfile { it.copy(workspace=it.workspace.copy(widgetBalance=show)) }}},enabled=!busy);Text("Show balance in widget") }
                    Text("Balance stays hidden by default and whenever app lock, privacy mode or any private entry is present. No plaintext balance cache is stored.")
                } }
            }
            if(page=="Private entries" && vault) {
                item{Text("Private edit history",style=MaterialTheme.typography.titleMedium)}
                items(workspace.revisions.filter { r -> listOf(r.before,r.after).any { it.isNotBlank() && JSONObject(it).optBoolean("private") } }.sortedByDescending{it.at},key={"private-${it.id}"}) { RevisionCard(it,masked) }
            }
        }
    }
    addKind?.let { kind -> FinanceItemDialog(kind,workspace.accounts,::accountBalance,{addKind=null;editPlan=null},initial=editPlan) { item,advance,count ->
        save {
            when(kind) {
                FinanceKind.REIMBURSEMENT -> finance.createReimbursement(item.title,item.amount,item.category,item.date,item.account)
                FinanceKind.INSTALLMENT -> finance.createInstallments(item.title,item.amount,advance,count,LocalDate.parse(item.date),item.account,item.category)
                else -> finance.saveItem(if(kind==FinanceKind.GOAL && editPlan!=null)item.copy(settled=editPlan!!.settled,done=editPlan!!.settled==item.amount) else item)
            }
        }
    } }
    if(showEntry) FinanceEntryEditor(vm.ownerId,workspace.accounts,editing?.takeIf{it.id.isNotBlank()},editing?.takeIf{it.id.isBlank()},privateVisible,{showEntry=false}) { row -> save { saveTransactionRow(row,editing?.takeIf{it.id.isNotBlank()}?.updatedAt) } }
    refund?.let { parent -> AmountDateDialog("Record refund",(parent.amountMinor-tx.filter{it.refundOf==parent.id}.sumOf{it.amountMinor}),workspace.accounts,{refund=null}) { amount,date,account -> save { finance.refund(parent.id,amount,date,"Refund • ${parent.note.ifBlank{parent.category}}",account) } } }
    settle?.let { item -> AmountDateDialog("Record returned money",item.amount-item.settled,workspace.accounts,{settle=null}) { amount,date,account -> save{finance.settleReimbursement(item.id,amount,date,account)} } }
    goal?.let { item -> AmountDateDialog("Record saved contribution",item.amount-item.settled,(listOf(item.account)+workspace.accounts).distinct(),{goal=null}) { amount,date,account -> save { finance.contributeGoal(item.id,amount,date,account) } } }
    if(paying.isNotEmpty()) {
        val records=workspace.items.filter { it.id in paying };var payDate by remember { mutableStateOf(today.toString()) }
        FinanceFormDialog("Review payment",{paying=emptySet()},save={save{finance.payItems(paying,payDate)}},button="Save payment",enabled=isValidDate(payDate)) { paymentBusy ->
            records.forEach { Text("${it.title} • ${moneyText(it.amount)} • ${it.account}") }
            LedgerDateField(payDate,{payDate=it},enabled=!paymentBusy)
        }
    }
    if(split) SplitBillDialog(workspace.accounts,{split=false}) { rows,total,account,batch -> save { finance.splitBill(rows,total,account,batch) } }
}

@Composable private fun FinanceItemCard(row:FinanceItem,hidden:Boolean,actions:@Composable ColumnScope.()->Unit) {
    LedgerCard(Modifier.fillMaxWidth(),contentSpacing=6.dp) { Text(row.title,translate=false,style=MaterialTheme.typography.titleMedium)
        Text("${row.date} • ${row.account} • ${if(row.done)"Paid / completed" else "Planned"}")
        Text(if(hidden)"Rs •••••" else money(row.amount));if(row.quantity.isNotBlank())Text("${row.quantity} ${row.unit}")
        if(row.note.isNotBlank())Text(row.note,translate=false);actions()
    }
}
@Composable private fun RevisionCard(r:EntryRevision,hidden:Boolean) {
    var expanded by remember(r.id){mutableStateOf(false)}
    LedgerCard(Modifier.fillMaxWidth().clickable { expanded=!expanded },contentSpacing=6.dp) {
        Text("${r.action} • ${java.time.Instant.ofEpochMilli(r.at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}")
        if(expanded)listOf("Before" to r.before,"After" to r.after).forEach { (label,snapshot) ->
            if(snapshot.isNotBlank()){val o=JSONObject(snapshot);Text("$label: ${o.optString("date")} • ${o.optString("category")} • ${if(hidden)"Rs •••••" else money(o.getLong("amount"))}");if(!hidden)Text(o.optString("note"),translate=false)}
        }
    }
}
