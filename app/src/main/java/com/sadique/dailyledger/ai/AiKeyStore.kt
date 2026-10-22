package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.security.SecureSecretStore

/** The user's own personal AI API key (any supported provider), encrypted on this device. It is sent only to the chosen provider (or the user's own proxy). */
class AiKeyStore(context: Context, owner: String) {
    private val store = SecureSecretStore(context, "gemini-apikey:" + owner)

    var key: String
        get() = runCatching { store.loadBytes()?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
        set(value) {
            val cleaned = value.trim()
            if (cleaned.isBlank()) store.clearBytes() else store.saveBytes(cleaned.toByteArray(Charsets.UTF_8))
        }
}
