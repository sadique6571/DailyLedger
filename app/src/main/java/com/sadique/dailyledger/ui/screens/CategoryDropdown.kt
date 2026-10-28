package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    inlineResults: Boolean = com.sadique.dailyledger.ui.LocalEntrySheet.current,
) {
    val strings = com.sadique.dailyledger.i18n.LocalStrings.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val density=LocalDensity.current
    val view=LocalView.current
    val margin=with(density){8.dp.roundToPx()}
    val shared = LocalCategoryOptions.current?.get(type)
    val allHistory = LocalCategoryHistory.current
    val options = shared ?: remember(context, allHistory, strings, type, CategoryCatalog.overlayVersion) {
        CategoryOptions.build(CategoryCatalog.load(context), allHistory, strings::text).getValue(type)
    }
    val categories = options.indexed.map { it.first }
    val frequent = options.frequent
    var expanded by remember { mutableStateOf(false) }
    val inlineView = remember { BringIntoViewRequester() }
    var inlineHeight by remember { mutableIntStateOf(0) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(inlineResults, expanded, keyboardVisible) {
        if (inlineResults && expanded) {
            // Relocate once per open/keyboard transition, never for each IME animation frame.
            withFrameNanos { }
            val firstRows = with(density) { 220.dp.toPx() }
            inlineView.bringIntoView(Rect(0f, 0f, 1f,
                if (inlineHeight > 0) minOf(firstRows, inlineHeight.toFloat()) else firstRows))
        }
    }

    // Dismissing the suggestions is not cancelling an edit. Keep the search draft
    // through focus changes, keyboard/back dismissal and lazy-list recreation.
    // Only an explicit selection/clear or a change of transaction type replaces it.
    var query by rememberSaveable(type) { mutableStateOf("") }
    var editing by rememberSaveable(type) { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(type, editing, value) {
        // A parent form may recreate its initial selection after state restore.
        // An unfinished search must not silently become that old selection.
        if (editing && value.isNotBlank()) currentOnSelect("")
    }
    fun choose(label: String) { onSelect(label); editing = false; query = ""; expanded = false; focus.clearFocus() }
    val filtered = remember(options, query) {
        options.search(query)
    }
    val results: @Composable (Modifier) -> Unit = { resultsModifier ->
            // Bound the inner lazy viewport; all matches remain reachable without composing the catalog at once.
            val recent = if (query.isBlank()) frequent.mapNotNull { label -> filtered.find { it.label == label } } else emptyList()
            val recentLabels = recent.map { it.label }.toSet()
            val sections = linkedMapOf<String, List<LedgerCategory>>()
            if (recent.isNotEmpty()) sections["Frequently used"] = recent
            sections.putAll(filtered.filterNot { it.label in recentLabels }.groupBy { it.group })
            LazyColumn(resultsModifier.testTag("category-results"),contentPadding=PaddingValues(vertical=8.dp)) {
                sections.forEach { (group, rows) ->
                    if (!inlineResults) item(key = "heading-$group") {
                        Text(group, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(rows, key = { "category-${it.type}-${it.label}" }) { category ->
                        DropdownMenuItem(text = { Text(category.label) },
                            leadingIcon = { CategoryLeadingIcon(category.label, type) },
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
    ExposedDropdownMenuBox(expanded = expanded && enabled,
        // Editing/re-focusing the search field always reveals the current results.
        onExpandedChange = { if (enabled) expanded = true },
        modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(inlineView)
            .onSizeChanged { inlineHeight = it.height }) {
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
                    // Give the arrow its own expansion callback. Its secondary anchor
                    // consumes the tap before the surrounding editable anchor handles it.
                    ExposedDropdownMenuBox(expanded = expanded && enabled,
                        onExpandedChange = { if (enabled) expanded = it }) {
                        IconButton(enabled = enabled, onClick = {},
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable, enabled)
                                .testTag("category-toggle").semantics {
                                    contentDescription = strings.text("Categories")
                                }) { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .testTag("category-dropdown"),
        )
        if (expanded && enabled && inlineResults) {
            BackHandler { expanded = false }
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                results(Modifier.fillMaxWidth().heightIn(max = 132.dp))
            }
        }
        }
        if(expanded && enabled && !inlineResults) {
          BackHandler { expanded = false }
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
          Popup(popupPositionProvider=position,onDismissRequest={expanded=false},properties=PopupProperties(focusable=false)) {
           androidx.compose.material3.Surface(modifier = Modifier.testTag("category-floating-popup"), shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,shadowElevation=3.dp) {
            results(Modifier.exposedDropdownSize(true).heightIn(max = 300.dp))
           }
          }
        }
    }
}
