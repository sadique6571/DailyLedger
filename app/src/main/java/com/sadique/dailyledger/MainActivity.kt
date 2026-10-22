package com.sadique.dailyledger

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadique.dailyledger.auth.AccountManager
import com.sadique.dailyledger.auth.friendlyAuthError
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.security.Biometrics
import com.sadique.dailyledger.ui.DailyLedgerApp
import com.sadique.dailyledger.ui.DailyLedgerTheme
import com.sadique.dailyledger.ui.CardEntrance
import com.sadique.dailyledger.ui.LedgerLogo
import com.sadique.dailyledger.ui.screens.LockedScreen
import com.sadique.dailyledger.ui.screens.LoginScreen
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : FragmentActivity() {
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val settings = remember { SettingsStore(applicationContext) }
            val account = remember { AccountManager(this) }
            var sessionReady by remember { mutableStateOf(false) }
            var storageError by remember { mutableStateOf(false) }
            var retry by remember { mutableStateOf(0) }
            LaunchedEffect(retry) {
                sessionReady = false; storageError = false
                try {
                    withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).openHelper.writableDatabase }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { storageError = true; return@LaunchedEffect }
                try { account.restoreSession() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { settings.clearUser() }
                finally { sessionReady = true }
            }
            // null until DataStore has produced its first snapshot -> avoids flashing the login/unlocked UI.
            val loaded by settings.all.collectAsStateWithLifecycle(null)
            val current = loaded
            LaunchedEffect(current?.user?.id, current?.biometric) { com.sadique.dailyledger.widget.LedgerWidget.refresh(applicationContext) }
            var unlocked by remember { mutableStateOf(false) }
            var unlockParty by remember { mutableStateOf(false) }
            LaunchedEffect(current != null) {
                if (current != null) unlocked = !current.biometric && current.pinHash.isBlank()
            }

            val displayPrefs = remember { com.sadique.dailyledger.ui.DisplayPreferences(applicationContext) }
            val privatePresent by androidx.compose.runtime.produceState(false, current?.user?.id, sessionReady) {
                val owner=current?.user?.id
                if(owner!=null && sessionReady) {
                    try { AppDatabase.get(applicationContext).ledgerDao().observeTransactions(owner).collect { rows -> value=rows.any { it.privateEntry } } }
                    catch(e: CancellationException){throw e}
                    catch(_:Exception){value=true}
                } else value=false
            }
            val sensitiveEditors by com.sadique.dailyledger.security.SensitiveEditors.active.collectAsStateWithLifecycle()
            DisposableEffect(displayPrefs.secureScreens,privatePresent,sensitiveEditors) {
                if (displayPrefs.secureScreens || privatePresent || sensitiveEditors>0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }
            androidx.compose.runtime.CompositionLocalProvider(com.sadique.dailyledger.ui.LocalDisplayPreferences provides displayPrefs) {
            DailyLedgerTheme(current?.theme ?: "AQUA") {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        storageError -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                            Text("Your local database could not be opened safely. Your files have not been erased. Do not clear app data unless you have a verified backup.")
                            Button(onClick = { retry++ }) { Text("Retry") }
                        }
                        current == null || !sessionReady -> LoadingScreen()
                        current.user == null -> AuthGate()
                        unlockParty && !unlocked -> UnlockSuccess { unlocked = true; unlockParty = false }
                        (current.biometric || current.pinHash.isNotBlank()) && !unlocked -> Locked(current.pinHash, current.biometric) { unlockParty = true }
                        !displayPrefs.onboardingSeen -> com.sadique.dailyledger.ui.screens.OnboardingScreen(onDone = { displayPrefs.updateOnboardingSeen(true) })
                        else -> DailyLedgerApp(
                            user = current.user,
                            settings = settings,
                            theme = current.theme,
                            biometric = current.biometric,
                            pinHash = current.pinHash,
                            driveEnabled = current.driveEnabled,
                            lastSync = current.lastSync,
                            driveStatus = current.driveStatus,
                            weatherEnabled = current.weatherEnabled,
                            weatherCity = current.weatherCity,
                            weatherTemperature = current.weatherTemperature,
                            weatherCondition = current.weatherCondition,
                            weatherUpdatedAt = current.weatherUpdatedAt,
                            transactionSounds = current.transactionSounds,
                            onLogout = {},
                        )
                    }
                }
            }
            }
        }
    }

    @Composable
    private fun LoadingScreen() {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            CardEntrance {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Coin flip (L): the logo spins like a tossed coin while things load.
                    val flip = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(Unit) { flip.animateTo(720f, androidx.compose.animation.core.tween(950)) }
                    Box(Modifier.graphicsLayer { rotationY = flip.value; cameraDistance = 12f * density }) { LedgerLogo(64.dp) }
                    Text("Opening your ledger…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth(.55f))
                    // Skeleton shimmer (B): placeholder cards while data loads.
                    Column(Modifier.fillMaxWidth(.8f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.sadique.dailyledger.ui.ShimmerBox(Modifier.fillMaxWidth().height(16.dp))
                        com.sadique.dailyledger.ui.ShimmerBox(Modifier.fillMaxWidth(.85f).height(16.dp))
                        com.sadique.dailyledger.ui.ShimmerBox(Modifier.fillMaxWidth(.6f).height(16.dp))
                    }
                }
            }
        }
    }

    /** Brief open-lock celebration between a successful unlock and the app (T). */
    @Composable
    private fun UnlockSuccess(onDone: () -> Unit) {
        val pop = remember { androidx.compose.animation.core.Animatable(0.55f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, androidx.compose.animation.core.tween(320))
            kotlinx.coroutines.delay(480)
            onDone()
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.CheckCircle, null,
                    Modifier.size(72.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }

    @Composable
    private fun AuthGate() {
        val scope = rememberCoroutineScope()
        val account = remember { AccountManager(this) }
        var error by remember { mutableStateOf<String?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        fun submit(action: suspend () -> Unit) {
            if (busy) return
            busy = true; error = null; message = null
            scope.launch {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = friendlyAuthError(e) }
                finally { busy = false }
            }
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            LoginScreen(
                configured = account.configured,
                googleConfigured = account.googleConfigured,
                busy = busy, error = error, message = message,
                onGoogle = { submit { account.google() } },
                onSignIn = { email, password -> submit { account.signIn(email, password) } },
                onSignUp = { name, email, password -> submit { account.signUp(name, email, password) } },
                onResetPassword = { email -> submit {
                    account.resetPassword(email)
                    message = "If this email has an account, a reset link will be sent. Check your inbox and spam folder."
                } },
                onClearMessage = { error = null; message = null },
            )
        }
    }

    @Composable
    private fun Locked(pinHash: String, biometricEnabled: Boolean, onUnlock: () -> Unit) {
        val authenticators = remember { Biometrics.pick(this) }
        val prompt = remember {
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlock()
                    }
                },
            )
        }
        val start: () -> Unit = {
            if (authenticators == null) {
                // No screen lock / biometrics enrolled on this device: never trap the user out of their data,
                // unless a PIN is set — then the PIN field below is the way in.
                if (pinHash.isBlank()) onUnlock()
            } else {
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Daily Ledger")
                    .setSubtitle("Use biometrics or your device credential")
                    .setAllowedAuthenticators(authenticators)
                    .build()
                prompt.authenticate(info)
            }
        }
        LaunchedEffect(Unit) { start() }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            LockedScreen(unlock = start, pinHash = pinHash, showBiometric = biometricEnabled, onPinUnlock = onUnlock)
        }
    }
}
