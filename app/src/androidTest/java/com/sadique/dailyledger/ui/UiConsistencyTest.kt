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
