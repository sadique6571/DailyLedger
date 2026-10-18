package com.sadique.dailyledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared raised edge, soft shadow and highlight for all rectangular cards and nested boxes. */
@Composable
fun LedgerSurface(
    modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 4.dp, border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val circular = shape == CircleShape
    val edge = MaterialTheme.colorScheme.primary.copy(alpha = if (ledgerIsDark()) .28f else .18f)
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = if (circular) CircleShape else RoundedCornerShape(LedgerDesignTokens.CardCorner),
        color = color.compositeOver(MaterialTheme.colorScheme.surface), contentColor = contentColor, tonalElevation = 0.dp,
        shadowElevation = if (circular) 0.dp else 4.dp,
        border = if (circular) border else BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (ledgerIsDark()) .14f else .85f), edge, edge))), content = content,
    )
}

@Composable
fun LedgerRaisedCard(
    modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LedgerSurface(if (onClick == null) modifier else modifier.clickable(enabled = enabled, onClick = onClick), color = colors.containerColor) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}
