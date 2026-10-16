package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.content.Context
import android.content.Intent
import android.widget.Toast

internal fun Context.openContactAction(intent: Intent) {
    try { startActivity(intent) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "No app is available for this action.", Toast.LENGTH_SHORT).show() }
    catch (_: SecurityException) { Toast.makeText(this, "Android could not open this action.", Toast.LENGTH_SHORT).show() }
}
