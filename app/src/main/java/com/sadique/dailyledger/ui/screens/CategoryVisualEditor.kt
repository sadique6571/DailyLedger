package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.CategoryCatalog
import com.sadique.dailyledger.data.LedgerCategory
import com.sadique.dailyledger.ui.CategoryOverrideColors
import com.sadique.dailyledger.ui.CategoryOverrideIcons
import com.sadique.dailyledger.ui.CategoryVisual
import com.sadique.dailyledger.ui.CategoryVisualOverrides
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.categoryVisual

/** Dialog to pick a category and give it a custom icon and color (Settings → Category icons & colors). */
@Composable
fun CategoryVisualEditorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember {
        CategoryCatalog.load(context)
            .distinctBy { it.label.lowercase(java.util.Locale.ROOT) }
            .sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
    }
    var selected by remember { mutableStateOf<LedgerCategory?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category icons & colors") },
        text = {
            val cat = selected
            if (cat == null) {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(catalog, key = { it.type + ":" + it.label }) { item ->
                        CategoryPickerRow(item.label, item.type, onClick = { selected = item })
                    }
                }
            } else {
                CategoryVisualEditorPane(cat, onBack = { selected = null })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CategoryVisualEditorPane(cat: LedgerCategory, onBack: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(cat.label) { CategoryVisualOverrides.get(context, cat.label) }
    val base = categoryVisual(cat.label, cat.type)
    var iconName by remember(cat.label) { mutableStateOf(existing?.first ?: "") }
    var color by remember(cat.label) { mutableStateOf(existing?.second ?: base.color) }
    val preview = CategoryVisual(CategoryOverrideIcons[iconName] ?: base.icon, color)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(preview.icon, preview.color, size = 40.dp)
            Column {
                Text(cat.label, translate = false, style = MaterialTheme.typography.titleMedium)
                Text(if (existing != null) "Custom icon & color" else "Default icon & color", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        CategoryOverrideIcons.keys.chunked(6).forEach { rowNames ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowNames.forEach { name ->
                    Surface(
                        shape = CircleShape,
                        color = if (iconName == name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp).clickable { iconName = name },
                    ) {
                        Icon(CategoryOverrideIcons.getValue(name), name, Modifier.padding(9.dp), tint = if (iconName == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Color", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryOverrideColors.forEach { option ->
                Box(
                    Modifier.size(32.dp).background(option, CircleShape)
                        .border(if (color == option) 3.dp else 1.dp, if (color == option) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { color = option },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (existing != null) TextButton(onClick = { CategoryVisualOverrides.clear(context, cat.label); onBack() }) { Text("Reset") }
            Button(
                onClick = { CategoryVisualOverrides.set(context, cat.label, iconName, color); onBack() },
                enabled = iconName.isNotBlank(),
            ) { Text("Save") }
        }
    }
}
