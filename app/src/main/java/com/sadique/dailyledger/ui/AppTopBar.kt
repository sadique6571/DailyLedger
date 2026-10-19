package com.sadique.dailyledger.ui

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    tab: Int,
    subtitle: String,
    showNavigateUp: Boolean,
    navigateUpLabel: String,
    navigateUpTestTag: String,
    onNavigateUp: () -> Unit,
    onSearch: () -> Unit,
    onReminders: () -> Unit,
    onAiEntry: () -> Unit,
) {
    LedgerBackdrop {
        LedgerBrandHeader(
            subtitle = subtitle, modifier = Modifier.statusBarsPadding(),
        ) {
            if (showNavigateUp) IconButton(onClick = onNavigateUp, modifier = Modifier.testTag(navigateUpTestTag)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, navigateUpLabel)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search transactions", Modifier.size(23.dp)) }
            IconButton(onClick = onReminders) {
                Icon(Icons.Rounded.NotificationsNone, "Reminders", Modifier.size(23.dp))
            }
            TextButton(onClick = onAiEntry, modifier = Modifier.testTag("global-ai"), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.AutoAwesome, "AI Auto Fill", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(3.dp))
                Text("AI Entry", fontSize = 11.sp)
            }
        }
    }
}
