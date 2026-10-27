package com.sadique.dailyledger.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat-completions client used for a personal API key from any
 * non-Gemini provider (OpenAI, Groq, OpenRouter, DeepSeek, Mistral, or a custom
 * endpoint). Plain HTTPS with a Bearer key — no SDK. JSON answers are enforced
 * twice: json_object response format when the provider accepts it, and the
 * prompt itself (AiPrompts demands JSON-only). Code fences are stripped.
 */
internal object OpenAiGateway {

    suspend fun generate(
        request: AiModelRequest,
        provider: AiProvider,
        apiKey: String,
        modelOverride: String,
        baseOverride: String,
    ): String = withContext(Dispatchers.IO) {
        val base = (if (provider.id == "custom") baseOverride else provider.baseUrl).trim().trimEnd('/')
        val model = modelOverride.trim().ifBlank { provider.defaultModel }
        if (base.isBlank() || model.isBlank()) {
            throw AiException("Provider ki settings adhuri hain — Settings mein base URL aur model lazmi likhein. [AI_REQUEST]")
        }
        val jsonMode = request.task != AiTask.TEXT
        // Groq's gpt-oss models are reasoning models: their reasoning tokens
        // share the max_tokens budget, so a long reasoning chain truncates the
        // JSON — the nondeterministic decode failure (works once, fails next).
        // "low" keeps reasoning short. Other providers/models are untouched.
        val reasoningEffort = if (provider.id == "groq" && model.contains("gpt-oss")) "low" else null
        val first = post(base, model, request, apiKey, jsonMode, reasoningEffort)
        // Some providers/models reject response_format — retry once without it.
        // The retry also drops reasoning_effort, so a provider that rejects
        // either field still gets a plain request.
        val result = if (first.code == 400 && jsonMode) post(base, model, request, apiKey, false, null) else first
        if (result.code !in 200..299) {
            throw AiException(when (result.code) {
                401, 403 -> "API key durust nahi hai. Settings mein provider aur key check karein. [AI_KEY]"
                404 -> "Provider ya model nahi mila. Settings mein model ka naam check karein. [AI_REQUEST]"
                429 -> "Is provider ki limit khatam ho gayi hai. Kuch der baad dobara koshish karein. [AI_LIMIT]"
                400 -> "AI request mein masla hai — model ka naam check karke dobara koshish karein. [AI_REQUEST]"
                else -> "AI se rabta nahi ho saka (error ${result.code}). Internet check karke dobara koshish karein. [AI_NETWORK]"
            })
        }
        val root = try { JSONObject(result.body) } catch (_: Exception) {
            throw AiException("AI returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
        }
        val choices = root.optJSONArray("choices")
            ?: throw AiException("AI returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
        if (choices.length() < 1) throw AiException("AI returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
        val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().stripFences()
        if (content.isBlank()) throw AiException("AI returned no text. Nothing was saved. [AI_RESPONSE]")
        content
    }

    private data class Reply(val code: Int, val body: String)

    private fun post(base: String, model: String, request: AiModelRequest, apiKey: String, jsonMode: Boolean, reasoningEffort: String? = null): Reply {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", request.instruction))
                .put(JSONObject().put("role", "user").put("content", request.input)))
            .put("temperature", 0.2)
            .put("max_tokens", when (request.task) {
                AiTask.AUTOFILL -> 6_000
                // Reasoning models share this budget with their reasoning tokens; 3,000 truncated the insights JSON every time.
                AiTask.INSIGHTS -> 8_000
                AiTask.TEXT -> 3_000
            })
        if (reasoningEffort != null) body.put("reasoning_effort", reasoningEffort)
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        val url = URL("$base/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            Reply(code, stream?.readBytes()?.toString(Charsets.UTF_8).orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    /** Some models wrap JSON in ``` fences despite instructions — remove them. */
    private fun String.stripFences(): String {
        val t = trim()
        if (!t.startsWith("```")) return t
        val firstNl = t.indexOf('\n')
        if (firstNl < 0) return t
        var out = t.substring(firstNl + 1)
        if (out.endsWith("```")) out = out.dropLast(3)
        return out.trim()
    }
}
