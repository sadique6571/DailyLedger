package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.sadique.dailyledger.ui.LocalDisplayPreferences
import com.sadique.dailyledger.ui.rememberLedgerHaptics
import com.sadique.dailyledger.ui.plainAmount
import com.sadique.dailyledger.data.TransactionEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    items: List<TransactionEntity>,
    onSave: (String, Long, String, String, String, TransactionEntity?) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onAutoFill: (() -> Unit)? = null,
    initialQuery: String = "",
    initialFilter: String = "ALL",
    onDetailedEntry: ((TransactionEntity) -> Unit)? = null,
    onSaveAsync: (suspend (String, Long, String, String, String, TransactionEntity?) -> Unit)? = null,
) {
    var show by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var undoPendingId by remember { mutableStateOf<String?>(null) }
    val undoBar = remember { SnackbarHostState() }
    val haptics = rememberLedgerHaptics()
    val undoScope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var duplicateWarning by remember { mutableStateOf(false) }
    var duplicateDecision by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null) }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    val listState = rememberLazyListState()
    val display = LocalDisplayPreferences.current!!
    val dateContext = LocalContext.current
    val favoritesStore = remember(dateContext) { dateContext.applicationContext.getSharedPreferences("transaction_favorites_v2", android.content.Context.MODE_PRIVATE) }
    var favoriteIds by remember { mutableStateOf(favoritesStore.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    fun toggleFavorite(id: String) {
        val key = id.toString()
        favoriteIds = if (key in favoriteIds) favoriteIds - key else favoriteIds + key
        favoritesStore.edit().putStringSet("ids", favoriteIds).apply()
    }
    var dateRange by rememberSaveable { mutableStateOf("ALL") }
    var selectedDay by rememberSaveable { mutableStateOf("") }
    var calendarMode by rememberSaveable { mutableStateOf(false) }
    var sortOrder by rememberSaveable { mutableStateOf("NEWEST") }
    var expandedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var compactRows by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickTemplate by remember { mutableStateOf<TransactionEntity?>(null) }
    val frequentCategories = remember(items) { items.groupingBy { it.type to it.category }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key } }
    val favorites = items.filter { it.id.toString() in favoriteIds }.take(8)
    val today = LocalDate.now()
    val searchStrings = com.sadique.dailyledger.i18n.LocalStrings.current
    val filtered = remember(items, undoPendingId, favoritesOnly, favoriteIds, filter, dateRange, selectedDay, query, searchStrings, today) { items.filter {
        it.id != undoPendingId &&
        (!favoritesOnly || it.id in favoriteIds) &&
        (filter == "ALL" || it.type == filter) &&
            (query.isBlank() || it.category.contains(query, true) || searchStrings.text(it.category).contains(query, true) || it.note.contains(query, true) || it.date.contains(query, true) || plainAmount(it.amountMinor).contains(query)) &&
            when (dateRange) {
                "TODAY" -> it.date == today.toString()
                "WEEK" -> runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today) } == true
                "MONTH" -> it.date.startsWith(today.toString().take(7))
                "DAY" -> it.date == selectedDay
                else -> true
            }
    } }

    // Same filters as the list, minus the date range — the calendar needs the whole month (Round 2, #2).
    val calendarItems = remember(items, undoPendingId, favoritesOnly, favoriteIds, filter, query, searchStrings) { items.filter {
        it.id != undoPendingId &&
        (!favoritesOnly || it.id in favoriteIds) &&
        (filter == "ALL" || it.type == filter) &&
            (query.isBlank() || it.category.contains(query, true) || searchStrings.text(it.category).contains(query, true) || it.note.contains(query, true) || it.date.contains(query, true) || plainAmount(it.amountMinor).contains(query))
    } }

    val sorted = remember(filtered, sortOrder) { when (sortOrder) {
        "OLDEST" -> filtered.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
        "AMOUNT_HIGH" -> filtered.sortedByDescending { it.amountMinor }
        "AMOUNT_LOW" -> filtered.sortedBy { it.amountMinor }
        else -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
    } }

    // Entry celebration (A + D): coin drop + tick when a brand-new transaction lands.
    val entryIds = items.map { it.id }
    var entrySeenInit by remember { mutableStateOf(false) }
    var entrySeenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var entryCelebrate by remember { mutableStateOf(false) }
    LaunchedEffect(entryIds) {
        if (!entrySeenInit) { entrySeenInit = true; entrySeenIds = entryIds.toSet() }
        else {
            val fresh = items.filter { it.id !in entrySeenIds }
            entrySeenIds = entryIds.toSet()
            if (fresh.any { !it.category.equals("Salary", true) }) entryCelebrate = true
        }
    }
    LaunchedEffect(entryCelebrate) { if (entryCelebrate) { kotlinx.coroutines.delay(1150); entryCelebrate = false } }

    Scaffold(
        snackbarHost = { SnackbarHost(undoBar) { data -> com.sadique.dailyledger.ui.UndoCountdownSnackbar(data) } },
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = null; quickTemplate = null; show = true }) { Icon(Icons.Default.Add, "Add transaction") }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = if (display.compact) 10.dp else 16.dp, end = if (display.compact) 10.dp else 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            enteringItem {
                TransactionFilterHeader(
                    query = query,
                    onQueryChange = { query = it },
                    dateRange = dateRange,
                    onDateRangeChange = { dateRange = it },
                    selectedDay = selectedDay,
                    onSelectedDayChange = { selectedDay = it },
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    favoritesOnly = favoritesOnly,
                    onToggleFavoritesOnly = { favoritesOnly = !favoritesOnly },
                    compactRows = compactRows,
                    onToggleCompactRows = { compactRows = !compactRows },
                    onClearAll = { query = ""; filter = "ALL"; dateRange = "ALL"; selectedDay = ""; sortOrder = "NEWEST"; favoritesOnly = false },
                    onAutoFill = onAutoFill,
                    favorites = favorites,
                    frequentCategories = frequentCategories,
                    onQuickFavorite = { favorite -> edit = null; quickTemplate = favorite; show = true },
                    onQuickCategory = { type, category -> edit = null; quickTemplate = items.firstOrNull { it.type == type && it.category == category }; show = true },
                    dateContext = dateContext,
                    today = today,
                )
            }
            enteringItem {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !calendarMode, onClick = { calendarMode = false }, label = { Text("List") })
                    FilterChip(selected = calendarMode, onClick = { calendarMode = true }, label = { Text("Calendar") })
                }
            }
            if (calendarMode) {
                enteringItem {
                    MonthCalendarView(calendarItems) { day ->
                        selectedDay = day.toString(); dateRange = "DAY"; calendarMode = false
                    }
                }
            }
            if (!calendarMode && filtered.isEmpty()) {
                item { com.sadique.dailyledger.ui.LedgerEmptyState(
                    if (items.isEmpty()) "Start your ledger" else "No matching transactions",
                    if (items.isEmpty()) "No transactions yet. Tap + to add your first entry." else "No matching transactions. Try another search or filter.",
                    if (items.isEmpty()) "Add transaction" else "Clear filters", Icons.Default.Add,
                    onAction = {
                        if (items.isEmpty()) { edit = null; quickTemplate = null; show = true }
                        else { query = ""; filter = "ALL"; dateRange = "ALL"; selectedDay = ""; favoritesOnly = false }
                    },
                ) }
            }
            if (!calendarMode) sorted.forEachIndexed { index, x ->
                if (sortOrder in setOf("NEWEST", "OLDEST") && (index == 0 || sorted[index - 1].date != x.date)) {
                    stickyHeader(key = "transaction-date-${x.date}") {
                        Text(
                            when (x.date) {
                                today.toString() -> "Today"
                                today.minusDays(1).toString() -> "Yesterday"
                                else -> x.date
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                }
                enteringItem(key = x.id) {
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> { pendingDelete = x; false }
                                SwipeToDismissBoxValue.StartToEnd -> { quickTemplate = null; edit = x; show = true; false }
                                SwipeToDismissBoxValue.Settled -> false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundContent = {
                            val direction = swipeState.dismissDirection
                            Box(
                                Modifier.fillMaxSize().background(
                                    when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                ).padding(horizontal = 16.dp),
                                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                Icon(
                                    if (direction == SwipeToDismissBoxValue.EndToStart) Icons.Rounded.Delete else Icons.Rounded.Edit,
                                    null,
                                    Modifier.graphicsLayer { val s = 0.6f + 0.5f * swipeState.progress.coerceIn(0f, 1f); scaleX = s; scaleY = s },
                                    tint = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        },
                    ) {
                    TransactionRow(
                        x = x,
                        compact = compactRows,
                        isFavorite = x.id.toString() in favoriteIds,
                        noteExpanded = expandedNoteId == x.id,
                        privateAmounts = display.privateAmounts,
                        onToggleFavorite = { toggleFavorite(x.id) },
                        onDelete = { pendingDelete = x },
                        onEdit = { quickTemplate = null; edit = x; show = true },
                        onShowDetails = { showDetails = x },
                        onToggleNote = { expandedNoteId = if (expandedNoteId == x.id) null else x.id },
                    )
                    }
                }
            }
        }
        if (entryCelebrate) com.sadique.dailyledger.ui.EntrySavedCelebration(Modifier.align(Alignment.Center).size(128.dp))
        }
    }
    showDetails?.let { detail ->
        TransactionDetailsDialog(
            detail = detail,
            privateAmounts = display.privateAmounts,
            onDismiss = { showDetails = null },
            onCopy = { },
        )
    }
    pendingDelete?.let { target ->
        DeleteTransactionDialog(
            target = target,
            privateAmounts = display.privateAmounts,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                haptics.confirm()
                pendingDelete = null
                undoPendingId = target.id
                undoScope.launch {
                    val result = undoBar.showSnackbar("Transaction removed", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
                    undoPendingId = null
                    if (result != SnackbarResult.ActionPerformed) onDelete(target)
                }
            },
        )
    }
    if (duplicateWarning) DuplicateWarningDialog(
        onDismiss = { duplicateWarning = false; pendingSave = null; duplicateDecision?.complete(false) },
        onConfirm = { duplicateDecision?.complete(true); val saveAction = pendingSave; duplicateWarning = false; pendingSave = null; saveAction?.invoke() },
    )
    if (show) {
        key(edit?.id, quickTemplate?.id) {
        TransactionDialog(edit, { show = false }, save = { type, amount, cat, note, date ->
            val action = { onSave(type, amount, cat, note, date, edit); show = false; Unit }
            val duplicate = items.any { it.id != edit?.id && it.type == type && it.amountMinor == amount && it.category.equals(cat, true) && it.date == date }
            if (duplicate) { pendingSave = action; duplicateWarning = true } else action()
        }, saveAsync = if (onSaveAsync == null) null else { type, amount, cat, note, date ->
            val duplicate = items.any { it.id != edit?.id && it.type == type && it.amountMinor == amount && it.category.equals(cat, true) && it.date == date }
            val approved = if (duplicate) {
                val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
                duplicateDecision = decision
                duplicateWarning = true
                try { decision.await() } finally { duplicateDecision = null; duplicateWarning = false }
            } else true
            if (approved) { onSaveAsync(type, amount, cat, note, date, edit); show = false }
        }, onAdvanced = onDetailedEntry?.let { action -> { draft:TransactionEntity -> show=false;edit=null;action(draft) } }, initialType = quickTemplate?.type ?: "EXPENSE", initialCategory = quickTemplate?.category ?: "", initialAmount = quickTemplate?.let { plainAmount(it.amountMinor) } ?: "")
        }
    }
}
