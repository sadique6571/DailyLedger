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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val focus = LocalFocusManager.current
    val shared = LocalCategoryOptions.current?.get(type)
    val allHistory = LocalCategoryHistory.current
    val options = shared ?: remember(context, allHistory, strings, type) {
        CategoryOptions.build(CategoryCatalog.load(context), allHistory, strings::text).getValue(type)
    }
    val categories = options.indexed.map { it.first }
    val frequent = options.frequent
    var expanded by remember { mutableStateOf(false) }
    var query by remember(type, value) { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    fun choose(label: String) { onSelect(label); editing = false; query = ""; expanded = false; focus.clearFocus() }
    val filtered = remember(options, query) {
        val words = OfflineVocabulary.normalize(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        options.indexed.filter { (_, text) -> words.all { it in text } }.map { it.first }
    }
    val visible = filtered.take(80)
    ExposedDropdownMenuBox(expanded = expanded && enabled,
        onExpandedChange = { if (enabled) { expanded = it; if (it) editing = true } },
        modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (editing) query else strings.text(value),
            onValueChange = { query = it.take(80); editing = true; expanded = true },
            enabled = enabled, singleLine = true,
            label = { Text("Category") }, placeholder = { Text("Search categories") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .onFocusChanged { if (!it.isFocused && !expanded) editing = false }
                .testTag("category-dropdown"),
        )
        ExposedDropdownMenu(expanded = expanded && enabled,
            onDismissRequest = { expanded = false; editing = false },
            modifier = Modifier.heightIn(max = 320.dp)) {
            val sections = linkedMapOf<String, List<LedgerCategory>>()
            if (query.isBlank()) {
                val recent = frequent.mapNotNull { label -> filtered.find { it.label == label } }
                if (recent.isNotEmpty()) sections["Frequently used"] = recent
            }
            sections.putAll(visible.groupBy { it.group })
            sections.forEach { (group, rows) ->
                Text(group, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                rows.forEach { category ->
                    val visual = categoryVisual(category.label, type)
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        leadingIcon = { GlowIcon(visual.icon, visual.color, size = 30.dp) },
                        onClick = { choose(category.label) },
                    )
                }
            }
            if (filtered.size > visible.size) Text("${visible.size} of ${filtered.size} matches. Type more to narrow the list.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            if (filtered.isEmpty()) Text("No matching category", Modifier.padding(16.dp))
            if (allowCustom && query.isNotBlank() && categories.none { it.label.equals(query.trim(), true) }) {
                DropdownMenuItem(text = { Text("Add custom: ${query.trim().take(60)}") },
                    onClick = { choose(query.trim().take(60)) })
            }
        }
    }
}
