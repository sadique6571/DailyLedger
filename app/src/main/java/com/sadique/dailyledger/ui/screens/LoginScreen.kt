package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    configured: Boolean,
    googleConfigured: Boolean,
    busy: Boolean,
    error: String?,
    message: String?,
    onGoogle: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onClearMessage: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("LOGIN") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are never persisted to saved-instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }

    fun changeMode(next: String) {
        mode = next; validation = null; password = ""; confirmation = ""; onClearMessage()
    }
    fun submit() {
        validation = when {
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            mode == "SIGNUP" && name.trim().isEmpty() -> "Enter your name."
            mode == "SIGNUP" && password.length < 8 -> "Use at least 8 characters for your password."
            mode == "SIGNUP" && password != confirmation -> "The passwords do not match."
            mode == "LOGIN" && password.isEmpty() -> "Enter your password."
            else -> null
        }
        if (validation != null) return
        when (mode) {
            "SIGNUP" -> onSignUp(name.trim(), email.trim(), password)
            "RESET" -> onResetPassword(email.trim())
            else -> onSignIn(email.trim(), password)
        }
    }

    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Daily Ledger", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Expenses, loans, kameti and savings in one place.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text(when (mode) { "SIGNUP" -> "Create your account"; "RESET" -> "Reset your password"; else -> "Welcome back" },
                style = MaterialTheme.typography.titleLarge)
            if (mode != "RESET") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = mode == "LOGIN", onClick = { changeMode("LOGIN") },
                        enabled = !busy, label = { Text("Sign in") })
                    FilterChip(selected = mode == "SIGNUP", onClick = { changeMode("SIGNUP") },
                        enabled = !busy, label = { Text("Sign up") })
                }
                OutlinedButton(onClick = onGoogle, enabled = googleConfigured && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google", Modifier.padding(6.dp))
                }
                Text("Your first Google sign-in creates your account.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f)); Text("or use email"); HorizontalDivider(Modifier.weight(1f))
                }
            }
            if (mode == "SIGNUP") {
                OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Your name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(email, { email = it }, enabled = !busy, label = { Text("Email address") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth())
            if (mode != "RESET") {
                OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (visible) "Hide password" else "Show password")
                        }
                    })
            }
            if (mode == "SIGNUP") {
                OutlinedTextField(confirmation, { confirmation = it }, enabled = !busy,
                    label = { Text("Confirm password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Use at least 8 characters.") })
            }
            (validation ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!configured) Text("Online sign-in is not available in this build. The app owner needs to complete account setup.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { submit() }, enabled = configured && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Please wait…")
                } else Text(when (mode) { "SIGNUP" -> "Sign up"; "RESET" -> "Send reset email"; else -> "Sign in" },
                    Modifier.padding(6.dp))
            }
            TextButton(onClick = { changeMode(if (mode == "RESET") "LOGIN" else "RESET") }, enabled = !busy) {
                Text(if (mode == "RESET") "Back to sign in" else "Forgot password?")
            }
            HorizontalDivider()
            Text("Your records stay on this phone and can be backed up to your account. Sign in with the same account to recover them on another phone.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
