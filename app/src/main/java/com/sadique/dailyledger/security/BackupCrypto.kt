package com.sadique.dailyledger.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedResult(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val contentHash: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedResult
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv) && contentHash == other.contentHash
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + contentHash.hashCode()
        return result
    }
}

object BackupCrypto {
    private val magic = "DLB1".toByteArray()

    /** Generate a cryptographically random 256-bit AES Data Encryption Key (DEK). */
    fun generateDek(): SecretKeySpec {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return try { SecretKeySpec(keyBytes, "AES") } finally { keyBytes.fill(0) }
    }

    /** Encrypt raw bytes with AES-256-GCM using a fresh 12-byte IV. */
    fun encryptPayload(plain: ByteArray, dek: SecretKeySpec, aad: ByteArray = byteArrayOf()): EncryptedResult {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        require(dek.encoded.size == 32)
        cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plain)
        val hash = sha256Hex(ciphertext)
        return EncryptedResult(ciphertext, iv, hash)
    }

    /** Decrypt AES-256-GCM ciphertext using the given IV and DEK. */
    fun decryptPayload(ciphertext: ByteArray, iv: ByteArray, dek: SecretKeySpec, aad: ByteArray = byteArrayOf()): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        require(dek.encoded.size == 32 && iv.size == 12 && ciphertext.size >= 16)
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun snapshotAad(owner: String, revision: String): ByteArray =
        "DailyLedger/v3/${owner.length}:$owner/$revision".toByteArray(Charsets.UTF_8)
    fun plainFingerprint(json: String, dek: SecretKeySpec, owner: String): String =
        EnvelopeCrypto.fingerprint(dek.encoded, owner, json)

    /** Stable keyed digest for Firestore change detection; the passphrase-derived key prevents plain-data hashes. */
    fun passphraseFingerprint(json: String, passphrase: CharArray, owner: String): String {
        val context = "DailyLedger/FirestoreBackup/v4/$owner"
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(context.toByteArray(Charsets.UTF_8)).copyOf(16)
        val key = derivePassphraseKey(passphrase, salt)
        val keyBytes = key.encoded
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            mac.doFinal("$context\u0000$json".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } finally {
            keyBytes.fill(0)
            salt.fill(0)
        }
    }

    // Passphrase-based fallback methods retained for optional user Google Drive exports
    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derivePassphraseKey(passphrase, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return magic + salt + iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > 32 && blob.copyOfRange(0, 4).contentEquals(magic)) { "Invalid backup" }
        val salt = blob.copyOfRange(4, 20)
        val iv = blob.copyOfRange(20, 32)
        val data = blob.copyOfRange(32, blob.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derivePassphraseKey(passphrase, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data)
    }

    private fun derivePassphraseKey(p: CharArray, s: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(p, s, 180_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
}
