package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.ledgerCardBrush
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import com.sadique.dailyledger.ui.displayMoney
import com.sadique.dailyledger.ui.ledgerIsDark

/** Small tappable stat tile used in the dashboard monthly-totals grid. */
@Composable
internal fun DashboardStat(title: String, amount: Long, detail: String, icon: ImageVector, color: Color, modifier: Modifier, action: () -> Unit) {
    val dark = ledgerIsDark()
    val hidden = LocalDisplayPreferences.current!!.privateAmounts
    Surface(modifier.clickable(onClick = action), shape = LedgerTokens.ShapeCard, color = MaterialTheme.colorScheme.surface,
        shadowElevation = LedgerTokens.Elevation1, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (dark) .08f else 1f))) {
        Column(Modifier.fillMaxWidth().clip(LedgerTokens.ShapeCard).background(ledgerCardBrush(color), LedgerTokens.ShapeCard).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space6)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space8)) {
                GlowIcon(icon, color, size = 26.dp)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hidden) {
                Text("Rs •••••", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                com.sadique.dailyledger.ui.AnimatedMoneyText(amount, { com.sadique.dailyledger.ui.displayMoney(it) },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
