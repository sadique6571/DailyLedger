package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerRaisedCard as Card
import com.sadique.dailyledger.ui.LedgerTextField as OutlinedTextField
import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.money

@Composable
internal fun TransactionFilterHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    dateRange: String,
    onDateRangeChange: (String) -> Unit,
    selectedDay: String,
    onSelectedDayChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    compactRows: Boolean,
    onToggleCompactRows: () -> Unit,
    onClearAll: () -> Unit,
    onAutoFill: (() -> Unit)?,
    favorites: List<TransactionEntity>,
    frequentCategories: List<Pair<String, String>>,
    onQuickFavorite: (TransactionEntity) -> Unit,
    onQuickCategory: (String, String) -> Unit,
    dateContext: Context,
    today: LocalDate,
) {
    Text("Transactions", style = MaterialTheme.typography.headlineMedium)
    if (favorites.isNotEmpty()) {
        Text("Favorite transactions • tap to repeat", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            favorites.forEach { favorite ->
                AssistChip(onClick = { onQuickFavorite(favorite) }, label = { Text(favorite.category) }, leadingIcon = { Icon(Icons.Filled.Star, null) })
            }
        }
    }
    if (frequentCategories.isNotEmpty()) {
        Text("Frequently used categories", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            frequentCategories.forEach { (type, category) ->
                AssistChip(onClick = { onQuickCategory(type, category) }, label = { Text(category) })
            }
        }
    }
    onAutoFill?.let { action -> FilledTonalButton(onClick = action) { Text("AI Auto Fill") } }
    OutlinedTextField(query, onQueryChange, label = { Text("Search categories or notes") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear search") } })
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL" to "All dates", "TODAY" to "Today", "WEEK" to "Last 7 days", "MONTH" to "This month").forEach { (value, label) ->
            FilterChip(selected = dateRange == value, onClick = { onDateRangeChange(value) }, label = { Text(label) })
        }
        FilterChip(selected = dateRange == "DAY", onClick = {
            val date = runCatching { LocalDate.parse(selectedDay) }.getOrDefault(today)
            DatePickerDialog(dateContext, { _, year, month, day ->
                onSelectedDayChange(LocalDate.of(year, month + 1, day).toString())
                onDateRangeChange("DAY")
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, label = { Text(if (dateRange == "DAY") selectedDay else "Pick date") })
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("NEWEST" to "Newest", "OLDEST" to "Oldest", "AMOUNT_HIGH" to "Highest amount", "AMOUNT_LOW" to "Lowest amount").forEach { (value, label) ->
            FilterChip(selected = sortOrder == value, onClick = { onSortOrderChange(value) }, label = { Text(label) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onClearAll) { Text("Clear all filters") }
        TextButton(onClick = onToggleFavoritesOnly) { Text(if (favoritesOnly) "Show all entries" else "Favorites only") }
        TextButton(onClick = onToggleCompactRows) { Text(if (compactRows) "Comfortable rows" else "Compact rows") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL", "EXPENSE", "INCOME").forEach {
            FilterChip(filter == it, { onFilterChange(it) }, { Text(it.lowercase().replaceFirstChar(Char::uppercase)) })
        }
    }
}

@Composable
internal fun TransactionRow(
    x: TransactionEntity,
    compact: Boolean,
    isFavorite: Boolean,
    noteExpanded: Boolean,
    privateAmounts: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShowDetails: () -> Unit,
    onToggleNote: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(x.category, style = MaterialTheme.typography.titleMedium)
                 Text(x.type.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall,
                     color = if ((x.type == "INCOME" || x.refundOf.isNotBlank())) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(x.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onShowDetails, contentPadding = PaddingValues(0.dp)) { Text("Details") }
                if (x.note.isNotBlank()) {
                    Text(x.note, translate = false, modifier = Modifier.clickable { onToggleNote() }, maxLines = if (noteExpanded) Int.MAX_VALUE else 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (isFavorite) "Remove favorite" else "Add favorite")
                }
                Text(if (privateAmounts) "Rs •••••" else money(if (x.type == "EXPENSE" && x.refundOf.isBlank()) -x.amountMinor else x.amountMinor))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${x.category} transaction") }
            }
        }
    }
}
