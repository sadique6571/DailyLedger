package com.sadique.dailyledger.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class LedgerScreensTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.now().toString()
    private val kameti = CommitteeEntity("k", "test", "Office kameti", 500000, 12, month, null, false, true, "", 1, 2)

    @Test fun dashboardKeepsSavingsAndKametiOutsideSalaryAndOpensTheirTabs() {
        var opened = ""
        val tx = listOf(
            TransactionEntity("salary", "test", "INCOME", 10000000, "Salary", "", "$month-01", 1, 1),
            TransactionEntity("expense", "test", "EXPENSE", 3000000, "Food", "", "$month-02", 1, 1)
        )
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                DashboardScreen(
                    tx = tx, loans = emptyList(), lp = emptyList(), committees = listOf(kameti),
                    cp = listOf(CommitteePaymentEntity("p", "test", "k", 1, month, 1000000, 1)),
                    receipts = emptyList(), savings = listOf(SavingEntity("s", "test", "DIRECT", 2000000, "$month-01", "", 1)),
                    userName = "Test",
                    onAddSalary = { opened = "salary" }, onAddExpense = { opened = "expense" },
                    onOpenSavings = { opened = "savings" }, onOpenKameti = { opened = "kameti" })
            }
        }
        compose.onNodeWithText("Remaining balance").assertIsDisplayed()
        compose.onNodeWithText(money(4000000)).assertIsDisplayed()
        screenshot("dashboard")
        compose.onNodeWithTag("dashboard-list").performScrollToKey("monthly-totals");compose.onNodeWithTag("dashboard-add-salary").performScrollTo().performClick()
        assertEquals("salary", opened)
        compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals");compose.onNodeWithText("Savings total").performScrollTo().performClick()
        assertEquals("savings", opened)
        compose.onNodeWithText("Kameti paid").performScrollTo().performClick()
        assertEquals("kameti", opened)
        screenshot("dashboard-separate-totals")
    }

    @Test fun homeDoesNotShowAutomaticInsightsButKeepsManualShortcutAndKametiCard() {
        val rows = listOf(TransactionEntity("salary", "test", "INCOME", 1000000, "Salary",
            "", "$month-01", 1, 1))
        val analysis = com.sadique.dailyledger.ai.SpendingInsights.calculate("test", rows)
        var opened = false
        var kametiOpened = false
        compose.setContent { DailyLedgerTheme("LIGHT") {
            DashboardScreen(
                tx = rows, loans = emptyList(), lp = emptyList(), committees = listOf(kameti),
                cp = emptyList(), receipts = emptyList(), savings = emptyList(), userName = "Test", snapshot = analysis, onAddSalary = {}, onAddExpense = {},
                onOpenSavings = {}, onOpenKameti = { kametiOpened = true },
                onOpenInsights = { opened = true },
            )
        } }
        compose.onNodeWithTag("startup-insight-preview").assertDoesNotExist()
        compose.onNodeWithText("Your offline money insight").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals")
        // Card semantics may merge the tag into its clickable parent on Android.
        // Use the visible label as the stable interaction target (also covered above).
        compose.onNodeWithText("Kameti paid").performScrollTo().assertIsDisplayed().performClick()
        assertTrue("Kameti paid card must open Kameti", kametiOpened)
        compose.onNodeWithTag("dashboard-list").performScrollToKey("insights-shortcut")
        compose.onNodeWithText("Spending insights").performClick()
        assertTrue("Manual Insights remains accessible", opened)
    }

    @Test fun dashboardRequiresExplicitSalaryCycleStartAndOffersArchive() {
        var action = ""
        val cycle = LedgerCycleEntity("test", 1, 250)
        val tx = listOf(TransactionEntity("salary", "test", "INCOME", 1000, "Salary", "", "$month-01", 2, 2))
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                DashboardScreen(
                    tx = tx, loans = emptyList(), lp = emptyList(), committees = emptyList(), cp = emptyList(),
                    receipts = emptyList(), savings = emptyList(), userName = "Test",
                    ledgerCycle = cycle, onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {},
                    onStartNewMonth = { action = "start" },
                    onArchivePreviousMonth = { callback -> action = "archive"; callback(true) },
                )
            }
        }
        compose.onNodeWithText(money(1250)).assertIsDisplayed()
        compose.onNodeWithTag("dashboard-list").performScrollToKey("salary-cycle-controls")
        compose.onNodeWithTag("start-new-salary-month").performClick()
        compose.onNodeWithTag("salary-actions-dialog").assertExists()
        compose.onNodeWithTag("salary-received-option").assertIsOn()
        compose.onNodeWithText("Salary received (Rs)").assertIsDisplayed()
        compose.onNodeWithTag("salary-options-continue").performClick()
        compose.onNodeWithText("Archive & start").performClick()
        assertEquals("start", action)
        compose.onNodeWithTag("dashboard-list").performScrollToKey("salary-cycle-controls")
        compose.onNodeWithTag("start-new-salary-month").performClick()
        compose.onNodeWithTag("salary-received-option").assertIsOn().performClick()
        compose.onNodeWithTag("salary-received-option").assertIsOff()
        compose.onNodeWithTag("archive-previous-option").assertIsOff().performClick()
        compose.onNodeWithTag("archive-previous-option").assertIsOn()
        compose.onNodeWithTag("salary-options-continue").performClick()
        compose.onNodeWithText("Archive month").assertIsDisplayed().performClick()
        assertEquals("archive", action)
    }

    @Test fun frequentPurchaseShowsOnlyCurrentMonthReadOnlyEntriesAndTotal() {
        var edited = false
        val tx = listOf(
            TransactionEntity("veg-1", "test", "EXPENSE", 50000, "Vegetables", "Sabzi", "$month-01", 1, 1),
            TransactionEntity("veg-2", "test", "EXPENSE", 20000, "Vegetables", "Sabzi", "$month-02", 2, 2),
            TransactionEntity("old", "test", "EXPENSE", 30000, "Vegetables", "Sabzi", "${YearMonth.parse(month).minusMonths(1)}-01", 3, 3),
        )
        compose.setContent { DailyLedgerTheme("LIGHT") {
            DashboardScreen(tx = tx, loans = emptyList(), lp = emptyList(), committees = emptyList(), cp = emptyList(),
                receipts = emptyList(), savings = emptyList(), userName = "Test",
                onAddSalary = {}, onAddExpense = { edited = true }, onOpenSavings = {}, onOpenKameti = {},
                onAddCategoryExpense = { _, _ -> edited = true }, ledgerCycle = LedgerCycleEntity("test", 1, 0))
        } }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("frequent-items")
        compose.onNodeWithTag("frequent-item-0").performScrollTo().performClick()
        compose.onAllNodes(hasText("Sabzi") and hasAnyAncestor(isDialog())).assertCountEquals(3)
        compose.onNode(hasText("Total this month: ${displayMoney(70000)}") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithTag("frequent-history-entry-old").assertDoesNotExist()
        compose.onNodeWithTag("frequent-history-entry-veg-1").assertHasNoClickAction()
        compose.onNodeWithTag("frequent-history-entry-veg-2").assertHasNoClickAction()
        assertFalse(edited); screenshot("frequent-current-month")
    }

    @Test fun dashboardFrequentRecentAndMoneySpaceTilesHaveVisibleGaps() {
        val rows = listOf(
            TransactionEntity("s", "test", "INCOME", 1000000, "Salary", "", "$month-01", 1, 1),
            TransactionEntity("a", "test", "EXPENSE", 15000, "Food", "Vegetables", "$month-02", 2, 2),
            TransactionEntity("b", "test", "EXPENSE", 12000, "Bills", "Internet", "$month-03", 3, 3),
        )
        compose.setContent { DailyLedgerTheme("LIGHT") {
            DashboardScreen(
                tx = rows, loans = emptyList(), lp = emptyList(), committees = emptyList(),
                cp = emptyList(), receipts = emptyList(), savings = emptyList(), userName = "Test",
                onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = {},
            )
        } }

        compose.onNodeWithTag("dashboard-list").performScrollToKey("frequent-items")
        val frequent0 = compose.onNodeWithTag("frequent-item-0").fetchSemanticsNode().boundsInRoot
        val frequent1 = compose.onNodeWithTag("frequent-item-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Frequent purchases must have a gap", frequent1.top > frequent0.bottom + 2f)
        screenshot("frequent-raised-cards")

        compose.onNodeWithTag("dashboard-list").performScrollToKey("recent")
        val recent0 = compose.onNodeWithTag("recent-transaction-0").fetchSemanticsNode().boundsInRoot
        val recent1 = compose.onNodeWithTag("recent-transaction-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Recent transactions must have a gap", recent1.top > recent0.bottom + 2f)
        screenshot("recent-raised-cards")

        compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals")
        val first = compose.onNodeWithTag("money-space-first").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("money-space-second").fetchSemanticsNode().boundsInRoot
        assertTrue("Money spaces must have horizontal or vertical breathing room",
            second.top > first.bottom + 2f || second.left > first.right + 2f)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Money space cards should stay compact", first.height < 166f * density && second.height < 166f * density)
        val screenWidthDp = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.widthPixels / density
        if (screenWidthDp >= 360f) {
            assertTrue("Money spaces should use two columns on standard phones",
                kotlin.math.abs(first.top - second.top) <= 3f * density)
        }
    }

    @Test fun upcomingKametiTurnIsABoxedClickableTile() {
        var opened = false
        val member = CommitteeMemberEntity(
            id = "me", ownerId = "test", committeeId = "k", name = "Me",
            turnNumber = 1, turnMonth = month, isMe = true, createdAt = 1,
        )
        compose.setContent { DailyLedgerTheme("LIGHT") {
            DashboardScreen(
                tx = emptyList(), loans = emptyList(), lp = emptyList(),
                committees = listOf(kameti), cp = emptyList(), receipts = emptyList(),
                committeeMembers = listOf(member), savings = emptyList(), userName = "Test",
                onAddSalary = {}, onAddExpense = {}, onOpenSavings = {}, onOpenKameti = { opened = true },
            )
        } }
        compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals")
        compose.onNodeWithTag("kameti-turn-card").performScrollTo().assertIsDisplayed().performClick()
        assertTrue("Boxed Kameti turn should open Kameti", opened)
    }

    @Test fun firstKametiCanBeReceivedBeforeSecond() {
        val receipts = mutableStateOf(emptyList<CommitteeReceiptEntity>())
        compose.setContent {
            DailyLedgerTheme("LIGHT") {
                CommitteeScreen(
                    committees = listOf(kameti), payments = emptyList(), receipts = receipts.value, members = emptyList(),
                    onAdd = { _, _, _, _, _, _, _, _ -> }, onPaid = { _, _, _, _ -> },
                    onReceive = { id, amount, date, note, method -> receipts.value = receipts.value + CommitteeReceiptEntity("r", "test", id, amount, date, note, 1, method) },
                    onDeleteReceipt = {}, onAddMembers = { _, _, _ -> }, onMemberReceived = { _, _ -> }, onDeleteMember = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("Receive amount").performScrollTo().performClick()
        compose.onNodeWithText("Received amount PKR").performTextReplacement("60000")
        screenshot("kameti-entry-fields")
        compose.onNodeWithText("Save receiving").performClick()
        compose.onNodeWithText("Partially received").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Receive amount").assertIsEnabled()
        assertEquals(6000000L, receipts.value.single().amountMinor)
        screenshot("kameti-partial")
        compose.onNodeWithText("Receive amount").performClick()
        compose.onNodeWithText("Received amount PKR").performTextReplacement("60001")
        compose.onNodeWithText("Save receiving").assertIsNotEnabled()
        compose.onNodeWithText("Received amount PKR").performTextReplacement("60000")
        compose.onNodeWithText("Save receiving").performClick()
        compose.onNodeWithText("Fully received").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Received").filter(hasClickAction()).onFirst().assertIsNotEnabled()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Keep previews outside app storage: Gradle removes the test app after the run.
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
