package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.*

@Composable
fun MoreScreen(onTransactions: () -> Unit, onLoans: () -> Unit, onKameti: () -> Unit, onSavings: () -> Unit, onSettings: () -> Unit, onCredit: () -> Unit, onInsights: () -> Unit = {}, listState: LazyListState = rememberLazyListState(),
    onBudgets: () -> Unit = {}, onBills: () -> Unit = {}, onCalendar: () -> Unit = {}, onComparison: () -> Unit = {}, onSearch: () -> Unit = {}, onFinanceTools: () -> Unit = {}) {
    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            enteringItem {
                Text("Your money, organized", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Everything you need in one place", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rows = listOf(
                Triple("Money tools", "Plans, accounts, shopping, refunds and private records", Icons.Rounded.AccountBalanceWallet) to onFinanceTools,
                Triple("Insights", "Spending patterns and offline saving suggestions", Icons.Rounded.BarChart) to onInsights,
                Triple("Transactions", "Search, edit and manage your entries", Icons.Rounded.Description) to onTransactions,
                Triple("Loans", "Borrowed, lent and payment history", Icons.Rounded.Handshake) to onLoans,
                Triple("Settings & backup", "Account, themes and Google Drive backup", Icons.Rounded.Settings) to onSettings,
                Triple("Category budgets", "Monthly limits and spending progress", Icons.Rounded.AccountBalanceWallet) to onBudgets,
                Triple("Bills & reminders", "Review recurring entries before saving", Icons.Rounded.EventNote) to onBills,
                Triple("Kameti calendar", "Paid, pending and expected receiving", Icons.Rounded.CalendarMonth) to onCalendar,
                Triple("Monthly comparison", "Compare income, expenses and categories", Icons.Rounded.CompareArrows) to onComparison,
                Triple("Search all ledgers", "Find records across your money spaces", Icons.Rounded.Search) to onSearch,
            )
            rows.forEachIndexed { i, (entry, action) -> enteringItem(index = i) {
                val color = listOf(LedgerColors.Blue, LedgerColors.Teal, LedgerColors.Amber, LedgerColors.Purple, LedgerColors.Green, LedgerColors.Teal)[i % 6]
                LedgerCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${entry.first}", onClick = action)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlowIcon(entry.third, color, size = 46.dp)
                        Column(Modifier.weight(1f)) {
                            Text(entry.first, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(entry.second, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
            enteringItem { EncryptionBanner() }
        }
    }
}
