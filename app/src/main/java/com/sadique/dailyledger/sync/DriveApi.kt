package com.sadique.dailyledger.sync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

data class DriveBackupInfo(val modifiedTime: String, val version: String, internal val fileId: String)
data class DriveBackupFile(val info: DriveBackupInfo, val data: ByteArray)

class DriveBackupConflictException(
    message: String = "Automatic Drive backup stopped to protect the existing backup. Restore the latest copy, or choose Back up now to replace it intentionally."
) : IllegalStateException(message)

internal fun driveBackupHasConflict(
    expectedVersion: String,
    remoteVersion: String?,
    hasLocalRecords: Boolean = true,
): Boolean = if (remoteVersion == null) expectedVersion.isNotBlank()
else !hasLocalRecords || expectedVersion.isBlank() || remoteVersion != expectedVersion

internal fun driveBackupFilename(ownerId: String): String {
    require(ownerId.isNotBlank()) { "A signed-in account is required for backup." }
    val digest = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
    val accountKey = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "daily-ledger-backup-$accountKey.json"
}

internal fun driveAuthorizationHeader(accessToken: String): String {
    require(accessToken.isNotBlank()) { "Google Drive authorization returned an empty access token." }
    return "Bearer $accessToken"
}

class DriveApi(private val token: String, ownerId: String) {
    private val name = driveBackupFilename(ownerId)

    private fun conn(url: String, method: String = "GET", type: String? = null) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", driveAuthorizationHeader(token))
            type?.let { setRequestProperty("Content-Type", it) }
        }

    private fun body(connection: HttpURLConnection): ByteArray {
        val code = connection.responseCode
        val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.use { it.readBytes() } ?: byteArrayOf()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("error")?.optString("message").orEmpty() }
                .getOrDefault("")
            throw IOException("Google Drive request failed (HTTP $code)${if (detail.isBlank()) "." else ": $detail"}")
        }
        return bytes
    }

    fun findBackup(): DriveBackupInfo? {
        val query = URLEncoder.encode("name = '$name' and trashed = false", Charsets.UTF_8.name())
        val connection = conn(
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime,version)&orderBy=modifiedTime%20desc&pageSize=1",
        )
        val files = JSONObject(String(body(connection), Charsets.UTF_8)).getJSONArray("files")
        if (files.length() == 0) return null
        val file = files.getJSONObject(0)
        val version = file.opt("version")?.toString().orEmpty()
        require(version.isNotBlank()) { "Google Drive did not return a backup version." }
        return DriveBackupInfo(file.optString("modifiedTime"), version, file.getString("id"))
    }

    fun upload(data: ByteArray, existing: DriveBackupInfo? = findBackup()) {
        val id = existing?.fileId ?: create()
        // HttpURLConnection rejects PATCH, so use Google's method-override header.
        val connection = conn(
            "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media",
            "POST",
            "application/json; charset=UTF-8",
        )
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connection.doOutput = true
        connection.outputStream.use { it.write(data) }
        body(connection)
    }

    fun download(): DriveBackupFile? {
        val info = findBackup() ?: return null
        return DriveBackupFile(info, body(conn("https://www.googleapis.com/drive/v3/files/${info.fileId}?alt=media")))
    }

    private fun create(): String {
        val connection = conn(
            "https://www.googleapis.com/drive/v3/files?fields=id",
            "POST",
            "application/json; charset=UTF-8",
        )
        connection.doOutput = true
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put("appDataFolder"))
        connection.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
        return JSONObject(String(body(connection), Charsets.UTF_8)).getString("id")
    }
}
