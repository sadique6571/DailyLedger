package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Pinned, equally sized secondary/primary actions in all entry sheets. */
@Composable
internal fun LedgerFormActions(
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
        Row(modifier.fillMaxWidth().testTag("entry-form-actions"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space12)) {
            dismissButton?.let { action ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp), propagateMinConstraints = true) { action() }
            }
            Box(Modifier.weight(1f).heightIn(min = 48.dp), propagateMinConstraints = true) { confirmButton() }
        }
    }
}
