package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ui.screens.TransactionDialog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EntrySaveAndKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saveLocksImmediatelyAndFailedSaveKeepsDraftForRetry() {
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        var open by mutableStateOf(true)
        compose.setContent { DailyLedgerTheme("LIGHT") {
            if (open) TransactionDialog(null, { open = false }, { _, _, _, _, _ -> fail("Async path expected") },
                initialAmount = "250", initialCategory = "Milk",
                saveAsync = { _, _, _, _, _ ->
                    attempts++
                    if (attempts == 1) { gate.await(); error("Test storage failure") }
                    open = false
                })
        } }
        val save = compose.onNodeWithTag("transaction-save")
        save.performClick()
        compose.waitUntil(5000) { attempts == 1 }
        save.assertIsNotEnabled()
        compose.onNodeWithText("Saving…").assertIsDisplayed()
        save.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, attempts); gate.complete(Unit) }
        compose.waitUntil(5000) {
            runCatching { compose.onNodeWithTag("transaction-save-error").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Amount PKR").assertTextContains("250")
        compose.onNodeWithTag("category-dropdown").assertTextContains("Milk")
        save.assertIsEnabled().performClick()
        compose.waitUntil(5000) { !open }
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun nextMovesToDateAndDoneClearsFocus() {
        compose.setContent { DailyLedgerTheme("LIGHT") {
            var amount by remember { mutableStateOf("") }
            var date by remember { mutableStateOf("2026-10-21") }
            Column {
                LedgerTextField(amount, { amount = it }, singleLine = true, modifier = Modifier.testTag("ime-amount"))
                LedgerDateField(date, { date = it }, modifier = Modifier.testTag("ime-date"))
            }
        } }
        compose.onNodeWithTag("ime-amount").performClick().performImeAction()
        compose.onNodeWithTag("ime-date").assertIsFocused().performImeAction()
        compose.onNodeWithTag("ime-date").assertIsNotFocused()
    }

    @Test fun largeFontEntryKeepsSaveAndScrollableDateReachable() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                DailyLedgerTheme("DARK") {
                    TransactionDialog(null, {}, { _, _, _, _, _ -> },
                        initialAmount = "100", initialCategory = "Milk")
                }
            }
        }
        compose.onNodeWithTag("transaction-save").assertIsDisplayed()
        compose.onNodeWithText("Entry date").performScrollTo().performTextReplacement("2026-10-20")
        compose.onNodeWithText("Entry date").assertTextContains("2026-10-20")
        compose.onNodeWithTag("transaction-save").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun simplifiedFormHasNoShortcutsAndNoteMovesToDate() {
        compose.setContent { DailyLedgerTheme("LIGHT") {
            TransactionDialog(null, {}, { _, _, _, _, _ -> }, initialAmount = "100", initialCategory = "Milk", onHistory = { _, _ -> })
        } }
        listOf("View history", "Quick notes", "Weekly", "Monthly", "Today", "Yesterday").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("transaction-note").performScrollTo().performTextInput("Lunch")
        compose.onNodeWithTag("transaction-note").performImeAction()
        compose.onNodeWithText("Entry date").assertIsFocused()
        compose.onNodeWithTag("transaction-note").assertTextContains("Lunch")
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun keyboardScrollDoesNotMoveSheetAndHandleExpandsWithoutLosingDraft() {
        compose.setContent { DailyLedgerTheme("LIGHT") {
            TransactionDialog(null, {}, { _, _, _, _, _ -> }, initialAmount = "150", initialCategory = "Milk")
        } }
        val sheet = compose.onNodeWithTag("wide-transaction-entry")
        compose.onNodeWithTag("transaction-note").performScrollTo().performClick().performTextInput("Keep my note")
        compose.waitUntil(5000) {
            var visible = false
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = android.view.inspector.WindowInspector.getGlobalWindowViews().any { view ->
                    androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
            }
            visible
        }
        var previousTop = Float.NaN
        var lastMovement = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(5000) {
            val top = sheet.fetchSemanticsNode().boundsInRoot.top
            if (top != previousTop) { previousTop = top; lastMovement = android.os.SystemClock.uptimeMillis() }
            android.os.SystemClock.uptimeMillis() - lastMovement > 400
        }
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        repeat(3) {
            compose.onNodeWithTag("transaction-entry-body").performTouchInput { swipeUp() }
            compose.waitForIdle()
            assertEquals("Keyboard scrolling must not drag the sheet", top, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        val heightBefore = sheet.fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("entry-sheet-handle").performTouchInput { swipeUp(endY = -100f) }
        compose.waitUntil(5000) { sheet.fetchSemanticsNode().boundsInRoot.height > heightBefore + 1f }
        compose.onNodeWithTag("transaction-note").performScrollTo().assertTextContains("Keep my note")
        compose.onNodeWithTag("transaction-save").assertIsDisplayed()
    }

}
