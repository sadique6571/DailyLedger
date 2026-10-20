package com.sadique.dailyledger.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import com.sadique.dailyledger.ui.screens.PaymentRowWithProofs
class NoAttachmentsUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun paymentsNoLongerExposeAttachmentActions(){
  compose.setContent{DailyLedgerTheme("LIGHT"){PaymentRowWithProofs("p","loan",null){androidx.compose.material3.Text("Payment record")}}}
  compose.onNodeWithText("Payment record").assertExists()
  compose.onNodeWithText("Attach").assertDoesNotExist()
  compose.onNodeWithContentDescription("Payment proofs").assertDoesNotExist()
 }
}
