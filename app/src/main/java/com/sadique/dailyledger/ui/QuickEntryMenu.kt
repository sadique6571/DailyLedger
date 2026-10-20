package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.i18n.LocalizedText as Text

internal data class QuickEntryAction(val id: String, val label: String, val icon: ImageVector)
internal val quickEntryActions = listOf(
    QuickEntryAction("expense", "Expense", Icons.Rounded.ShoppingCart),
    QuickEntryAction("salary", "Salary", Icons.Rounded.Payments),
    QuickEntryAction("income", "Other income", Icons.Rounded.AccountBalanceWallet),
    QuickEntryAction("saving", "Saving", Icons.Rounded.AccountBalanceWallet),
    QuickEntryAction("credit", "Udhar purchase", Icons.Rounded.Storefront),
    QuickEntryAction("loan", "Loan / lent money", Icons.Rounded.Handshake),
    QuickEntryAction("kameti", "Personal kameti", Icons.Rounded.Groups),
    QuickEntryAction("collection", "Collection kameti", Icons.Rounded.GroupAdd),
    QuickEntryAction("ai", "AI auto fill", Icons.Rounded.AutoAwesome),
    QuickEntryAction("detailed", "Detailed entry", Icons.Rounded.EditNote),
    QuickEntryAction("budget", "Category budget", Icons.Rounded.PieChart),
    QuickEntryAction("reminder", "Bill / reminder", Icons.Rounded.NotificationsActive),
    QuickEntryAction("SPLIT", "Split a bill", Icons.Rounded.CallSplit),
    QuickEntryAction("ACCOUNT", "Account opening balance", Icons.Rounded.AccountBalance),
    QuickEntryAction("TRANSFER", "Account transfer", Icons.Rounded.SwapHoriz),
    QuickEntryAction("ADJUSTMENT", "Balance adjustment", Icons.Rounded.Tune),
    QuickEntryAction("GOAL", "Saving goal", Icons.Rounded.Flag),
    QuickEntryAction("INSTALLMENT", "Installment purchase", Icons.Rounded.CalendarMonth),
    QuickEntryAction("REIMBURSEMENT", "Pay on someone's behalf", Icons.Rounded.People),
    QuickEntryAction("SHOPPING", "Shopping item", Icons.Rounded.ShoppingBag),
    QuickEntryAction("PLANNED", "Planned payment", Icons.Rounded.Event),
    QuickEntryAction("ALLOCATION", "Salary allocation", Icons.Rounded.AccountTree),
    QuickEntryAction("SHORTCUT", "Custom shortcut", Icons.Rounded.Bolt),
)

@Composable
internal fun QuickEntryMenu(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    LedgerEntrySheet(onDismissRequest = onDismiss, title = { Text("Add entry") }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickEntryActions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action ->
                        FilledTonalButton(onClick = { onSelect(action.id) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                                .testTag("quick-entry-${action.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                            Icon(action.icon, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(action.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
