package com.sadique.dailyledger.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.ui.screens.CategoryDropdown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDropdownTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchesSpellingAliasesAndSelectsAnIndividualCategory() {
        var picked = ""
        compose.setContent { DailyLedgerTheme("AQUA") {
            var selected by remember { mutableStateOf("") }
            Column { CategoryDropdown("EXPENSE", selected, { selected = it; picked = it }); Text("Selected: $selected") }
        } }
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithTag("category-search").performTextInput("piyaz")
        compose.onNodeWithText("Onions").performScrollTo().performClick()
        assertEquals("Onions", picked)
        compose.onNodeWithText("Selected: Onions").assertIsDisplayed()
    }
}
