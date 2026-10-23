package com.sadique.dailyledger.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

data class DriveBackupInfo(
    val modifiedTime: String, val version: String, internal val fileId: String,
    val checksum: String = "", val headRevision: String = "", internal val etag: String = "",
)
data class DriveBackupFile(val info: DriveBackupInfo, val data: ByteArray)
data class DriveRevision(val id: String, val modifiedTime: String, val checksum: String, val kept: Boolean)

class DriveBackupConflictException(
    message: String = "Automatic backup stopped to protect the existing backup. Restore the latest copy, or choose Back up now to replace it intentionally."
) : IllegalStateException(message)

class DriveHttpException(val code: Int, val reason: String) : IOException("Cloud backup request failed (HTTP $code).") {
    val retryable get() = code == 408 || code == 429 || code >= 500 ||
        (code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "backendError"))
}

internal fun driveBackupHasConflict(expectedVersion: String, remoteVersion: String?, hasLocalRecords: Boolean = true): Boolean =
    if (remoteVersion == null) expectedVersion.isNotBlank()
    else !hasLocalRecords || expectedVersion.isBlank() || remoteVersion != expectedVersion

internal fun driveBackupFilename(ownerId: String): String {
    require(ownerId.isNotBlank()) { "A signed-in account is required for backup." }
    return "daily-ledger-backup-${driveDigest(ownerId.toByteArray(), "SHA-256")}.json"
}

internal fun driveDigest(bytes: ByteArray, algorithm: String = "MD5"): String =
    MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Ignore export time and JSON object ordering; an unchanged ledger must not consume revisions. */
internal fun driveContentHash(json: String): String {
    fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    val root = JSONObject(json).apply { remove("exportedAt") }
    return driveDigest(canonical(root).toByteArray(Charsets.UTF_8), "SHA-256")
}

/** Keep only the current verified head. Cleanup is called only after a successful upload. */
internal fun revisionsToRemove(revisions: List<DriveRevision>, head: String): List<DriveRevision> =
    if (head.isBlank()) emptyList() else revisions.filter { it.id != head }

internal interface DriveStorage {
    fun findBackup(): DriveBackupInfo?
    fun upload(data: ByteArray, existing: DriveBackupInfo?): DriveBackupInfo
    fun download(info: DriveBackupInfo): DriveBackupFile
    fun revisions(info: DriveBackupInfo): List<DriveRevision>
    fun downloadRevision(info: DriveBackupInfo, revision: DriveRevision): DriveBackupFile
    fun trimHistory(info: DriveBackupInfo)
}

/**
 * Backup storage on the owner's Cloudflare Worker. The Worker encrypts every copy
 * server-side (AES-256-GCM) and keeps three slots: latest, prev1, prev2.
 * The snapshot's own exportedAt field serves as the copy's timestamp.
 */
internal class CfStorage(private val ownerId: String) : DriveStorage {
    private fun infoFor(slot: String, json: String): DriveBackupInfo {
        val exportedAt = runCatching { JSONObject(json).optString("exportedAt") }.getOrDefault("")
        val hash = driveContentHash(json)
        return DriveBackupInfo(exportedAt, hash, "cloudflare", hash, "latest")
    }

    override fun findBackup(): DriveBackupInfo? {
        val json = CfBackup.get(ownerId, "latest") ?: return null
        return infoFor("latest", json)
    }

    override fun upload(data: ByteArray, existing: DriveBackupInfo?): DriveBackupInfo {
        val json = String(data, Charsets.UTF_8)
        CfBackup.put(ownerId, json)
        return infoFor("latest", json)
    }

    override fun download(info: DriveBackupInfo): DriveBackupFile {
        val json = CfBackup.get(ownerId, "latest") ?: throw IOException("No cloud backup was found.")
        return DriveBackupFile(infoFor("latest", json), json.toByteArray(Charsets.UTF_8))
    }

    override fun revisions(info: DriveBackupInfo): List<DriveRevision> =
        listOf("latest", "prev1", "prev2").mapNotNull { slot ->
            val json = CfBackup.get(ownerId, slot) ?: return@mapNotNull null
            val exportedAt = runCatching { JSONObject(json).optString("exportedAt") }.getOrDefault("")
            DriveRevision(slot, exportedAt, driveContentHash(json), true)
        }

    override fun downloadRevision(info: DriveBackupInfo, revision: DriveRevision): DriveBackupFile {
        val json = CfBackup.get(ownerId, revision.id) ?: throw IOException("That backup copy is no longer available.")
        return DriveBackupFile(infoFor(revision.id, json), json.toByteArray(Charsets.UTF_8))
    }

    override fun trimHistory(info: DriveBackupInfo) {
        // The Worker always keeps exactly the latest three copies; nothing to trim.
    }
}
