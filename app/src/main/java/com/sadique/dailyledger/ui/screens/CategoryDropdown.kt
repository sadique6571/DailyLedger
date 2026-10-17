package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.sadique.dailyledger.ai.OfflineVocabulary
import com.sadique.dailyledger.data.CategoryCatalog
import com.sadique.dailyledger.data.LedgerCategory
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.categoryVisual

// Supplied from the signed-in owner's observable ledger. No separate cache/account mixing.
val LocalCategoryHistory = staticCompositionLocalOf { emptyList<TransactionEntity>() }

@Composable
fun CategoryDropdown(
    type: String,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowCustom: Boolean = true,
) {
    val strings = com.sadique.dailyledger.i18n.LocalStrings.current
    val context = LocalContext.current
    val history = LocalCategoryHistory.current.filter { it.type == type }
    val catalog = remember(context) { CategoryCatalog.load(context) }
    val categories = (catalog.filter { it.type == type } + history.map {
        LedgerCategory("Your categories", it.category, type, emptyList())
    }).filter { it.label.isNotBlank() }.distinctBy { it.label.lowercase() }
    val frequent = history.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val words = OfflineVocabulary.normalize(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val filtered = categories.filter { category ->
        val text = OfflineVocabulary.normalize((listOf(category.label, category.group, strings.text(category.label), strings.text(category.group)) + category.aliases).joinToString(" "))
        words.all { it in text }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { query = ""; expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("category-dropdown"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(value.ifBlank { "Choose category" }, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ArrowDropDown, "Open category search")
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth).heightIn(max = 380.dp),
            properties = PopupProperties(focusable = true),
        ) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                label = { Text("Search categories") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).testTag("category-search"),
            )
            val sections = linkedMapOf<String, List<LedgerCategory>>()
            if (query.isBlank()) {
                val recent = frequent.mapNotNull { label -> filtered.find { it.label == label } }
                if (recent.isNotEmpty()) sections["Frequently used"] = recent
            }
            sections.putAll(filtered.groupBy { it.group })
            sections.forEach { (group, rows) ->
                Text(group, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                rows.forEach { category ->
                    val visual = categoryVisual(category.label, type)
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        leadingIcon = { GlowIcon(visual.icon, visual.color, size = 30.dp) },
                        onClick = { onSelect(category.label); expanded = false },
                    )
                }
            }
            if (filtered.isEmpty()) Text("No matching category", Modifier.padding(16.dp))
            if (allowCustom && query.isNotBlank() && categories.none { it.label.equals(query.trim(), true) }) {
                DropdownMenuItem(text = { Text("Add custom: ${query.trim().take(60)}") },
                    onClick = { onSelect(query.trim().take(60)); expanded = false })
            }
        }
    }
}
