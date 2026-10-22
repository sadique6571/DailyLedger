package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.security.SecureSecretStore

/** The user's own free Gemini API key, encrypted on this device. It is sent only to Google (or the user's own proxy). */
class AiKeyStore(context: Context, owner: String) {
    private val store = SecureSecretStore(context, "gemini-apikey:" + owner)

    var key: String
        get() = runCatching { store.loadBytes()?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
        set(value) {
            val cleaned = value.trim()
            if (cleaned.isBlank()) store.clearBytes() else store.saveBytes(cleaned.toByteArray(Charsets.UTF_8))
        }
}
