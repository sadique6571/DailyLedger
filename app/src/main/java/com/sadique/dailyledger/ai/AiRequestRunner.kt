package com.sadique.dailyledger.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

enum class AiTask { AUTOFILL, INSIGHTS, TEXT }
data class AiModelRequest(val task: AiTask, val instruction: String, val input: String)

/** Re-check account and consent across every suspension before sending or accepting data. */
internal class AiRequestRunner(
    private val checkAccess: () -> Unit,
    private val prepare: suspend () -> Unit,
    private val reserve: (AiTask) -> Boolean,
    private val generate: suspend (AiModelRequest) -> String,
) {
    suspend fun execute(request: AiModelRequest): String {
        checkAccess()
        prepare()
        currentCoroutineContext().ensureActive()
        checkAccess()
        if (!reserve(request.task)) throw AiException("Today's AI limit on this device is reached. Manual entries and local insights still work. [AI_DEVICE_LIMIT]")
        val response = withTimeout(45_000) { generate(request) }
        currentCoroutineContext().ensureActive()
        checkAccess()
        if (response.isBlank() || response.length > 65_536) {
            throw AiException("Gemini returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
        }
        return response
    }
}
