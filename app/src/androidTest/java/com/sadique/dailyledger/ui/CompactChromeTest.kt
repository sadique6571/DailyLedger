package com.sadique.dailyledger.ui

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.ai.SpendingInsights
import com.sadique.dailyledger.ai.SpendingTip
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.ui.screens.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerKeepsSearchAndAiActionsAndRendersBothThemes() {
        val mode = mutableStateOf("LIGHT")
        var searches = 0
        var ai = 0
        val date = LocalDate.now().toString()
        val rows = listOf(
            TransactionEntity("s", "preview", "INCOME", 8000000, "Salary", "", date, 1, 1),
            TransactionEntity("e", "preview", "EXPENSE", 3500000, "Groceries", "", date, 2, 2),
        )
        compose.setContent { DailyLedgerTheme(mode.value) {
            Scaffold(topBar = {
                AppTopBar(0, "Your money, a brighter tomorrow", false, "Back", "back", {},
                    { searches++ }, {}, { ai++ })
            }, floatingActionButton = { ExpenseFloatingButton {} }, bottomBar = { AppBottomBar(0, {}) }) { pad ->
                Box(Modifier.padding(pad)) {
                    DashboardScreen(rows, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                        savings = emptyList(), userName = "Sadique", weatherEnabled = true,
                        weatherCity = "Okara", weatherTemperature = "27°C", weatherCondition = "Sunny",
                        onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {})
                }
            }
        } }
        compose.onNodeWithTag("greeting-name").assertTextContains("Sadique", substring = true)
        compose.onNodeWithTag("weather-chip").assertExists()
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("global-ai").performClick()
        compose.runOnIdle { assertEquals(1, searches); assertEquals(1, ai) }
        compose.onNodeWithTag("floating-add-expense").assertIsDisplayed()
        shot("compact-home-light")
        compose.runOnIdle { mode.value = "DARK" }
        compose.onNodeWithTag("compact-app-header").assertIsDisplayed()
        compose.onNodeWithTag("global-ai").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Remaining balance").assertIsDisplayed()
        shot("compact-home-dark")
    }

    @Test fun insightsKeepTheirCardsAndManualRefreshWithoutDismissActions() {
        var refreshes = 0
        val date = LocalDate.now()
        val rows = listOf(TransactionEntity("e", "o", "EXPENSE", 10000, "Milk", "", date.toString(), 1, 1))
        val snapshot = SpendingInsights.calculate("o", rows, date)
        compose.setContent { DailyLedgerTheme("LIGHT") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InsightsCard(snapshot, listOf(SpendingTip("Cloud suggestion", "Keep essentials first.")),
                    "Ready", true, {}, onRefresh = { refreshes++ })
            }
        } }
        compose.onNodeWithTag("ai-insights-cards").assertExists()
        compose.onAllNodesWithText("Not useful").assertCountEquals(0)
        compose.onNodeWithTag("online-ai-refresh").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText("Cloud suggestion").performScrollTo().assertIsDisplayed()
        shot("compact-insights-cards")
    }

    @Test fun reminderCenterRemainsAccessibleFromMore() {
        var opened = false
        compose.setContent { DailyLedgerTheme("LIGHT") {
            MoreScreen({}, {}, {}, {}, {}, {}, onReminders = { opened = true })
        } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reminders"))
        compose.onNodeWithText("Reminders").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
