package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.ui.screens.MoreScreen
import com.sadique.dailyledger.ui.screens.TransactionsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun moreOptionsOpenExpectedDestinations() {
        var destination = ""
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                MoreScreen(
                    onTransactions = { destination = "transactions" },
                    onLoans = { destination = "loans" },
                    onKameti = { destination = "kameti" },
                    onSavings = { destination = "savings" },
                    onSettings = { destination = "settings" },
                    onCredit = { destination = "credit" },
                    onInsights = { destination = "insights" },
                )
            }
        }
        compose.onNodeWithText("Money tools").assertDoesNotExist()
        compose.onNodeWithText("Loans", substring = false).performScrollTo().performClick()
        assertEquals("loans", destination)
        compose.onNodeWithText("Settings & backup").performScrollTo().performClick()
        assertEquals("settings", destination)
    }

    @Test fun transactionsSearchCanBeClearedWithoutChangingRecords() {
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                TransactionsScreen(items = emptyList(), onSave = { _, _, _, _, _, _ -> }, onDelete = {})
            }
        }
        compose.onNodeWithText("No transactions yet. Tap + to add your first entry.").assertExists()
        compose.onNodeWithText("Search categories or notes").performTextInput("milk")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("No transactions yet. Tap + to add your first entry.").assertExists()
    }
}
