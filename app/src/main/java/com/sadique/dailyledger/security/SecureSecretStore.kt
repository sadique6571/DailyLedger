package com.sadique.dailyledger.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small account-scoped secrets protected with Android Keystore. */
class SecureSecretStore(context: Context, ownerId: String) {
    private val prefs = context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
    private val suffix = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val alias = "daily_ledger_keystore"
    private val binding = ownerId.toByteArray(Charsets.UTF_8)
    private fun key(create: Boolean = true): SecretKey = synchronized(keyLock) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }
        check(create) { "Device encryption key is unavailable. Use your verified account backup to recover your records." }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        gen.generateKey()
    }
    fun clearBackupPassphrase() {
        prefs.edit().remove("iv:$suffix").remove("data:$suffix").remove("iv").remove("data").apply()
    }
    /** Strict, durable storage for database / account keys. Never silently regenerate on failure. */
    fun saveBytes(value: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(binding)
        }
        val blob = cipher.iv + cipher.doFinal(value)
        check(prefs.edit().putString("v2:$suffix", Base64.encodeToString(blob, Base64.NO_WRAP)).commit()) {
            "Could not save the device encryption key."
        }
    }
    fun loadBytes(): ByteArray? {
        val saved = prefs.getString("v2:$suffix", null) ?: return null
        val blob = Base64.decode(saved, Base64.NO_WRAP)
        require(blob.size >= 28) { "Device encryption key is damaged." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, blob, 0, 12)); updateAAD(binding)
        }
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
    fun clearBytes() { check(prefs.edit().remove("v2:$suffix").commit()) }
    companion object { private val keyLock = Any() }
}
