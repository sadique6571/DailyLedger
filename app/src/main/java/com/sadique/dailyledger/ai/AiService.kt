package com.sadique.dailyledger.ai

import android.content.Context
import com.sadique.dailyledger.data.CategoryCatalog
import com.sadique.dailyledger.data.TransactionDraft
import kotlinx.coroutines.TimeoutCancellationException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class AiService(private val context: Context, private val owner: String) {
    private val preferences = AiPreferences(context, owner)
    private val keyStore = AiKeyStore(context, owner)
    private val runner = AiRequestRunner(
        checkAccess = ::checkAccess,
        prepare = {
            // No key needed on the device: the built-in proxy holds the owner's key.
            // A personal key or custom proxy saved in Settings takes precedence when present.
        },
        reserve = { preferences.reserve(it) },
        generate = { GeminiGateway.generate(it, keyStore.key, preferences.proxyBase) },
    )

    private fun checkAccess() {
        if (!preferences.enabled) throw AiException("Enable Gemini AI in Settings before sending a request. [AI_CONSENT]")
    }

    private suspend fun call(request: AiModelRequest): String = try {
        runner.execute(request)
    } catch (e: TimeoutCancellationException) {
        throw AiException("Gemini took too long to reply. Try again later. Nothing was saved. [AI_TIMEOUT]")
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw AiFailures.describe(e) }

    suspend fun drafts(input: String): AiDraftResult {
        require(input.isNotBlank() && input.length <= AiProtocol.MAX_INPUT)
        val dates = EntryDateParser.extract(EntryText.normalize(input), LocalDate.now()).map { it.date.toString() }.toSet()
        val request = AiPrompts.drafts(input, LocalDate.now(), CategoryCatalog.load(context))
        val result = CategoryRules.normalizeResult(AiProtocol.decodeDrafts(call(request)))
        if (dates.isNotEmpty() && result.entries.any { it.date !in dates }) {
            throw AiException("AI did not preserve the dates you supplied. Nothing was saved. Please separate the entries or enter them manually.")
        }
        return result
    }

    suspend fun insights(snapshot: SpendingSnapshot): List<SpendingTip> {
        val request = AiPrompts.insights(snapshot, CategoryCatalog.load(context))
        return AiProtocol.decodeInsights(call(request))
    }

    /** Free-text Gemini answer (chat, reports, plans, reminder drafts). Nothing is saved by this call. */
    suspend fun advise(request: AiModelRequest): String {
        require(request.task == AiTask.TEXT)
        return call(request).trim()
    }

    /** Parse a pasted bank/wallet statement (plain text) into reviewable drafts, in chunks. */
    suspend fun statementDrafts(fullText: String): AiDraftResult {
        val text = fullText.trim()
        require(text.isNotBlank()) { "Statement text khali hai." }
        require(text.length <= 12_000) { "Statement bohat lamba hai — hisso mein paste karein (ek dafa mein ~12,000 characters)." }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotEmpty()) { chunks += current.toString(); current.clear() } }
        for (line in text.lineSequence()) {
            if (line.length > 2_600) {
                flush()
                chunks += line.chunked(2_600)
            } else {
                if (current.length + line.length + 1 > 2_600) flush()
                current.append(line).append('\n')
            }
        }
        flush()
        val today = LocalDate.now()
        val catalog = CategoryCatalog.load(context)
        val all = mutableListOf<TransactionDraft>()
        for (chunk in chunks) {
            val result = CategoryRules.normalizeResult(AiProtocol.decodeDrafts(call(AiPrompts.drafts(chunk, today, catalog))))
            all += result.entries
            if (all.size >= 90) break
        }
        if (all.isEmpty()) throw AiException("Statement se koi entry nahi mili. Text saaf paste karke dobara koshish karein.")
        return AiDraftResult("Statement se ${all.size} entries mili hain — check karke Save karein.", all.take(90))
    }
}
