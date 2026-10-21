package com.sadique.dailyledger.ui

import android.content.Context
import android.content.Intent

/** Opens the system share sheet with plain text (WhatsApp, SMS, email and more). */
fun shareText(context: Context, chooserTitle: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
