package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.appcheck.FirebaseAppCheck
import com.sadique.dailyledger.BuildConfig
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.auth.awaitResult
import com.sadique.dailyledger.ui.LedgerCard
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/** Tests attestation only. No ledger text, model prompts or tokens are displayed/sent here. */
@Composable
internal fun AppVerificationCard(context: Context) {
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LedgerCard(Modifier.fillMaxWidth()) {
        Text("Online AI connection", style = MaterialTheme.typography.titleMedium)
        Text("Check whether Firebase accepts this app installation. No ledger entries are sent.")
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                    if (!BuildConfig.DEBUG && availability != ConnectionResult.SUCCESS) {
                        status = "Google Play services need attention. Update/enable them in Android settings, then retry."
                    } else {
                        FirebaseRuntime.initAppCheck(context)
                        FirebaseAppCheck.getInstance().getAppCheckToken(true).awaitResult()
                        status = "App verification passed. You can now try Online AI. Model access and usage limits are checked separately."
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    status = if (BuildConfig.DEBUG) "This private test installation needs its own debug token registered by the owner. [AI_APP_CHECK]"
                    else "App verification failed. Check internet/Google Play services. The owner must verify the certificate and outside-Play settings in Firebase App Check. [AI_APP_CHECK]"
                } finally { busy = false }
            }
        }) { Text(if (busy) "Checking…" else "Check app verification") }
        if (status.isNotBlank()) Text(status)
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide app details" else "App details for support") }
        if (details) {
            val certificate = remember { signingFingerprint(context) }
            Text("Daily Ledger ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${BuildConfig.APPLICATION_ID}\nVerification: ${if (BuildConfig.DEBUG) "private debug" else "Play Integrity"}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Signing SHA-256\n$certificate", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Suppress("DEPRECATION")
private fun signingFingerprint(context: Context): String = runCatching {
    val signatures = if (Build.VERSION.SDK_INT >= 28) context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
    else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    MessageDigest.getInstance("SHA-256").digest(requireNotNull(signatures).first().toByteArray())
        .joinToString(":") { "%02X".format(it.toInt() and 0xff) }
}.getOrDefault("Unavailable")
