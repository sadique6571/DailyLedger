package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.enteringItem
import com.sadique.dailyledger.ui.enteringItems

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun MoreScreen(onTransactions: () -> Unit, onLoans: () -> Unit, onKameti: () -> Unit, onSavings: () -> Unit, onSettings: () -> Unit, onCredit: () -> Unit, onInsights: () -> Unit = {}) {
    LedgerBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            enteringItem {
                Text("Your money, organized", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Everything you need in one place", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rows = listOf(
                Triple("Insights", "Spending patterns and offline saving suggestions", Icons.Rounded.BarChart) to onInsights,
                Triple("Transactions", "Search, edit and manage your entries", Icons.Rounded.Description) to onTransactions,
                Triple("Udhar Saman", "Goods on credit, due dates and partial payments", Icons.Rounded.ShoppingCart) to onCredit,
                Triple("Loans", "Borrowed, lent and payment history", Icons.Rounded.Handshake) to onLoans,
                Triple("Kameti", "Shares, turns and partial receipts", Icons.Rounded.Groups) to onKameti,
                Triple("Savings", "Your savings in their own space", Icons.Rounded.AccountBalanceWallet) to onSavings,
                Triple("Settings & backup", "Account, themes and Google Drive backup", Icons.Rounded.Settings) to onSettings,
            )
            rows.forEachIndexed { i, (entry, action) -> enteringItem(index = i) {
                val color = listOf(LedgerColors.Blue, LedgerColors.Teal, LedgerColors.Amber, LedgerColors.Purple, LedgerColors.Green, LedgerColors.Teal)[i % 6]
                LedgerCard(Modifier.fillMaxWidth().clickable(onClick = action)) {
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
