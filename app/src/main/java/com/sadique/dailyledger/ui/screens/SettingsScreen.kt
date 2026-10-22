package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface
import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ai.AiConsent
import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.*
import com.sadique.dailyledger.audio.TransactionSoundPlayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.auth.UserProfile
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.export.ExportManager
import com.sadique.dailyledger.export.ExportPeriod
import com.sadique.dailyledger.notifications.ExportNotifications
import com.sadique.dailyledger.sync.*
import com.sadique.dailyledger.ui.MainViewModel
import com.sadique.dailyledger.weather.WeatherService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    context: Context,
    vm: MainViewModel,
    user: UserProfile,
    theme: String,
    biometric: Boolean,
    driveEnabled: Boolean,
    lastSync: Long,
    driveStatus: String,
    weatherEnabled: Boolean,
    weatherCity: String,
    weatherTemperature: String,
    weatherCondition: String,
    weatherUpdatedAt: Long,
    transactionSounds: Boolean,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onDriveEnabled: (Boolean) -> Unit,
    onWeatherEnabled: (Boolean) -> Unit,
    onTransactionSounds: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOpenSavings: () -> Unit = {},
    onAutoFill: () -> Unit = {},
    entranceVisit: Int = 0,
    pinHash: String = "",
    onSetPin: (String) -> Unit = {},
    onRemovePin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val aiEnabled by vm.aiEnabled.collectAsState()
    var aiConsent by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    if (showWhatsNew) WhatsNewDialog { showWhatsNew = false }
    if (aiConsent) {
        AlertDialog(
            onDismissRequest = { aiConsent = false },
            title = { Text(AiConsent.TITLE) },
            text = { Text(AiConsent.TEXT) },
            confirmButton = { TextButton(onClick = { vm.setAiEnabled(true); aiConsent = false }) { Text("Enable AI") } },
            dismissButton = { TextButton(onClick = { aiConsent = false }) { Text("Cancel") } },
        )
    }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val strings = com.sadique.dailyledger.i18n.LocalStrings.current
    fun matchesSetting(terms: String) = settingsQuery.isBlank() || settingsQuery.trim().split(Regex("\\s+")).all {
        (terms + " " + strings.text(terms)).contains(it, true)
    }
    var preview by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var exportPdf by remember { mutableStateOf(true) }
    var exportPeriod by remember { mutableStateOf(ExportPeriod.CURRENT_MONTH) }
    var exportFrom by remember { mutableStateOf("") }
    var exportThrough by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; message = null
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                    var imported = 0; var skipped = 0
                    vm.saveChange {
                        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                            if (line.startsWith("date", ignoreCase = true)) return@forEach
                            val parts = splitCsvLine(line)
                            val date = parts.getOrNull(0)?.trim().orEmpty()
                            val type = parts.getOrNull(1)?.trim()?.uppercase(java.util.Locale.ROOT).orEmpty()
                            val category = parts.getOrNull(2)?.trim().orEmpty()
                            val amount = parts.getOrNull(3)?.let { parseAmountExpression(it) } ?: 0L
                            val note = parts.getOrNull(4)?.trim().orEmpty()
                            if (isValidDate(date) && (type == "INCOME" || type == "EXPENSE") && category.isNotBlank() && amount > 0L) {
                                saveTransaction(type, amount, category, note, date)
                                imported++
                            } else skipped++
                        }
                    }
                    message = "CSV import complete: $imported entries added, $skipped lines skipped."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = "CSV import failed: ${e.message ?: "unknown error"}" }
                finally { busy = false }
            }
        }
    }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var resetStage by remember { mutableStateOf(0) }
    val offlineProfiles by vm.offlineProfiles.collectAsState()
    var offlineImport by remember { mutableStateOf<String?>(null) }
    var backupHistory by remember { mutableStateOf<List<DriveRevision>?>(null) }
    var selectedRevision by remember { mutableStateOf<DriveRevision?>(null) }
    var cityInput by rememberSaveable(weatherCity) { mutableStateOf(weatherCity) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val backupState by remember(settingsStore) { settingsStore.all.map { it.driveState } }.collectAsState(initial = "unknown")
    val display = LocalDisplayPreferences.current!!
    val salaryTransactions by vm.transactions.collectAsState()
    val savings by vm.savings.collectAsState()
    val backupReady = lastSync > 0L
    var backupDetails by rememberSaveable { mutableStateOf(false) }
    var cityEditor by rememberSaveable { mutableStateOf(false) }
    fun export(pdf: Boolean, previewOnly: Boolean = false, periodOverride: ExportPeriod? = null) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val (from, through) = when (periodOverride ?: exportPeriod) {
                    ExportPeriod.CURRENT_MONTH -> {
                        val month = YearMonth.now()
                        month.atDay(1) to month.atEndOfMonth()
                    }
                    ExportPeriod.ALL_TIME -> null to null
                    ExportPeriod.CUSTOM -> {
                        val start = exportFrom.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                        val end = exportThrough.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                        require(start == null || end == null || !start.isAfter(end)) {
                            "Start date must be on or before end date."
                        }
                        start to end
                    }
                }
                val accountLabel = user.name.ifBlank { user.email }
                if (previewOnly) {
                    preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ExportManager.statementBytes(vm.repo, from, through, accountLabel) }
                    return@launch
                }
                val location = if (pdf) ExportManager.pdf(context, vm.repo, from, through, accountLabel)
                    else ExportManager.csv(context, vm.repo, from, through)
                val format = if (pdf) "PDF" else "CSV"
                val notificationPosted = ExportNotifications.post(context, format, location)
                val notificationStatus = if (notificationPosted) ""
                    else "\nNotifications are disabled. Enable them in Android app settings to receive export alerts."
                exportResult = "$format export completed successfully.\nSaved to: $location$notificationStatus"
                message = exportResult
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exportResult = null
                message = "Export failed: ${e.message ?: "Please try again."}"
            } finally {
                busy = false
            }
        }
    }
    suspend fun runDrive(action: String) {
        try {
            if (action == "HISTORY") {
                backupHistory = DriveSync(context, vm.ownerId).history()
            } else if (action == "RESTORE" || action == "HISTORY_RESTORE") {
                DriveSync(context, vm.ownerId).restore(if (action == "HISTORY_RESTORE") selectedRevision else null)
                message = if (action == "HISTORY_RESTORE") "Backup copy restored. Check the backup status before making more changes." else "Cloud backup restored to this phone."
                selectedRevision = null
            } else {
                DriveSync(context, vm.ownerId).upload(allowReplace = true)
                message = "Backup saved to the cloud."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Cloud backup operation failed."
            settingsStore.setDriveStatus(user.id, message.orEmpty())
        } finally {
            busy = false
        }
    }
    fun drive(action: String) {
        if (busy) return
        busy = true; message = null
        scope.launch { runDrive(action) }
    }

    backupHistory?.let { copies ->
        AlertDialog(
            onDismissRequest = { backupHistory = null },
            title = { Text("Backup history") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Choose a copy to restore. Restoring replaces this phone's ledger; export any newer records you want to keep first.")
                    if (copies.isEmpty()) Text("No backup copies were found.")
                    copies.take(30).forEach { copy ->
                        val time = runCatching { formatTime(java.time.Instant.parse(copy.modifiedTime).toEpochMilli()) }.getOrDefault(copy.modifiedTime)
                        TextButton(onClick = {
                            selectedRevision = copy; backupHistory = null; confirmation = "DRIVE_HISTORY_RESTORE"
                        }, modifier = Modifier.fillMaxWidth()) { Text(time) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { backupHistory = null }) { Text("Close") } },
        )
    }

    offlineImport?.let { oldOwner ->
        AlertDialog(
            onDismissRequest = { offlineImport = null },
            title = { Text("Import old offline records?") },
            text = { Text("These records will be moved into ${user.email} on this phone and included in this account's future backups. Existing account records are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    offlineImport = null
                    vm.importOffline(oldOwner)
                }) { Text("Import records") }
            },
            dismissButton = { TextButton(onClick = { offlineImport = null }) { Text("Cancel") } },
        )
    }

    confirmation?.let { action ->
        val restore = action.endsWith("RESTORE")
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    if (restore) "Restore cloud backup?" else "Back up to the cloud?"
                )
            },
            text = {
                Text(
                    if (action == "DRIVE_HISTORY_RESTORE")
                        "This replaces this phone's ledger with the selected older copy. Automatic backup pauses after restoring an older copy so your latest cloud backup stays safe. Review the restored records before using Back up now."
                    else if (restore)
                        "This replaces the records for ${user.email} on this phone with the backup in the selected Google account. Export any changes you want to keep first."
                    else
                        "This makes this phone's ledger the latest cloud backup. The latest three copies are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        "DRIVE_RESTORE" -> drive("RESTORE")
                        "DRIVE_UPLOAD" -> drive("UPLOAD")
                        "DRIVE_HISTORY_RESTORE" -> drive("HISTORY_RESTORE")
                    }
                }) { Text(if (restore) "Restore" else "Back up now") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }

    if (resetStage > 0) {
        AlertDialog(
            onDismissRequest = { resetStage = 0 },
            title = { Text(if (resetStage == 1) "Reset local data?" else "Final confirmation") },
            text = { Text(if (resetStage == 1) "This permanently deletes the current account's local entries on this phone. A cloud backup is kept separately and can be restored later." else "This is the second confirmation. Your local records will be removed from this phone after you confirm.") },
            confirmButton = {
                TextButton(onClick = {
                    if (resetStage == 1) {
                        resetStage = 2
                    } else {
                        resetStage = 0
                        scope.launch {
                            try {
                                BackupLock.mutex.withLock {
                                    check(com.sadique.dailyledger.auth.FirebaseRuntime.auth(context).currentUser?.uid == vm.ownerId) { "Please sign in again." }
                                    settingsStore.pauseDriveBackup(vm.ownerId, "Local reset in progress. Automatic backup is paused.")
                                    vm.repo.clearAllForOwner()
                                    settingsStore.setDriveStatus(vm.ownerId, "Local data was reset. Automatic backup is paused to protect your Drive copy. Restore data, or use Back up now to replace it intentionally.", "paused")
                                }
                                SyncScheduler.cancelImmediate(context)
                                message = "Local records for this account were reset on this phone."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "Could not reset local records."
                            }
                        }
                    }
                }) { Text(if (resetStage == 1) "Continue" else "Delete local data") }
            },
            dismissButton = { TextButton(onClick = { resetStage = 0 }) { Text("Cancel") } },
        )
    }

    fun backFromSettings() {
        when {
            cityEditor -> cityEditor = false
            backupDetails -> backupDetails = false
            settingsQuery.isNotBlank() -> settingsQuery = ""
            else -> onBack()
        }
    }
    BackHandler(enabled = cityEditor || backupDetails || settingsQuery.isNotBlank()) { backFromSettings() }

    LedgerBackdrop(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .testTag("settings-list"),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        enteringItem(key = "settings-header", index = 0, entranceKey = entranceVisit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::backFromSettings, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                LedgerLogo(34.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Daily Ledger", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Your account & preferences", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAutoFill, enabled = !busy) { Icon(Icons.Rounded.AutoAwesome, "AI Auto Fill") }
            }
        }

        enteringItem(key = "settings-search", index = 2, entranceKey = entranceVisit) {
            OutlinedTextField(settingsQuery, { settingsQuery = it.take(80) }, label = { Text("Search settings") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings-search"), trailingIcon = { if (settingsQuery.isNotEmpty()) IconButton(onClick = { settingsQuery = "" }) { Icon(Icons.Rounded.Close, "Clear search") } })
        }
        enteringItem(key = "settings-language", index = 1, entranceKey = entranceVisit) {
            SectionCard("Language", "Choose your interface language") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = display.language == "en", onClick = { display.updateLanguage("en") }, label = { Text("English") }, modifier = Modifier.testTag("language-en"))
                    FilterChip(selected = display.language == "ur", onClick = { display.updateLanguage("ur") }, label = { Text("اردو") }, modifier = Modifier.testTag("language-ur"))
                }
                Text("Urdu uses a bundled Nastaleeq font and works offline. Saved categories and records keep their original keys.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (matchesSetting("Appearance theme text size font")) enteringItem(key = "settings-appearance", index = 2, entranceKey = entranceVisit) {
            SectionCard("Appearance", "Pick a theme and text size — changes apply right away") {
                LedgerThemeOptions.chunked(4).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowOptions.forEach { option ->
                            val isSelected = theme.uppercase() == option.key
                            Column(
                                modifier = Modifier.weight(1f).clickable { onTheme(option.key) }.testTag("theme-${option.key.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                        .background(option.background, RoundedCornerShape(12.dp))
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .padding(6.dp),
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(14.dp).background(option.primary, RoundedCornerShape(50)))
                                        Box(Modifier.size(14.dp).background(option.secondary, RoundedCornerShape(50)))
                                    }
                                }
                                Text(option.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        repeat(4 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Accent color", style = MaterialTheme.typography.labelLarge)
                Text("Optional — overrides the theme's main color across the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val accentChoices = listOf<Pair<String, androidx.compose.ui.graphics.Color?>>(("Theme" to null)) + com.sadique.dailyledger.ui.LedgerAccentColors.map { it.first to it.second }
                accentChoices.chunked(5).forEach { rowChoices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowChoices.forEach { (name, color) ->
                            val argb = color?.toArgb() ?: 0
                            val isSel = display.accentColor == argb
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(34.dp)
                                    .background(color ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                                    .border(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                                    .clickable { display.updateAccent(argb) }.testTag("accent-${name.lowercase()}"),
                                    contentAlignment = Alignment.Center) {
                                    if (color == null) Text("A", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        repeat(5 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Text size", style = MaterialTheme.typography.labelLarge)
                listOf(0 to "Small", 1 to "Normal", 2 to "Large", 3 to "Extra large").chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (value, label) ->
                            FilterChip(selected = display.fontSize == value, onClick = { display.updateFontSize(value) }, label = { Text(label) }, modifier = Modifier.testTag("font-size-$value"))
                        }
                    }
                }
            }
        }
        if (matchesSetting("Category icons colors appearance")) enteringItem(key = "settings-category-icons", index = 2, entranceKey = entranceVisit) {
            var categoryEditorOpen by remember { mutableStateOf(false) }
            SectionCard("Category icons & colors", "Give any category your own icon and color") {
                Text("Apni pasand ka icon aur rang chunein — lists, entry aur reports mein foran nazar ayega.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { categoryEditorOpen = true }, modifier = Modifier.testTag("open-category-icons")) { Text("Choose a category") }
            }
            if (categoryEditorOpen) CategoryVisualEditorDialog(onDismiss = { categoryEditorOpen = false })
        }
        if (matchesSetting("Your categories custom add")) enteringItem(key = "settings-custom-categories", index = 2, entranceKey = entranceVisit) {
            SectionCard("Your categories", "Add your own categories — they appear in the category list right away") {
                var newCatName by remember { mutableStateOf("") }
                var newCatType by remember { mutableStateOf("EXPENSE") }
                display.customCategories.forEach { entry ->
                    val parts = entry.split("|", limit = 2)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(parts.getOrNull(1) ?: entry, Modifier.weight(1f))
                        Text(if (parts.getOrNull(0) == "INCOME") "Income" else "Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { display.removeCustomCategory(entry) }) { Icon(Icons.Rounded.DeleteOutline, "Remove category") }
                    }
                }
                if (display.customCategories.isEmpty()) Text("No custom categories yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(newCatName, { newCatName = it }, label = { Text("New category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(newCatType == "EXPENSE", { newCatType = "EXPENSE" }, { Text("Expense") })
                    FilterChip(newCatType == "INCOME", { newCatType = "INCOME" }, { Text("Income") })
                }
                GradientButton("Add category", { display.addCustomCategory(newCatType, newCatName); newCatName = "" }, Modifier.fillMaxWidth(), enabled = newCatName.isNotBlank())
            }
        }
        if (matchesSetting("Widget balance home screen")) enteringItem(key = "settings-widget", index = 2, entranceKey = entranceVisit) {
            SectionCard("Home screen widget", "Widget kya dikhaye, tumhare ikhtiyar mein") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Widget par balance dikhao")
                        Text("On ho to home-screen widget par balance aur Udhar/Kameti dues nazar ayenge. Privacy mode on ho to balance phir bhi chhupa rahega.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = display.widgetShowBalance, onCheckedChange = { display.updateWidgetShowBalance(it); com.sadique.dailyledger.widget.LedgerWidget.refresh(context) })
                }
            }
        }
        if (matchesSetting("Salary calendar next expected date")) enteringItem(key = "settings-salary", index = 3, entranceKey = entranceVisit) {
            SalarySettingsCard(vm.salarySchedule, salaryTransactions)
        }

        if (matchesSetting("Cloud backup Restore account")) enteringItem(key = "settings-backup", index = 1, entranceKey = entranceVisit) {
            AccountBackupCard(driveEnabled, backupReady, if (lastSync > 0) formatTime(lastSync) else "Not yet backed up",
                driveStatus, busy, onDriveEnabled, { confirmation = "DRIVE_RESTORE" }, { confirmation = "DRIVE_UPLOAD" }, { backupDetails = !backupDetails; cityEditor = false }, backupState)
            TextButton(onClick = { drive("HISTORY") }, enabled = !busy, modifier = Modifier.testTag("backup-history")) {
                Icon(Icons.Rounded.History, null)
                Spacer(Modifier.width(8.dp))
                Text("Backup history")
            }
        }

        if (backupDetails && matchesSetting("Cloud backup Restore account")) enteringItem(key = "settings-backup-details", index = 2, entranceKey = entranceVisit) {
            SectionCard("Cloud backup details", "Encrypted and stored on the Daily Ledger cloud backup service.") {
                Text("Local records use SQLCipher encryption. Cloud backups are protected with 256-bit AES encryption on the backup server. The full backup includes private entries and payment proofs. Restore uses the same Daily Ledger account.", style = MaterialTheme.typography.bodySmall)
                Text("Tap Back up now once. Later changes upload automatically when internet is available — edits queue a backup within about 15 minutes, with a background attempt every 3 hours and a catch-up check when the app opens. Use the same Daily Ledger account to restore on another phone.", style = MaterialTheme.typography.bodySmall)
                Text("Cloud backup is tied to your Daily Ledger account sign-in.", style = MaterialTheme.typography.bodySmall)
                Text("Changes queue a backup when saved. Failed connections retry automatically, with a background attempt every 3 hours and a catch-up check when the app opens. Android battery restrictions can delay it. Keep the app installed until the latest backup is verified.", style = MaterialTheme.typography.bodySmall)
                Text("The latest three copies are kept. Budgets, bill reminders, handled occurrences, salary calendar and AI learning are included. Unchanged records are verified without an extra upload.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (matchesSetting("Weather location sounds personalization")) enteringItem(key = "settings-personalization", index = 2, entranceKey = entranceVisit) {
            PersonalizationCard(transactionSounds, weatherEnabled, weatherCity, onTransactionSounds, onWeatherEnabled,
                { cityEditor = !cityEditor; backupDetails = false }, { TransactionSoundPlayer.playBatchSuccess(context) })
        }

        if (cityEditor && matchesSetting("Weather location city")) enteringItem(key = "settings-weather", index = 3, entranceKey = entranceVisit) {
            SectionCard("Weather location", "Choose your city. No GPS permission is needed.") {
                OutlinedTextField(cityInput, { cityInput = it }, label = { Text("City") }, placeholder = { Text("Gujranwala, Pakistan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (weatherTemperature.isNotBlank()) Text("$weatherTemperature · $weatherCondition", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            settingsStore.setWeatherCity(cityInput)
                            val weather = WeatherService().currentForCity(cityInput)
                            settingsStore.setWeatherSnapshot(weather.city, weather.temperature, weather.condition, weather.fetchedAt)
                            cityInput = weather.city; message = "Weather updated for ${weather.city}."; cityEditor = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "Could not update weather. Try again when online." }
                        finally { busy = false }
                    }
                }, enabled = !busy && cityInput.trim().length >= 2) { Text("Save city & refresh weather") }
                Text("Weather data by Open-Meteo.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (matchesSetting("Savings")) enteringItem(key = "settings-savings", index = 3, entranceKey = entranceVisit) {
            SavingsSettingsCard(savings.sumOf { it.amountMinor }, onOpenSavings)
        }

        if (matchesSetting("Encryption privacy security")) enteringItem(key = "settings-encryption", index = 4, entranceKey = entranceVisit) {
            EncryptionBanner()
        }

        if (offlineProfiles.isNotEmpty() && matchesSetting("Import old offline records")) enteringItem(key = "settings-offline-import", index = 5, entranceKey = entranceVisit) {
                SectionCard("Previous offline data", "Choose an old profile on this phone to add its records to this account.") {
                    offlineProfiles.forEachIndexed { index, profile ->
                        OutlinedButton(onClick = { offlineImport = profile.ownerId }, enabled = !busy) {
                            Text("Import profile ${index + 1} (${profile.records} records)")
                        }
                    }
                }
        }

        if (matchesSetting("AI Auto Fill suggestions")) enteringItem(key = "settings-ai", index = 5, entranceKey = entranceVisit) {
            SectionCard("Mini AI & Auto Fill", "Offline Mini AI and saving suggestions work without internet or API cost. Online suggestions update only when you tap Refresh. Cloud AI is optional for complex wording.") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Optional Cloud AI", Modifier.weight(1f))
                    Switch(aiEnabled, onCheckedChange = { if (it) aiConsent = true else vm.setAiEnabled(false) })
                }
                var aiKeyText by remember { mutableStateOf(vm.aiKey()) }
                var aiProxyText by remember { mutableStateOf(vm.aiProxy()) }
                var aiProviderId by remember { mutableStateOf(vm.aiProvider()) }
                var aiModelText by remember { mutableStateOf(vm.aiProviderModel()) }
                var aiBaseText by remember { mutableStateOf(vm.aiProviderBase()) }
                var aiKeyMsg by remember { mutableStateOf("") }
                var aiTesting by remember { mutableStateOf(false) }
                var providerMenu by remember { mutableStateOf(false) }
                val aiProvider = com.sadique.dailyledger.ai.AiProviders.byId(aiProviderId)
                fun saveAiFields() { vm.saveAiKey(aiKeyText); vm.saveAiProxy(aiProxyText); vm.saveAiProvider(aiProviderId); vm.saveAiProviderModel(aiModelText); vm.saveAiProviderBase(aiBaseText) }
                Text("Personal AI provider (agar apni key lagani ho)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { providerMenu = true }) { Text(aiProvider.label + "  ▾") }
                    androidx.compose.material3.DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        com.sadique.dailyledger.ai.AiProviders.ALL.forEach { p ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(p.label) }, onClick = { aiProviderId = p.id; providerMenu = false })
                        }
                    }
                }
                OutlinedTextField(aiKeyText, { aiKeyText = it }, label = { Text(aiProvider.label + " API key") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (!aiProvider.geminiNative) {
                    if (aiProvider.id == "custom") {
                        OutlinedTextField(aiBaseText, { aiBaseText = it }, label = { Text("Base URL (masalan https://.../v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(aiModelText, { aiModelText = it }, label = { Text("Model (khali = default: " + aiProvider.defaultModel + ")") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("AI tayyar hai — app built-in proxy (Gemini) se chalti hai, key dalne ki zaroorat nahi. Chahein to apni personal API key laga sakte hain — Gemini, OpenAI, Groq, OpenRouter, DeepSeek, Mistral ya koi bhi OpenAI-compatible service. Personal key sirf is phone mein mehfooz rehti hai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(aiProxyText, { aiProxyText = it }, label = { Text("Proxy URL (optional — key ki jagah)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { saveAiFields(); aiKeyMsg = "Save ho gaya." }) { Text("Save") }
                    OutlinedButton(enabled = !aiTesting, onClick = {
                        saveAiFields(); aiTesting = true
                        scope.launch { aiKeyMsg = try { vm.testAiConnection(); "Connection kamyab hai ✓" } catch (e: Exception) { e.message ?: "Connection fail ho gaya." }; aiTesting = false }
                    }) { Text(if (aiTesting) "Check ho raha hai..." else "Connection test") }
                }
                if (aiKeyMsg.isNotBlank()) Text(aiKeyMsg, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (matchesSetting("Security privacy screenshot biometric device lock")) enteringItem(key = "settings-security", index = 7, entranceKey = entranceVisit) {
            SectionCard("Security & privacy", null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide financial amounts (privacy)", Modifier.weight(1f))
                    Switch(checked = display.privateAmounts, onCheckedChange = display::updatePrivateAmounts)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Block screenshots and app previews", Modifier.weight(1f))
                    Switch(checked = display.secureScreens, onCheckedChange = display::updateSecureScreens)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Biometric / device lock", Modifier.weight(1f))
                    Switch(biometric, onCheckedChange = onBiometric)
                }
                var pinEditor by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock PIN")
                        Text("PIN set ho to app khulne par PIN ya biometric — jo asan ho — chalega.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pinHash.isNotBlank()) {
                        TextButton(onClick = { pinEditor = true }) { Text("Change") }
                        TextButton(onClick = onRemovePin) { Text("Remove") }
                    } else {
                        TextButton(onClick = { pinEditor = true }) { Text("Set PIN") }
                    }
                }
                if (pinEditor) {
                    var pin1 by remember { mutableStateOf("") }
                    var pin2 by remember { mutableStateOf("") }
                    val pinValid = com.sadique.dailyledger.security.PinLock.isValidFormat(pin1) && pin1 == pin2
                    AlertDialog(
                        onDismissRequest = { pinEditor = false },
                        title = { Text(if (pinHash.isNotBlank()) "Change app PIN" else "Set app PIN") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedTextField(
                                    value = pin1,
                                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin1 = it },
                                    label = { Text("New PIN (4–8 digits)") }, singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                androidx.compose.material3.OutlinedTextField(
                                    value = pin2,
                                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin2 = it },
                                    label = { Text("Confirm PIN") }, singleLine = true,
                                    isError = pin2.isNotEmpty() && pin1 != pin2,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (pin2.isNotEmpty() && pin1 != pin2) Text("PINs do not match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = { TextButton(onClick = { onSetPin(pin1); pinEditor = false }, enabled = pinValid) { Text("Save PIN") } },
                        dismissButton = { TextButton(onClick = { pinEditor = false }) { Text("Cancel") } },
                    )
                }
            }
        }

        if (matchesSetting("Export PDF CSV statement Share preview")) enteringItem(key = "settings-export", index = 8, entranceKey = entranceVisit) {
            SectionCard("Export your ledger", "A statement to share, or a spreadsheet to work with.") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true to "PDF statement", false to "CSV spreadsheet").forEach { (pdf, title) ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable(enabled = !busy) { exportPdf = pdf },
                            shape = RoundedCornerShape(20.dp),
                            color = if (exportPdf == pdf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (exportPdf == pdf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (pdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.TableChart, null, tint = MaterialTheme.colorScheme.primary)
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (pdf) "Designed for reading" else "Full descriptions & ledgers", fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text("Statement period", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ExportPeriod.CURRENT_MONTH to "Current month",
                        ExportPeriod.ALL_TIME to "All records",
                        ExportPeriod.CUSTOM to "Choose dates",
                    ).forEach { (period, label) ->
                        FilterChip(
                            selected = exportPeriod == period,
                            onClick = { exportPeriod = period },
                            label = { Text(label) },
                        )
                    }
                }
                if (exportPeriod == ExportPeriod.CUSTOM) {
                    OutlinedTextField(
                        exportFrom,
                        { exportFrom = it },
                        label = { Text("From date (optional)") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = exportFrom.isNotBlank() && runCatching { LocalDate.parse(exportFrom.trim()) }.isFailure,
                        supportingText = { Text("Use YYYY-MM-DD, or leave blank") },
                    )
                    OutlinedTextField(
                        exportThrough,
                        { exportThrough = it },
                        label = { Text("To date (optional)") },
                        placeholder = { Text("YYYY-MM-DD") },
                        isError = exportThrough.isNotBlank() && runCatching { LocalDate.parse(exportThrough.trim()) }.isFailure,
                        supportingText = { Text("Use YYYY-MM-DD, or leave blank") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Leave either date empty to include records before or after the entered date.", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (exportPdf) "PDF includes income, expenses and a separate kameti statement with paid installments and each receiving. Payments follow their installment month; undated receiving appears in All records. It does not replace a backup."
                    else "CSV includes transactions, savings, loan payments and kameti records. Keep downloaded files private.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) Text("Working on your request…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (exportPdf) OutlinedButton(onClick = { export(true, previewOnly = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export-preview")) { Text("Preview & share PDF") }
                OutlinedButton(onClick = { export(true, periodOverride = ExportPeriod.CURRENT_MONTH) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export-monthly-pdf")) { Text("Monthly PDF report (this month)") }
                GradientButton(if (exportPdf) "Save PDF statement" else "Save CSV spreadsheet", { export(exportPdf) },
                    Modifier.fillMaxWidth().testTag("export-save"), enabled = !busy, icon = Icons.Rounded.FileDownload)
                Text("Saved to Downloads on Android 10+. Exported files are readable, not encrypted.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("Import transactions from CSV", style = MaterialTheme.typography.labelLarge)
                Text("One entry per line: date, type, category, amount, note — for example: 2026-10-01, EXPENSE, Food, 1250, Lunch. A header line is skipped automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("import-csv")) { Text("Import CSV") }
            }
        }

        if (matchesSetting("App info version")) enteringItem(key = "settings-info", index = 9, entranceKey = entranceVisit) {
            SectionCard("App info", "Version ${com.sadique.dailyledger.BuildConfig.VERSION_NAME} (code ${com.sadique.dailyledger.BuildConfig.VERSION_CODE})") {
                Text("Optimized for quick local data loading, backup safety and household money tracking.", style = MaterialTheme.typography.bodySmall)
                Text("Records stay on this phone unless you use cloud backup, export, or optional Cloud AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showWhatsNew = true }, modifier = Modifier.fillMaxWidth().testTag("whats-new")) { Text("What's New") }
            }
        }

        if (matchesSetting("Reset local records")) enteringItem(key = "settings-reset", index = 10, entranceKey = entranceVisit) {
            SectionCard("Local data reset", "Permanent reset only on this phone. A cloud backup can still be restored later.") {
                OutlinedButton(onClick = { resetStage = 1 }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reset local records") }
            }
        }

        if (matchesSetting("Account sign out email")) enteringItem(key = "settings-account", index = 11, entranceKey = entranceVisit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
                Text(user.name, translate = false, style = MaterialTheme.typography.titleMedium)
                Text(user.email, translate = false, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
    }
    preview?.let { ExportPreview(it) { preview = null } }
    exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { exportResult = null },
            title = { Text("Export complete") },
            text = { Text(result) },
            confirmButton = { TextButton(onClick = { exportResult = null }) { Text("Done") } },
        )
    }
}

@Composable
internal fun SectionCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        accent = com.sadique.dailyledger.ui.ledgerCardAccent("settings:$title"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        })
    }
}


@Composable
private fun StatusBadge(text: String, positive: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTime(value: Long) = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(value))
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity found in context chain")
}

/** Splits one CSV line, honoring double-quoted fields that may contain commas. */
private fun splitCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (c in line) {
        when {
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += current.toString(); current.clear() }
            else -> current.append(c)
        }
    }
    out += current.toString()
    return out
}
