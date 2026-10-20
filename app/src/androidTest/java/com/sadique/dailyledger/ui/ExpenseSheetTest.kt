package com.sadique.dailyledger.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import com.sadique.dailyledger.ui.screens.TransactionDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExpenseSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floatingButtonOpensBottomSheetAndSavesSelectedDateAndAmount() {
        var saved = ""
        compose.setContent { DailyLedgerTheme("LIGHT") {
            var open by remember { mutableStateOf(false) }
            Scaffold(floatingActionButton = { ExpenseFloatingButton { open = true } }) { _ -> }
            if (open) TransactionDialog(null, { open = false },
                { type, amount, category, _, date -> saved = "$type:$amount:$category:$date"; open = false },
                initialCategory = "Milk")
        } }
        compose.onNodeWithTag("floating-add-expense").performClick()
        compose.onNodeWithTag("transaction-bottom-sheet").assertIsDisplayed()
        compose.onNodeWithTag("transaction-save").assertIsNotEnabled()
        compose.onNodeWithText("Rs 500").performClick()
        compose.onNodeWithText("Entry date").performScrollTo().performTextReplacement("2026-10-19")
        compose.onNodeWithTag("transaction-save").assertIsEnabled()
        compose.waitForIdle()
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-expense-bottom-sheet.png")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        compose.onNodeWithTag("transaction-save").performClick()
        compose.runOnIdle { assertEquals("EXPENSE:50000:Milk:2026-10-19", saved) }
        compose.onNodeWithTag("transaction-bottom-sheet").assertDoesNotExist()
    }

    @Test fun cancellingDirtySheetKeepsDraftUntilDiscardIsConfirmed() {
        var dismissed = false
        compose.setContent { DailyLedgerTheme("DARK") {
            TransactionDialog(null, { dismissed = true }, { _, _, _, _, _ -> fail("No save expected") },
                initialCategory = "Milk", initialAmount = "100")
        } }
        compose.onNodeWithText("Amount PKR").performTextReplacement("250")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Amount PKR").assertTextContains("250")
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
