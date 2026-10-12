package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.*

@Composable
fun AccountBackupCard(enabled: Boolean, knownBackup: Boolean, lastCheck: String, status: String, busy: Boolean,
    onToggle: (Boolean) -> Unit, onRestore: () -> Unit, onBackup: () -> Unit, onDetails: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(Icons.Rounded.CloudUpload, Color(0xFF468FFF), size = 49.dp)
            Column(Modifier.weight(1f)) {
                Text("Google Drive Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Restore with the same account", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(enabled, onCheckedChange = onToggle, enabled = !busy, colors = SwitchDefaults.colors(checkedTrackColor = LedgerColors.Green, checkedThumbColor = Color.White))
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = if (ledgerIsDark()) Color(0xFF153449) else Color(0xFFEDF9FF)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlowIcon(if (knownBackup) Icons.Rounded.CheckCircle else Icons.Rounded.CloudSync,
                        if (knownBackup) LedgerColors.Green else LedgerColors.Blue, size = 42.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (knownBackup) "Google Drive backup available" else if (enabled) "Awaiting first Drive backup" else "Automatic backup is off",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(if (knownBackup) "Last backup: $lastCheck" else if (enabled) "Approve Drive access with Back up now. Manual restore is also available." else "Automatic backup is off. Manual backup and restore are still available.",
                            fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Cloud, null, Modifier.size(40.dp), tint = Color(0xFF91CBFF))
                        Icon(Icons.Rounded.VerifiedUser, null, Modifier.align(Alignment.BottomEnd).size(29.dp), tint = if (knownBackup) LedgerColors.Green else LedgerColors.Teal)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GradientButton(
                        text = "Restore data",
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        icon = Icons.Rounded.Restore,
                    )
                    GradientButton(
                        text = "Back up now",
                        onClick = onBackup,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        icon = Icons.Rounded.CloudUpload,
                    )
                }
                TextButton(onClick = onDetails, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                    Text("View details", fontSize = 12.sp)
                }
            }
        }
        if (status.isNotBlank()) Text(status, Modifier.padding(top = 10.dp), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PersonalizationCard(sounds: Boolean, weather: Boolean, city: String, onSounds: (Boolean) -> Unit,
    onWeather: (Boolean) -> Unit, onLocation: () -> Unit, onPreviewSound: () -> Unit) {
    Column {
        Text("Personalization", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("Make Daily Ledger feel like home", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        LedgerCard(Modifier.fillMaxWidth()) {
            PersonalizationRow(Icons.Rounded.MusicNote, LedgerColors.Green, "Transaction sounds", "Play a sound after each save") {
                Switch(sounds, onCheckedChange = onSounds, colors = SwitchDefaults.colors(checkedTrackColor = LedgerColors.Green, checkedThumbColor = Color.White))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            PersonalizationRow(Icons.Rounded.GraphicEq, LedgerColors.Blue, "Sound style", "Tap to preview Retro Soft", onClick = onPreviewSound) {
                Text("Retro Soft", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            PersonalizationRow(Icons.Rounded.LightMode, LedgerColors.Amber, "Weather on Home", "Show your selected city's weather") {
                Switch(weather, onCheckedChange = onWeather, colors = SwitchDefaults.colors(checkedTrackColor = LedgerColors.Green, checkedThumbColor = Color.White))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            PersonalizationRow(Icons.Rounded.Place, LedgerColors.Blue, "Location", city.ifBlank { "Choose a city · no GPS needed" }, onClick = onLocation) {
                Text("Change", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PersonalizationRow(icon: ImageVector, color: Color, title: String, subtitle: String,
    onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlowIcon(icon, color, size = 39.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SavingsSettingsCard(total: Long, onOpen: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your savings", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("View savings", fontSize = 12.sp); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp)) }
        }
        LedgerCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowIcon(Icons.Rounded.AccountBalanceWallet, LedgerColors.Green, size = 45.dp)
                Column(Modifier.weight(1f)) {
                    Text("A brighter tomorrow", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Separate from your monthly salary", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(displayMoney(total), Modifier.padding(top = 12.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Total recorded savings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
