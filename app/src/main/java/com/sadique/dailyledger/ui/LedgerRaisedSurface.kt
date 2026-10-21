package com.sadique.dailyledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
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
    modifier: Modifier = Modifier, shape: Shape = LedgerTokens.ShapeCard,
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 2.dp, border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val circular = shape == CircleShape
    val edge = MaterialTheme.colorScheme.primary.copy(alpha = if (ledgerIsDark()) .24f else .15f)
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = if (circular) CircleShape else LedgerTokens.ShapeCard,
        color = color.compositeOver(MaterialTheme.colorScheme.surface), contentColor = contentColor, tonalElevation = 0.dp,
        shadowElevation = if (circular) 0.dp else 2.dp,
        border = if (circular) border else BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (ledgerIsDark()) .12f else .8f), edge, edge))), content = content,
    )
}

/** A varied palette, selected once by stable screen/list identity rather than Random. */
private val cardAccents = listOf(LedgerColors.Green, LedgerColors.Blue, LedgerColors.Purple,
    LedgerColors.Amber, LedgerColors.Teal, LedgerColors.Red)

fun ledgerCardAccent(key: String): Color = cardAccents[(key.hashCode() and Int.MAX_VALUE) % cardAccents.size]

@Composable
fun ledgerCardAccent(): Color = cardAccents[(currentCompositeKeyHash and Int.MAX_VALUE) % cardAccents.size]

/** The Home-style shell for rectangular content cards throughout the app. */
@Composable
fun LedgerCardSurface(modifier: Modifier = Modifier, accent: Color = ledgerCardAccent(), content: @Composable () -> Unit) {
    LedgerSurface(modifier = modifier, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.background(ledgerCardBrush(accent)), propagateMinConstraints = true) { content() }
    }
}

@Composable
fun LedgerRaisedCard(
    modifier: Modifier = Modifier, shape: Shape = LedgerTokens.ShapeCard,
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true,
    accent: Color = ledgerCardAccent(),
    content: @Composable ColumnScope.() -> Unit,
) {
    LedgerCardSurface(if (onClick == null) modifier else modifier.clickable(enabled = enabled, onClick = onClick), accent) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
