package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.ui.screens.TransactionsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayCustomizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun displayPreferencesArePersistentAndResettable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = DisplayPreferences(context)
        first.updateFontSize(3)
        first.updateIconSize(2)
        first.updateCardSize(0)
        first.updateCompact(true)
        first.updatePrivateAmounts(true)
        val restored = DisplayPreferences(context)
        assertEquals(3, restored.fontSize)
        assertEquals(2, restored.iconSize)
        assertEquals(0, restored.cardSize)
        assertEquals(true, restored.compact)
        assertEquals(true, restored.privateAmounts)
        restored.reset()
        assertEquals(false, DisplayPreferences(context).privateAmounts)
    }

    @Test fun transactionDateFiltersAreAvailableWithoutChangingRecords() {
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                TransactionsScreen(items = emptyList(), onSave = { _, _, _, _, _, _ -> }, onDelete = {})
            }
        }
        compose.onNodeWithText("All dates").assertExists()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Pick date").assertExists()
        compose.onNodeWithText("No transactions yet. Tap + to add your first entry.").assertExists()
    }
}
