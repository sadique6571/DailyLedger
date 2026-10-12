package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ai.AiConsent
import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.auth.UserProfile
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.export.ExportManager
import com.sadique.dailyledger.export.ExportPeriod
import com.sadique.dailyledger.notifications.ExportNotifications
import com.sadique.dailyledger.security.SecureSecretStore
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
    cloudEnabled: Boolean,
    lastCloudSync: Long,
    cloudStatus: String,
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
    onCloudEnabled: (Boolean) -> Unit,
    onWeatherEnabled: (Boolean) -> Unit,
    onTransactionSounds: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOpenSavings: () -> Unit = {},
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
    val secrets = remember(user.id) { SecureSecretStore(context, user.id) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseReady by remember(user.id) { mutableStateOf(secrets.hasPassphrase()) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var exportPeriod by remember { mutableStateOf(ExportPeriod.CURRENT_MONTH) }
    var exportFrom by remember { mutableStateOf("") }
    var exportThrough by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var resetStage by remember { mutableStateOf(0) }
    val offlineProfiles by vm.offlineProfiles.collectAsState()
    var offlineImport by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf("UPLOAD") }
    var cityInput by remember(weatherCity) { mutableStateOf(weatherCity) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val savings by vm.savings.collectAsState()
    var backupReady by remember(user.id) { mutableStateOf(false) }
    var backupDetails by remember { mutableStateOf(false) }
    var cityEditor by remember { mutableStateOf(false) }
    LaunchedEffect(user.id, lastCloudSync, cloudStatus) {
        val state = settingsStore.cloudState(user.id)
        backupReady = state.ready && state.revision.isNotBlank()
    }

    fun cloud(action: String) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try {
                val backup = CloudBackup(context, user.id)
                message = when (action) {
                    "RESTORE" -> backup.restore()
                    "REPLACE" -> backup.replaceWithLocal()
                    "DELETE" -> backup.deleteBackup()
                    else -> backup.sync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = backupError(e)
                SettingsStore(context).cloudStatus(user.id, message.orEmpty())
            } finally {
                busy = false
            }
        }
    }
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
                SyncScheduler.syncNow(context)
                message = "Google Drive backup restored."
            } else {
                DriveSync(context, vm.ownerId).upload(token)
                message = "An encrypted copy was saved to Google Drive."
            }
            onDriveEnabled(true)
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
        val email = user.googleEmail ?: return
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
        val delete = action == "CLOUD_DELETE"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    when {
                        delete -> "Delete cloud backup?"
                        restore -> "Restore backup?"
                        else -> "Replace existing backup?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        delete -> "This deletes the current cloud backup. Older retained copies may remain under the storage retention policy. Records already on this phone are kept, and automatic backup will be turned off."
                        restore -> "This replaces this account's records on this phone with the selected backup. Export any changes you want to keep first."
                        else -> "This saves this phone's records over the existing backup for this account. Changes from another phone will be replaced."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        "CLOUD_RESTORE" -> cloud("RESTORE")
                        "CLOUD_REPLACE" -> cloud("REPLACE")
                        "CLOUD_DELETE" -> cloud("DELETE")
                        "DRIVE_RESTORE" -> drive("RESTORE")
                        "DRIVE_UPLOAD" -> drive("UPLOAD")
                    }
                }) { Text(if (delete) "Delete backup" else if (restore) "Restore" else "Save this phone's data") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }

    if (resetStage > 0) {
        AlertDialog(
            onDismissRequest = { resetStage = 0 },
            title = { Text(if (resetStage == 1) "Reset local data?" else "Final confirmation") },
            text = { Text(if (resetStage == 1) "This permanently deletes the current account's local entries on this phone. Cloud backup is kept separate and can be restored later if needed." else "This is the second confirmation. Your local records will be removed from this phone after you confirm.") },
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

    LedgerBackdrop(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            LedgerLogo(34.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Daily Ledger", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Your account & preferences", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AccountBackupCard(cloudEnabled, backupReady, if (lastCloudSync > 0) formatTime(lastCloudSync) else "Not yet checked",
            cloudStatus, busy, onCloudEnabled, { confirmation = "CLOUD_RESTORE" }, { cloud("SYNC") }, { backupDetails = !backupDetails })
        SectionCard("Backup encryption passphrase", "Free Firebase Spark backup is encrypted on this phone before upload.") {
            Text("Use at least 6 characters and keep this passphrase safe. You will need the same passphrase to restore on another phone. This passphrase is also used for optional Google Drive copies.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                passphrase,
                { passphrase = it },
                label = { Text("Backup passphrase") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy && !(backupReady && passphraseReady),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(onClick = {
                try {
                    secrets.savePassphrase(passphrase)
                    passphrase = ""
                    passphraseReady = true
                    message = "Backup passphrase saved securely on this phone."
                } catch (e: Exception) {
                    message = e.message ?: "Could not save the backup passphrase."
                }
            }, enabled = passphrase.length >= 6 && !busy && !(backupReady && passphraseReady)) { Text("Save passphrase") }
            Text(if (passphraseReady) "A passphrase is saved on this phone." else "Save a passphrase before the first cloud backup.", style = MaterialTheme.typography.bodySmall)
            if (backupReady && passphraseReady) {
                Text("Passphrase changes are locked while a cloud backup exists so its restore key is not accidentally replaced. Delete that backup first only if you understand that it removes the remote copy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (backupDetails) SectionCard("Backup details", "Free Firebase Spark Firestore · encrypted payload · up to 900 KB") {
            Text("Backups are encrypted on this phone with your saved passphrase before upload. Firebase stores the ciphertext only; keep your passphrase because it cannot be recovered by the app owner.", style = MaterialTheme.typography.bodySmall)
            Text("Restore and manual backup stay available even when automatic backup is turned off.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { confirmation = "CLOUD_REPLACE" }, enabled = !busy) { Text("Replace backup with this phone's data") }
            TextButton(onClick = { confirmation = "CLOUD_DELETE" }, enabled = !busy) { Text("Delete cloud backup", color = MaterialTheme.colorScheme.error) }
        }

        PersonalizationCard(transactionSounds, weatherEnabled, weatherCity, onTransactionSounds, onWeatherEnabled,
            { cityEditor = !cityEditor }, { TransactionSoundPlayer.playBatchSuccess(context) })
        if (cityEditor) SectionCard("Weather location", "Choose your city. No GPS permission is needed.") {
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
        SavingsSettingsCard(savings.sumOf { it.amountMinor }, onOpenSavings)
        EncryptionBanner()

        if (offlineProfiles.isNotEmpty()) {
            SectionCard("Previous offline data", "Choose an old profile on this phone to add its records to this account.") {
                offlineProfiles.forEachIndexed { index, profile ->
                    OutlinedButton(onClick = { offlineImport = profile.ownerId }, enabled = !busy) {
                        Text("Import profile ${index + 1} (${profile.records} records)")
                    }
                }
            }
        }

        if (user.googleEmail != null) {
            SectionCard("Google Drive copy", "Optional extra backup for ${user.googleEmail}.") {
                Text(
                    "This optional copy is encrypted with the same backup passphrase set above.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirmation = "DRIVE_UPLOAD" }, enabled = passphraseReady && !busy) { Text("Save copy") }
                    OutlinedButton(onClick = { confirmation = "DRIVE_RESTORE" }, enabled = passphraseReady && !busy) { Text("Restore copy") }
                }
                Text(
                    if (driveEnabled && lastSync > 0) "Last Drive operation: ${formatTime(lastSync)}" else "No Drive copy completed on this phone yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard("Mini AI & Auto Fill", "Offline Mini AI and saving suggestions work without internet or API cost. Cloud AI is optional for complex wording.") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Optional Cloud AI", Modifier.weight(1f))
                Switch(aiEnabled, onCheckedChange = { if (it) aiConsent = true else vm.setAiEnabled(false) })
            }
        }

        AppVerificationCard(context)

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Biometric / device lock", Modifier.weight(1f))
                Switch(biometric, onCheckedChange = onBiometric)
            }
        }

        SectionCard("Export", "Choose a statement period. Dates use YYYY-MM-DD.") {
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
                )
                OutlinedTextField(
                    exportThrough,
                    { exportThrough = it },
                    label = { Text("To date (optional)") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Leave either date empty to include records before or after the entered date.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { export(false) }) { Text("Export CSV") }
                OutlinedButton(enabled = !busy, onClick = { export(true) }) { Text("Export PDF") }
            }
        }

        SectionCard("App info", "Version ${com.sadique.dailyledger.BuildConfig.VERSION_NAME} (code ${com.sadique.dailyledger.BuildConfig.VERSION_CODE})") {
            Text("Optimized for quick local data loading, backup safety and household money tracking.", style = MaterialTheme.typography.bodySmall)
            Text("Data stays on this phone unless you choose a cloud backup or export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Local data reset", "Permanent reset only on this phone. Cloud backup can still be restored later.") {
            OutlinedButton(onClick = { resetStage = 1 }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reset local records") }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(8.dp))
        Text(user.name, style = MaterialTheme.typography.titleMedium)
        Text(user.email, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
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
