package com.sadique.dailyledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Theme-aware, softly raised money cards that fit in the two-column dashboard layout. */
@Composable
fun SummaryCard(
    title: String,
    amount: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = ledgerIsDark()
    val accent = when {
        title.contains("kameti", true) -> scheme.tertiary
        title.contains("saving", true) -> scheme.primary
        title.contains("owe", true) -> scheme.error
        title.contains("udhar", true) -> scheme.secondary
        else -> scheme.secondary
    }
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier.fillMaxWidth().clip(shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = shape,
        border = BorderStroke(1.dp, accent.copy(alpha = if (dark) .42f else .28f)),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(
                    Brush.verticalGradient(listOf(
                        accent.copy(alpha = if (dark) .16f else .11f),
                        scheme.surface,
                    )),
                    shape = shape,
                )
                .padding(horizontal = if (compact) 10.dp else 12.dp,
                    vertical = if (compact) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            GlowIcon(icon, accent, size = if (compact) 27.dp else 32.dp)
            Text(title, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            val hidden = LocalDisplayPreferences.current?.privateAmounts == true
            Text(if (hidden) "Rs •••••" else money(amount), fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, fontSize = 10.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant)
            }
        }
    }
}
