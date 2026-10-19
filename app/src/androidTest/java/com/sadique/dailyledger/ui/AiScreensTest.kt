package com.sadique.dailyledger.ui
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.ai.*
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
@RunWith(AndroidJUnit4::class) class AiScreensTest {
 @get:Rule val compose=createComposeRule()
 @Test fun privacyMasksInsightAmountsAndCloudDetails() {
  val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val display = DisplayPreferences(context)
  display.updatePrivateAmounts(true)
  val today = LocalDate.now()
  val snapshot = SpendingInsights.calculate("owner", listOf(TransactionEntity("e", "owner", "EXPENSE", 100000, "Restaurant", "", today.toString(), 1, 1)), today)
  try {
   compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalDisplayPreferences provides display) {
    DailyLedgerTheme("AQUA") {
     androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
      InsightsCard(snapshot, listOf(SpendingTip("Cloud tip", "Private cloud value Rs 99999")), "Ready", true, {})
     }
    }
   } }
   compose.onAllNodesWithText("Rs 200", substring = true).assertCountEquals(0)
   compose.onAllNodesWithText("Private cloud value", substring = true).assertCountEquals(0)
   compose.onNodeWithText("Rs •••••").assertExists()
   shot("private-insights")
  } finally { compose.runOnIdle { display.updatePrivateAmounts(false) } }
 }

 @Test fun donutKeepsTotalAndStartsWithThreeRowsThenExpandsAndCollapses() {
  val categories = (1..8).map { "Category $it" to it * 10000L }
  compose.setContent { DailyLedgerTheme("AQUA") {
   androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
    SpendingDonutCard(categories, categories.sumOf { it.second }, {})
   }
  } }
  compose.onNodeWithTag("chart-total").assertExists()
  compose.onNodeWithTag("donut-context-tip").assertDoesNotExist()
  compose.onNodeWithTag("chart-category-Category 3").assertExists()
  compose.onNodeWithTag("chart-category-Category 4").assertDoesNotExist()
  compose.onNodeWithTag("chart-toggle-categories").performScrollTo().performClick()
  compose.onNodeWithTag("chart-category-Category 8").performScrollTo().assertIsDisplayed()
  shot("donut-expanded")
  compose.onNodeWithTag("chart-toggle-categories").performScrollTo().performClick()
  compose.onNodeWithTag("chart-category-Category 4").assertDoesNotExist()
  compose.onNodeWithTag("chart-total").performScrollTo().assertIsDisplayed()
  shot("donut-collapsed")
 }
 @Test fun reviewEditThenSave(){var saved=emptyList<TransactionDraft>();val drafts=listOf(TransactionDraft("EXPENSE",150000,"Fuel","Petrol","2026-10-06"),TransactionDraft("EXPENSE",30000,"Milk","Doodh","2026-10-06"))
  compose.setContent{DailyLedgerTheme("AQUA"){AutoFillScreen(true,{},{},{AiDraftResult("Entries check karein",drafts)},{rows,_->saved=rows})}}
  compose.onNodeWithTag("autofill-list").performScrollToKey("input");compose.onNodeWithTag("autofill-input").performTextInput("Petrol 1500 aur doodh 300");compose.onNodeWithTag("autofill-generate").performScrollTo().performClick();compose.onNodeWithTag("autofill-list").performScrollToKey("review-heading");compose.onNodeWithText("Review 2 entries").performScrollTo().assertIsDisplayed();assertTrue(saved.isEmpty());compose.onNodeWithTag("autofill-list").performScrollToKey("draft-0");compose.onNodeWithTag("draft-edit-0").performScrollTo().performClick();compose.onNodeWithText("Amount PKR").performTextReplacement("1600");compose.onNodeWithText("Save",useUnmergedTree=true).performClick();compose.onNodeWithTag("autofill-list").performScrollToKey("save-drafts");compose.onNodeWithTag("autofill-save").assertIsEnabled();shot("autofill-review");compose.onNodeWithTag("autofill-save").performClick();compose.onNodeWithTag("autofill-confirm").assertIsEnabled().performClick();compose.waitForIdle();assertEquals(2,saved.size);assertEquals(160000L,saved.first().amountMinor);compose.onNodeWithTag("autofill-save").assertDoesNotExist()
 }
 @Test fun offlineAutoFillDoesNotRequireCloudConsent(){var sent=false;compose.setContent{DailyLedgerTheme("MIDNIGHT"){AutoFillScreen(false,{},{},{sent=true;AiDraftResult("Offline Mini AI",listOf(TransactionDraft("EXPENSE",150000,"Fuel","Petrol","2026-10-06")))},{_,_->})}};compose.onNodeWithTag("autofill-list").performScrollToKey("input");compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("Petrol 1500");compose.onNodeWithTag("autofill-generate").performScrollTo().assertIsEnabled().performClick();compose.waitForIdle();assertTrue(sent);shot("ai-offline") }
 @Test fun themesAndAutomaticInsights(){val today=LocalDate.now();val date=today.toString();val tx=listOf(TransactionEntity("s","owner","INCOME",10000000,"Salary","",date,1,1),TransactionEntity("e","owner","EXPENSE",600000,"Restaurant","",date,1,1));val snap=SpendingInsights.calculate("owner",tx,today)
  compose.setContent{DailyLedgerTheme("AQUA"){DashboardScreen(tx=tx,loans=emptyList(),lp=emptyList(),committees=emptyList(),cp=emptyList(),receipts=emptyList(),savings=listOf(SavingEntity("s","owner","DIRECT",200000,date,"",1)),userName="Owner",weatherEnabled=false,weatherCity="",weatherTemperature="",weatherCondition="",onAddSalary={},onAddExpense={},onOpenSavings={},onOpenKameti={},onAutoFill={},snapshot=snap)}}
  compose.onNodeWithText(money(9200000)).assertIsDisplayed();shot("aqua-home");compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals");compose.onNodeWithText("Savings total").performScrollTo().assertIsDisplayed();shot("wallet-savings");compose.onNodeWithTag("dashboard-list").performScrollToKey("insights");compose.onNodeWithText("Mini AI • Offline",useUnmergedTree=true).assertIsDisplayed();shot("saving-insights")
 }
 @Test fun onlineSuggestionsRequestOnlyOnRefreshAndDisableTheButtonWhileBusy() {
  var calls = 0
  val busy = androidx.compose.runtime.mutableStateOf(false)
  val status = androidx.compose.runtime.mutableStateOf("Tap Refresh to get online suggestions.")
  val today = LocalDate.now()
  val snap = SpendingInsights.calculate("owner", listOf(TransactionEntity("e", "owner", "EXPENSE", 10000, "Milk", "", today.toString(), 1, 1)), today)
  compose.setContent { DailyLedgerTheme("AQUA") {
   androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
    InsightsCard(snap, emptyList(), status.value, true, {}, busy.value, { calls++; busy.value = true })
   }
  } }
  compose.onNodeWithTag("online-ai-refresh").performScrollTo().assertIsEnabled()
  compose.runOnIdle { status.value = "Records changed. Tap Refresh." }
  compose.runOnIdle { assertEquals(0, calls) }
  compose.onNodeWithTag("online-ai-refresh").performClick().assertIsNotEnabled()
  compose.runOnIdle { assertEquals(1, calls); busy.value = false }
  compose.onNodeWithTag("online-ai-refresh").assertIsEnabled(); shot("online-manual-refresh")
 }
 @Test fun uncertainDraftNeedsReviewAndFinalConfirmationBeforeSaving() {
  var saved = 0
  val draft = TransactionDraft("EXPENSE", 10000, "Other", "Mystery item", LocalDate.now().toString())
  compose.setContent { DailyLedgerTheme("DARK") { AutoFillScreen(false, {}, {},
   { AiDraftResult("Review this", listOf(draft), listOf(listOf("Category unclear"))) }, { _, _ -> saved++ }) } }
  compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("mystery item 100")
  compose.onNodeWithTag("autofill-generate").performScrollTo().performClick()
  compose.onNodeWithTag("autofill-list").performScrollToKey("draft-0")
  compose.onNodeWithTag("draft-warning-0").assertExists()
  compose.onNodeWithTag("autofill-list").performScrollToKey("save-drafts")
  compose.onNodeWithTag("autofill-save").performClick()
  compose.onNodeWithTag("autofill-confirm").assertIsNotEnabled()
  compose.onNodeWithText("Back to review").performClick()
  compose.onNodeWithTag("autofill-list").performScrollToKey("draft-0")
  compose.onNodeWithTag("draft-checked-0").performScrollTo().performClick()
  compose.onNodeWithTag("autofill-list").performScrollToKey("save-drafts")
  compose.onNodeWithTag("autofill-save").performClick()
  assertEquals(0, saved)
  compose.onNodeWithTag("autofill-confirm").assertIsEnabled().performClick()
  compose.runOnIdle { assertEquals(1, saved) }
 }
 @Test fun missingAmountNeverCallsCloudOrSilentlyDropsAnItem() {
  var generated = false
  compose.setContent { DailyLedgerTheme("LIGHT") { AutoFillScreen(true, {}, {},
   { generated = true; AiDraftResult("", emptyList()) }, { _, _ -> }) } }
  compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("doodh 100 aur sabzi")
  compose.onNodeWithTag("autofill-generate").performScrollTo().performClick()
  compose.onNodeWithTag("autofill-message").assertTextContains("Amount missing", substring = true)
  compose.runOnIdle { assertFalse(generated) }
 }
 private fun shot(name:String){compose.waitForIdle();val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png");ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
}
