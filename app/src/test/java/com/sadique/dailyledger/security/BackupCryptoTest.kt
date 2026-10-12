package com.sadique.dailyledger.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class BackupCryptoTest {

    @Test
    fun testDekGenerationIs256Bits() {
        val dek = BackupCrypto.generateDek()
        assertEquals("AES", dek.algorithm)
        assertEquals(32, dek.encoded.size) // 32 bytes = 256 bits
    }

    @Test
    fun testAesGcmEncryptDecryptRoundtrip() {
        val dek = BackupCrypto.generateDek()
        val originalText = "Daily Ledger Test Plaintext Data 123456"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        val encrypted = BackupCrypto.encryptPayload(plainBytes, dek)
        assertNotNull(encrypted.ciphertext)
        assertEquals(12, encrypted.iv.size)
        assertEquals(64, encrypted.contentHash.length) // SHA-256 hex string length

        val decryptedBytes = BackupCrypto.decryptPayload(encrypted.ciphertext, encrypted.iv, dek)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun passphraseBackupRoundTripsAndRejectsTheWrongPassphrase() {
        val original = """{"ownerId":"owner","transactions":[{"amountMinor":12500}]}"""
        val encrypted = BackupCrypto.encrypt(original.toByteArray(), "strong passphrase".toCharArray())

        assertEquals(original, String(BackupCrypto.decrypt(encrypted, "strong passphrase".toCharArray())))
        try {
            BackupCrypto.decrypt(encrypted, "incorrect passphrase".toCharArray())
            fail("Expected a wrong passphrase to fail authentication")
        } catch (_: javax.crypto.AEADBadTagException) {
            // AES-GCM rejects altered ciphertext and incorrect passphrases.
        }
    }

    @Test
    fun passphraseFingerprintIsStableButDoesNotExposePlaintextHash() {
        val json = """{"ownerId":"owner","balance":12500}"""
        val first = BackupCrypto.passphraseFingerprint(json, "strong passphrase".toCharArray(), "owner")
        val same = BackupCrypto.passphraseFingerprint(json, "strong passphrase".toCharArray(), "owner")
        val wrongPassphrase = BackupCrypto.passphraseFingerprint(json, "different phrase".toCharArray(), "owner")

        assertEquals(first, same)
        assertNotEquals(first, wrongPassphrase)
        assertNotEquals(BackupCrypto.sha256Hex(json.toByteArray()), first)
    }

    @Test
    fun testNoncesAreUniquePerEncryptionRun() {
        val dek = BackupCrypto.generateDek()
        val plainBytes = "Sample Data".toByteArray(Charsets.UTF_8)

        val enc1 = BackupCrypto.encryptPayload(plainBytes, dek)
        val enc2 = BackupCrypto.encryptPayload(plainBytes, dek)

        assertFalse(enc1.iv.contentEquals(enc2.iv))
    }

    @Test
    fun testWrongKeyFailsDecryption() {
        val dek1 = BackupCrypto.generateDek()
        val dek2 = BackupCrypto.generateDek()
        val plainBytes = "Secret Data".toByteArray(Charsets.UTF_8)

        val encrypted = BackupCrypto.encryptPayload(plainBytes, dek1)

        try {
            BackupCrypto.decryptPayload(encrypted.ciphertext, encrypted.iv, dek2)
            fail("Expected exception when decrypting with wrong DEK")
        } catch (_: Exception) {
            // Expected AES-GCM tag mismatch failure
        }
    }

    @Test
    fun testTamperedCiphertextFailsDecryption() {
        val dek = BackupCrypto.generateDek()
        val plainBytes = "Integrity Check Data".toByteArray(Charsets.UTF_8)

        val encrypted = BackupCrypto.encryptPayload(plainBytes, dek)
        val tamperedCiphertext = encrypted.ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        try {
            BackupCrypto.decryptPayload(tamperedCiphertext, encrypted.iv, dek)
            fail("Expected exception when decrypting tampered ciphertext")
        } catch (_: Exception) {
            // Expected AES-GCM tag mismatch failure
        }
    }
}
