package com.sadique.dailyledger.ui

import com.sadique.dailyledger.ui.LedgerSurface as Surface
import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.NavigationHistory.Companion.BUDGETS
import com.sadique.dailyledger.ui.NavigationHistory.Companion.LEARNING

private data class BottomTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomBar(
    tab: Int,
    onSelectTab: (Int) -> Unit,
) {
    val tabs = remember {
        listOf(
            BottomTab("Home", Icons.Rounded.Home, Icons.Rounded.Home),
            BottomTab("Udhar", Icons.Rounded.ShoppingCart, Icons.Rounded.ShoppingCart),
            BottomTab("Kameti", Icons.Rounded.Groups, Icons.Rounded.Groups),
            BottomTab("Savings", Icons.Rounded.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
            BottomTab("More", Icons.Rounded.Apps, Icons.Rounded.Apps),
        )
    }
    Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        tabs.forEachIndexed { i, t ->
            val selected = when (i) { 0 -> tab == 0; 1 -> tab == 7; 2 -> tab == 3; 3 -> tab == 4; 4 -> tab in listOf(1, 2, 5, 6) || tab in BUDGETS..NavigationHistory.FINANCE_TOOLS; else -> false }
            NavigationBarItem(selected = selected,
                onClick = { onSelectTab(i) },
                icon = { Icon(t.icon, t.label, Modifier.size(25.dp)) },
                label = { Text(t.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = LedgerColors.Teal, selectedTextColor = LedgerColors.Teal,
                    indicatorColor = Color.Transparent, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
    }
}
