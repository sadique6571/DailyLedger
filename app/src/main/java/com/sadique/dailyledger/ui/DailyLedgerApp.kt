package com.sadique.dailyledger.ui

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ai.AiConsent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.screens.MoreScreen
import com.sadique.dailyledger.ui.screens.InsightsCard
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import com.sadique.dailyledger.ui.screens.CategoryOptions
import com.sadique.dailyledger.ui.screens.AiChatScreen
import com.sadique.dailyledger.ui.screens.LocalCategoryOptions
import androidx.compose.ui.Alignment
import com.sadique.dailyledger.ui.screens.LocalCategoryHistory
import com.sadique.dailyledger.ui.screens.SavingDialog
import com.sadique.dailyledger.ui.screens.CreditAiDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import com.sadique.dailyledger.ui.NavigationHistory.Companion.HOME
import com.sadique.dailyledger.ui.NavigationHistory.Companion.TRANSACTIONS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.LOANS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.KAMETI
import com.sadique.dailyledger.ui.NavigationHistory.Companion.SAVINGS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.INSIGHTS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.MORE
import com.sadique.dailyledger.ui.NavigationHistory.Companion.UDHAR
import com.sadique.dailyledger.ui.NavigationHistory.Companion.SETTINGS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.AUTO_FILL
import com.sadique.dailyledger.ui.NavigationHistory.Companion.BUDGETS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.BILLS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.CALENDAR
import com.sadique.dailyledger.ui.NavigationHistory.Companion.COMPARISON
import com.sadique.dailyledger.ui.NavigationHistory.Companion.SEARCH
import com.sadique.dailyledger.ui.NavigationHistory.Companion.LEARNING
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sadique.dailyledger.ai.SpendingInsights
import com.sadique.dailyledger.auth.AccountManager
import com.sadique.dailyledger.auth.UserProfile
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.security.Biometrics
import com.sadique.dailyledger.sync.DriveBackupInfo
import com.sadique.dailyledger.sync.DriveSync
import com.sadique.dailyledger.sync.SyncScheduler
import com.sadique.dailyledger.ui.screens.AutoFillScreen
import com.sadique.dailyledger.ui.screens.CommitteeScreen
import com.sadique.dailyledger.ui.screens.DashboardScreen
import com.sadique.dailyledger.ui.screens.CreditScreen
import com.sadique.dailyledger.ui.screens.LoansScreen
import com.sadique.dailyledger.ui.screens.SavingsScreen
import com.sadique.dailyledger.ui.screens.SettingsScreen
import com.sadique.dailyledger.ui.screens.TransactionDialog
import com.sadique.dailyledger.ui.screens.TransactionsScreen
import com.sadique.dailyledger.ui.screens.BudgetScreen
import com.sadique.dailyledger.ui.screens.BillsScreen
import com.sadique.dailyledger.ui.screens.KametiCalendarScreen
import com.sadique.dailyledger.ui.screens.MonthlyComparisonScreen
import com.sadique.dailyledger.ui.screens.GlobalSearchScreen
import com.sadique.dailyledger.ui.screens.LearningScreen
import com.sadique.dailyledger.planning.PlannerMath
import com.sadique.dailyledger.planning.BillReminderWorker
import com.sadique.dailyledger.audio.TransactionSoundPlayer
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private data class EntryShortcut(val type: String, val category: String = "", val note: String = "", val amount: String = "")

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLedgerApp(
    user: UserProfile,
    settings: SettingsStore,
    theme: String,
    biometric: Boolean,
    driveEnabled: Boolean,
    lastSync: Long,
    driveStatus: String,
    transactionSounds: Boolean,
    onLogout: () -> Unit,
    pinHash: String = "",
) {
    val context = LocalContext.current
    val maskSaveAmounts = LocalDisplayPreferences.current?.privateAmounts == true
    fun savedNotice(label: String, amount: Long) {
        val amountText = if (maskSaveAmounts) "Rs •••••" else money(amount)
        Toast.makeText(context, "$label saved · $amountText", Toast.LENGTH_SHORT).show()
    }
    var databaseReady by remember(user.id) { mutableStateOf(false) }
    var loadError by remember(user.id) { mutableStateOf<String?>(null) }
    var loadAttempt by remember(user.id) { mutableIntStateOf(0) }
    LaunchedEffect(user.id, loadAttempt) {
        loadError = null
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val db = com.sadique.dailyledger.data.AppDatabase.get(context.applicationContext)
                db.openHelper.writableDatabase
            }
            databaseReady = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = e.message ?: "Saved data could not be opened. Your data was kept." }
    }
    if (!databaseReady) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (loadError == null) { CircularProgressIndicator(); Text("Loading your saved records…", Modifier.padding(top = 16.dp)) }
            else { Text(loadError!!); Button(onClick = { loadAttempt++ }) { Text("Retry") }; TextButton(onClick = onLogout) { Text("Sign out") } }
        }
        return
    }
    val vm: MainViewModel = viewModel(
        key = user.id,
        factory = MainViewModel.Factory(context.applicationContext as Application, user.id),
    )
    var navigation by rememberSaveable(user.id, stateSaver = listSaver(
        save = { it.entries }, restore = { NavigationHistory(it.filterNot { entry -> entry == NavigationHistory.FINANCE_TOOLS }) }
    )) { mutableStateOf(NavigationHistory()) }
    val tab = navigation.current
    var detailedDraft by remember { mutableStateOf<TransactionEntity?>(null) }
    val accountNames by vm.accountNames.collectAsState()
    val tx by vm.transactions.collectAsState()
    val loans by vm.loans.collectAsState()
    val credits by vm.creditPurchases.collectAsState()
    val creditPayments by vm.creditPayments.collectAsState()
    val lp by vm.loanPayments.collectAsState()
    val committees by vm.committees.collectAsState()
    val cp by vm.committeePayments.collectAsState()
    val receipts by vm.committeeReceipts.collectAsState()
    val committeeMembers by vm.committeeMembers.collectAsState()
    val organizerCircles = if (tab == KAMETI) vm.organizerCircles.collectAsState().value else emptyList()
    val organizerMembers = if (tab == KAMETI) vm.organizerMembers.collectAsState().value else emptyList()
    val organizerCollections = if (tab == KAMETI) vm.organizerCollections.collectAsState().value else emptyList()
    // Due badges (Round 2, #9): counts of items due today or overdue, shown on the bottom tabs.
    val badgeToday = java.time.LocalDate.now().toString()
    val dueCreditCount = remember(credits, creditPayments, badgeToday) { credits.count { c -> !c.closed && c.dueDate != null && c.dueDate <= badgeToday && (c.amountMinor - creditPayments.filter { it.creditId == c.id }.sumOf { it.amountMinor }) > 0 } }
    val dueLoanCount = remember(loans, lp, badgeToday) { loans.count { l -> !l.closed && l.dueDate != null && l.dueDate <= badgeToday && (l.principalMinor - lp.filter { it.loanId == l.id }.sumOf { it.amountMinor }) > 0 } }
    val dueKametiCount = remember(committees, cp) {
        val nowYm = java.time.YearMonth.now()
        committees.count { c ->
            if (!c.active) false else {
                val startYm = runCatching { java.time.YearMonth.parse(c.startMonth) }.getOrNull()
                if (startYm == null) false else {
                    val elapsed = ((nowYm.year - startYm.year) * 12 + (nowYm.monthValue - startYm.monthValue) + 1).coerceIn(0, c.totalInstallments)
                    val paidInstallments = cp.filter { it.committeeId == c.id }.map { it.installmentNumber }.distinct().size
                    elapsed > 0 && paidInstallments < elapsed
                }
            }
        }
    }
    val organizerPayouts = if (tab == KAMETI) vm.organizerPayouts.collectAsState().value else emptyList()
    val savings by vm.savings.collectAsState()
    val ledgerCycle by vm.ledgerCycle.collectAsState()
    val ledgerArchives by vm.ledgerArchives.collectAsState()
    val planning by vm.planner.data.collectAsState()
    val today by androidx.compose.runtime.produceState(java.time.LocalDate.now()) {
        while (true) { value = java.time.LocalDate.now(); kotlinx.coroutines.delay(30_000) }
    }
    val planningNotices = remember(planning, tx, today, user.id) {
        PlannerMath.budgets(user.id, planning.budgets, tx, java.time.YearMonth.from(today))
            .filter { it.nearLimit }.map { "${it.category}: ${if (it.exceeded) "Budget limit reached" else "Near budget limit"}" } +
            PlannerMath.due(planning.bills, today).takeIf { it.isNotEmpty() }?.let { listOf("${it.size} bills ready for review") }.orEmpty()
    }
    val categoryOptions = remember(user.id) { mutableStateOf<Map<String, CategoryOptions>?>(null) }
    val categoryStrings = com.sadique.dailyledger.i18n.LocalStrings.current
    val displayPrefs = LocalDisplayPreferences.current
    LaunchedEffect(user.id, tx, categoryStrings, displayPrefs?.customCategories) {
        categoryOptions.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            CategoryOptions.build(com.sadique.dailyledger.data.CategoryCatalog.load(context), tx.filterNot { it.privateEntry }, categoryStrings::text, displayPrefs?.customCategories ?: emptyList())
        }
    }
    LaunchedEffect(planning.budgets, tx) {
        com.sadique.dailyledger.notifications.BudgetNotifier.check(context, user.id, planning.budgets, tx.filterNot { it.privateEntry })
    }
    CompositionLocalProvider(LocalCategoryHistory provides tx.filterNot { it.privateEntry }, LocalCategoryOptions provides categoryOptions.value) {
    val settingsOpen = tab == SETTINGS
    val settingsReturn = navigation.parent == SETTINGS
    val settingsState = rememberSaveableStateHolder()
    // Replay card entrance on each Settings visit while preserving list scroll position.
    var settingsEntranceVisit by remember(user.id) { mutableIntStateOf(0) }
    fun open(destination: Int) {
        if (destination == SETTINGS && navigation.current != SETTINGS) settingsEntranceVisit++
        navigation = navigation.open(destination)
    }
    fun goBack() {
        val next = navigation.back()
        if (navigation.current != SETTINGS && next.current == SETTINGS) settingsEntranceVisit++
        navigation = next
    }
    var remindersOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = navigation.canGoBack) { goBack() }
    var creditAiOpen by remember(user.id) { mutableStateOf(false) }
    var addCreditRequested by remember { mutableStateOf(false) }
    var addSaving by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf("ALL") }
    var aiConsent by remember(user.id) { mutableStateOf(false) }
    val aiEnabled by vm.aiEnabled.collectAsState()
    val aiEntryEngine by vm.aiEntryEngine.collectAsState()
    val aiTips by vm.aiTips.collectAsState()
    val dismissedTips by vm.dismissedInsights.collectAsState()
    val feedbackScope = rememberCoroutineScope()
    var feedbackError by remember(user.id) { mutableStateOf<String?>(null) }
    fun dismissTip(tip: com.sadique.dailyledger.ai.SpendingTip) {
        feedbackScope.launch { feedbackError = vm.dismissInsight(tip) }
    }
    val aiStatus by vm.aiStatus.collectAsState()
    val aiBusy by vm.aiBusy.collectAsState()
    val snapshotState = remember(user.id) { mutableStateOf(SpendingInsights.calculate(user.id, emptyList(), today)) }
    LaunchedEffect(tx, savings, loans, lp, cp, creditPayments, ledgerCycle, user.id, today, planning, vm.salarySchedule.day, vm.salarySchedule.calendar) {
        snapshotState.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SpendingInsights.calculate(user.id, tx.filterNot { it.privateEntry }, savings = savings, loans = loans, loanPayments = lp,
                committeePayments = cp, ledgerCycle = ledgerCycle, creditPayments = creditPayments, today = today, planning = planning,
                salaryDay = vm.salarySchedule.day, plannedSalaryDate = vm.salarySchedule.calendar.expected,
                salaryRecordedWhenPlanned = vm.salarySchedule.calendar.lastRecorded)
        }
    }
    val snapshot = snapshotState.value
    var restoreInfo by remember(user.id) { mutableStateOf<DriveBackupInfo?>(null) }
    var restoreBusy by remember(user.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(snapshot, aiEnabled) { vm.updateInsightContext(snapshot) }
    LaunchedEffect(user.id) {
        if (!vm.repo.hasRecords()) {
            try {
                val info = DriveSync(context, user.id).backupInfo()
                if (info != null && settings.skippedDriveBackup(user.id) != info.version) {
                    restoreInfo = info
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings.setDriveStatus(user.id, e.message ?: "Could not check the cloud for a backup.")
            }
        }
    }
    restoreInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup found") },
            text = {
                val whenText = runCatching { java.time.Instant.parse(info.modifiedTime).toString() }.getOrDefault("date unavailable")
                Text("A cloud backup for ${user.email} was found ($whenText). Restore it to this phone, or Skip for now.")
            },
            confirmButton = {
                TextButton(
                    enabled = !restoreBusy,
                    onClick = {
                        restoreBusy = true
                        scope.launch {
                            try {
                                DriveSync(context, user.id).restore()
                                restoreInfo = null
                                Toast.makeText(context, "Cloud backup restored to this phone.", Toast.LENGTH_LONG).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Could not restore backup.", Toast.LENGTH_LONG).show()
                            } finally {
                                restoreBusy = false
                            }
                        }
                    }
                ) { Text(if (restoreBusy) "Restoring…" else "Restore") }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoreBusy,
                    onClick = {
                        scope.launch {
                            settings.setSkippedDriveBackup(user.id, info.version)
                            restoreInfo = null
                        }
                    }
                ) { Text("Skip") }
            },
        )
    }
    if (aiConsent) {
        AlertDialog(
            onDismissRequest = { aiConsent = false },
            title = { Text(AiConsent.TITLE) },
            text = { Text(AiConsent.TEXT) },
            confirmButton = { TextButton(onClick = { vm.setAiEnabled(true); aiConsent = false }) { Text("Enable AI") } },
            dismissButton = { TextButton(onClick = { aiConsent = false }) { Text("Cancel") } },
        )
    }
    var aiEntryOpen by remember { mutableStateOf(false) }
    var aiEntryBusy by remember { mutableStateOf(false) }
    var aiChatOpen by remember { mutableStateOf(false) }
    var aiChatBusy by remember { mutableStateOf(false) }
    val aiContextJson = remember(tx, loans, lp, credits, creditPayments, committees, cp, savings, planning.budgets) {
        buildAiContextJson(tx, loans, lp, credits, creditPayments, committees, cp, savings,
            PlannerMath.budgets(user.id, planning.budgets, tx, java.time.YearMonth.from(today)))
    }
    var quickTransaction by remember { mutableStateOf<EntryShortcut?>(null) }
    var duplicateConfirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var entryRequest by remember { mutableStateOf<String?>(null) }
    if (aiEntryOpen) LedgerEntrySheet(
        onDismissRequest = { aiEntryOpen = false }, canDismiss = !aiEntryBusy,
        title = { Text("AI Auto Fill") }, scrollBody = false,
        text = { AutoFillScreen(
            enabled = aiEnabled, onEnable = { aiConsent = true },
            onBack = { aiEntryOpen = false }, generate = { input -> vm.autoFill(input, aiEntryEngine) },
            engine = aiEntryEngine, onEngineChange = vm::saveAiEntryEngine,
            save = { drafts, batchId ->
                vm.saveAiDrafts(drafts, batchId)
                savedNotice("AI entries", drafts.sumOf { it.amountMinor })
                if (transactionSounds) TransactionSoundPlayer.playBatchSuccess(context)
            }, onSaved = { aiEntryOpen = false }, onLearnCategory = vm::rememberCategory,
            embedded = true, onBusyChanged = { aiEntryBusy = it },
            importGenerate = vm::importStatement,
        ) }, confirmButton = {},
        dismissButton = { TextButton(onClick = { aiEntryOpen = false }, enabled = !aiEntryBusy) { Text("Close") } },
    )
    if (aiChatOpen) LedgerEntrySheet(
        onDismissRequest = { aiChatOpen = false }, canDismiss = !aiChatBusy,
        title = { Text("AI Assistant") }, scrollBody = false,
        text = { AiChatScreen(
            contextJson = aiContextJson, advise = vm::aiAdvise,
            onBack = { aiChatOpen = false },
            embedded = true, onBusyChanged = { aiChatBusy = it },
        ) }, confirmButton = {},
        dismissButton = { TextButton(onClick = { aiChatOpen = false }, enabled = !aiChatBusy) { Text("Close") } },
    )
    if (creditAiOpen) CreditAiDialog({ creditAiOpen = false }, { drafts, batch -> vm.saveCreditDrafts(drafts, batch); savedNotice("Udhar", drafts.sumOf { it.amountMinor }) })
    if (addSaving) SavingDialog(
        dismiss = { addSaving = false },
        onHistory = { addSaving = false; open(4) },
    ) { k, a, d, n -> vm.launch { saveSaving(k, a, d, n) }; addSaving = false }
    feedbackError?.let { error -> AlertDialog(onDismissRequest = { feedbackError = null }, title = { Text("AI learning") },
        text = { Text(error) }, confirmButton = { TextButton(onClick = { feedbackError = null }) { Text("Close") } }) }
    val saveError by vm.error.collectAsState()
    LaunchedEffect(user.id) { SyncScheduler.catchUp(context); BillReminderWorker.schedule(context) }
    val backupLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(backupLifecycle, user.id) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch { SyncScheduler.catchUp(context) }
        }
        backupLifecycle.lifecycle.addObserver(observer)
        onDispose { backupLifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(saveError) {
        saveError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    val tabs = remember {
        listOf(
            Tab("Home", Icons.Rounded.Home, Icons.Rounded.Home),
            Tab("Udhar", Icons.Rounded.ShoppingCart, Icons.Rounded.ShoppingCart),
            Tab("Kameti", Icons.Rounded.Groups, Icons.Rounded.Groups),
            Tab("Savings", Icons.Rounded.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
            Tab("More", Icons.Rounded.Apps, Icons.Rounded.Apps),
        )
    }
    if (remindersOpen) {
        val reminders = loans.filter { loan -> !loan.closed && loan.dueDate != null &&
            loan.principalMinor > lp.filter { it.loanId == loan.id }.sumOf { it.amountMinor } }
            .sortedBy { it.dueDate }.take(5).map { "${it.person} · ${it.dueDate}" } +
            committeeMembers.filter { it.isMe && !it.received }.sortedBy { it.turnMonth }.take(5).map { "Kameti turn · ${it.turnMonth}" } +
            credits.filter { !it.closed && it.dueDate != null }.sortedBy { it.dueDate }.take(5).map { "Udhar: ${it.creditor} · ${it.dueDate}" } +
            PlannerMath.due(planning.bills, today).take(5).map { "${it.title} · ${it.nextDue()}" }
        AlertDialog(onDismissRequest = { remindersOpen = false }, title = { Text("Your reminders") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reminders.isEmpty()) Text("No pending loan dates or personal kameti turns in your records.")
                reminders.forEach { Text(it) }
            } }, confirmButton = { TextButton(onClick = { remindersOpen = false }) { Text("Close") } })
    }
    if (settingsOpen) {
        settingsState.SaveableStateProvider("settings-${user.id}") {
        SettingsScreen(
            context = context,
            vm = vm,
            user = user,
            driveStatus = driveStatus,
            theme = theme,
            biometric = biometric,
            driveEnabled = driveEnabled,
            lastSync = lastSync,
            transactionSounds = transactionSounds,
            onBack = ::goBack,
            onAutoFill = { run { aiEntryOpen = true } },
            onOpenSavings = { open(SAVINGS) },
            entranceVisit = settingsEntranceVisit,
            onTheme = { scope.launch { settings.setTheme(it) } },
            onBiometric = { enable ->
                if (enable && Biometrics.pick(context) == null) {
                    Toast.makeText(context, "Set up a screen lock or fingerprint in Android settings first", Toast.LENGTH_LONG).show()
                } else {
                    scope.launch { settings.setBiometric(enable) }
                }
            },
            pinHash = pinHash,
            onSetPin = { pin -> scope.launch { settings.setPinHash(user.id, com.sadique.dailyledger.security.PinLock.hash(pin)) } },
            onRemovePin = { scope.launch { settings.setPinHash(user.id, "") } },
            onDriveEnabled = { enabled ->
                scope.launch {
                    settings.setDriveEnabled(user.id, enabled)
                    if (enabled) SyncScheduler.catchUp(context)
                    else SyncScheduler.cancelImmediate(context)
                }
            },
            onTransactionSounds = { scope.launch { settings.setTransactionSounds(it) } },
            onLogout = {
                scope.launch {
                    try {
                        AccountManager(context).signOut(); onLogout()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not sign out. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
        }
        return@CompositionLocalProvider
    }

    val entryMenuExpanded = rememberSaveable { mutableStateOf(false) }
    var entryMenuBounds by remember { mutableStateOf(Rect.Zero) }
    Scaffold(
        modifier = Modifier.pointerInput(entryMenuExpanded.value, entryMenuBounds) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (entryMenuExpanded.value && !entryMenuBounds.contains(down.position)) {
                    entryMenuExpanded.value = false
                    down.consume()
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                tab = tab,
                subtitle = when (tab) {
                    1 -> "All your transactions"; 2 -> "Borrowed & lent"; 3 -> "Your kameti, organized"
                    4 -> "For a brighter tomorrow"; 5 -> "Understand your spending"; 6 -> "Everything in one place"; 7 -> "Udhar Saman"
                    in BUDGETS..NavigationHistory.FINANCE_TOOLS -> NavigationHistory.label(tab)
                    else -> "Your money, a brighter tomorrow"
                },
                showNavigateUp = navigation.entries.size > 2 || tab in BUDGETS..NavigationHistory.FINANCE_TOOLS,
                navigateUpLabel = "Back to ${NavigationHistory.label(navigation.parent)}",
                navigateUpTestTag = if (settingsReturn) "return-to-settings" else "navigate-up",
                onNavigateUp = ::goBack,
                onSearch = { open(SEARCH) },
                onReminders = { remindersOpen = true },
                onAiEntry = { if (tab == 7) creditAiOpen = true else run { aiEntryOpen = true } },
            )
        },
        floatingActionButton = {
            DashboardEntryDial(tab = tab, expandedState = entryMenuExpanded, onBoundsChanged = { entryMenuBounds = it }) { action ->
                when (action) {
                    "ai" -> aiEntryOpen = true
                    "aichat" -> aiChatOpen = true
                    "expense" -> quickTransaction = EntryShortcut("EXPENSE")
                    "salary" -> quickTransaction = EntryShortcut("INCOME", "Salary")
                    "kameti" -> { entryRequest = "kameti"; open(3) }
                    "credit" -> { addCreditRequested = true; open(7) }
                }
            }
        },
        bottomBar = {
            AppBottomBar(
                tab = tab,
                udharBadge = dueCreditCount, kametiBadge = dueKametiCount, moreBadge = dueLoanCount,
                onSelectTab = { i -> navigation = navigation.selectTab(when (i) { 0 -> HOME; 1 -> UDHAR; 2 -> KAMETI; 3 -> SAVINGS; else -> MORE }) },
            )
        },
    ) { pad ->
        val dialBlur = androidx.compose.animation.core.animateDpAsState(if (entryMenuExpanded.value) 9.dp else 0.dp, tween(220), label = "dial-blur").value
        Box(Modifier.padding(pad).consumeWindowInsets(pad).blur(dialBlur)) {
          AnimatedContent(targetState = tab, transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { w -> w / 14 } + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.965f)).togetherWith(fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 1.02f)) }, label = "tab-content") { targetTab ->
          settingsState.SaveableStateProvider("screen-${user.id}-$targetTab") {
            when (targetTab) {
                0 -> DashboardScreen(
                    salaryDay = vm.salarySchedule.day, plannedSalaryDate = vm.salarySchedule.calendar.expected,
                    salaryRecordedWhenPlanned = vm.salarySchedule.calendar.lastRecorded,
                    tx = tx,
                    loans = loans,
                    lp = lp,
                    committees = committees,
                    cp = cp,
                    receipts = receipts,
                    committeeMembers = committeeMembers,
                    savings = savings,
                    ledgerCycle = ledgerCycle,
                    ledgerArchives = ledgerArchives,
                    userName = user.name,
                    onAddSalary = { quickTransaction = EntryShortcut("INCOME", "Salary") },
                    onAddSalaryWithAmount = { amount -> quickTransaction = EntryShortcut("INCOME", "Salary", amount = amount) },
                    onAddExpense = { quickTransaction = EntryShortcut("EXPENSE") },
                    onOpenSavings = { addSaving = true },
                    onAddOtherIncome = { quickTransaction = EntryShortcut("INCOME", "Other income") },
                    onAddCategoryExpense = { category, note -> quickTransaction = EntryShortcut("EXPENSE", category, note) },
                    onOpenInsights = { open(5) },
                    onOpenLoans = { open(2) },
                    onOpenUdhar = { addCreditRequested = true; open(7) },
                    udharOutstanding = credits.sumOf { c -> (c.amountMinor - creditPayments.filter { it.creditId == c.id }.sumOf { it.amountMinor }).coerceAtLeast(0) },
                    creditPayments = creditPayments,
                    lastSync = lastSync,
                    driveEnabled = driveEnabled,
                    onOpenKameti = { open(3) },
                    onAutoFill = { run { aiEntryOpen = true } },
                    onAllTransactions = { historyQuery = ""; historyFilter = "ALL"; open(1) },
                    onExpenseHistory = { historyQuery = ""; historyFilter = "EXPENSE"; open(1) },
                    onStartNewMonth = vm::startNewMonth,
                    onArchivePreviousMonth = { callback -> vm.archivePreviousMonth(callback) },
                    snapshot = snapshot,
                    aiTips = aiTips,
                    aiStatus = aiStatus,
                    aiEnabled = aiEnabled,
                    aiBusy = aiBusy,
                    onRefreshAi = { vm.refreshInsights(snapshot) },
                    onDismissTip = ::dismissTip, dismissedTipKeys = dismissedTips,
                    onEnableAi = { aiConsent = true },
                    planningNotices = planningNotices,
                    onOpenPlanning = { open(if (PlannerMath.due(planning.bills, today).isNotEmpty()) BILLS else BUDGETS) },
                )
                1 -> TransactionsScreen(
                    items = tx.filterNot { it.privateEntry },
                    initialQuery = historyQuery, initialFilter = historyFilter,
                    onAutoFill = { run { aiEntryOpen = true } },
                    onDetailedEntry = { detailedDraft=it },
                    onSaveAsync = { t, a, c, n, d, e ->
                        vm.saveChange {
                            if (e != null && e.type == t && e.category != c && n.isNotBlank()) updateLearning { it.remember(t, n, c) }
                            saveTransaction(t, a, c, n, d, e)
                        }
                        if (transactionSounds) TransactionSoundPlayer.play(context, t)
                        savedNotice(if (t == "EXPENSE") "Expense" else if (c.equals("Salary", true)) "Salary" else "Income", a)
                    },
                    onSave = { t, a, c, n, d, e ->
                        vm.launch(onSuccess = {
                            if (transactionSounds) TransactionSoundPlayer.play(context, t)
                    savedNotice(if (t == "EXPENSE") "Expense" else if (c.equals("Salary", true)) "Salary" else "Income", a)
                        }) {
                            if (e != null && e.type == t && e.category != c && n.isNotBlank()) {
                                updateLearning { it.remember(t, n, c) }
                            }
                            saveTransaction(t, a, c, n, d, e)
                        }
                    },
                    onDelete = { x -> vm.launch { deleteTransaction(x) } },
                )
                2 -> LoansScreen(
                    loans = loans,
                    requestAdd = entryRequest == "loan", onAddOpened = { entryRequest = null },
                    requestPayment = entryRequest == "loan-payment",
                    payments = lp,
                    repo = vm.repo,
                    onAdd = { d, p, a, due, n, phone, wa -> vm.launch { saveLoan(d, p, a, due, n, phone, wa) } },
                    onPayment = { id, a, d, n, method -> vm.launch { addLoanPayment(id, a, d, n, method) } },
                    onDelete = { x -> vm.launch { deleteLoan(x) } },
                )
                3 -> CommitteeScreen(
                    committees = committees,
                    entryRequest = entryRequest, onEntryOpened = { entryRequest = null },
                    payments = cp,
                    receipts = receipts,
                    members = committeeMembers,
                    repo = vm.repo,
                    organizerCircles = organizerCircles,
                    organizerMembers = organizerMembers,
                    organizerCollections = organizerCollections,
                    organizerPayouts = organizerPayouts,
                    onAddCircle = { name, amount, turns, month -> vm.launch { organizer.addCircle(name, amount, turns, month) } },
                    onAddOrganizerMembers = { circle, rows, requestId -> vm.saveChange { organizer.addMembers(circle, rows, requestId) } },
                    onAssignOrganizerTurn = { member, turn, method -> vm.launch { organizer.assignTurn(member, turn, method) } },
                    onUpdateOrganizerContact = { id, name, phone, wa -> vm.launch { organizer.updateContact(id, name, phone, wa) } },
                    onOrganizerCollect = { member, month, amount, date, method -> vm.launch { organizer.collect(member, month, amount, date, method) } },
                    onOrganizerPayout = { member, amount, date, method -> vm.launch { organizer.payOut(member, amount, date, method) } },
                    onDeleteOrganizerMember = { id -> vm.launch { organizer.deleteMember(id) } },
                    onDeleteOrganizerCircle = { id -> vm.launch { organizer.deleteCircle(id) } },
                    onAdd = { n, a, t, s, p, note, halfUnits, phone -> vm.launch(onSuccess = { savedNotice("Kameti", a) }) { saveCommittee(n, a, t, s, p, note, (halfUnits + 1) / 2, phone, sharesHalfUnits = halfUnits) } },
                    onPaid = { c, i, m, method -> vm.launch { markCommitteePaid(c, i, m, method) } },
                    onReceive = { id, a, d, n, method -> vm.launch { addCommitteeReceipt(id, a, d, n, method) } },
                    onDeleteReceipt = { r -> vm.launch { deleteCommitteeReceipt(r) } },
                    onAddMembers = { id, rows, requestId -> vm.saveChange { saveCommitteeMembers(id, rows, requestId) } },
                    onMemberReceived = { member, date -> vm.launch { markCommitteeMemberReceived(member, date) } },
                    onPersonalTurnReceive = { memberId, amount, date, note, method -> vm.launch { addPersonalTurnReceipt(memberId, amount, date, note, method) } },
                    onDeleteMember = { member -> vm.launch { deleteCommitteeMember(member) } },
                    onDelete = { c -> vm.launch { deleteCommittee(c) } },
                    onCalendar = { open(CALENDAR) },
                )
                4 -> SavingsScreen(
                    savings = savings,
                    onAdd = { k, a, d, n -> vm.launch { saveSaving(k, a, d, n) } },
                    onDelete = { x -> vm.launch { deleteSaving(x) } },
                )
                5 -> LedgerBackdrop(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Your monthly insights", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { open(COMPARISON) }) { Text("Monthly comparison") }
                        com.sadique.dailyledger.ui.screens.SpendingTrendCard(tx)
                        InsightsCard(snapshot, aiTips, aiStatus, aiEnabled, onEnable = { aiConsent = true },
                            busy = aiBusy, onRefresh = { vm.refreshInsights(snapshot) }, onDismissTip = ::dismissTip, dismissedTipKeys = dismissedTips)
                    }
                }
                7 -> CreditScreen(credits, creditPayments, vm.repo, vm::saveCreditDrafts, vm::saveCreditPayment,
                    onDelete = { credit -> vm.launch { deleteCreditPurchase(credit) } },
                    requestAdd = addCreditRequested, onAddOpened = { addCreditRequested = false },
                    requestPayment = entryRequest == "credit-payment", onPaymentOpened = { entryRequest = null },
                    onAiAdvise = vm::aiAdvise)
                BUDGETS -> BudgetScreen(user.id, vm.planner, tx, requestAdd = entryRequest == "budget", onAddOpened = { entryRequest = null })
                BILLS -> BillsScreen(vm, requestAdd = entryRequest == "reminder", onAddOpened = { entryRequest = null })
                CALENDAR -> KametiCalendarScreen(user.id, committees, cp, receipts, committeeMembers)
                COMPARISON -> MonthlyComparisonScreen(user.id, tx, savings)
                SEARCH -> GlobalSearchScreen(vm, ::open)
                LEARNING -> LearningScreen(vm)
                else -> MoreScreen(
                    onTransactions = { historyQuery = ""; historyFilter = "ALL"; open(1) },
                    onLoans = { open(2) },
                    onKameti = { open(3) },
                    onSavings = { open(4) },
                    onSettings = { open(SETTINGS) },
                    onCredit = { open(7) },
                    onInsights = { open(5) },
                    onBudgets = { open(BUDGETS) },
                    onBills = { open(BILLS) },
                    onCalendar = { open(CALENDAR) },
                    onComparison = { open(COMPARISON) },
                    onSearch = { open(SEARCH) },
                    onReminders = { remindersOpen = true },
                )
            }
          }
        }
    }
    }
    LaunchedEffect(user.id) {
        val activity=context as? android.app.Activity
        when (activity?.intent?.getStringExtra("ledger_action")) {
            "expense" -> {
                activity.intent.removeExtra("ledger_action")
                quickTransaction=EntryShortcut("EXPENSE")
            }
            "credit" -> {
                activity.intent.removeExtra("ledger_action")
                addCreditRequested = true; open(7)
            }
        }
        com.sadique.dailyledger.widget.LedgerWidget.refresh(context)
    }
    detailedDraft?.let { draft ->
        com.sadique.dailyledger.ui.screens.FinanceEntryEditor(user.id,accountNames,
            draft.takeIf { it.id.isNotBlank() },draft.takeIf { it.id.isBlank() },false,{detailedDraft=null}) { row ->
            vm.saveChange { saveTransactionRow(row,draft.takeIf { it.id.isNotBlank() }?.updatedAt) }
            savedNotice("Entry", row.amountMinor)
        }
    }
    quickTransaction?.let { entry ->
        TransactionDialog(
            null,
            dismiss = { quickTransaction = null },
            onAdvanced = { detailedDraft=it;quickTransaction=null },
            initialType = entry.type,
            initialCategory = entry.category, initialNote = entry.note, initialAmount = entry.amount,
            onHistory = { type, category -> quickTransaction = null; historyFilter = type; historyQuery = category; open(1) },
            save = { _, _, _, _, _ -> },
            saveAsync = { t, a, c, n, d ->
                val doSave: suspend () -> Unit = {
                    vm.saveChange { saveTransaction(t, a, c, n, d) }
                    if (transactionSounds) TransactionSoundPlayer.play(context, t)
                    savedNotice(if (t == "EXPENSE") "Expense" else if (c.equals("Salary", true)) "Salary" else "Income", a)
                    quickTransaction = null
                }
                val warning = duplicateWarning(tx, t, a, c, d)
                if (warning == null) doSave() else duplicateConfirm = warning to doSave
            },
        )
    }
    duplicateConfirm?.let { (warning, action) ->
        AlertDialog(onDismissRequest = { duplicateConfirm = null }, title = { Text("Dobara entry?") },
            text = { Text(warning) },
            confirmButton = { TextButton(onClick = { duplicateConfirm = null; scope.launch { action() } }) { Text("Phir bhi save karein") } },
            dismissButton = { TextButton(onClick = { duplicateConfirm = null }) { Text("Cancel") } })
    }
}
}

