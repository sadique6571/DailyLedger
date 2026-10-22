package com.sadique.dailyledger.sync

import com.sadique.dailyledger.ai.GeminiGateway
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport for the owner's Cloudflare Worker backup store.
 * The Worker encrypts every copy server-side (AES-256-GCM, key held in the Worker),
 * so no key or passphrase is ever needed on the phone. Blocking calls - use on IO.
 */
object CfBackup {
    private const val BASE = "https://dailyledger-ai.sadique6571.workers.dev"

    fun put(ownerId: String, json: String) {
        val connection = (URL("$BASE/backup?user=$ownerId").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-app-token", GeminiGateway.APP_TOKEN)
        }
        try {
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw DriveHttpException(code, "")
        } finally {
            connection.disconnect()
        }
    }

    /** Returns the decrypted snapshot JSON, or null when no copy exists in that slot. */
    fun get(ownerId: String, slot: String = "latest"): String? {
        val connection = (URL("$BASE/backup?user=$ownerId&slot=$slot").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("x-app-token", GeminiGateway.APP_TOKEN)
        }
        try {
            return when (val code = connection.responseCode) {
                404 -> null
                in 200..299 -> connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                else -> throw DriveHttpException(code, "")
            }
        } finally {
            connection.disconnect()
        }
    }
}
