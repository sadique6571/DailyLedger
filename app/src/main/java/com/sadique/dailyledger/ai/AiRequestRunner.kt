package com.sadique.dailyledger.ai

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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
        // Transient failures (slow network, per-minute rate limits) used to surface
        // at once, so the user had to tap 3-4 times until one attempt landed.
        // Retry them here with backoff instead; permanent failures still throw at once.
        // The daily-cap reservation above runs once, not once per attempt.
        var backoffMs = 2_000L
        repeat(3) { attempt ->
            try {
                val response = withTimeout(45_000) { generate(request) }
                currentCoroutineContext().ensureActive()
                checkAccess()
                if (response.isBlank() || response.length > 65_536) {
                    throw AiException("AI returned an incomplete response. Nothing was saved. [AI_RESPONSE]")
                }
                return response
            } catch (e: TimeoutCancellationException) {
                if (attempt == 2) throw e
                delay(backoffMs)
                backoffMs *= 2
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 2 || !isTransient(e)) throw e
                if (isRateLimit(e)) delay(20_000) else {
                    delay(backoffMs)
                    backoffMs *= 2
                }
            }
        }
        throw AiException("AI is unavailable right now. Nothing was saved. [AI_UNAVAILABLE]")
    }

    /** 429 rate-limit responses only — not the daily-cap or any other limit. */
    private fun isRateLimit(e: Exception): Boolean =
        e is AiException && e.message?.contains("[AI_LIMIT]") == true

    /** Failures worth retrying: timeouts, network IO (covers socket timeouts), 429s. */
    private fun isTransient(e: Exception): Boolean =
        e is IOException || isRateLimit(e)
}
