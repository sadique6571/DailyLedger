package com.sadique.dailyledger.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sadique.dailyledger.ui.screens.LoansScreen
import com.sadique.dailyledger.ui.screens.SavingDialog
import com.sadique.dailyledger.ui.screens.SavingsScreen
import com.sadique.dailyledger.ui.screens.TransactionDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiConsistencyTest {
    @get:Rule val compose = createComposeRule()

    private fun styledTransactionActions(theme: String) {
        var edited = 0; var deleted = 0; var details = 0; var favorites = 0
        var hidden by mutableStateOf(false)
        val entry = com.sadique.dailyledger.data.TransactionEntity("card-check", "owner", "EXPENSE", 12500, "Milk", "Morning purchase", "2026-10-21", 1, 1)
        compose.setContent { DailyLedgerTheme(theme) {
            androidx.compose.foundation.layout.Column {
                LedgerCard { androidx.compose.material3.Text("Home-style card") }
                LedgerHistoryCard(accent = ledgerCardAccent("history")) { androidx.compose.material3.Text("Payment history") }
                com.sadique.dailyledger.ui.screens.TransactionRow(entry, true, false, false, hidden,
                    { favorites++ }, { deleted++ }, { edited++ }, { details++ }, {})
            }
        } }
        compose.onNodeWithText("Milk").performClick()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithContentDescription("Add favorite").performClick()
        compose.onNodeWithContentDescription("Delete Milk transaction").performClick()
        compose.runOnIdle { assertEquals(1, edited); assertEquals(1, details); assertEquals(1, favorites); assertEquals(1, deleted) }
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-unified-cards-${theme.lowercase()}.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shot).use { it.readBytes() }
        compose.runOnIdle { hidden = true }
        compose.onNodeWithText("Rs •••••").assertExists()
    }

    @Test fun styledTransactionActionsAndPrivacyInLight() = styledTransactionActions("LIGHT")
    @Test fun styledTransactionActionsAndPrivacyInDark() = styledTransactionActions("DARK")

    @Test fun globalPlusIsHomeOnlyAndReturningHomeDoesNotReopenMenu() {
        var tab by mutableIntStateOf(NavigationHistory.HOME)
        val expanded = mutableStateOf(false)
        compose.setContent { DailyLedgerTheme("LIGHT") {
            Scaffold(floatingActionButton = { DashboardEntryDial(tab, expanded) {} }) { _ -> }
        } }
        listOf(NavigationHistory.UDHAR, NavigationHistory.KAMETI, NavigationHistory.SAVINGS,
            NavigationHistory.MORE, NavigationHistory.TRANSACTIONS, NavigationHistory.LOANS,
            NavigationHistory.SETTINGS).forEach { destination ->
            compose.onNodeWithTag("floating-add-expense").assertIsDisplayed().performClick()
            compose.onNodeWithTag("quick-entry-expense").assertIsDisplayed()
            compose.runOnIdle { tab = destination }
            compose.onNodeWithTag("floating-add-expense").assertDoesNotExist()
            compose.runOnIdle { assertFalse(expanded.value); tab = NavigationHistory.HOME }
            compose.onNodeWithTag("floating-add-expense").assertIsDisplayed()
            compose.onNodeWithTag("quick-entry-expense").assertDoesNotExist()
        }
    }

    @Test fun entryFooterKeepsEqualSaveAndCancelSizesAcrossForms() {
        var saving by mutableStateOf(false)
        compose.setContent { DailyLedgerTheme("LIGHT") {
            if (saving) SavingDialog({}, save = { _, _, _, _ -> })
            else TransactionDialog(null, {}, { _, _, _, _, _ -> }, initialCategory = "Milk", initialAmount = "100")
        } }
        fun checkActions() {
            compose.onNodeWithTag("entry-form-actions").assertIsDisplayed()
            val cancel = compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
            val save = compose.onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
            assertEquals(cancel.width, save.width, 1f)
            assertEquals(cancel.height, save.height, 1f)
            assertTrue(cancel.right < save.left)
        }
        checkActions()
        compose.runOnIdle { saving = true }
        checkActions()
    }

    @Test fun savingsEmptyActionOpensItsOwnEntryForm() {
        compose.setContent { DailyLedgerTheme("LIGHT") {
            SavingsScreen(emptyList(), { _, _, _, _ -> }, {})
        } }
        compose.onNodeWithText("No savings yet").assertIsDisplayed()
        compose.onNodeWithText("Add saving").performScrollTo().performClick()
        compose.onNodeWithTag("ledger-entry-sheet").assertIsDisplayed()
        compose.onNodeWithText("Amount PKR").assertExists()
    }

    @Test fun loansEmptyActionOpensItsOwnEntryForm() {
        compose.setContent { DailyLedgerTheme("DARK") {
            LoansScreen(emptyList(), emptyList(), onAdd = { _, _, _, _, _, _, _ -> },
                onPayment = { _, _, _, _, _ -> }, onDelete = {})
        } }
        compose.onNodeWithText("No loans yet").assertIsDisplayed()
        compose.onNodeWithText("Add loan").performScrollTo().performClick()
        compose.onNodeWithText("New loan").assertIsDisplayed()
    }
}
