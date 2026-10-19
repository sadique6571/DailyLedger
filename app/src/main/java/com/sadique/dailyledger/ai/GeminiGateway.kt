package com.sadique.dailyledger.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.*

internal object GeminiGateway {
    // Stable Flash-Lite model with a Gemini Developer API free tier (checked 2026-10-07).
    const val MODEL = "gemini-3.5-flash-lite"

    private val draftSchema = Schema.obj(mapOf(
        "message" to Schema.string(),
        "transactions" to Schema.array(
            Schema.obj(mapOf(
                "type" to Schema.enumeration(listOf("INCOME", "EXPENSE")),
                "amount_pkr" to Schema.string(),
                "category" to Schema.string(),
                "note" to Schema.string(),
                "date" to Schema.string(),
            )), maxItems = AiProtocol.MAX_DRAFTS,
        ),
    ))
    private val insightSchema = Schema.obj(mapOf(
        "suggestions" to Schema.array(
            Schema.obj(mapOf("title" to Schema.string(), "detail" to Schema.string())),
            minItems = 1, maxItems = 3,
        ),
    ))

    suspend fun generate(request: AiModelRequest): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            systemInstruction = content { text(request.instruction) },
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = if (request.task == AiTask.AUTOFILL) draftSchema else insightSchema
                maxOutputTokens = if (request.task == AiTask.AUTOFILL) 6_000 else 3_000
            },
        )
        val response = model.generateContent(request.input)
        if (response.candidates.size != 1 || response.candidates.single().finishReason != FinishReason.STOP ||
            response.functionCalls.isNotEmpty()) {
            throw AiException("Gemini returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
        }
        return response.text ?: throw AiException("Gemini returned no entries. Nothing was saved. [AI_RESPONSE]")
    }
}
