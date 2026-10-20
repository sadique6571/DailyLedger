package com.sadique.dailyledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.ai.AiDraftResult
import com.sadique.dailyledger.data.TransactionDraft
import com.sadique.dailyledger.ui.screens.AutoFillScreen
import com.sadique.dailyledger.ui.screens.CategoryDropdown
import com.sadique.dailyledger.ui.screens.TransactionDialog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryTypingTest {
    @get:Rule val compose = createComposeRule()

    private fun assertDraft(text: String) = compose.onNodeWithTag("category-dropdown")
        .assert(SemanticsMatcher("Editable text is '$text'") { node ->
            val key = androidx.compose.ui.semantics.SemanticsProperties.EditableText
            // IME composition can add underline spans without changing the text.
            node.config.contains(key) && node.config[key].text == text
        })

    private fun typingSurvivesDismissal(initial: String) {
        var committed = initial
        compose.setContent { DailyLedgerTheme("LIGHT") {
            var selected by remember { mutableStateOf(initial) }
            var note by remember { mutableStateOf("") }
            Column {
                CategoryDropdown("EXPENSE", selected, { selected = it; committed = it })
                TextField(note, { note = it }, label = { Text("Note field") })
            }
        } }
        compose.onNodeWithTag("category-dropdown").performClick().performTextReplacement("p")
        assertDraft("p")
        compose.onNodeWithText("Note field").performClick().performTextInput("test")
        assertDraft("p")
        compose.runOnIdle { assertEquals("", committed) }
        compose.onNodeWithTag("category-dropdown").performClick().performTextInput("iyaz")
        assertDraft("piyaz")
        pressBack()
        assertDraft("piyaz")
        compose.onNodeWithTag("category-dropdown").performClick().performTextReplacement("milk pack")
        assertDraft("milk pack")
        compose.onNodeWithText("Packaged milk").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Packaged milk", committed) }
        assertDraft("Packaged milk")
    }

    @Test fun newEntryKeepsTypedSearchAfterFocusAndBackChanges() = typingSurvivesDismissal("")
    @Test fun replacingOtherKeepsTypedSearchAfterFocusAndBackChanges() = typingSurvivesDismissal("Other")

    @Test fun unfinishedCustomCategorySurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var committed = "Other"
        restoration.setContent { DailyLedgerTheme("LIGHT") {
            var selected by remember { mutableStateOf("Other") }
            CategoryDropdown("EXPENSE", selected, { selected = it; committed = it })
        } }
        compose.onNodeWithTag("category-dropdown").performClick().performTextReplacement("Office lunch box")
        pressBack()
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("Office lunch box")
        compose.runOnIdle { assertEquals("", committed) }
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithText("Add custom: Office lunch box").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Office lunch box", committed) }
        assertDraft("Office lunch box")
        compose.onNodeWithTag("category-clear").performClick()
        pressBack()
        assertDraft("")
        compose.runOnIdle { assertEquals("", committed) }
    }

    @Test fun manualReviewRetainsSearchAndCannotSaveItAsOther() {
        var savedCategory: String? = null
        compose.setContent { DailyLedgerTheme("LIGHT") {
            TransactionDialog(null, {}, { _, _, category, _, _ -> savedCategory = category },
                initialCategory = "Other", initialAmount = "100")
        } }
        compose.onNodeWithTag("category-dropdown").performClick().performTextReplacement("milk pack")
        compose.onNodeWithText("Note").performScrollTo().performClick().performTextInput("Morning milk")
        assertDraft("milk pack")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithText("Packaged milk").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Packaged milk", savedCategory) }
    }

    @Test fun aiReviewRetainsCategorySearchWhenSuggestionsClose() {
        val draft = TransactionDraft("EXPENSE", 10000, "Other", "Morning purchase", LocalDate.now().toString())
        compose.setContent { DailyLedgerTheme("LIGHT") {
            AutoFillScreen(false, {}, {}, { AiDraftResult("Review", listOf(draft)) }, { _, _ -> })
        } }
        compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("Morning purchase 100")
        compose.onNodeWithTag("autofill-generate").performScrollTo().performClick()
        compose.onNodeWithTag("autofill-list").performScrollToKey("draft-0")
        compose.onNodeWithTag("category-dropdown").performScrollTo().performClick().performTextReplacement("piyaz")
        pressBack()
        assertDraft("piyaz")
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithText("Onions").performScrollTo().performClick()
        assertDraft("Onions")
    }
}
