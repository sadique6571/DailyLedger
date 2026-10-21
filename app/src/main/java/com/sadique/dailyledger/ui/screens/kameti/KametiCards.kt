@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
internal fun MemberTurnCard(member: CommitteeMemberEntity, onMarkReceived: () -> Unit, onDelete: () -> Unit) {
    Card(accent = com.sadique.dailyledger.ui.ledgerCardAccent(member.id), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${member.turnNumber} • ${member.turnMonth} • ${member.name} • ${if (member.shareHalfUnits == 1) "0.5" else "1"} share", translate = false, style = MaterialTheme.typography.titleSmall)
                    Text(monthCountdown(member.turnMonth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (member.isMe) AssistChip(onClick = {}, enabled = false, label = { Text("Me") })
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete member") }
            }
            if (member.phone.isNotBlank() || member.whatsapp.isNotBlank()) {
                ContactActionRow(
                    phone = member.phone,
                    whatsapp = member.whatsapp.ifBlank { member.phone },
                    message = "Assalam o Alaikum ${member.name}, aapki kameti turn ${member.turnMonth} (#${member.turnNumber}) record hai. Daily Ledger reminder.",
                )
            }
            if (member.received) {
                Text("Received${member.receivedDate?.let { " • $it" } ?: ""}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            } else {
                OutlinedButton(onClick = onMarkReceived) { Text(if (member.isMe) "Receive my turn" else "Mark turn received") }
            }
            if (member.note.isNotBlank()) Text(member.note, translate = false, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ContactActionRow(phone: String, whatsapp: String, message: String, callLabel: String = "Call") {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (phone.isNotBlank()) {
            AssistChip(
                onClick = { context.openContactAction(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
                label = { Text(callLabel) },
                leadingIcon = { Icon(Icons.Outlined.Call, null) },
            )
            AssistChip(
                onClick = { context.openContactAction(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", message)) },
                label = { Text("SMS") },
                leadingIcon = { Icon(Icons.Outlined.Sms, null) },
            )
        }
        val wa = kametiWhatsappNumber(whatsapp)
        if (wa.isNotBlank()) {
            AssistChip(
                onClick = {
                    val msg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
                    context.openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=$msg")))
                },
                label = { Text("WhatsApp") },
                leadingIcon = { Icon(Icons.Outlined.Chat, null) },
            )
        }
    }
}

@Composable
internal fun BalanceRow(label: String, amount: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(visibleMoney(amount), style = MaterialTheme.typography.titleSmall)
    }
}
