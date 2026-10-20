package com.sadique.dailyledger.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Shared input treatment: theme-tinted surfaces, rounded corners and visible focus/errors. */
@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val palette = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, readOnly = readOnly, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText,
        isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = LedgerTokens.ShapeSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = lerp(palette.surface, palette.primaryContainer, .75f),
            unfocusedContainerColor = lerp(palette.surface, palette.primaryContainer, .45f),
            disabledContainerColor = palette.surfaceContainerLow,
            errorContainerColor = lerp(palette.surface, palette.errorContainer, .5f),
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = palette.primary.copy(alpha = .55f),
            focusedLabelColor = palette.primary,
            unfocusedLabelColor = palette.primary,
            focusedLeadingIconColor = palette.primary,
            unfocusedLeadingIconColor = palette.primary,
            focusedTrailingIconColor = palette.primary,
            unfocusedTrailingIconColor = palette.primary,
            focusedPlaceholderColor = palette.onSurfaceVariant,
            unfocusedPlaceholderColor = palette.onSurfaceVariant,
            focusedSupportingTextColor = palette.onSurfaceVariant,
            unfocusedSupportingTextColor = palette.onSurfaceVariant,
            focusedTextColor = palette.onSurface,
            unfocusedTextColor = palette.onSurface,
            errorTextColor = palette.onSurface,
        ),
    )
}
