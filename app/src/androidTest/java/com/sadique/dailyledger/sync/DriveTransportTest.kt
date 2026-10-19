package com.sadique.dailyledger.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class DriveTransportTest {
    private class Response(val text: String, val code: Int = 200, val etag: String = "tag")
    private class Connection(url: URL, private val response: Response) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = response.code
        override fun getInputStream() = ByteArrayInputStream(response.text.toByteArray())
        override fun getErrorStream() = inputStream
        override fun getOutputStream() = sent
        override fun getHeaderField(name: String?) = if (name == "ETag") response.etag else null
    }
    private class Server(vararg replies: Response) {
        val replies = ArrayDeque(replies.toList())
        val requests = mutableListOf<Connection>()
        val api = DriveApi("token", "owner") { url ->
            check(this.replies.isNotEmpty()) { "Unexpected request: $url" }
            Connection(url, this.replies.removeFirst()).also { requests += it }
        }
    }
    private fun metadata(bytes: ByteArray, version: String = "1", revision: String = "r1") = JSONObject()
        .put("id", "file").put("version", version).put("headRevisionId", revision)
        .put("md5Checksum", driveDigest(bytes)).put("modifiedTime", "2026-10-17T00:00:00Z").toString()

    @Test fun semanticHashIgnoresExportTimeAndObjectOrderButNotRecords() {
        val first = """{"ownerId":"a","exportedAt":1,"transactions":[{"amount":100,"note":"Milk"}]}"""
        val same = """{"transactions":[{"note":"Milk","amount":100}],"exportedAt":999,"ownerId":"a"}"""
        assertEquals(driveContentHash(first), driveContentHash(same))
        assertNotEquals(driveContentHash(first), driveContentHash(same.replace("100", "200")))
    }
    @Test fun firstUploadIsAtomicAndVerifiedAgainstReturnedAndFetchedChecksum() {
        val bytes = "{\"records\":[1,2]}".toByteArray()
        val server = Server(Response(metadata(bytes)), Response(metadata(bytes)))
        val result = server.api.upload(bytes, null)
        assertEquals(driveDigest(bytes), result.checksum)
        assertEquals(2, server.requests.size)
        val request = server.requests.first()
        assertTrue(request.url.query.contains("uploadType=multipart"))
        assertTrue(request.url.query.contains("keepRevisionForever=false"))
        assertTrue(request.sent.toString().contains("appDataFolder"))
        assertTrue(request.sent.toString().contains(String(bytes)))
        assertEquals("Bearer token", request.getRequestProperty("Authorization"))
        assertTrue(server.requests.last().url.path.endsWith("/files/file"))
    }
    @Test fun checksumMismatchIsNeverReportedAsSuccessfulUpload() {
        val server = Server(Response(metadata("different data".toByteArray())))
        assertThrows(IOException::class.java) { server.api.upload("real ledger".toByteArray(), null) }
        assertEquals(1, server.requests.size)
    }
    @Test fun badDownloadedChecksumCannotReachRestore() {
        val server = Server(Response("damaged"))
        assertThrows(IOException::class.java) { server.api.download(DriveBackupInfo("", "1", "file", driveDigest("good".toByteArray()))) }
    }
    @Test fun backupChangingDuringDownloadIsRejected() {
        val bytes = "good".toByteArray()
        val server = Server(Response("good"), Response(metadata(bytes, "2", "r2")))
        assertThrows(DriveBackupConflictException::class.java) { server.api.download(DriveBackupInfo("", "1", "file", driveDigest(bytes))) }
    }
    @Test fun conditionalReplacementUsesEtagWithoutPinningOldCopies() {
        val old = "old".toByteArray(); val next = "next".toByteArray()
        val server = Server(Response(metadata(old)), Response(metadata(old)),
            Response(metadata(next, "2", "r2")), Response(metadata(next, "2", "r2")))
        server.api.upload(next, DriveBackupInfo("", "1", "file", driveDigest(old), "r1"))
        assertEquals("PATCH", server.requests[2].getRequestProperty("X-HTTP-Method-Override"))
        assertEquals("tag", server.requests[2].getRequestProperty("If-Match"))
        assertEquals("next", server.requests[2].sent.toString())
    }
    @Test fun staleDeviceCannotWriteAndDuplicateBackupsAreNotArbitrarilyChosen() {
        val server = Server(Response(metadata("old".toByteArray(), "2")))
        assertThrows(DriveBackupConflictException::class.java) { server.api.upload("next".toByteArray(), DriveBackupInfo("", "1", "file")) }
        assertTrue(server.requests.all { it.requestMethod == "GET" })
        val duplicate = Server(Response("""{"files":[{"id":"one"},{"id":"two"}]}"""))
        assertThrows(DriveBackupConflictException::class.java) { duplicate.api.findBackup() }
    }
    @Test fun preconditionFailureAndQuotaReasonArePreserved() {
        val changed = Server(Response("{}", 412))
        assertThrows(DriveBackupConflictException::class.java) { changed.api.findBackup() }
        val quota = Server(Response("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""", 403))
        val error = assertThrows(DriveHttpException::class.java) { quota.api.findBackup() }
        assertEquals("storageQuotaExceeded", error.reason)
        assertFalse(error.retryable)
    }
}
