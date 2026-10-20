package com.sadique.dailyledger.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.sadique.dailyledger.data.LedgerRepository

/** Historical payment rows stay read-only; attaching receipts is no longer a feature. */
@Composable
fun PaymentRowWithProofs(paymentId:String,kind:String,repo:LedgerRepository?,rowContent:@Composable ()->Unit) {
    Box(Modifier.fillMaxWidth()) { rowContent() }
}
