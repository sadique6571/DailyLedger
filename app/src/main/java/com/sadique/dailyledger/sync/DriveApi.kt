package com.sadique.dailyledger.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class DriveBackupInfo(
    val modifiedTime: String, val version: String, internal val fileId: String,
    val checksum: String = "", val headRevision: String = "", internal val etag: String = "",
)
data class DriveBackupFile(val info: DriveBackupInfo, val data: ByteArray)
data class DriveRevision(val id: String, val modifiedTime: String, val checksum: String, val kept: Boolean)

class DriveBackupConflictException(
    message: String = "Automatic Drive backup stopped to protect the existing backup. Restore the latest copy, or choose Back up now to replace it intentionally."
) : IllegalStateException(message)

class DriveHttpException(val code: Int, val reason: String) : IOException("Google Drive request failed (HTTP $code).") {
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
internal fun driveAuthorizationHeader(accessToken: String): String {
    require(accessToken.isNotBlank()) { "Google Drive authorization returned an empty access token." }
    return "Bearer $accessToken"
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

internal class DriveApi(
    private val token: String, ownerId: String,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : DriveStorage {
    private val name = driveBackupFilename(ownerId)
    private val fields = "id,modifiedTime,version,md5Checksum,headRevisionId"
    private fun escape(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun conn(url: String, method: String = "GET", type: String? = null) = open(URL(url)).apply {
        requestMethod = method; connectTimeout = 20_000; readTimeout = 30_000
        setRequestProperty("Authorization", driveAuthorizationHeader(token))
        type?.let { setRequestProperty("Content-Type", it) }
    }
    private fun body(connection: HttpURLConnection): ByteArray = try {
        val code = connection.responseCode
        val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: byteArrayOf()
        if (code == 412) throw DriveBackupConflictException()
        if (code !in 200..299) {
            val reason = runCatching { JSONObject(String(bytes, Charsets.UTF_8)).getJSONObject("error")
                .optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty() }.getOrDefault("")
            throw DriveHttpException(code, reason)
        }
        bytes
    } finally { connection.disconnect() }
    private fun info(file: JSONObject, etag: String = ""): DriveBackupInfo {
        val version = file.opt("version")?.toString().orEmpty()
        require(version.isNotBlank()) { "Google Drive did not return a backup version." }
        return DriveBackupInfo(file.optString("modifiedTime"), version, file.getString("id"),
            file.optString("md5Checksum"), file.optString("headRevisionId"), etag)
    }
    private fun metadata(id: String): DriveBackupInfo {
        val connection = conn("https://www.googleapis.com/drive/v3/files/${escape(id)}?fields=$fields")
        val bytes = body(connection)
        return info(JSONObject(String(bytes, Charsets.UTF_8)), connection.getHeaderField("ETag").orEmpty())
    }
    override fun findBackup(): DriveBackupInfo? {
        val query = escape("name = '$name' and trashed = false")
        val files = JSONObject(String(body(conn("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id)&orderBy=modifiedTime%20desc&pageSize=2")), Charsets.UTF_8)).getJSONArray("files")
        if (files.length() == 0) return null
        if (files.length() > 1) throw DriveBackupConflictException("More than one backup was found. Automatic backup paused to protect both copies. Contact support before replacing either copy.")
        return metadata(files.getJSONObject(0).getString("id"))
    }
    override fun upload(data: ByteArray, existing: DriveBackupInfo?): DriveBackupInfo {
        var current = existing
        if (existing != null) {
            val latest = metadata(existing.fileId)
            if (latest.version != existing.version) throw DriveBackupConflictException()
            current = metadata(existing.fileId)
            if (current.headRevision != latest.headRevision || current.checksum != latest.checksum) throw DriveBackupConflictException()
        }
        val connection: HttpURLConnection
        val payload: ByteArray
        if (current == null) {
            // Create metadata + content atomically; a failed first upload must not leave an empty backup.
            val boundary = "ledger-${UUID.randomUUID()}"
            connection = conn("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&keepRevisionForever=false&fields=$fields", "POST", "multipart/related; boundary=$boundary")
            val meta = JSONObject().put("name", name).put("mimeType", "application/json").put("parents", JSONArray().put("appDataFolder"))
            payload = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n").toByteArray() + data + "\r\n--$boundary--\r\n".toByteArray()
        } else {
            connection = conn("https://www.googleapis.com/upload/drive/v3/files/${escape(current.fileId)}?uploadType=media&keepRevisionForever=false&fields=$fields", "POST", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            if (current.etag.isNotBlank()) connection.setRequestProperty("If-Match", current.etag)
            payload = data
        }
        connection.doOutput = true
        connection.outputStream.use { it.write(payload) }
        val uploaded = info(JSONObject(String(body(connection), Charsets.UTF_8)))
        if (uploaded.checksum != driveDigest(data)) throw IOException("Google Drive backup verification failed. The upload will be checked again.")
        val verified = metadata(uploaded.fileId)
        if (verified.version != uploaded.version || verified.checksum != uploaded.checksum) throw DriveBackupConflictException()
        return verified
    }
    override fun download(info: DriveBackupInfo): DriveBackupFile {
        val bytes = body(conn("https://www.googleapis.com/drive/v3/files/${escape(info.fileId)}?alt=media"))
        if (info.checksum.isBlank() || driveDigest(bytes) != info.checksum) throw IOException("Backup checksum did not match. Local records were not changed.")
        val after = metadata(info.fileId)
        if (after.version != info.version || after.checksum != info.checksum) throw DriveBackupConflictException()
        return DriveBackupFile(info, bytes)
    }
    private fun pin(fileId: String, revisionId: String) {
        val connection = conn("https://www.googleapis.com/drive/v3/files/${escape(fileId)}/revisions/${escape(revisionId)}", "POST", "application/json")
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connection.doOutput = true
        connection.outputStream.use { it.write("{\"keepForever\":true}".toByteArray()) }
        body(connection)
    }
    override fun revisions(info: DriveBackupInfo): List<DriveRevision> {
        val result = mutableListOf<DriveRevision>()
        var next = ""
        do {
            val root = JSONObject(String(body(conn("https://www.googleapis.com/drive/v3/files/${escape(info.fileId)}/revisions?pageSize=200&fields=nextPageToken,revisions(id,modifiedTime,md5Checksum,keepForever)${if (next.isBlank()) "" else "&pageToken=${escape(next)}"}")), Charsets.UTF_8))
            val rows = root.optJSONArray("revisions") ?: JSONArray()
            for (i in 0 until rows.length()) rows.getJSONObject(i).let {
                result += DriveRevision(it.getString("id"), it.optString("modifiedTime"), it.optString("md5Checksum"), it.optBoolean("keepForever"))
            }
            next = root.optString("nextPageToken")
        } while (next.isNotBlank())
        return result.sortedByDescending { it.modifiedTime }
    }
    override fun downloadRevision(info: DriveBackupInfo, revision: DriveRevision): DriveBackupFile {
        val selected = revisions(info).singleOrNull { it.id == revision.id } ?: error("This backup copy is no longer available.")
        pin(info.fileId, selected.id)
        val bytes = body(conn("https://www.googleapis.com/drive/v3/files/${escape(info.fileId)}/revisions/${escape(selected.id)}?alt=media"))
        if (selected.checksum.isBlank() || driveDigest(bytes) != selected.checksum) throw IOException("Backup checksum did not match. Local records were not changed.")
        return DriveBackupFile(info, bytes)
    }
    override fun trimHistory(info: DriveBackupInfo) {
        revisionsToRemove(revisions(info), info.headRevision).forEach {
            if (!it.kept) pin(info.fileId, it.id)
            body(conn("https://www.googleapis.com/drive/v3/files/${escape(info.fileId)}/revisions/${escape(it.id)}", "DELETE"))
        }
    }
}
