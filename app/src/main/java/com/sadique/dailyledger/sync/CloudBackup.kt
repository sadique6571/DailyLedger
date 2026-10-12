package com.sadique.dailyledger.sync

import android.content.Context
import android.util.Base64
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.auth.awaitResult
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.security.BackupCrypto
import com.sadique.dailyledger.security.KeyManager
import com.sadique.dailyledger.security.SecureSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.crypto.BadPaddingException
import kotlin.coroutines.cancellation.CancellationException

class BackupConflictException : IllegalStateException(
    "A different backup exists for this account. Restore it or explicitly replace it with this phone's data."
)

class MigrationFailedException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

data class BackupInfo(
    val revision: String,
    val formatVersion: Long,
    val updatedAt: Long,
)

class CloudBackup(private val context: Context, private val ownerId: String) {
    private val repo = LedgerRepository(AppDatabase.get(context), ownerId)
    private val settings = SettingsStore(context)
    private val keyManager = KeyManager(context, ownerId)
    private val secrets = SecureSecretStore(context, ownerId)
    private val firestore get() = FirebaseRuntime.firestore(context)
    private val storage get() = FirebaseRuntime.storage(context)
    private val document get() = firestore.collection("users").document(ownerId).collection("backups").document("latest")

    private fun checkAccount() {
        check(FirebaseRuntime.auth(context).currentUser?.uid == ownerId) { "Please sign in to this account again." }
    }

    private fun revision(snapshot: DocumentSnapshot): String? {
        if (!snapshot.exists()) return null
        val ver = snapshot.getLong("formatVersion") ?: 1L
        require(ver in 1L..4L) { "Unsupported cloud backup format version: $ver" }
        return snapshot.getString("revision") ?: throw IllegalStateException("Cloud backup revision missing")
    }

    private fun validPath(path: String, revision: String) =
        revision.matches(Regex("[a-f0-9-]{36}")) && path == "users/$ownerId/backups/$revision.enc"
    private suspend fun localJson() = JSONObject(repo.exportJson()).apply { remove("exportedAt") }.toString()

    /** Read-only discovery for the fresh-install Restore / Skip prompt. */
    suspend fun backupInfo(): BackupInfo? = withContext(Dispatchers.IO) {
        checkAccount()
        FirebaseRuntime.prepareBackup(context)
        val snapshot = document.get(Source.SERVER).awaitResult()
        checkAccount()
        if (!snapshot.exists()) return@withContext null
        val rev = requireNotNull(revision(snapshot))
        BackupInfo(
            revision = rev,
            formatVersion = snapshot.getLong("formatVersion") ?: 1L,
            updatedAt = snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        )
    }

    suspend fun sync(): String = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            FirebaseRuntime.prepareBackup(context)
            val remote = document.get(Source.SERVER).awaitResult()
            checkAccount()
            val state = settings.cloudState(ownerId)
            if (!repo.hasRecords() && remote.exists()) {
                settings.cloudStatus(ownerId, "Cloud backup found while this phone has no records. Restore it, or explicitly replace it from Backup details; no data was uploaded.")
                return@withContext "Cloud backup found. Restore it or explicitly replace it from Backup details; no data was uploaded."
            }
            val json = localJson()
            val format = remote.getLong("formatVersion") ?: 1L
            val remoteRevision = revision(remote)
            if (remote.exists() && format == 3L) {
                // Do not call paid KMS just to compare a legacy snapshot. Only migrate it automatically
                // when this phone's last-known revision still matches; otherwise require an explicit choice.
                if (!state.ready || state.revision != remoteRevision) throw BackupConflictException()
                return@withContext writeFirestoreSnapshot(json, remoteRevision)
            }
            val localPlainHash = if (remote.exists() && format == 4L) {
                val passphrase = secrets.loadPassphrase() ?: error("Set the original backup encryption passphrase to sync this account.")
                val chars = passphrase.toCharArray()
                try { BackupCrypto.passphraseFingerprint(json, chars, ownerId) }
                finally { chars.fill('\u0000') }
            } else SnapshotCodec.hash(json)

            // V2 stores a canonical hash for change detection only; ciphertext is separately authenticated.
            val remotePlainHash = if (remote.exists() && remote.getLong("formatVersion") == 2L) {
                remote.getString("plainHash")
            } else if (remote.exists() && (remote.getLong("formatVersion") ?: 1L) in listOf(1L, 4L)) {
                remote.getString("contentHash")
            } else null