/** Compact totals-only snapshot for the AI Assistant. Notes and private entries are never included. */
private fun buildAiContextJson(
    tx: List<com.sadique.dailyledger.data.TransactionEntity>,
    loans: List<com.sadique.dailyledger.data.LoanEntity>,
    loanPayments: List<com.sadique.dailyledger.data.LoanPaymentEntity>,
    credits: List<com.sadique.dailyledger.data.CreditPurchaseEntity>,
    creditPayments: List<com.sadique.dailyledger.data.CreditPaymentEntity>,
    committees: List<com.sadique.dailyledger.data.CommitteeEntity>,
    committeePayments: List<com.sadique.dailyledger.data.CommitteePaymentEntity>,
    savings: List<com.sadique.dailyledger.data.SavingEntity>,
    budgets: List<com.sadique.dailyledger.planning.BudgetProgress>,
): String {
    fun money(v: Long) = java.math.BigDecimal.valueOf(v, 2).toPlainString()
    val today = java.time.LocalDate.now()
    val month = java.time.YearMonth.from(today)
    val prev = month.minusMonths(1)
    fun totals(ym: java.time.YearMonth): Pair<Long, Long> {
        val rows = tx.filter { it.date.startsWith(ym.toString()) && !it.privateEntry }
        return rows.filter { it.type == "INCOME" }.sumOf { it.amountMinor } to rows.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
    }
    val (income, expenses) = totals(month)
    val (prevIncome, prevExpenses) = totals(prev)
    val topCategories = tx.filter { it.date.startsWith(month.toString()) && it.type == "EXPENSE" && !it.privateEntry }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountMinor } }.entries.sortedByDescending { it.value }.take(8)
    val paidByLoan = loanPayments.groupBy { it.loanId }.mapValues { e -> e.value.sumOf { it.amountMinor } }
    val paidByCredit = creditPayments.groupBy { it.creditId }.mapValues { e -> e.value.sumOf { it.amountMinor } }
    val root = org.json.JSONObject()
        .put("today", today.toString())
        .put("this_month", org.json.JSONObject().put("income", money(income)).put("expenses", money(expenses)).put("remaining", money(income - expenses)))
        .put("last_month", org.json.JSONObject().put("income", money(prevIncome)).put("expenses", money(prevExpenses)))
        .put("top_expense_categories", org.json.JSONArray().apply { topCategories.forEach { (name, value) -> put(org.json.JSONObject().put("name", name.take(40)).put("amount", money(value))) } })
        .put("udhar_to_pay", org.json.JSONArray().apply {
            credits.filter { !it.closed }.groupBy { it.creditor }.forEach { (name, rows) ->
                val remaining = rows.sumOf { it.amountMinor } - rows.sumOf { paidByCredit[it.id] ?: 0L }
                if (remaining > 0) put(org.json.JSONObject().put("name", name.take(40)).put("remaining", money(remaining)).put("earliest_due", rows.mapNotNull { it.dueDate }.minOrNull() ?: ""))
            }
        })
        .put("loans_borrowed", org.json.JSONArray().apply {
            loans.filter { !it.closed && it.direction == "BORROWED" }.forEach { loan ->
                val remaining = loan.principalMinor - (paidByLoan[loan.id] ?: 0L)
                if (remaining > 0) put(org.json.JSONObject().put("person", loan.person.take(40)).put("remaining", money(remaining)).put("due", loan.dueDate ?: ""))
            }
        })
        .put("loans_lent", org.json.JSONArray().apply {
            loans.filter { !it.closed && it.direction == "LENT" }.forEach { loan ->
                val remaining = loan.principalMinor - (paidByLoan[loan.id] ?: 0L)
                if (remaining > 0) put(org.json.JSONObject().put("person", loan.person.take(40)).put("remaining", money(remaining)).put("due", loan.dueDate ?: ""))
            }
        })
        .put("kameti", org.json.JSONArray().apply {
            committees.filter { it.active }.forEach { committee ->
                val paidCount = committeePayments.count { it.committeeId == committee.id }
                val remainingCount = (committee.totalInstallments - paidCount).coerceAtLeast(0)
                put(org.json.JSONObject().put("name", committee.name.take(40)).put("monthly", money(committee.monthlyAmountMinor)).put("paid_installments", paidCount).put("total_installments", committee.totalInstallments).put("remaining_amount", money(committee.monthlyAmountMinor * remainingCount)))
            }
        })
        .put("savings_total", money(savings.sumOf { it.amountMinor }))
        .put("budgets", org.json.JSONArray().apply { budgets.forEach { b -> put(org.json.JSONObject().put("category", b.category.take(40)).put("spent", money(b.spent)).put("limit", money(b.limit))) } })
    return root.toString()
}

/** Warn before saving an exact duplicate, or an amount wildly out of line for its category. */
private fun duplicateWarning(
    tx: List<com.sadique.dailyledger.data.TransactionEntity>,
    type: String,
    amountMinor: Long,
    category: String,
    date: String,
): String? {
    if (tx.any { it.type == type && it.amountMinor == amountMinor && it.category.equals(category, ignoreCase = true) && it.date == date }) {
        return "Isi tareekh par is category mein bilkul itni hi raqam ki entry pehle se maujood hai. Kahin dobara to nahi likh rahe?"
    }
    val history = tx.filter { it.type == type && it.category.equals(category, ignoreCase = true) }.map { it.amountMinor }.sorted()
    if (history.size >= 3) {
        val median = history[history.size / 2]
        if (median > 0 && amountMinor >= median * 10) {
            return "Ye raqam is category ki aam entry se bohat zyada hai — rakam dobara check kar lein. Phir bhi save karna hai to neeche wala button dabayein."
        }
    }
    return null
}
