package com.sadique.dailyledger.ui

import com.sadique.dailyledger.ui.LedgerRaisedCard as Card

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    val isKameti = title.contains("kameti", true)
    // Material theme's tertiary is almost white in several light themes.
    // Give Kameti a real accent, contrasting border and tinted fill so it reads as a card.
    val accent = when {
        isKameti -> lerp(LedgerColors.Purple, scheme.primary, .22f)
        title.contains("saving", true) -> scheme.primary
        title.contains("owe", true) -> scheme.error
        title.contains("udhar", true) -> scheme.secondary
        else -> scheme.secondary
    }
    val shape = LedgerTokens.ShapeCard
    val cardFill = scheme.surface
    val horizontal = if (compact) LedgerTokens.Space10 else LedgerTokens.Space12
    val vertical = if (compact) LedgerTokens.Space10 else LedgerTokens.Space12
    Card(
        modifier = modifier.fillMaxWidth()
            .then(if (isKameti) Modifier.testTag("kameti-paid-card") else Modifier)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = shape,
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = if (isKameti) .3f else if (dark) .38f else .24f),
        ),
        colors = CardDefaults.cardColors(containerColor = cardFill),
        elevation = CardDefaults.cardElevation(defaultElevation = LedgerTokens.Elevation2),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(
                    ledgerCardBrush(accent),
                    shape = shape,
                )
                .padding(horizontal = horizontal, vertical = vertical),
            verticalArrangement = Arrangement.spacedBy(if (compact) LedgerTokens.Space4 else LedgerTokens.Space6),
        ) {
            GlowIcon(icon, accent, size = if (compact) 27.dp else 32.dp)
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            val hidden = LocalDisplayPreferences.current?.privateAmounts == true
            AnimatedMoneyText(amount, { if (hidden) "Rs •••••" else money(it) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant)
            }
        }
    }
}
