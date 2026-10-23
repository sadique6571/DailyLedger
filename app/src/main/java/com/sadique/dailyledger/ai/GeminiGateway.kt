package com.sadique.dailyledger.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct Gemini Developer API over HTTPS — no Firebase, no SDK.
 * Uses the user's own free API key (x-goog-api-key), or a user proxy that injects the key server-side.
 */
internal object GeminiGateway {
    // Flash-Lite model with a Gemini Developer API free tier.
    const val MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_BASE = "https://generativelanguage.googleapis.com"
    /** Shared proxy holding the owner's key server-side, so app users never enter a key. */
    const val BUILT_IN_PROXY = "https://dailyledger-ai.sadique6571.workers.dev"
    const val APP_TOKEN = "dlgr-proxy-786-okara-22"

    private fun draftSchema(): JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put("properties", JSONObject()
            .put("message", JSONObject().put("type", "STRING"))
            .put("transactions", JSONObject().put("type", "ARRAY").put("maxItems", AiProtocol.MAX_DRAFTS)
                .put("items", JSONObject().put("type", "OBJECT")
                    .put("properties", JSONObject()
                        .put("type", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("INCOME", "EXPENSE"))))
                        .put("amount_pkr", JSONObject().put("type", "STRING"))
                        .put("category", JSONObject().put("type", "STRING"))
                        .put("note", JSONObject().put("type", "STRING"))
                        .put("date", JSONObject().put("type", "STRING")))
                    .put("required", JSONArray(listOf("type", "amount_pkr", "category", "note", "date"))))))
        .put("required", JSONArray(listOf("message", "transactions")))

    private fun insightSchema(): JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put("properties", JSONObject()
            .put("suggestions", JSONObject().put("type", "ARRAY").put("minItems", 1).put("maxItems", 3)
                .put("items", JSONObject().put("type", "OBJECT")
                    .put("properties", JSONObject()
                        .put("title", JSONObject().put("type", "STRING"))
                        .put("detail", JSONObject().put("type", "STRING")))
                    .put("required", JSONArray(listOf("title", "detail"))))))
        .put("required", JSONArray(listOf("suggestions")))

    suspend fun generate(request: AiModelRequest, apiKey: String, proxyBase: String): String = withContext(Dispatchers.IO) {
        val useBuiltInProxy = proxyBase.isBlank() && apiKey.isBlank()
        val base = when {
            proxyBase.isNotBlank() -> proxyBase.trim().trimEnd('/')
            apiKey.isNotBlank() -> DEFAULT_BASE
            else -> BUILT_IN_PROXY
        }
        val url = URL("$base/v1beta/models/$MODEL:generateContent")
        val generation = JSONObject().put("maxOutputTokens", when (request.task) {
            AiTask.AUTOFILL -> 6_000
            AiTask.INSIGHTS -> 3_000
            AiTask.TEXT -> 3_000
        })
        when (request.task) {
            AiTask.AUTOFILL -> generation.put("responseMimeType", "application/json").put("responseSchema", draftSchema())
            AiTask.INSIGHTS -> generation.put("responseMimeType", "application/json").put("responseSchema", insightSchema())
            AiTask.TEXT -> Unit
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.instruction))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.input)))))
            .put("generationConfig", generation)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 40_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (proxyBase.isBlank() && apiKey.isNotBlank()) setRequestProperty("x-goog-api-key", apiKey)
            if (useBuiltInProxy) setRequestProperty("x-app-token", APP_TOKEN)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw AiException(when (code) {
                    401, 403 -> "Gemini API key durust nahi hai. Settings mein key check karein. [AI_KEY]"
                    429 -> "Gemini ki free limit khatam ho gayi hai. Kuch der baad ya kal dobara koshish karein. [AI_LIMIT]"
                    400 -> "AI request mein masla hai — dobara koshish karein. [AI_REQUEST]"
                    else -> "Gemini se rabta nahi ho saka (error $code). Internet check karke dobara koshish karein. [AI_NETWORK]"
                })
            }
            val root = JSONObject(connection.inputStream.readBytes().toString(Charsets.UTF_8))
            val candidates = root.optJSONArray("candidates")
                ?: throw AiException("Gemini returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
            if (candidates.length() != 1) throw AiException("Gemini returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
            val candidate = candidates.getJSONObject(0)
            val finish = candidate.optString("finishReason")
            if (finish.isNotBlank() && finish != "STOP") throw AiException("Gemini returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
            val parts = candidate.getJSONObject("content").getJSONArray("parts")
            val out = StringBuilder()
            for (i in 0 until parts.length()) out.append(parts.getJSONObject(i).optString("text"))
            if (out.isBlank()) throw AiException("Gemini returned no text. Nothing was saved. [AI_RESPONSE]")
            out.toString()
        } finally {
            connection.disconnect()
        }
    }
}
