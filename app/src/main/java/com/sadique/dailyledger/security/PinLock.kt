package com.sadique.dailyledger.security

import java.security.MessageDigest

/**
 * Hashing for the optional app-lock PIN. The PIN itself is never stored —
 * only a salted SHA-256 hash in the settings store. This is a convenience
 * lock for the app UI; ledger data itself stays SQLCipher-encrypted.
 */
object PinLock {
    private const val SALT = "dailyledger-app-lock-v1:"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((SALT + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || pin.isEmpty()) return false
        return MessageDigest.isEqual(
            hash(pin).toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
    }

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }
}
