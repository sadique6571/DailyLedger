package com.sadique.dailyledger.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FinanceToolsUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun completeCategoryListCanScrollBeyondEightyAndSelectLastMatch(){
        var chosen=""
        val catalog=(1..240).map{LedgerCategory("Group ${(it-1)/40}","Item $it","EXPENSE",emptyList())}
        val options=CategoryOptions.build(catalog,emptyList()){it}
        compose.setContent{DailyLedgerTheme("LIGHT"){CompositionLocalProvider(LocalCategoryOptions provides options){CategoryDropdown("EXPENSE","",{chosen=it})}}}
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onNodeWithTag("category-results").performScrollToKey("category-EXPENSE-Item 240")
        screenshot("category-list-last")
        compose.onNodeWithText("Item 240").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals("Item 240",chosen)}
    }
    @Test fun detailedEntryCalculatesQuantityRateBeforeSave(){
        var saved:TransactionEntity?=null
        compose.setContent{DailyLedgerTheme("AQUA"){FinanceEntryEditor("o",listOf("Cash","Bank"),null,dismiss={},onSave={saved=it})}}
        compose.onNodeWithText("Item / note").performScrollTo().performTextInput("Milk")
        compose.onNodeWithText("Quantity (optional)").performScrollTo().performTextInput("1.5")
        compose.onNodeWithText("Unit (litres, kg, pieces)").performScrollTo().performTextInput("litre")
        compose.onNodeWithText("Rate per unit PKR").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Calculated amount: Rs 150").assertExists()
        screenshot("quantity-rate-entry")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000){saved!=null}
        assertEquals(15000L,saved!!.amountMinor);assertEquals("1.5",saved!!.quantity)
    }
    @Test fun advancedHandoffPreservesUnsavedNormalFormValues(){
        var draft:TransactionEntity?=null;var saved=false
        compose.setContent{DailyLedgerTheme("LIGHT"){TransactionDialog(null,{}, {_,_,_,_,_->saved=true},initialAmount="250",initialCategory="Milk",initialNote="Doodh",onAdvanced={draft=it})}}
        compose.onNodeWithText("Quantity, account & privacy options").performScrollTo().performClick()
        compose.runOnIdle{assertFalse(saved);assertEquals(25000L,draft!!.amountMinor);assertEquals("Milk",draft!!.category);assertEquals("Doodh",draft!!.note)}
    }
    @Test fun monthlyChecklistAndWidgetLinksAppearInMoneyToolsNavigationLabel(){
        assertEquals("Money tools",NavigationHistory.label(NavigationHistory.FINANCE_TOOLS))
        val h=NavigationHistory().open(NavigationHistory.MORE).open(NavigationHistory.FINANCE_TOOLS)
        assertEquals(NavigationHistory.MORE,h.back().current)
    }
    @Test fun privateEditorClosesAndClearsTemporaryScreenProtectionWhenBackgrounded(){
        var open by mutableStateOf(true)
        compose.setContent{DailyLedgerTheme("DARK"){if(open)FinanceEntryEditor("o",listOf("Cash","Bank"),null,defaultPrivate=true,dismiss={open=false},onSave={}) else androidx.compose.material3.Text("Private editor closed")}}
        compose.runOnIdle{assertTrue(com.sadique.dailyledger.security.SensitiveEditors.active.value>0)}
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Private editor closed").assertExists()
        compose.runOnIdle{assertEquals(0,com.sadique.dailyledger.security.SensitiveEditors.active.value)}
    }
    private fun screenshot(name:String){compose.waitForIdle();val fd=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png");android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
}
