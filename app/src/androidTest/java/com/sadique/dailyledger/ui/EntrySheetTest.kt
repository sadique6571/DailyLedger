package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sadique.dailyledger.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EntrySheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plusMenuShowsLabelsAndOpensSelectedEntrySheet() {
        var selected by mutableStateOf("")
        var menu by mutableStateOf(false)
        compose.setContent { DailyLedgerTheme("LIGHT") {
            ExpenseFloatingButton { menu = true }
            if (menu) QuickEntryMenu({ menu = false }) { selected = it; menu = false }
            if (selected == "saving") SavingDialog({}, save = { _, _, _, _ -> })
        } }
        compose.onNodeWithTag("floating-add-expense").performClick()
        compose.onNodeWithTag("quick-entry-expense").assertTextContains("Expense")
        compose.onNodeWithTag("quick-entry-salary").assertTextContains("Salary")
        compose.onNodeWithTag("quick-entry-saving").performClick()
        compose.onNodeWithText("Add saving").assertIsDisplayed()
        compose.onNodeWithText("Amount PKR").performTextInput("250")
        val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("input keyevent 4")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        compose.waitForIdle()
        compose.onNodeWithTag("ledger-entry-sheet").assertIsDisplayed()
        compose.onNodeWithText("Amount PKR").assertTextContains("250")
    }

    @Test fun savingsOpensAsSheetAndSavesWithoutChangingAmount() {
        var saved = 0L
        compose.setContent { DailyLedgerTheme("LIGHT") {
            SavingDialog(dismiss = {}, save = { _, amount, _, _ -> saved = amount })
        } }
        compose.onNodeWithTag("ledger-entry-sheet").assertIsDisplayed()
        compose.onNodeWithText("Amount PKR").performTextInput("250")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(25000L, saved) }
    }

    @Test fun detailedEntryUsesInlineCategoriesInsideSharedSheet() {
        compose.setContent { DailyLedgerTheme("LIGHT") {
            FinanceEntryEditor("test", listOf("Cash", "Bank"), null, dismiss = {}, onSave = {})
        } }
        compose.onNodeWithTag("category-dropdown").performScrollTo().performClick()
        compose.onNodeWithTag("category-dropdown").performTextReplacement("Milk")
        compose.onNodeWithTag("category-results")
            .assert(hasAnyAncestor(hasTestTag("ledger-entry-sheet")))
        compose.onNodeWithTag("category-floating-popup").assertDoesNotExist()
    }

    @Test fun sharedSheetDoesNotRestartWhileTypingAndHonoursBusyDismissGuard() {
        var busy by mutableStateOf(true)
        var closed by mutableStateOf(false)
        var imeBottom = 0
        compose.setContent { DailyLedgerTheme("LIGHT") {
            var value by remember { mutableStateOf("") }
            if (!closed) LedgerEntrySheet(
                onDismissRequest = { if (!busy) closed = true },
                title = { Text("Entry") },
                text = {
                    val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                    SideEffect { imeBottom = bottom }
                    LedgerTextField(value, { value = it }, label = { Text("Amount") })
                },
                confirmButton = {},
            )
        } }
        val field = compose.onNodeWithText("Amount")
        field.performClick()
        compose.waitUntil(5000) { imeBottom > 0 }
        val sheet = compose.onNodeWithTag("ledger-entry-sheet")
        var previousTop = Float.NaN
        var lastMovement = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(5000) {
            val current = sheet.fetchSemanticsNode().boundsInRoot.top
            if (current != previousTop) { previousTop = current; lastMovement = android.os.SystemClock.uptimeMillis() }
            android.os.SystemClock.uptimeMillis() - lastMovement > 400
        }
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        try {
            listOf("1", "2", "3").forEach {
                field.performTextInput(it)
                compose.mainClock.advanceTimeByFrame()
                assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
                compose.mainClock.advanceTimeBy(100)
            }
        } finally { compose.mainClock.autoAdvance = true }
        field.assertTextContains("123")
        val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("input keyevent 4")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        compose.waitUntil(5000) { imeBottom == 0 }
        sheet.assertIsDisplayed()
        field.assertTextContains("123")
        // Dismiss via a downward gesture; the form remains while its save guard is busy.
        sheet.performTouchInput { swipeDown() }
        compose.runOnIdle { assertFalse(closed); busy = false }
        sheet.performTouchInput { swipeDown() }
        compose.waitUntil(5000) { closed }
    }
}