            when (BackupPolicy.decide(
                state.ready, repo.hasRecords(), state.revision,
                remoteRevision, localPlainHash, remotePlainHash
            )) {
                BackupPolicy.Decision.RESTORE -> {
                    // Never auto-restore on a fresh install. UI must ask Restore / Skip first.
                    settings.cloudStatus(ownerId, "Backup found for this account. Choose Restore or Skip in the app.")
                    "Backup found. Waiting for your restore choice."
                }
                BackupPolicy.Decision.UNCHANGED -> {
                    if (format < 4L) writeFirestoreSnapshot(json, remoteRevision)
                    else {
                        settings.cloudComplete(ownerId, requireNotNull(remoteRevision), "Encrypted Spark backup is up to date.")
                        "Encrypted Spark backup is up to date."
                    }
                }
                BackupPolicy.Decision.UPLOAD -> writeFirestoreSnapshot(json, remoteRevision)
                BackupPolicy.Decision.CONFLICT -> throw BackupConflictException()
            }
        }
    }

    suspend fun restore(expectedRevision: String? = null): String = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            FirebaseRuntime.prepareBackup(context)
            val snapshot = document.get(Source.SERVER).awaitResult()
            check(snapshot.exists()) { "No backup was found for this account." }
            if (expectedRevision != null && revision(snapshot) != expectedRevision) throw BackupConflictException()
            restoreSnapshot(snapshot)
        }
    }

    suspend fun replaceWithLocal(): String = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            FirebaseRuntime.prepareBackup(context)
            val remote = document.get(Source.SERVER).awaitResult()
            val json = localJson()
            val result = writeFirestoreSnapshot(json, revision(remote))
            settings.setSkippedRestoreRevision(ownerId, "")
            result
        }
    }

    suspend fun deleteBackup(): String = BackupLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount()
            FirebaseRuntime.prepareBackup(context)
            val remote = document.get(Source.SERVER).awaitResult()
            if (remote.exists()) {
                val expected = revision(remote)
                settings.setCloudEnabled(ownerId, false)
                firestore.runTransaction { transaction ->
                    checkAccount()
                    if (revision(transaction.get(document)) != expected) throw BackupConflictException()
                    transaction.delete(document)
                }.awaitResult()
            }
            keyManager.clearLocalCache()
            settings.setCloudEnabled(ownerId, false)
            settings.resetCloudState(ownerId, "Cloud backup deleted. Automatic backup is off until you enable it again.")
            "Cloud backup was deleted and automatic backup was turned off."
        }
    }

    private suspend fun restoreSnapshot(snapshot: DocumentSnapshot): String {
        checkAccount()
        val formatVersion = snapshot.getLong("formatVersion") ?: 1L
        val rev = requireNotNull(revision(snapshot))

        val json: String = if (formatVersion == 4L) {
            val payload = requireNotNull(snapshot.getString("payload")) { "Encrypted backup payload is missing." }
            require(payload.length <= MAX_FIRESTORE_PAYLOAD_CHARS) { "Cloud backup payload is too large." }
            val passphrase = secrets.loadPassphrase() ?: error("Enter the backup encryption passphrase saved on the original phone.")
            val encrypted = try { Base64.decode(payload, Base64.NO_WRAP) }
            catch (_: IllegalArgumentException) { error("Cloud backup payload is invalid.") }
            val passphraseChars = passphrase.toCharArray()
            val plain = try {
                try { BackupCrypto.decrypt(encrypted, passphraseChars) }
                catch (e: javax.crypto.AEADBadTagException) {
                    throw IllegalStateException("The backup passphrase is incorrect or the cloud backup is damaged.", e)
                }
            } finally {
                encrypted.fill(0)
                passphraseChars.fill('\u0000')
            }
            try {
                val fingerprintPassphrase = passphrase.toCharArray()
                String(plain, Charsets.UTF_8).also {
                    val fingerprint = try { BackupCrypto.passphraseFingerprint(it, fingerprintPassphrase, ownerId) }
                    finally { fingerprintPassphrase.fill('\u0000') }
                    require(fingerprint == snapshot.getString("contentHash")) {
                        "Backup content verification failed."
                    }
                }
            } finally { plain.fill(0) }
        } else if (formatVersion in 2L..3L) {
            val storagePath = requireNotNull(snapshot.getString("storagePath")) { "Missing backup storage path" }
            require(validPath(storagePath, rev)) { "Invalid backup storage path." }
            val ivBase64 = requireNotNull(snapshot.getString("iv")) { "Missing encryption IV" }
            val expectedCipherHash = requireNotNull(snapshot.getString("contentHash")) { "Missing content hash" }

            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            require(iv.size == 12) { "Invalid encryption IV." }
            val ciphertext = storage.reference.child(storagePath)
                .getBytes(20 * 1024 * 1024L)
                .awaitResult()
            require(BackupCrypto.sha256Hex(ciphertext) == expectedCipherHash) { "Backup ciphertext integrity check failed." }

            val wrappedKey = requireNotNull(snapshot.getString("wrappedKey")) { "Missing wrapped backup key" }
            val plainBytes = decryptV2WithSingleKeyRefresh(ciphertext, iv, wrappedKey, rev, formatVersion)
            try {
                val decoded = String(plainBytes, Charsets.UTF_8)
                val dek = keyManager.getExistingDek(wrappedKey, rev, formatVersion)
                val fingerprint = if (formatVersion == 3L) BackupCrypto.plainFingerprint(decoded, dek, ownerId) else SnapshotCodec.hash(decoded)
                require(fingerprint == snapshot.getString("plainHash")) { "Backup content verification failed." }
                decoded
            } finally { plainBytes.fill(0) }
        } else {
            val payload = requireNotNull(snapshot.getString("payload")) { "Legacy backup payload missing" }
            val decoded = SnapshotCodec.decode(payload)
            val expectedHash = snapshot.getString("contentHash")
            if (expectedHash != null) {
                require(SnapshotCodec.hash(decoded) == expectedHash) { "Legacy backup integrity check failed." }
            }
            decoded
        }

        require(JSONObject(json).getString("ownerId") == ownerId) { "This backup belongs to a different account." }
        // LedgerRepository.importJson is a Room transaction, so replace-on-restore is atomic.
        checkAccount()
        repo.importJson(json)
        settings.cloudComplete(ownerId, rev, "Backup restored to this phone.")
        settings.setSkippedRestoreRevision(ownerId, "")

        if (formatVersion < 4L) {
            try {
                writeFirestoreSnapshot(json, rev)
                settings.setSkippedRestoreRevision(ownerId, "")
                return "Your account backup was restored and upgraded to encrypted Spark format."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                throw MigrationFailedException(
                    "Records were restored to this phone. Encrypted Spark backup upgrade is pending; check the passphrase and Firestore limit, then try Back up now. Your earlier remote backup was retained.",
                    e
                )
            }
        }

        settings.cloudComplete(ownerId, rev, "Your account backup was restored to this phone.")
        settings.setSkippedRestoreRevision(ownerId, "")
        return "Your account backup was restored to this phone."
    }

    /**
     * A cached key may be stale after another device rotates/recreates the backup.
     * Retry exactly once with a server-unwrapped current key. A second AEAD failure is treated as corruption/tamper.
     */
    private suspend fun decryptV2WithSingleKeyRefresh(ciphertext: ByteArray, iv: ByteArray, wrappedKey: String, revision: String, format: Long): ByteArray {
        val aad = if (format == 3L) BackupCrypto.snapshotAad(ownerId, revision) else byteArrayOf()
        val firstKey = keyManager.getExistingDek(wrappedKey, revision, format)
        return try {
            BackupCrypto.decryptPayload(ciphertext, iv, firstKey, aad)
        } catch (first: BadPaddingException) {
            val refreshed = keyManager.refreshExistingDek(wrappedKey, revision, format)
            try {
                BackupCrypto.decryptPayload(ciphertext, iv, refreshed, aad)
            } catch (second: BadPaddingException) {
                second.addSuppressed(first)
                throw IllegalStateException(
                    "Backup authentication failed after refreshing the account key. The backup may be damaged or changed.",
                    second
                )
            }
        }
    }

    private suspend fun writeFirestoreSnapshot(json: String, expectedRevision: String?): String {
        checkAccount()
        val remoteBefore = document.get(Source.SERVER).awaitResult()
        if (revision(remoteBefore) != expectedRevision) throw BackupConflictException()
        val passphrase = secrets.loadPassphrase() ?: error("Set and save a backup encryption passphrase before enabling cloud backup.")
        val plainBytes = json.toByteArray(Charsets.UTF_8)
        val passphraseChars = passphrase.toCharArray()
        val encrypted = try {
            BackupCrypto.encrypt(plainBytes, passphraseChars)
        } finally {
            plainBytes.fill(0)
            passphraseChars.fill('\u0000')
        }
        val payload = try { Base64.encodeToString(encrypted, Base64.NO_WRAP) }
        finally { encrypted.fill(0) }
        require(payload.length <= MAX_FIRESTORE_PAYLOAD_CHARS) {
            "Encrypted backup is too large for Firebase Spark Firestore (maximum 900 KB). Use the encrypted Google Drive backup or export instead."
        }
        val nextRevision = UUID.randomUUID().toString()
        val hashPassphraseChars = passphrase.toCharArray()
        val contentHash = try { BackupCrypto.passphraseFingerprint(json, hashPassphraseChars, ownerId) }
        finally { hashPassphraseChars.fill('\u0000') }
        firestore.runTransaction { transaction ->
            checkAccount()
            if (revision(transaction.get(document)) != expectedRevision) throw BackupConflictException()
            transaction.set(document, mapOf(
                "formatVersion" to 4L,
                "revision" to nextRevision,
                "payload" to payload,
                "contentHash" to contentHash,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
            nextRevision
        }.awaitResult()
        settings.cloudComplete(ownerId, nextRevision, "Encrypted backup saved to Firebase Spark Firestore.")
        return "Encrypted backup saved to your Firebase account."
    }

    private companion object {
        const val MAX_FIRESTORE_PAYLOAD_CHARS = 900_000
    }
}
