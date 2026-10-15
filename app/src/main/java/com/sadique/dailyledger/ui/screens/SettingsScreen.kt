package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ai.AiConsent
import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
) {
    val scope = rememberCoroutineScope()
    val aiEnabled by vm.aiEnabled.collectAsState()
    var aiConsent by remember { mutableStateOf(false) }
    if (aiConsent) {
        AlertDialog(
            onDismissRequest = { aiConsent = false },
            title = { Text(AiConsent.TITLE) },
            text = { Text(AiConsent.TEXT) },
            confirmButton = { TextButton(onClick = { vm.setAiEnabled(true); aiConsent = false }) { Text("Enable AI") } },
            dismissButton = { TextButton(onClick = { aiConsent = false }) { Text("Cancel") } },
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var exportPdf by remember { mutableStateOf(true) }
    var exportPeriod by remember { mutableStateOf(ExportPeriod.CURRENT_MONTH) }
    var exportFrom by remember { mutableStateOf("") }
    var exportThrough by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var resetStage by remember { mutableStateOf(0) }
    val offlineProfiles by vm.offlineProfiles.collectAsState()
    var offlineImport by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf("UPLOAD") }
    var cityInput by rememberSaveable(weatherCity) { mutableStateOf(weatherCity) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val display = LocalDisplayPreferences.current!!
    val savings by vm.savings.collectAsState()
    val backupReady = driveEnabled && lastSync > 0L
    var backupDetails by rememberSaveable { mutableStateOf(false) }
    var cityEditor by rememberSaveable { mutableStateOf(false) }
    fun export(pdf: Boolean) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val (from, through) = when (exportPeriod) {
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
                val location = if (pdf) ExportManager.pdf(context, vm.repo, from, through)
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
    suspend fun runDrive(token: String, action: String) {
        try {
            if (action == "RESTORE") {
                DriveSync(context, vm.ownerId).restore(token)
                message = "Google Drive backup restored to this phone."
            } else {
                DriveSync(context, vm.ownerId).upload(token, allowReplace = true)
                message = "Backup saved to your Google Drive account."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Google Drive operation failed."
        } finally {
            busy = false
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = if (result.resultCode == Activity.RESULT_OK) DriveAuth.tokenFromResult(context, result.data) else null
        if (token != null) scope.launch { runDrive(token, pendingDriveAction) }
        else { busy = false; message = "Google Drive access was not granted. Your data is unchanged." }
    }
    fun drive(action: String) {
        val email = user.googleEmail ?: user.email
        if (email.isBlank()) {
            message = "Add a Google account to this phone before using Google Drive backup."
            return
        }
        if (busy) return
        busy = true; pendingDriveAction = action; message = null
        scope.launch {
            try {
                when (val result = DriveAuth.authorize(context.findActivity(), email)) {
                    is DriveAuthOutcome.Token -> runDrive(result.value, action)
                    is DriveAuthOutcome.Resolution -> launcher.launch(result.request)
                    is DriveAuthOutcome.Error -> { busy = false; message = result.message }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false; message = e.message ?: "Google Drive access failed."
            }
        }
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
                    if (restore) "Restore Google Drive backup?" else "Back up to Google Drive?"
                )
            },
            text = {
                Text(
                    if (restore)
                        "This replaces the records for ${user.email} on this phone with the backup in the selected Google account. Export any changes you want to keep first."
                    else
                        "This uploads this phone's full ledger to the selected Google account and replaces its previous Daily Ledger backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        "DRIVE_RESTORE" -> drive("RESTORE")
                        "DRIVE_UPLOAD" -> drive("UPLOAD")
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
            text = { Text(if (resetStage == 1) "This permanently deletes the current account's local entries on this phone. A Google Drive backup is kept separately and can be restored later." else "This is the second confirmation. Your local records will be removed from this phone after you confirm.") },
            confirmButton = {
                TextButton(onClick = {
                    if (resetStage == 1) {
                        resetStage = 2
                    } else {
                        resetStage = 0
                        scope.launch {
                            try {
                                vm.repo.clearAllForOwner()
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
            else -> onBack()
        }
    }
    BackHandler(enabled = cityEditor || backupDetails) { backFromSettings() }

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
        enteringItem(key = "settings-header", index = 0) {
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

        enteringItem(key = "settings-backup", index = 1) {
            AccountBackupCard(driveEnabled, backupReady, if (lastSync > 0) formatTime(lastSync) else "Not yet backed up",
                driveStatus, busy, onDriveEnabled, { confirmation = "DRIVE_RESTORE" }, { confirmation = "DRIVE_UPLOAD" }, { backupDetails = !backupDetails; cityEditor = false })
        }

        if (backupDetails) enteringItem(key = "settings-backup-details", index = 2) {
            SectionCard("Google Drive backup details", "Saved to this Google account's private Daily Ledger app-data folder.") {
                Text("The backup contains your full ledger as readable data without a separate encryption passphrase. Google account security protects the Drive account; anyone who can access your account or this app's Drive permission may be able to read it.", style = MaterialTheme.typography.bodySmall)
                Text("Tap Back up now and approve Google Drive access once. Later changes upload automatically when internet and Drive access are available. Use the same Daily Ledger account and Google account to restore on another phone.", style = MaterialTheme.typography.bodySmall)
                Text("Google Drive backup is separate from Firebase sign-in. Firestore is no longer used for ledger backups.", style = MaterialTheme.typography.bodySmall)
            }
        }

        enteringItem(key = "settings-personalization", index = 2) {
            PersonalizationCard(transactionSounds, weatherEnabled, weatherCity, onTransactionSounds, onWeatherEnabled,
                { cityEditor = !cityEditor; backupDetails = false }, { TransactionSoundPlayer.playBatchSuccess(context) })
        }

        if (cityEditor) enteringItem(key = "settings-weather", index = 3) {
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

        enteringItem(key = "settings-savings", index = 3) {
            SavingsSettingsCard(savings.sumOf { it.amountMinor }, onOpenSavings)
        }

        enteringItem(key = "settings-encryption", index = 4) {
            EncryptionBanner()
        }

        if (offlineProfiles.isNotEmpty()) enteringItem(key = "settings-offline-import", index = 5) {
                SectionCard("Previous offline data", "Choose an old profile on this phone to add its records to this account.") {
                    offlineProfiles.forEachIndexed { index, profile ->
                        OutlinedButton(onClick = { offlineImport = profile.ownerId }, enabled = !busy) {
                            Text("Import profile ${index + 1} (${profile.records} records)")
                        }
                    }
                }
        }

        enteringItem(key = "settings-ai", index = 5) {
            SectionCard("Mini AI & Auto Fill", "Offline Mini AI and saving suggestions work without internet or API cost. Online suggestions update only when you tap Refresh. Cloud AI is optional for complex wording.") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Optional Cloud AI", Modifier.weight(1f))
                    Switch(aiEnabled, onCheckedChange = { if (it) aiConsent = true else vm.setAiEnabled(false) })
                }
            }
        }

        enteringItem(key = "settings-verification", index = 6) {
            AppVerificationCard(context)
        }

        enteringItem(key = "settings-appearance", index = 7) {
            SectionCard("Appearance", null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SYSTEM", "LIGHT", "DARK", "AQUA", "SUNSET", "FOREST", "ROSE", "MIDNIGHT").forEach {
                        FilterChip(
                            selected = theme == it,
                            onClick = { onTheme(it) },
                            label = { Text(it.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                Text("Accessibility and layout presets", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        display.updateFontSize(1)
                        display.updateIconSize(1)
                        display.updateCardSize(1)
                        display.updateCompact(false)
                    }) { Text("Standard") }
                    OutlinedButton(onClick = {
                        display.updateFontSize(0)
                        display.updateIconSize(0)
                        display.updateCardSize(0)
                        display.updateCompact(true)
                    }) { Text("Compact") }
                    OutlinedButton(onClick = {
                        display.updateFontSize(3)
                        display.updateIconSize(2)
                        display.updateCardSize(2)
                        display.updateCompact(false)
                    }) { Text("Easy read") }
                }
                Text("Font size", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Small", "Medium", "Large", "XL").forEachIndexed { index, label ->
                        FilterChip(selected = display.fontSize == index, onClick = { display.updateFontSize(index) }, label = { Text(label) })
                    }
                }
                Text("Icon size", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Small", "Medium", "Large").forEachIndexed { index, label ->
                        FilterChip(selected = display.iconSize == index, onClick = { display.updateIconSize(index) }, label = { Text(label) })
                    }
                }
                Text("Card size", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Compact", "Normal", "Spacious").forEachIndexed { index, label ->
                        FilterChip(selected = display.cardSize == index, onClick = { display.updateCardSize(index) }, label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Compact layout", Modifier.weight(1f))
                    Switch(checked = display.compact, onCheckedChange = display::updateCompact)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide financial amounts (privacy)", Modifier.weight(1f))
                    Switch(checked = display.privateAmounts, onCheckedChange = display::updatePrivateAmounts)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Block screenshots and app previews", Modifier.weight(1f))
                    Switch(checked = display.secureScreens, onCheckedChange = display::updateSecureScreens)
                }
                Text("Dashboard cards", style = MaterialTheme.typography.titleMedium)
                listOf(
                    Triple("Spending chart", display.showSpending, display::updateShowSpending),
                    Triple("Recent transactions", display.showRecent, display::updateShowRecent),
                    Triple("Savings, kameti & loans", display.showSpaces, display::updateShowSpaces),
                    Triple("AI insights", display.showInsights, display::updateShowInsights),
                ).forEach { (label, checked, change) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Switch(checked = checked, onCheckedChange = change)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Savings before loans", Modifier.weight(1f))
                    Switch(checked = display.savingsFirst, onCheckedChange = display::updateSavingsFirst)
                }
                Text("Live preview", style = MaterialTheme.typography.titleMedium)
                LedgerCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowIcon(Icons.Rounded.AccountBalanceWallet, MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Sample balance card", style = MaterialTheme.typography.titleMedium)
                            Text(if (display.privateAmounts) "Rs •••••" else "Rs 45,500",
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text("Changes apply immediately", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton(onClick = display::reset, modifier = Modifier.fillMaxWidth()) { Text("Reset display settings") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Biometric / device lock", Modifier.weight(1f))
                    Switch(biometric, onCheckedChange = onBiometric)
                }
            }
        }

        enteringItem(key = "settings-export", index = 8) {
            SectionCard("Export your ledger", "A statement to share, or a spreadsheet to work with.") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true to "PDF statement", false to "CSV spreadsheet").forEach { (pdf, title) ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable(enabled = !busy) { exportPdf = pdf },
                            shape = RoundedCornerShape(16.dp),
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
                GradientButton(if (exportPdf) "Save PDF statement" else "Save CSV spreadsheet", { export(exportPdf) },
                    Modifier.fillMaxWidth().testTag("export-save"), enabled = !busy, icon = Icons.Rounded.FileDownload)
                Text("Saved to Downloads on Android 10+. Exported files are readable, not encrypted.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        enteringItem(key = "settings-info", index = 9) {
            SectionCard("App info", "Version ${com.sadique.dailyledger.BuildConfig.VERSION_NAME} (code ${com.sadique.dailyledger.BuildConfig.VERSION_CODE})") {
                Text("Optimized for quick local data loading, backup safety and household money tracking.", style = MaterialTheme.typography.bodySmall)
                Text("Records stay on this phone unless you use Google Drive backup, export, or optional Cloud AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        enteringItem(key = "settings-reset", index = 10) {
            SectionCard("Local data reset", "Permanent reset only on this phone. Google Drive backup can still be restored later.") {
                OutlinedButton(onClick = { resetStage = 1 }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reset local records") }
            }
        }

        enteringItem(key = "settings-account", index = 11) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text(user.email, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
    }
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
private fun SectionCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
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
