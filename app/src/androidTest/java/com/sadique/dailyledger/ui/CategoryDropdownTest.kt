package com.sadique.dailyledger.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.ui.screens.CategoryDropdown
import com.sadique.dailyledger.ui.screens.CreditScreen
import com.sadique.dailyledger.data.CreditDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDropdownTest {
    @get:Rule val compose = createComposeRule()
    @Test fun manualUdharUsesCategoryWithoutRequiringAnItemField() {
        var saved: CreditDraft? = null
        compose.setContent { DailyLedgerTheme("AQUA") {
            CreditScreen(
                credits = emptyList(), payments = emptyList(),
                onSave = { drafts, _ -> saved = drafts.single() },
                onPayment = { _, _, _, _, _ -> }, onDelete = {},
            )
        } }
        compose.onNodeWithContentDescription("Add udhar").performClick()
        compose.onNodeWithText("Saman / item").assertDoesNotExist()
        compose.onNodeWithTag("udhar-category").assertExists()
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithTag("category-dropdown").performTextInput("piyaz")
        compose.onNodeWithText("Onions").performScrollTo().performClick()
        compose.onNodeWithText("Amount PKR").performTextInput("120")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { saved != null }
        assertEquals("Onions", saved?.category)
        assertEquals("Onions", saved?.item)
    }

    @Test fun searchesSpellingAliasesAndSelectsAnIndividualCategory() {
        var picked = ""
        compose.setContent { DailyLedgerTheme("AQUA") {
            var selected by remember { mutableStateOf("") }
            Column { CategoryDropdown("EXPENSE", selected, { selected = it; picked = it }); Text("Selected: $selected") }
        } }
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithTag("category-dropdown").performTextInput("piyaz")
        compose.onNodeWithText("Onions").performScrollTo().performClick()
        assertEquals("Onions", picked)
        compose.onNodeWithText("Selected: Onions").assertIsDisplayed()
    }

    @Test fun clearingOtherKeepsItEmptyAndSearchingDoesNotRestoreOldSelection() {
        var chosen="Other"
        compose.setContent { DailyLedgerTheme("LIGHT") {
            var selected by remember { mutableStateOf("Other") }
            Column { CategoryDropdown("EXPENSE",selected,{selected=it;chosen=it}); androidx.compose.material3.TextField("",{},label={Text("Other field")}) }
        } }
        compose.onNodeWithTag("category-clear").performClick()
        compose.runOnIdle { assertEquals("",chosen) }
        compose.onNodeWithText("Other field").performClick()
        compose.onNodeWithTag("category-dropdown").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("category-dropdown").performClick().performTextInput("milk pack")
        compose.onNodeWithText("Packaged milk").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Packaged milk",chosen) }
        compose.onNodeWithTag("category-dropdown").performClick().performTextInput("papar")
        compose.runOnIdle { assertEquals("",chosen) }
        compose.onNodeWithText("Papar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Papar",chosen) }
    }
}
