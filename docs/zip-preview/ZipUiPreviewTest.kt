package com.sadique.dailyledger.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.*
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ZipUiPreviewTest {
 @get:Rule val compose = createComposeRule()
 @Composable private fun Chrome(mode:String, tab:Int=0, content:@Composable ()->Unit) {
  DailyLedgerTheme(mode) {
   Scaffold(topBar={AppTopBar(tab,"Your daily money companion",false,"Back","back",{},{},{},{})},bottomBar={AppBottomBar(tab,{})}) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)){content()}
   }
  }
 }
 private fun home(mode:String) {
  val m=YearMonth.now().toString()
  val tx=listOf(
   TransactionEntity("s","preview","INCOME",10000000,"Salary","Monthly salary","$m-01",1,1),
   TransactionEntity("a","preview","EXPENSE",1500000,"Groceries","Monthly groceries","$m-02",2,2),
   TransactionEntity("b","preview","EXPENSE",800000,"Rent","House rent","$m-03",3,3),
   TransactionEntity("c","preview","EXPENSE",200000,"Milk","Milk","$m-04",4,4))
  compose.setContent { Chrome(mode) {
   DashboardScreen(tx=tx,loans=emptyList(),lp=emptyList(),committees=emptyList(),cp=emptyList(),receipts=emptyList(),savings=emptyList(),
    userName="Sadique",weatherEnabled=false,weatherCity="",weatherTemperature="",weatherCondition="",onAddSalary={},onAddExpense={},onOpenSavings={},onOpenKameti={})
  }}
  capture("zip-original-home-${mode.lowercase()}")
  compose.onNodeWithTag("dashboard-list").performScrollToKey("monthly-totals")
  capture("zip-original-totals-${mode.lowercase()}")
 }
 @Test fun lightHome(){home("LIGHT")}
 @Test fun darkHome(){home("DARK")}
 @Test fun more(){compose.setContent{Chrome("LIGHT",6){MoreScreen({},{},{},{},{},{})}};capture("zip-original-more")}
 private fun capture(name:String){compose.waitForIdle();Thread.sleep(1000);val d=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png");ParcelFileDescriptor.AutoCloseInputStream(d).use{it.readBytes()}}
}
