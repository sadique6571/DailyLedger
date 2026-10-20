package com.sadique.dailyledger.ui

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact app chrome; all existing navigation callbacks keep their destinations. */
@Composable
fun AppTopBar(
    tab: Int, subtitle: String, showNavigateUp: Boolean,
    navigateUpLabel: String, navigateUpTestTag: String,
    onNavigateUp: () -> Unit, onSearch: () -> Unit,
    onReminders: () -> Unit, onAiEntry: () -> Unit,
) {
    LedgerBackdrop {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("compact-app-header"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showNavigateUp) IconButton(onClick = onNavigateUp, modifier = Modifier.testTag(navigateUpTestTag)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, navigateUpLabel)
            }
            LedgerLogo(30.dp)
            Column(Modifier.weight(1f).padding(start = 3.dp)) {
                Text("Daily Ledger", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tab != 0) Text(subtitle, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSearch, modifier = Modifier.testTag("global-search")) {
                Icon(Icons.Rounded.Search, "Search transactions", Modifier.size(23.dp))
            }
            FilledTonalIconButton(onClick = onAiEntry,
                modifier = Modifier.testTag("global-ai"), shape = RoundedCornerShape(15.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Rounded.AutoAwesome, "AI Auto Fill", Modifier.size(23.dp))
            }
        }
    }
}
