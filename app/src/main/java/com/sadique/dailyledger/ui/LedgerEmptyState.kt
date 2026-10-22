package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlin.math.roundToInt

@Composable
fun LedgerEmptyState(title: String, description: String, actionLabel: String, icon: ImageVector,
    modifier: Modifier = Modifier, onAction: () -> Unit) {
    LedgerCard(modifier.fillMaxWidth().testTag("ledger-empty-state"), contentSpacing = LedgerTokens.Space8) {
        val floatAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(-5f) }
        if (!androidx.compose.ui.platform.LocalInspectionMode.current) androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                floatAnim.animateTo(5f, androidx.compose.animation.core.tween(1500))
                floatAnim.animateTo(-5f, androidx.compose.animation.core.tween(1500))
            }
        }
        GlowIcon(icon, MaterialTheme.colorScheme.primary, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, floatAnim.value.roundToInt()) }, size = 40.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAction, shape = LedgerTokens.ShapeSmall, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
    }
}
