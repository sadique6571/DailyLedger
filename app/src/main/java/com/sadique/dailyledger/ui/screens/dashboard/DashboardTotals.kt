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
        Row(Modifier.fillMaxWidth().clip(LedgerTokens.ShapeCard).background(Brush.verticalGradient(listOf(color.copy(alpha = if (dark) .14f else .1f), MaterialTheme.colorScheme.surface)), LedgerTokens.ShapeCard).padding(LedgerTokens.Space10),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space8)) {
            GlowIcon(icon, color, size = 30.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (hidden) "Rs •••••" else displayMoney(amount), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
                Text(detail, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
