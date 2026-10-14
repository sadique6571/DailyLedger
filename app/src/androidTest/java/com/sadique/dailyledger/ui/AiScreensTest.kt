package com.sadique.dailyledger.ui
import android.os.ParcelFileDescriptor
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
 @Test fun reviewEditThenSave(){var saved=emptyList<TransactionDraft>();val drafts=listOf(TransactionDraft("EXPENSE",150000,"Fuel","Petrol","2026-10-06"),TransactionDraft("EXPENSE",30000,"Milk","Doodh","2026-10-06"))
  compose.setContent{DailyLedgerTheme("AQUA"){AutoFillScreen(true,{},{},{AiDraftResult("Entries check karein",drafts)},{rows,_->saved=rows})}}
  compose.onNodeWithTag("autofill-list").performScrollToKey("input");compose.onNodeWithTag("autofill-input").performTextInput("Petrol 1500 aur doodh 300");compose.onNodeWithTag("autofill-generate").performScrollTo().performClick();compose.onNodeWithTag("autofill-list").performScrollToKey("review-heading");compose.onNodeWithText("Review 2 entries").performScrollTo().assertIsDisplayed();assertTrue(saved.isEmpty());compose.onNodeWithTag("autofill-list").performScrollToKey("draft-0");compose.onNodeWithTag("draft-edit-0").performScrollTo().performClick();compose.onNodeWithText("Amount PKR").performTextReplacement("1600");compose.onNodeWithText("Save",useUnmergedTree=true).performClick();compose.onNodeWithTag("autofill-list").performScrollToKey("save-drafts");compose.onNodeWithTag("autofill-save").assertIsEnabled();shot("autofill-review");compose.onNodeWithTag("autofill-save").performClick();compose.waitForIdle();assertEquals(2,saved.size);assertEquals(160000L,saved.first().amountMinor);compose.onNodeWithTag("autofill-save").assertDoesNotExist()
 }
 @Test fun offlineAutoFillDoesNotRequireCloudConsent(){var sent=false;compose.setContent{DailyLedgerTheme("MIDNIGHT"){AutoFillScreen(false,{},{},{sent=true;AiDraftResult("Offline Mini AI",listOf(TransactionDraft("EXPENSE",150000,"Fuel","Petrol","2026-10-06")))},{_,_->})}};compose.onNodeWithTag("autofill-list").performScrollToKey("input");compose.onNodeWithTag("autofill-input").performScrollTo().performTextInput("Petrol 1500");compose.onNodeWithTag("autofill-generate").performScrollTo().assertIsEnabled().performClick();compose.waitForIdle();assertTrue(sent);shot("ai-offline") }
 @Test fun themesAndAutomaticInsights(){val today=LocalDate.now();val date=today.toString();val tx=listOf(TransactionEntity("s","owner","INCOME",10000000,"Salary","",date,1,1),TransactionEntity("e","owner","EXPENSE",600000,"Restaurant","",date,1,1));val snap=SpendingInsights.calculate("owner",tx,today)
  compose.setContent{DailyLedgerTheme("AQUA"){DashboardScreen(tx=tx,loans=emptyList(),lp=emptyList(),committees=emptyList(),cp=emptyList(),receipts=emptyList(),savings=listOf(SavingEntity("s","owner","DIRECT",200000,date,"",1)),userName="Owner",weatherEnabled=false,weatherCity="",weatherTemperature="",weatherCondition="",onAddSalary={},onAddExpense={},onOpenSavings={},onOpenKameti={},onAutoFill={},snapshot=snap)}}
  compose.onNodeWithText(money(9200000)).assertIsDisplayed();shot("aqua-home");compose.onNodeWithTag("dashboard-list").performScrollToKey("separate-totals");compose.onNodeWithText("Savings total").performScrollTo().assertIsDisplayed();shot("wallet-savings");compose.onNodeWithTag("dashboard-list").performScrollToKey("insights");compose.onNodeWithText("Mini AI • Offline",useUnmergedTree=true).assertIsDisplayed();shot("saving-insights")
 }
 private fun shot(name:String){compose.waitForIdle();val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png");ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
}
