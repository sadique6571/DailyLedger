package com.sadique.dailyledger.ui

import com.sadique.dailyledger.ai.AiConsent
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.security.Biometrics
import com.sadique.dailyledger.sync.SyncScheduler
import com.sadique.dailyledger.sync.CloudBackup
import com.sadique.dailyledger.sync.BackupInfo
import com.sadique.dailyledger.ui.screens.AutoFillScreen
import com.sadique.dailyledger.ui.screens.CommitteeScreen
import com.sadique.dailyledger.ui.screens.DashboardScreen
import com.sadique.dailyledger.ui.screens.CreditScreen
import com.sadique.dailyledger.ui.screens.LoansScreen
import com.sadique.dailyledger.ui.screens.SavingsScreen
import com.sadique.dailyledger.ui.screens.SettingsScreen
import com.sadique.dailyledger.ui.screens.TransactionDialog
import com.sadique.dailyledger.ui.screens.TransactionsScreen
import com.sadique.dailyledger.weather.WeatherService
import com.sadique.dailyledger.audio.TransactionSoundPlayer
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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
    cloudEnabled: Boolean,
    lastCloudSync: Long,
    cloudStatus: String,
    weatherEnabled: Boolean,
    weatherCity: String,
    weatherTemperature: String,
    weatherCondition: String,
    weatherUpdatedAt: Long,
    transactionSounds: Boolean,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel(
        key = user.id,
        factory = MainViewModel.Factory(context.applicationContext as Application, user.id),
    )
    val tx by vm.transactions.collectAsState()
    val loans by vm.loans.collectAsState()
    val credits by vm.creditPurchases.collectAsState()
    val creditPayments by vm.creditPayments.collectAsState()
    val lp by vm.loanPayments.collectAsState()
    val committees by vm.committees.collectAsState()
    val cp by vm.committeePayments.collectAsState()
    val receipts by vm.committeeReceipts.collectAsState()
    val committeeMembers by vm.committeeMembers.collectAsState()
    val savings by vm.savings.collectAsState()
    val ledgerCycle by vm.ledgerCycle.collectAsState()
    val ledgerArchives by vm.ledgerArchives.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var remindersOpen by remember { mutableStateOf(false) }
    var autoFillOpen by remember(user.id) { mutableStateOf(false) }
    var aiConsent by remember(user.id) { mutableStateOf(false) }
    val aiEnabled by vm.aiEnabled.collectAsState()
    val aiTips by vm.aiTips.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val snapshot = remember(tx, savings, loans, lp, cp, ledgerCycle, user.id) {
        SpendingInsights.calculate(user.id, tx, savings = savings, loans = loans, loanPayments = lp, committeePayments = cp, ledgerCycle = ledgerCycle)
    }
    var restoreInfo by remember(user.id) { mutableStateOf<BackupInfo?>(null) }
    var restoreBusy by remember(user.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(snapshot, aiEnabled) { vm.refreshInsights(snapshot) }
    LaunchedEffect(user.id, cloudEnabled, cloudStatus) {
        if (cloudEnabled && !vm.repo.hasRecords()) {
            runCatching { CloudBackup(context, user.id).backupInfo() }
                .onSuccess { info ->
                    if (info != null && settings.skippedRestoreRevision(user.id) != info.revision) {
                        restoreInfo = info
                    }
                }
        }
    }
    restoreInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup found") },
            text = {
                val whenText = if (info.updatedAt > 0L)
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(info.updatedAt))
                else "date unavailable"
                Text("A cloud backup for this account was found ($whenText). Restore it to this phone, or Skip to keep this phone empty for now.")
            },
            confirmButton = {
                TextButton(
                    enabled = !restoreBusy,
                    onClick = {
                        restoreBusy = true
                        scope.launch {
                            try {
                                val message = CloudBackup(context, user.id).restore(info.revision)
                                restoreInfo = null
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
                            settings.setSkippedRestoreRevision(user.id, info.revision)
                            settings.cloudStatus(user.id, "Backup found and skipped on this phone. You can restore it later from Settings.")
                            restoreInfo = null
                        }
                    }
                ) { Text("Skip") }
            },
        )
    }
    LaunchedEffect(weatherEnabled, weatherCity, weatherUpdatedAt) {
        val stale = System.currentTimeMillis() - weatherUpdatedAt > 30 * 60 * 1000L
        if (weatherEnabled && weatherCity.isNotBlank() && stale) {
            runCatching { WeatherService().currentForCity(weatherCity) }
                .onSuccess { weather ->
                    settings.setWeatherSnapshot(weather.city, weather.temperature, weather.condition, weather.fetchedAt)
                }
        }
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
    if (autoFillOpen) {
        AutoFillScreen(
            enabled = aiEnabled,
            onEnable = { aiConsent = true },
            onBack = { autoFillOpen = false },
            generate = vm::autoFill,
            save = { drafts, batchId ->
                vm.saveAiDrafts(drafts, batchId)
                if (transactionSounds) TransactionSoundPlayer.playBatchSuccess(context)
            },
            onSaved = { autoFillOpen = false },
            onLearnCategory = vm::rememberCategory,
        )
        return
    }
    var quickTransaction by remember { mutableStateOf<String?>(null) }
    val saveError by vm.error.collectAsState()
    LaunchedEffect(user.id) { SyncScheduler.syncNow(context) }
    LaunchedEffect(saveError) {
        saveError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    val tabs = remember {
        listOf(
            Tab("Home", Icons.Rounded.Home, Icons.Rounded.Home),
            Tab("Add", Icons.Rounded.AddCircle, Icons.Rounded.AddCircle),
            Tab("Insights", Icons.Rounded.BarChart, Icons.Rounded.BarChart),
            Tab("Savings", Icons.Rounded.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
            Tab("More", Icons.Rounded.Apps, Icons.Rounded.Apps),
        )
    }
    if (remindersOpen) {
        val reminders = loans.filter { loan -> !loan.closed && loan.dueDate != null &&
            loan.principalMinor > lp.filter { it.loanId == loan.id }.sumOf { it.amountMinor } }
            .sortedBy { it.dueDate }.take(5).map { "${it.person} · ${it.dueDate}" } +
            committeeMembers.filter { it.isMe && !it.received }.sortedBy { it.turnMonth }.take(5).map { "Kameti turn · ${it.turnMonth}" } +
            credits.filter { !it.closed && it.dueDate != null }.sortedBy { it.dueDate }.take(5).map { "Udhar: ${it.creditor} · ${it.dueDate}" }
        AlertDialog(onDismissRequest = { remindersOpen = false }, title = { Text("Your reminders") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reminders.isEmpty()) Text("No pending loan dates or personal kameti turns in your records.")
                reminders.forEach { Text(it) }
            } }, confirmButton = { TextButton(onClick = { remindersOpen = false }) { Text("Close") } })
    }
    BackHandler(enabled = !settingsOpen && tab != 0) { tab = 0 }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    if (settingsOpen) {
        SettingsScreen(
            context = context,
            vm = vm,
            user = user,
            cloudEnabled = cloudEnabled,
            lastCloudSync = lastCloudSync,
            cloudStatus = cloudStatus,
            theme = theme,
            biometric = biometric,
            driveEnabled = driveEnabled,
            lastSync = lastSync,
            weatherEnabled = weatherEnabled,
            weatherCity = weatherCity,
            weatherTemperature = weatherTemperature,
            weatherCondition = weatherCondition,
            weatherUpdatedAt = weatherUpdatedAt,
            transactionSounds = transactionSounds,
            onBack = { settingsOpen = false },
            onOpenSavings = { settingsOpen = false; tab = 4 },
            onTheme = { scope.launch { settings.setTheme(it) } },
            onBiometric = { enable ->
                if (enable && Biometrics.pick(context) == null) {
                    Toast.makeText(context, "Set up a screen lock or fingerprint in Android settings first", Toast.LENGTH_LONG).show()
                } else {
                    scope.launch { settings.setBiometric(enable) }
                }
            },
            onDriveEnabled = { scope.launch { settings.setDriveEnabled(user.id, it) } },
            onCloudEnabled = { enabled ->
                scope.launch {
                    settings.setCloudEnabled(user.id, enabled)
                    if (enabled) SyncScheduler.syncNow(context)
                    else SyncScheduler.cancelImmediate(context)
                }
            },
            onWeatherEnabled = { scope.launch { settings.setWeatherEnabled(it) } },
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
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LedgerBackdrop {
                LedgerBrandHeader(
                    subtitle = when (tab) {
                        1 -> "All your transactions"; 2 -> "Borrowed & lent"; 3 -> "Your kameti, organized"
                        4 -> "For a brighter tomorrow"; 5 -> "Understand your spending"; 6 -> "Everything in one place"; 7 -> "Udhar Saman"
                        else -> "Your money, a brighter tomorrow"
                    }, modifier = Modifier.statusBarsPadding(),
                ) {
                    IconButton(onClick = { tab = 1 }) { Icon(Icons.Rounded.Search, "Search transactions", Modifier.size(23.dp)) }
                    IconButton(onClick = { if (tab == 6) settingsOpen = true else remindersOpen = true }) {
                        Icon(if (tab == 6) Icons.Rounded.Settings else Icons.Rounded.NotificationsNone,
                            if (tab == 6) "Settings" else "Reminders", Modifier.size(23.dp))
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    val selected = when (i) { 0 -> tab == 0; 2 -> tab == 5; 3 -> tab == 4; 4 -> tab in listOf(1, 2, 3, 6, 7); else -> false }
                    NavigationBarItem(selected = selected,
                        onClick = { when (i) { 0 -> tab = 0; 1 -> autoFillOpen = true; 2 -> tab = 5; 3 -> tab = 4; else -> tab = 6 } },
                        icon = { Icon(t.icon, t.label, Modifier.size(25.dp)) },
                        label = { Text(t.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = LedgerColors.Teal, selectedTextColor = LedgerColors.Teal,
                            indicatorColor = Color.Transparent, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
            when (tab) {
                0 -> DashboardScreen(
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
                    weatherEnabled = weatherEnabled,
                    weatherCity = weatherCity,
                    weatherTemperature = weatherTemperature,
                    weatherCondition = weatherCondition,
                    onAddSalary = { quickTransaction = "INCOME" },
                    onAddExpense = { quickTransaction = "EXPENSE" },
                    onOpenSavings = { tab = 4 },
                    onOpenKameti = { tab = 3 },
                    onAutoFill = { autoFillOpen = true },
                    onAllTransactions = { tab = 1 },
                    onStartNewMonth = vm::startNewMonth,
                    onArchivePreviousMonth = { callback -> vm.archivePreviousMonth(callback) },
                    snapshot = snapshot,
                    aiTips = aiTips,
                    aiStatus = aiStatus,
                    aiEnabled = aiEnabled,
                    onEnableAi = { aiConsent = true },
                )
                1 -> TransactionsScreen(
                    items = tx,
                    onAutoFill = { autoFillOpen = true },
                    onSave = { t, a, c, n, d, e ->
                        vm.launch(onSuccess = {
                            if (transactionSounds) TransactionSoundPlayer.play(context, t)
                        }) {
                            if (e != null && e.type == t && e.category != c && n.isNotBlank()) {
                                vm.rememberCategory(t, n, c)?.let { error(it) }
                            }
                            saveTransaction(t, a, c, n, d, e)
                        }
                    },
                    onDelete = { x -> vm.launch { deleteTransaction(x) } },
                )
                2 -> LoansScreen(
                    loans = loans,
                    payments = lp,
                    onAdd = { d, p, a, due, n, phone, wa -> vm.launch { saveLoan(d, p, a, due, n, phone, wa) } },
                    onPayment = { id, a, d, n, method -> vm.launch { addLoanPayment(id, a, d, n, method) } },
                    onDelete = { x -> vm.launch { deleteLoan(x) } },
                )
                3 -> CommitteeScreen(
                    committees = committees,
                    payments = cp,
                    receipts = receipts,
                    members = committeeMembers,
                    onAdd = { n, a, t, s, p, note, shares, phone -> vm.launch { saveCommittee(n, a, t, s, p, note, shares, phone) } },
                    onPaid = { c, i, m, method -> vm.launch { markCommitteePaid(c, i, m, method) } },
                    onReceive = { id, a, d, n, method -> vm.launch { addCommitteeReceipt(id, a, d, n, method) } },
                    onDeleteReceipt = { r -> vm.launch { deleteCommitteeReceipt(r) } },
                    onAddMember = { id, name, phone, wa, turn, month, me, note -> vm.launch { saveCommitteeMember(id, name, phone, wa, turn, month, me, note) } },
                    onMemberReceived = { member, date -> vm.launch { markCommitteeMemberReceived(member, date) } },
                    onDeleteMember = { member -> vm.launch { deleteCommitteeMember(member) } },
                    onDelete = { c -> vm.launch { deleteCommittee(c) } },
                )
                4 -> SavingsScreen(
                    savings = savings,
                    onAdd = { k, a, d, n -> vm.launch { saveSaving(k, a, d, n) } },
                    onDelete = { x -> vm.launch { deleteSaving(x) } },
                )
                5 -> LedgerBackdrop(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Your monthly insights", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        InsightsCard(snapshot, aiTips, aiStatus, aiEnabled) { aiConsent = true }
                    }
                }
                7 -> CreditScreen(credits, creditPayments, vm::saveCreditDrafts, vm::saveCreditPayment,
                    onDelete = { credit -> vm.launch { deleteCreditPurchase(credit) } })
                else -> MoreScreen({ tab = 1 }, { tab = 2 }, { tab = 3 }, { tab = 4 }, { settingsOpen = true }, { tab = 7 })
            }
        }
    }
    quickTransaction?.let { type ->
        TransactionDialog(
            null,
            dismiss = { quickTransaction = null },
            initialType = type,
            initialCategory = if (type == "INCOME") "Salary" else "",
            save = { t, a, c, n, d ->
                vm.launch(onSuccess = {
                    if (transactionSounds) TransactionSoundPlayer.play(context, t)
                }) { saveTransaction(t, a, c, n, d) }
                quickTransaction = null
            },
        )
    }
}
