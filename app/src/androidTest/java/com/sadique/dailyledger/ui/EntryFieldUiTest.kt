package com.sadique.dailyledger.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sadique.dailyledger.ui.screens.TransactionDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EntryFieldUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expenseDateSupportsTypingCancelCalendarAndSaveWithoutChangingDate() {
        var saved = ""
        compose.setContent { DailyLedgerTheme("LIGHT") {
            TransactionDialog(null, {}, { _, _, _, _, date -> saved = date }, initialCategory = "Milk", initialAmount = "100")
        } }
        compose.onNodeWithText("Entry date").performScrollTo().performTextReplacement("2026-10-01")
        compose.onNodeWithTag("transaction-calendar").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Entry date").assertTextContains("2026-10-01")
        compose.onNodeWithTag("transaction-calendar").performClick()
        compose.onNodeWithText("Apply date").performClick()
        compose.onNodeWithText("Entry date").assertTextContains("2026-10-01")
        screenshot("expense-entry-fields")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("2026-10-01", saved) }
    }

    @Test fun salaryDateHasCorrectTitleAndInvalidTypedDateCannotBeSaved() {
        compose.setContent { DailyLedgerTheme("DARK") {
            TransactionDialog(null, {}, { _, _, _, _, _ -> fail("Invalid date must not save") },
                initialType = "INCOME", initialCategory = "Salary", initialAmount = "10000")
        } }
        compose.onNodeWithText("Salary date").performScrollTo().performTextReplacement("2026-02-30")
        compose.onNodeWithText("Use a valid YYYY-MM-DD date").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Salary date").performTextReplacement("2026-10-20")
        compose.onNodeWithText("Save").assertIsEnabled()
        screenshot("salary-entry-fields")
    }

    @Test fun optionalDueDateRemainsBlankAfterCalendarCancelAndEditableAfterSelection() {
        var value by mutableStateOf("")
        compose.setContent { DailyLedgerTheme("LIGHT") {
            LedgerDateField(value, { value = it }, "Due date (optional)", optional = true)
        } }
        compose.onNodeWithText("Use a valid YYYY-MM-DD date").assertDoesNotExist()
        compose.onNodeWithTag("date-calendar-Due date (optional)").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("", value) }
        compose.onNodeWithTag("date-calendar-Due date (optional)").performClick()
        compose.onNodeWithText("Apply date").performClick()
        compose.runOnIdle { assertTrue(isValidDate(value)) }
        compose.onNodeWithText("Due date (optional)").performTextReplacement("")
        compose.runOnIdle { assertEquals("", value) }
        compose.onNodeWithText("Use a valid YYYY-MM-DD date").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
