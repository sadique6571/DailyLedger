package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Entry sheets keep category suggestions in the form rather than a floating popup. */
val LocalEntrySheet = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEntrySheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    scrollBody: Boolean = true,
) {
    // The callback identity must not change while typing: it is a sheet-state key.
    // Route every close through the form's existing busy/validation guard.
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val latestKeyboardVisible = rememberUpdatedState(keyboardVisible)
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    val dismissSafely = {
        if (latestKeyboardVisible.value) keyboard?.hide() else latestDismiss.value()
    }
    val confirmChange: (SheetValue) -> Boolean = remember {
        { target ->
            if (target == SheetValue.Hidden) {
                if (latestKeyboardVisible.value) keyboard?.hide() else latestDismiss.value()
                false
            } else true
        }
    }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true, confirmValueChange = confirmChange,
    )
    ModalBottomSheet(
        onDismissRequest = dismissSafely,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = properties.dismissOnBackPress && !keyboardVisible),
    ) {
        CompositionLocalProvider(LocalEntrySheet provides true) {
            BackHandler(enabled = keyboardVisible) { keyboard?.hide() }
            Column(modifier.fillMaxWidth().fillMaxHeight(.82f).imePadding()
                .testTag("ledger-entry-sheet")) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                val body = Modifier.weight(1f).fillMaxWidth()
                Box((if (scrollBody) body.verticalScroll(rememberScrollState()) else body)
                    .padding(horizontal = 20.dp).testTag("ledger-entry-body")) { text() }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    dismissButton?.invoke()
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { confirmButton() }
                }
            }
        }
    }
}
