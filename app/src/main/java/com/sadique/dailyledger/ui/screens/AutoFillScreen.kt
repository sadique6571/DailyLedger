package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ai.*
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun AutoFillScreen(enabled: Boolean, onEnable: () -> Unit, onBack: () -> Unit,
                   generate: suspend (String) -> AiDraftResult, save: suspend (List<TransactionDraft>, String) -> Unit,
                   onSaved: () -> Unit = {},
                   onLearnCategory: suspend (String, String, String) -> String? = { _, _, _ -> null }) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<TransactionDraft>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualCategory by remember { mutableStateOf("") }
    var allCategories by remember { mutableStateOf(false) }
    var reviewIssues by remember { mutableStateOf(emptyList<List<String>>()) }
    var checked by remember { mutableStateOf(emptySet<String>()) }
    var confirmSave by remember { mutableStateOf(false) }
    fun learn(type: String, note: String, category: String) {
        busy = true
        scope.launch {
            try { message = onLearnCategory(type, note, category) ?: "Category correction learned and queued for backup." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not save AI learning. Please retry." }
            finally { busy = false }
        }
    }
    fun warnings(index: Int, draft: TransactionDraft) = reviewIssues.getOrNull(index) ?: DraftReviewPolicy.issues(input, draft)


    LaunchedEffect(entries.isNotEmpty()) { if (entries.isNotEmpty()) listState.animateScrollToItem(2) }
    BackHandler { if (!busy) onBack() }

    fun prepare() {
        if (busy || input.isBlank() || entries.isNotEmpty()) return
        focus.clearFocus(); busy = true; message = ""
        scope.launch {
            try {
                DraftReviewPolicy.checkMissingDetails(input)
                val result = generate(input)
                entries = result.entries; reviewIssues = result.reviewIssues.ifEmpty { result.entries.map { DraftReviewPolicy.issues(input, it) } }; checked = emptySet(); message = result.message; batch = UUID.randomUUID().toString()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = when (e) {
                    is AiException, is IllegalArgumentException, is IllegalStateException -> e.message ?: "Could not prepare entries."
                    else -> "Could not prepare entries. Nothing was saved."
                }
            } finally { busy = false }
        }
    }

    LedgerBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Daily Ledger", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Add transaction", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(12.dp).size(24.dp), tint = LedgerColors.Purple)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().imePadding().testTag("autofill-list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                enteringItem(key = "mode") {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                        Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GradientButton("AI Quick Add", {}, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome)
                            TextButton(onClick = { manualCategory = ""; manual = true }, enabled = !busy && entries.size < AiProtocol.MAX_DRAFTS, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                                Icon(Icons.Rounded.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Manual Entry", fontSize = 12.sp)
                            }
                        }
                    }
                }
                enteringItem(key = "input") {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = input, onValueChange = { input = it.take(AiProtocol.MAX_INPUT) },
                            modifier = Modifier.fillMaxWidth().testTag("autofill-input"),
                            placeholder = { Text("Daal 500, petrol 2000, salary 35000", fontSize = 13.sp) },
                            label = { Text("Expenses or income", fontSize = 12.sp) },
                            minLines = 2, maxLines = 5,
                            enabled = !busy && entries.isEmpty(),
                            supportingText = { Text("${input.length}/${AiProtocol.MAX_INPUT} • Roman Urdu, Urdu or English", fontSize = 10.sp) })
                        Spacer(Modifier.height(7.dp))
                        GradientButton(if (busy) "Preparing…" else "Prepare entries", ::prepare,
                            Modifier.fillMaxWidth().testTag("autofill-generate"), !busy && input.isNotBlank() && entries.isEmpty(), Icons.AutoMirrored.Rounded.Send)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlowIcon(Icons.Rounded.SmartToy, LedgerColors.Purple, size = 32.dp)
                            Text("Kal = yesterday; parson = 2 days ago. Mini AI learns your category corrections and repeated saved items with your account backup. Review every date and amount before saving.", fontSize = 11.sp,
                                lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                        if (message.isNotBlank()) Text(message, Modifier.padding(top = 10.dp).testTag("autofill-message"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (entries.isNotEmpty()) enteringItem(key = "review-heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Review ${entries.size} entries", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Not saved yet", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(entries, key = { i, _ -> "draft-$i" }) { i, entry ->
                    val visual = categoryVisual(entry.category, entry.type)
                    val issues = warnings(i, entry)
                    val reviewed = DraftReviewPolicy.signature(entry) in checked
                    LedgerCard(Modifier.fillMaxWidth().then(if (issues.isNotEmpty() && !reviewed)
                        Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp)) else Modifier)) {
                        if (issues.isNotEmpty()) {
                            Surface(color = if (reviewed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("draft-warning-$i")) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(if (reviewed) "Reviewed by you" else "Review needed", fontWeight = FontWeight.SemiBold)
                                    issues.forEach { Text(it, fontSize = 11.sp) }
                                    if (!reviewed) TextButton(enabled = !busy, onClick = { checked = checked + DraftReviewPolicy.signature(entry) },
                                        modifier = Modifier.testTag("draft-checked-$i")) { Text("I checked this entry") }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            GlowIcon(visual.icon, visual.color, size = 43.dp)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f).clickable(enabled = !busy) { editing = i }.testTag("draft-edit-$i")) {
                                        Text(if (entry.note.isBlank()) com.sadique.dailyledger.i18n.LocalStrings.current.text(entry.category) else entry.note, translate = false, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(displayMoney(entry.amountMinor), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { entries = entries.filterIndexed { j, _ -> j != i }; reviewIssues = reviewIssues.filterIndexed { j, _ -> j != i } }, enabled = !busy, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.DeleteOutline, "Remove entry ${i + 1}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { editing = i }, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
                                        Text(entry.date, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { editing = i }, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                        Icon(Icons.Rounded.Edit, null, Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)); Text("Edit", fontSize = 10.sp)
                                    }
                                }
                                CategoryDropdown(entry.type, entry.category, onSelect = { category ->
                                    if (category != entry.category) {
                                        learn(entry.type, entry.note, category)
                                    }
                                    entries = entries.mapIndexed { j, old -> if (j == i) old.copy(category = category) else old }
                                }, enabled = !busy)
                                DraftChip(if (entry.type == "INCOME") "Income" else "Expense", if (entry.type == "INCOME") Icons.Rounded.AddCircle else Icons.Rounded.RemoveCircle,
                                    if (entry.type == "INCOME") LedgerColors.Green else LedgerColors.Red, Modifier, !busy) { editing = i }

                            }
                        }
                    }
                }
                enteringItem(key = "manual-draft") {
                    LedgerCard(Modifier.fillMaxWidth().clickable(enabled = !busy && entries.size < AiProtocol.MAX_DRAFTS) { manualCategory = ""; manual = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowIcon(Icons.Rounded.AddCircle, LedgerColors.Teal, size = 35.dp)
                            Column {
                                Text("Add another transaction", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(if (entries.size < AiProtocol.MAX_DRAFTS) "Create a draft manually" else "Review this batch of ${AiProtocol.MAX_DRAFTS} first", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (entries.isNotEmpty()) enteringItem(key = "save-drafts") {
                    Text("Expenses total: ${displayMoney(DraftReviewPolicy.total(entries, "EXPENSE"))}", fontWeight = FontWeight.SemiBold)
                    Text("Income total: ${displayMoney(DraftReviewPolicy.total(entries, "INCOME"))}", fontWeight = FontWeight.SemiBold)
                    GradientButton("Review & Save (${entries.size})", onClick = { confirmSave = true },
                        modifier = Modifier.fillMaxWidth().testTag("autofill-save"), enabled = !busy)
                    TextButton(onClick = { entries = emptyList(); message = "Drafts discarded."; batch = UUID.randomUUID().toString() }, enabled = !busy) { Text("Discard drafts") }
                }
                enteringItem(key = "categories") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Popular categories", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { allCategories = true }, enabled = !busy && entries.size < AiProtocol.MAX_DRAFTS) { Text("View all", fontSize = 11.sp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Food", "Transport", "Shopping", "Bills", "Health").forEach { category ->
                            val visual = categoryVisual(category)
                            Column(Modifier.clickable(enabled = !busy && entries.size < AiProtocol.MAX_DRAFTS) { manualCategory = category; manual = true }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                GlowIcon(visual.icon, visual.color, size = 43.dp)
                                Text(category, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
                enteringItem(key = "offline-details") {
                    Text("Offline Mini AI is always available", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Income and expenses only. Savings, loans and kameti use their own spaces.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!enabled) TextButton(onClick = onEnable) { Text("Enable optional Cloud AI", fontSize = 11.sp) }
                }
            }
        }
    }
    editing?.let { i -> entries.getOrNull(i)?.let { draft ->
        TransactionDialog(TransactionEntity("draft", "", draft.type, draft.amountMinor, draft.category, draft.note, draft.date, 0, 0),
            dismiss = { editing = null }, save = { type, amount, category, note, date ->
                if (category != draft.category) learn(type, note, category)
                val updated = TransactionDraft(type, amount, category, note, date)
                checked = checked + DraftReviewPolicy.signature(updated)
                entries = entries.mapIndexed { j, old -> if (i == j) TransactionDraft(type, amount, category, note, date) else old }; editing = null
            })
    } }
    if (manual) TransactionDialog(null, dismiss = { manual = false }, initialCategory = manualCategory, save = { type, amount, category, note, date ->
        if (entries.size < AiProtocol.MAX_DRAFTS) {
            val draft = TransactionDraft(type, amount, category, note, date)
            entries = entries + draft; reviewIssues = reviewIssues + listOf(emptyList())
            checked = checked + DraftReviewPolicy.signature(draft)
        }
        manual = false
    })
    if (confirmSave) {
        val unchecked = entries.indices.any { warnings(it, entries[it]).isNotEmpty() && DraftReviewPolicy.signature(entries[it]) !in checked }
        AlertDialog(onDismissRequest = { if (!busy) confirmSave = false }, title = { Text("Confirm reviewed entries") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEachIndexed { i, entry ->
                    Text("${i + 1}. ${entry.date} • ${entry.category}", fontWeight = FontWeight.SemiBold)
                    Text("${if (entry.type == "INCOME") "Income" else "Expense"}: ${displayMoney(entry.amountMinor)}")
                    if (entry.note.isNotBlank()) Text(entry.note, translate = false, fontSize = 11.sp)
                    HorizontalDivider()
                }
                Text("Expenses total: ${displayMoney(DraftReviewPolicy.total(entries, "EXPENSE"))}")
                Text("Income total: ${displayMoney(DraftReviewPolicy.total(entries, "INCOME"))}")
                if (unchecked) Text("Check the highlighted entries before saving.", color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { Button(enabled = !busy && !unchecked && entries.isNotEmpty(), modifier = Modifier.testTag("autofill-confirm"), onClick = {
                busy = true
                val snapshot = entries.toList()
                scope.launch {
                    try {
                        save(snapshot, batch)
                        entries = emptyList(); reviewIssues = emptyList(); checked = emptySet(); input = ""
                        confirmSave = false; message = "${snapshot.size} entries saved to your ledger."
                        batch = UUID.randomUUID().toString(); onSaved()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.message ?: "Could not save entries. Your drafts are still here."; confirmSave = false }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Saving…" else "Confirm & Save") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmSave = false }) { Text("Back to review") } },
        )
    }
    if (allCategories) CategoryPicker("EXPENSE", onSelect = { manualCategory = it; manual = true; allCategories = false }, onDismiss = { allCategories = false })
}

@Composable
private fun DraftChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, enabled: Boolean, action: () -> Unit) {
    Surface(modifier.clickable(enabled = enabled, onClick = action), shape = RoundedCornerShape(20.dp), color = color.copy(alpha = .09f)) {
        Row(Modifier.heightIn(min = 38.dp).padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Text(label, Modifier.weight(1f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(13.dp), tint = color)
        }
    }
}
