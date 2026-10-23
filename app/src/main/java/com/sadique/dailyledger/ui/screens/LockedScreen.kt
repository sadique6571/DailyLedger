package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.security.PinLock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockedScreen(
    unlock: () -> Unit,
    pinHash: String = "",
    showBiometric: Boolean = true,
    onPinUnlock: () -> Unit = {},
) {
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    val shakeScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (showBiometric) unlock() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp))
            Text("Daily Ledger is locked", style = MaterialTheme.typography.headlineSmall)
            if (pinHash.isNotBlank()) {
                var pin by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pin = it; error = false } },
                    label = { Text("App PIN") },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(shake.value.roundToInt(), 0) },
                )
                if (error) Text("Incorrect PIN — try again", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        if (PinLock.matches(pin, pinHash)) onPinUnlock()
                        else {
                            error = true; pin = ""
                            shakeScope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(0f, androidx.compose.animation.core.keyframes {
                                    durationMillis = 400
                                    0f at 0; -12f at 60; 12f at 130; -8f at 200; 8f at 270; -4f at 330; 0f at 400
                                })
                            }
                        }
                    },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unlock with PIN") }
            }
            if (showBiometric) Button(onClick = unlock) { Text(if (pinHash.isNotBlank()) "Use biometrics" else "Unlock") }
        }
    }
}
