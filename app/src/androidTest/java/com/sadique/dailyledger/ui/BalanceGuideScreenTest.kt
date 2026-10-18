package com.sadique.dailyledger.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.screens.DashboardScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BalanceGuideScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun balanceShowsDailyGuideCountdownLowNoticeAndCategoryEntryShortcut() {
        val today = LocalDate.now()
        val rows = listOf(
            TransactionEntity("salary", "owner", "INCOME", 1000000, "Salary", "", today.toString(), 1, 1),
            TransactionEntity("food", "owner", "EXPENSE", 950000, "Groceries", "Weekly shop", today.toString(), 2, 2),
        )
        var category = ""
        compose.setContent { DailyLedgerTheme("AQUA") {
            DashboardScreen(rows, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                savings = emptyList(), userName = "Demo", weatherEnabled = false, weatherCity = "", weatherTemperature = "", weatherCondition = "",
                onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {},
                onAddCategoryExpense = { name, _ -> category = name })
        } }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("balance")
        compose.onNodeWithTag("daily-spending-guide", useUnmergedTree = true).assertTextContains("/ day until salary", substring = true)
        compose.onNodeWithTag("actual-daily-average", useUnmergedTree = true).assertTextContains("/ day", substring = true)
        compose.onNodeWithTag("salary-countdown", useUnmergedTree = true).assertTextContains("to expected salary", substring = true)
        compose.onNodeWithTag("low-balance-notice", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        shot("balance-guide")
        compose.onNodeWithContentDescription("Hide balance").performScrollTo().performClick()
        compose.onNodeWithTag("daily-spending-guide", useUnmergedTree = true).assertTextContains("Rs •••••", substring = true)
        compose.onNodeWithTag("actual-daily-average", useUnmergedTree = true).assertTextContains("Rs •••••", substring = true)
        compose.onNodeWithTag("dashboard-list").performScrollToKey("spending")
        compose.onNodeWithTag("chart-category-Groceries").performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithTag("chart-total").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        assertEquals("", category)
        shot("spending-donut")
    }
    @Test fun donutRendersAllCategoryRowsAsReadOnlyBoxes() {
        val categories = listOf("Groceries", "Rent", "Transport", "Health", "Bills", "Education", "Shopping")
        val today = LocalDate.now().toString()
        val rows = categories.mapIndexed { index, name ->
            TransactionEntity("expense-$index", "owner", "EXPENSE", (index + 1) * 1000L,
                name, "", today, index + 1L, index + 1L)
        }
        compose.setContent { DailyLedgerTheme("AQUA") {
            DashboardScreen(rows, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                savings = emptyList(), userName = "Demo", weatherEnabled = false,
                weatherCity = "", weatherTemperature = "", weatherCondition = "",
                onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {})
        } }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("spending")
        compose.onNodeWithTag("chart-category-Groceries").assertDoesNotExist()
        compose.onNodeWithTag("chart-toggle-categories").performScrollTo().performClick()
        categories.forEach { category ->
            compose.onNodeWithTag("chart-category-$category").assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        }
        compose.onNodeWithTag("chart-total").assertExists()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
