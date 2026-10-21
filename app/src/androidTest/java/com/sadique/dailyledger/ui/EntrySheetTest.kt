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

    @Test fun plusShowsOnlyFiveShortcutsAndRetractsAtTheSamePosition() {
        var selected = ""
        compose.setContent { DailyLedgerTheme("LIGHT") {
            androidx.compose.material3.Scaffold(floatingActionButton = { QuickEntryDial { selected = it } }) { _ -> }
        } }
        val toggle = compose.onNodeWithTag("floating-add-expense")
        val before = toggle.fetchSemanticsNode().boundsInRoot
        toggle.performClick()
        quickEntryActions.forEach { compose.onNodeWithTag("quick-entry-${it.id}").assertIsDisplayed() }
        compose.onNodeWithTag("quick-entry-saving").assertDoesNotExist()
        compose.onNodeWithTag("quick-entry-budget").assertDoesNotExist()
        assertEquals(5, quickEntryActions.size)
        val bounds = quickEntryActions.map { compose.onNodeWithTag("quick-entry-${it.id}").fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (upper, lower) ->
            assertTrue("Shortcuts must form a vertical list", upper.bottom <= lower.top)
            assertEquals(upper.center.x, lower.center.x, 1f)
        }
        val toggleBounds = toggle.fetchSemanticsNode().boundsInRoot
        assertTrue("Menu must sit beside the plus", bounds.all { it.right < toggleBounds.left })
        assertTrue("Last action must share the plus vertical span", bounds.last().bottom > toggleBounds.top)
        compose.onNodeWithTag("quick-entry-glass").assertIsDisplayed()
        assertEquals(before, toggle.fetchSemanticsNode().boundsInRoot)
        compose.waitForIdle()
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-five-action-dial.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shot).use { it.readBytes() }
        toggle.performClick()
        compose.onNodeWithTag("quick-entry-ai").assertDoesNotExist()
        toggle.performClick()
        compose.onNodeWithTag("quick-entry-salary").performClick()
        compose.runOnIdle { assertEquals("salary", selected) }
    }

    @Test fun aiEntryUsesSheetAndKeyboardBackRetainsDraft() {
        var imeBottom = 0
        compose.setContent { DailyLedgerTheme("LIGHT") {
            LedgerEntrySheet(onDismissRequest = {}, title = { Text("AI Auto Fill") }, scrollBody = false,
                text = {
                    val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                    SideEffect { imeBottom = bottom }
                    AutoFillScreen(true, {}, {},
                        { com.sadique.dailyledger.ai.AiDraftResult("Ready", emptyList()) },
                        { _, _ -> }, embedded = true)
                }, confirmButton = {})
        } }
        compose.onNodeWithTag("autofill-list").performScrollToKey("input")
        compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("Petrol 1500")
        compose.waitUntil(5000) { imeBottom > 0 }
        val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("input keyevent 4")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        compose.waitUntil(5000) { imeBottom == 0 }
        compose.onNodeWithTag("ledger-entry-sheet").assertIsDisplayed()
        compose.onNodeWithTag("autofill-input").assertTextContains("Petrol 1500")
        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-ai-entry-sheet.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shot).use { it.readBytes() }
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
                onDismissRequest = { if (!busy) closed = true }, canDismiss = !busy,
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
        compose.onNodeWithTag("entry-sheet-handle").performTouchInput { swipeDown(endY = height + 200f) }
        compose.runOnIdle { assertFalse(closed); busy = false }
        compose.onNodeWithTag("entry-sheet-handle").performTouchInput { swipeDown(endY = height + 200f) }
        compose.waitUntil(5000) { closed }
    }
}
