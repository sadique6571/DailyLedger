package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.categoryVisual

/** Shared leading icon for category rows: themed glow icon from the category visual. */
@Composable
fun CategoryLeadingIcon(label: String, type: String, size: Dp = 30.dp) {
    val visual = categoryVisual(label, type)
    GlowIcon(visual.icon, visual.color, size = size)
}

/** Category row for the dropdown menu (shared with the dialog picker styling). */
@Composable
fun CategoryMenuRow(label: String, type: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { CategoryLeadingIcon(label, type) },
        onClick = onClick,
    )
}

/** Category row for the full-screen/dialog picker list. */
@Composable
fun CategoryPickerRow(label: String, type: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CategoryLeadingIcon(label, type, size = 34.dp)
        Text(label, Modifier.padding(start = 11.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
