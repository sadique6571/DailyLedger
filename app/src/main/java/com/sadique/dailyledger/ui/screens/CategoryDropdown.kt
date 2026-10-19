package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
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
    val density=LocalDensity.current
    val view=LocalView.current
    val margin=with(density){8.dp.roundToPx()}
    val shared = LocalCategoryOptions.current?.get(type)
    val allHistory = LocalCategoryHistory.current
    val options = shared ?: remember(context, allHistory, strings, type) {
        CategoryOptions.build(CategoryCatalog.load(context), allHistory, strings::text).getValue(type)
    }
    val categories = options.indexed.map { it.first }
    val frequent = options.frequent
    var expanded by remember { mutableStateOf(false) }

    var query by remember(type) { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    fun choose(label: String) { onSelect(label); editing = false; query = ""; expanded = false; focus.clearFocus() }
    val filtered = remember(options, query) {
        val words = OfflineVocabulary.normalize(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        options.indexed.filter { (_, text) -> words.all { it in text } }.map { it.first }
    }
    ExposedDropdownMenuBox(expanded = expanded && enabled,
        onExpandedChange = { if (enabled) { expanded = it } },
        modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (editing) query else strings.text(value),
            onValueChange = {
                query = it.take(80); editing = true; expanded = true
                if(value.isNotBlank()) onSelect("")
            },
            enabled = enabled, singleLine = true,
            label = { Text("Category") }, placeholder = { Text("Search categories") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                Row {
                    if(value.isNotBlank() || query.isNotBlank()) IconButton(enabled=enabled,onClick={
                        query="";editing=true;expanded=true;onSelect("")
                    },modifier=Modifier.testTag("category-clear")) { Icon(Icons.Rounded.Close,"Clear category") }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .onFocusChanged { if (!it.isFocused && !expanded) editing = false }
                .testTag("category-dropdown"),
        )
        if(expanded && enabled) {
          BackHandler { expanded=false;editing=false }
          val position=remember(view,margin) { object:PopupPositionProvider {
              override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
                  val frame=android.graphics.Rect();view.getWindowVisibleDisplayFrame(frame)
                  val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
                  val offsetY=screen[1]-window[1]
                  val top=(frame.top-offsetY+margin).coerceAtLeast(margin)
                  val bottom=minOf(windowSize.height-margin,frame.bottom-offsetY-margin).coerceAtLeast(top)
                  val below=anchorBounds.bottom
                  val above=anchorBounds.top-popupContentSize.height
                  val y=if(below+popupContentSize.height<=bottom)below else if(above>=top)above else (bottom-popupContentSize.height).coerceAtLeast(top)
                  val x=if(layoutDirection==LayoutDirection.Rtl)anchorBounds.right-popupContentSize.width else anchorBounds.left
                  return IntOffset(x.coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),y)
              }
          } }
          Popup(popupPositionProvider=position,onDismissRequest={expanded=false;editing=false},properties=PopupProperties(focusable=false)) {
           androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,shadowElevation=3.dp) {
            // Bound the inner lazy viewport; all matches remain reachable without composing the catalog at once.
            val recent = if (query.isBlank()) frequent.mapNotNull { label -> filtered.find { it.label == label } } else emptyList()
            val recentLabels = recent.map { it.label }.toSet()
            val sections = linkedMapOf<String, List<LedgerCategory>>()
            if (recent.isNotEmpty()) sections["Frequently used"] = recent
            sections.putAll(filtered.filterNot { it.label in recentLabels }.groupBy { it.group })
            LazyColumn(Modifier.exposedDropdownSize(true).heightIn(max=300.dp).testTag("category-results"),contentPadding=PaddingValues(vertical=8.dp)) {
                sections.forEach { (group, rows) ->
                    item(key = "heading-$group") {
                        Text(group, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(rows, key = { "category-${it.type}-${it.label}" }) { category ->
                        val visual = categoryVisual(category.label, type)
                        DropdownMenuItem(text = { Text(category.label) },
                            leadingIcon = { GlowIcon(visual.icon, visual.color, size = 30.dp) },
                            onClick = { choose(category.label) })
                    }
                }
                if (filtered.isEmpty()) item { Text("No matching category", Modifier.padding(16.dp)) }
                if (allowCustom && query.isNotBlank() && categories.none { it.label.equals(query.trim(), true) }) {
                    item { DropdownMenuItem(text = { Text("Add custom: ${query.trim().take(60)}") },
                        onClick = { choose(query.trim().take(60)) }) }
                }
            }
           }
          }
        }
    }
}
