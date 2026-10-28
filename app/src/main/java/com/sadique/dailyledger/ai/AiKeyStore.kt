package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.security.SecureSecretStore

/**
 * The user's own personal AI API keys, encrypted on this device — one slot per
 * provider, so switching provider auto-fills that provider's saved key instead
 * of showing another provider's key. Keys are sent only to the chosen provider
 * (or the user's own proxy), and are never logged.
 *
 * The old single slot ("gemini-apikey:<owner>") belonged to whichever provider
 * was selected when the key was saved. migrateLegacy() moves it once, into the
 * currently selected provider's slot, and then clears it. It is idempotent: a
 * second call finds the old slot empty and does nothing.
 */
class AiKeyStore(private val context: Context, private val owner: String) {
    private val legacy = SecureSecretStore(context, "gemini-apikey:" + owner)
    private fun storeFor(providerId: String) =
        SecureSecretStore(context, "gemini-apikey:" + owner + ":" + providerId)

    private fun read(store: SecureSecretStore): String =
        runCatching { store.loadBytes()?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()

    fun keyFor(providerId: String): String = read(storeFor(providerId))

    fun setKey(providerId: String, value: String) {
        val cleaned = value.trim()
        val store = storeFor(providerId)
        if (cleaned.isBlank()) store.clearBytes() else store.saveBytes(cleaned.toByteArray(Charsets.UTF_8))
    }

    fun migrateLegacy(providerId: String) {
        val old = read(legacy)
        if (old.isBlank()) return
        if (read(storeFor(providerId)).isBlank()) {
            storeFor(providerId).saveBytes(old.toByteArray(Charsets.UTF_8))
        }
        legacy.clearBytes()
    }
}
