package com.sadique.dailyledger.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for Daily Ledger's visual language.
 *
 * Corners follow a 16 / 20 / 28 scale: 16dp for inputs and chips, 20dp for
 * cards, 28dp for hero surfaces and sheets. Spacing follows an 8-point scale.
 * Nothing here changes behavior; it only names the values screens already use.
 */
object LedgerTokens {
    // Corner radii
    val CornerSmall: Dp = 16.dp
    val CornerCard: Dp = 20.dp
    val CornerLarge: Dp = 28.dp

    // Spacing scale
    val Space2: Dp = 2.dp
    val Space4: Dp = 4.dp
    val Space6: Dp = 6.dp
    val Space8: Dp = 8.dp
    val Space10: Dp = 10.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space20: Dp = 20.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp

    // Elevation levels
    val Elevation1: Dp = 2.dp
    val Elevation2: Dp = 4.dp
    val Elevation3: Dp = 8.dp

    // Entrance animation
    const val EntranceDurationMs: Int = 220
    const val EntranceStaggerMs: Int = 40
    val EntranceDistance: Dp = 48.dp

    // Shape shortcuts
    val ShapeSmall = RoundedCornerShape(CornerSmall)
    val ShapeCard = RoundedCornerShape(CornerCard)
    val ShapeLarge = RoundedCornerShape(CornerLarge)
}

/**
 * Legacy token names, kept so existing call sites compile unchanged.
 * New code should use [LedgerTokens] directly.
 */
object LedgerDesignTokens {
    val CardCorner = LedgerTokens.CornerCard
    val InnerCorner = LedgerTokens.CornerCard
    val RowGap = LedgerTokens.Space10
    val InnerPadding = LedgerTokens.Space12
}
