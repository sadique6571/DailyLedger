package com.sadique.dailyledger.ui

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.R
import com.sadique.dailyledger.ai.SpendingInsights
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.export.ExportManager
import com.sadique.dailyledger.i18n.*
import com.sadique.dailyledger.planning.*
import com.sadique.dailyledger.ui.screens.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UrduPlanningUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var display: DisplayPreferences
    @Before fun setup() { display = DisplayPreferences(context); display.reset(); display.updateLanguage("en") }
    @After fun cleanup() { compose.runOnIdle { display.updateLanguage("en"); display.reset() } }

    @Test fun settingsEntranceReplaysOnNewVisit() {
        var visit by mutableIntStateOf(1)
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { DailyLedgerTheme("LIGHT") {
                Box(Modifier.fillMaxWidth().padding(48.dp)) {
                    CardEntrance(index = 0, entranceKey = visit) {
                        androidx.compose.material3.Text(
                            "Settings animation", modifier = Modifier.testTag("settings-entrance-probe"),
                        )
                    }
                }
            } }
            compose.mainClock.advanceTimeBy(500)
            val settled = compose.onNodeWithTag("settings-entrance-probe").fetchSemanticsNode().boundsInRoot.left
            compose.runOnIdle { visit++ }
            compose.mainClock.advanceTimeByFrame()
            val startingAgain = compose.onNodeWithTag("settings-entrance-probe").fetchSemanticsNode().boundsInRoot.left
            val animationsDisabled = android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
            if (animationsDisabled) {
                // CI deliberately disables Android animations for deterministic emulator runs.
                assertEquals("Reduced-motion Settings cards must appear in place", settled, startingAgain, 2f)
            } else {
                assertTrue(
                    "Settings entrance must slide again after revisiting",
                    startingAgain < settled - 10f * context.resources.displayMetrics.density,
                )
            }
            compose.mainClock.advanceTimeBy(400)
            val finishedAgain = compose.onNodeWithTag("settings-entrance-probe").fetchSemanticsNode().boundsInRoot.left
            assertEquals("Settings entrance must settle in its normal position", settled, finishedAgain, 2f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun allBuiltInCategoriesHaveUrduLabelsAndFontIsBundled() {
        val labels = LedgerStrings.load(context)
        for (category in CategoryCatalog.load(context)) {
            assertTrue("Missing Urdu: ${category.label}", labels[category.label]?.any { it in '\u0600'..'\u06ff' } == true)
            assertTrue("Missing group: ${category.group}", labels.containsKey(category.group))
        }
        assertTrue(context.resources.openRawResource(R.font.noto_nastaliq_urdu).use { it.readBytes().size } > 100000)
        val strings = LedgerStrings(true, labels)
        assertEquals("نوٹ: Milk from Aslam", strings.text("Note: Milk from Aslam"))
        assertEquals("Paid: Rs 100", LedgerStrings(false, labels).text("Paid: Rs 100"))
        assertEquals("18%", progressPercent(18))
        assertEquals("90%", progressPercent(90))
    }

    @Test fun urduDropdownSearchReturnsOriginalCanonicalKeyAndRtl() {
        display.updateLanguage("ur")
        var selected = ""
        var direction = LayoutDirection.Ltr
        compose.setContent { CompositionLocalProvider(LocalDisplayPreferences provides display) { DailyLedgerTheme("LIGHT") {
            direction = LocalLayoutDirection.current
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                CategoryDropdown("EXPENSE", selected, { selected = it })
                LocalizedText("Milk from Aslam", translate = false)
            }
        } } }
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithTag("category-dropdown").performTextInput("مونگ")
        compose.onNodeWithText("دال مونگ").performClick()
        assertEquals("Moong dal", selected); assertEquals(LayoutDirection.Rtl, direction)
        compose.onNodeWithText("Milk from Aslam").assertExists()
        shot("urdu-category-selection")
    }

    @Test fun dashboardOrderPersistsAndResetsWithAccessibleButtons() {
        compose.setContent { CompositionLocalProvider(LocalDisplayPreferences provides display) { DailyLedgerTheme("LIGHT") {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DashboardOrderEditor(display) }
        } } }
        compose.onNodeWithContentDescription("Move up Remaining balance").performScrollTo().performClick()
        assertEquals("balance", display.dashboardOrder.first())
        assertEquals(display.dashboardOrder, DisplayPreferences(context).dashboardOrder)
        compose.onNodeWithText("Reset card order").performScrollTo().performClick()
        assertEquals(DisplayPreferences.DEFAULT_ORDER, display.dashboardOrder)
    }

    @Test fun reorderedHomeKeepsInsightsAndRefreshNeedsAnExplicitTap() {
        display.updateDashboardOrder(listOf("insights") + DisplayPreferences.DEFAULT_ORDER)
        var refreshes = 0
        val tx = listOf(TransactionEntity("salary", "test", "INCOME", 100000, "Salary", "", LocalDate.now().toString(), 1, 1))
        val snapshot = SpendingInsights.calculate("test", tx)
        compose.setContent { CompositionLocalProvider(LocalDisplayPreferences provides display) { DailyLedgerTheme("LIGHT") {
            DashboardScreen(tx, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), savings = emptyList(), userName = "Test", onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {}, snapshot = snapshot, aiEnabled = true, onRefreshAi = { refreshes++ })
        } } }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("insights")
        assertEquals(0, refreshes)
        compose.onNodeWithTag("online-ai-refresh").performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Test fun reviewDoesNotSaveUntilExplicitConfirmAndKeepsReviewedDate() {
        val bill = RecurringBill(UUID.randomUUID().toString(), "Rent", "EXPENSE", "Rent", 150000, 31, "2026-02")
        var saved: TransactionDraft? = null
        var closed = false
        compose.setContent { DailyLedgerTheme("LIGHT") { BillReview(bill, { closed = true }) { _, draft -> saved = draft } } }
        compose.onNodeWithText("2026-02-28").assertExists()
        assertNull(saved)
        compose.onNodeWithText("Entry date").performTextReplacement("2026-03-01")
        compose.onNodeWithText("Confirm & save").performClick()
        compose.waitUntil(5000) { saved != null }
        assertEquals("2026-03-01", saved?.date); assertEquals(150000L, saved?.amountMinor); assertTrue(closed)
    }

    @Test fun pdfPreviewRendersAndClosesWithoutSavingOrChangingRecords() {
        val rows = listOf(TransactionEntity("salary", "sample", "INCOME", 120000, "Salary", "", "2026-10-01", 1, 1))
        val bytes = ExportManager.statementBytes(rows, null, null)
        var open by mutableStateOf(true)
        compose.setContent { DailyLedgerTheme("LIGHT") { if (open) ExportPreview(bytes) { open = false } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 / 1").assertExists()
        compose.onNodeWithText("Share").assertIsEnabled()
        compose.onNodeWithText("Save PDF").assertIsEnabled()
        shot("pdf-preview-share")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("pdf-preview").assertDoesNotExist()
        assertEquals(1, rows.size)
    }

    @Test fun compactUrduHomeProgressKeepsPercentReadableOutsideThickTrack() {
        display.updateLanguage("ur")
        val today = LocalDate.now().toString()
        val rows = listOf(
            TransactionEntity("salary-progress", "test", "INCOME", 100000, "Salary", "", today, 1, 1),
            TransactionEntity("expense-progress", "test", "EXPENSE", 35000, "Food", "", today, 2, 2),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDisplayPreferences provides display,
                LocalDensity provides Density(density.density, 1.3f),
            ) { DailyLedgerTheme("DARK") {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    DashboardScreen(
                        tx = rows, loans = emptyList(), lp = emptyList(), committees = emptyList(),
                        cp = emptyList(), receipts = emptyList(), savings = emptyList(), userName = "Test",
                        onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {},
                    )
                }
            } }
        }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("balance")
        // Balance is a clickable parent and merges its descendants in the accessibility tree.
        // Inspect the unmerged tree to target the actual percentage and track.
        compose.onNodeWithTag("balance-progress-percent", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed().assertTextEquals("35%")
        val percent = compose.onNodeWithTag("balance-progress-percent", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithTag("balance-progress-track", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Home progress percent must not overlap its track", track.top >= percent.bottom)
        assertTrue("Home progress track should be at least 18dp", track.height >= 17f * context.resources.displayMetrics.density)
        shot("urdu-home-thick-progress")
    }

    @Test fun compactDarkUrduBudgetRemainsReadableWithLargeSystemText() {
        display.updateLanguage("ur")
        val store = PlannerStore(context, "budget-preview-${UUID.randomUUID()}")
        store.setBudget("Milk", 100000)
        val row = TransactionEntity("milk", store.ownerId, "EXPENSE", 90000, "Milk", "", LocalDate.now().toString(), 1, 1)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDisplayPreferences provides display, LocalDensity provides Density(density.density, 1.3f)) { DailyLedgerTheme("DARK") {
                Box(Modifier.width(320.dp).fillMaxHeight()) { BudgetScreen(store.ownerId, store, listOf(row)) }
            } }
        }
        compose.onNodeWithTag("budgets-list").performScrollToKey("Milk")
        compose.onNodeWithText("دودھ").assertIsDisplayed()
        compose.onNodeWithTag("budget-progress-percent-Milk").performScrollTo()
            .assertIsDisplayed().assertTextEquals("90%")
        val budgetLabel = compose.onNodeWithTag("budget-progress-percent-Milk").fetchSemanticsNode().boundsInRoot
        val budgetTrack = compose.onNodeWithTag("budget-progress-track-Milk").fetchSemanticsNode().boundsInRoot
        assertTrue("Urdu percent must sit above—not overlap—the budget bar", budgetTrack.top >= budgetLabel.bottom)
        assertTrue("Budget progress bar should be visibly thicker", budgetTrack.height >= 14f * context.resources.displayMetrics.density)
        compose.onNodeWithText(LedgerStrings(true, LedgerStrings.load(context)).text("Approaching your budget limit")).assertExists()
        shot("urdu-dark-large-budget")
        com.sadique.dailyledger.security.SecureSecretStore(context, "planner-v1:${store.ownerId}").clearBytes()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
