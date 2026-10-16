package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Lets Android's system contact picker grant one-time access to a selected phone row.
 * No broad READ_CONTACTS permission is requested.
 */
@Composable
internal fun ContactPickerButton(onPicked: (name: String, phone: String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    val phone = if (phoneIndex >= 0) cursor.getString(phoneIndex).orEmpty() else ""
                    if (phone.isNotBlank()) onPicked(name, phone)
                }
            }
        }
    }
    OutlinedButton(onClick = {
        try { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
        catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "Contact picker is unavailable. Enter the number manually.", android.widget.Toast.LENGTH_SHORT).show() }
    }) {
        Icon(Icons.Outlined.ContactPhone, contentDescription = null)
        Text(" Pick contact")
    }
}
