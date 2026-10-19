package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.PaymentRowWithProofs
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class PaymentProofUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun paidRecordExpandsProofsAndDeleteCanBeCancelledWithoutEditingPayment()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try {
   val repo=LedgerRepository(db,"proof-ui");repo.saveLoan("LENT","Test",10000,null,"")
   repo.addLoanPayment(db.ledgerDao().loansNow("proof-ui").single().id,500,"2026-10-19","")
   val id=db.ledgerDao().loanPaymentsNow("proof-ui").single().id
   repo.savePaymentProofData(id,"loan","%PDF-1.7 test receipt".toByteArray())
   compose.setContent { DailyLedgerTheme("LIGHT") { PaymentRowWithProofs(id,"loan",repo) { androidx.compose.material3.Text("Test payment") } } }
   compose.onNodeWithText("Attach").assertDoesNotExist()
   compose.onNodeWithContentDescription("Payment proofs").performClick()
   compose.waitUntil(5000){compose.onAllNodesWithContentDescription("View proof").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Attach").assertExists()
   compose.onNodeWithContentDescription("Delete proof").performClick()
   compose.onNodeWithText("Delete this proof?").assertExists()
   compose.onNodeWithText("Cancel").performClick()
   compose.onNodeWithContentDescription("View proof").assertExists()
   compose.waitForIdle()
   val fd=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-payment-proofs.png")
   android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}
   org.junit.Assert.assertEquals(500,db.ledgerDao().loanPaymentsNow("proof-ui").single().amountMinor.toInt())
  }finally{db.close()}
 }
}
