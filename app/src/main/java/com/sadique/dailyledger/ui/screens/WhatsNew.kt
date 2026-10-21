package com.sadique.dailyledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text

/** "What's New" changelog shown from Settings → App info (Round 2, #13). Static content per release. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Latest polish", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                listOf(
                    "Accent color: pick your own main color in Settings → Appearance",
                    "Due badges on the Udhar, Kameti and More tabs",
                    "Swipe on Loans and Kameti lists (swipe left to remove, with Undo)",
                    "Smoother screen changes and animated dashboard totals",
                    "This What's New screen in Settings → App info",
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                Text("The big UI update", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                listOf(
                    "Theme picker with 8 themes — including AMOLED black and Wallpaper (Material You)",
                    "Text size setting and a branded splash screen with intro slides",
                    "App-lock PIN (Settings → Security), on top of fingerprint",
                    "Swipe actions and Undo on deletes, sticky date headers in Transactions",
                    "Calculator in the amount field — type 1200+350",
                    "6-month spending trend chart on the Insights tab",
                    "Category icons & colors you can customize",
                    "Savings goal with progress, shareable udhar statements and monthly summary",
                    "CSV transaction import and a backup-status card on Home",
                    "App-icon shortcuts: long-press the icon for quick entry",
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Great") } },
    )
}
