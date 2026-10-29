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
        reserve = { task ->
            // The on-device daily cap protects the SHARED built-in proxy quota only.
            // A personal key or the user's own proxy spends their own quota — no cap here.
            if (usesBuiltInProxy()) preferences.reserve(task) else true
        },
        generate = { request -> route(request) },
    )

    /** The current provider's saved key. Migrates the old single key slot
     * once, so a key saved before per-provider storage still works. The key
     * itself is never logged or exposed — only its presence is checked. */
    private fun personalKey(): String {
        keyStore.migrateLegacy(preferences.providerId)
        preferences.migrateLegacy()
        return keyStore.keyFor(preferences.providerId)
    }

    /** True when this request would spend the shared built-in proxy quota. */
    private fun usesBuiltInProxy(): Boolean = personalKey().isBlank() && preferences.proxyBase.isBlank()

    /** True when the user saved their own API key in Settings. The entry AI must
     * then use the online model via that key, not the offline fallback. */
    fun hasPersonalKey(): Boolean = personalKey().isNotBlank()

    private suspend fun route(request: AiModelRequest): String {
        val key = personalKey()
        val provider = AiProviders.byId(preferences.providerId)
        // A stale built-in-proxy URL stored alongside a personal key must not
        // divert the request to an unauthenticated proxy call: treat it as blank
        // so a personal key always routes with its own auth (OpenAI-compatible
        // providers directly, Gemini via the key header).
        val proxyBase = if (key.isNotBlank() && preferences.proxyBase.trim() == GeminiGateway.BUILT_IN_PROXY) "" else preferences.proxyBase
        return if (key.isNotBlank() && proxyBase.isBlank() && !provider.geminiNative) {
            // Personal key of another AI service (OpenAI-compatible chat API).
            OpenAiGateway.generate(request, provider, key, preferences.providerModel, preferences.providerBase)
        } else {
            // Gemini key directly, the user's own proxy, or the built-in proxy.
            GeminiGateway.generate(request, key, proxyBase)
        }
    }

    /** Settings "Connection test": same routing as real calls, but never consumes any daily quota. */
    suspend fun testConnection(): String = try {
        checkAccess()
        route(AiModelRequest(AiTask.TEXT, "Reply with exactly this word: OK", "ping")).trim()
    } catch (e: TimeoutCancellationException) {
        throw AiException("AI ne jawab dene mein bohat der kar di. Dobara koshish karein. Nothing was saved. [AI_TIMEOUT]")
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw AiFailures.describe(e) }

    private fun checkAccess() {
        if (!preferences.enabled) throw AiException("Enable Cloud AI in Settings before sending a request. [AI_CONSENT]")
    }

    private suspend fun call(request: AiModelRequest): String = try {
        runner.execute(request)
    } catch (e: TimeoutCancellationException) {
        throw AiException("AI ne jawab dene mein bohat der kar di. Dobara koshish karein. Nothing was saved. [AI_TIMEOUT]")
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw AiFailures.describe(e) }

    suspend fun drafts(input: String, learned: List<com.sadique.dailyledger.data.LearnedItem> = emptyList()): AiDraftResult {
        require(input.isNotBlank() && input.length <= AiProtocol.MAX_INPUT)
        val dates = EntryDateParser.extract(EntryText.normalize(input), LocalDate.now()).map { it.date.toString() }.toSet()
        val catalog = (CategoryCatalog.load(context) +
            com.sadique.dailyledger.data.LearnedItems.toLedgerCategories(learned))
            .distinctBy { it.type to it.label }
        val learnedLabels = learned.map { it.name }.toSet()
        fun normalize(result: AiDraftResult): AiDraftResult {
            val normalized = CategoryRules.normalizeResult(result, learnedLabels).finalizeNames(input)
            // Sadique's rule (2026-10-29): when the draft's category is a catalog
            // item, the entry shows the catalog's canonical name instead of his
            // typed wording ("gass bill" -> "Gas", "bijli bill" -> "Electricity").
            // Items without a catalog match keep his wording.
            val labels = catalog.map { it.label }.toSet()
            return normalized.copy(entries = normalized.entries.map { draft ->
                val canonical = labels.firstOrNull { it.equals(draft.category.trim(), ignoreCase = true) }
                if (canonical != null) draft.copy(note = canonical) else draft
            })
        }
        suspend fun decodeOnce(request: AiModelRequest): AiDraftResult =
            normalize(AiProtocol.decodeDrafts(call(request)))
        // A decode failure is nondeterministic for reasoning models (truncated
        // JSON, bare array, one bad row value): the same prompt usually lands
        // on a fresh sample. Retry ONCE with the same prompt — costs one extra
        // call ONLY when the first answer failed to decode, zero on success.
        // Infrastructure failures are never retried here (the runner already
        // retries those); dangerous keys are re-checked on the retry, so a
        // smuggled key fails again instead of slipping through.
        val first = try {
            decodeOnce(AiPrompts.drafts(input, LocalDate.now(), catalog))
        } catch (e: AiDecodeException) {
            decodeOnce(AiPrompts.drafts(input, LocalDate.now(), catalog))
        }
        val result = if (first.entries.isNotEmpty()) first else {
            // The model took the "return no transactions" escape hatch even
            // though the input may carry an amount: one stricter retry. The
            // retry is skipped when the input has no digit at all — then an
            // empty answer is correct (the model must ask for the amount,
            // never invent one) and the model's own message is shown.
            // Infrastructure failures are rethrown as-is; only a second
            // empty/shape failure becomes the specific error below — never
            // the generic invalid-entry dead end, and never a silent switch
            // to offline.
            if (!input.any { it.isDigit() }) first else {
                // Plain try/catch inside this suspend function: runCatching's
                // lambda is not a coroutine body, so the suspend call() inside
                // it did not compile. Same semantics as before: success with
                // entries wins; any failure falls to the checks below, and a
                // CancellationException still propagates immediately.
                var repaired: AiDraftResult?
                var repairedFailure: Throwable?
                try {
                    repaired = normalize(AiProtocol.decodeDrafts(call(AiPrompts.draftsRepair(input, LocalDate.now(), catalog))))
                    repairedFailure = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    repaired = null
                    repairedFailure = e
                }
                val second = repaired
                if (second != null && second.entries.isNotEmpty()) second
                else {
                    val failure = repairedFailure
                    if (failure is CancellationException) throw failure
                    if (failure is AiException && isInfrastructure(failure)) throw failure
                    throw AiException("AI is jumlay se koi entry nahi bana saka. Item aur amount alag alag wazeh likh kar dobara koshish karein. Nothing was saved.")
                }
            }
        }
        if (dates.isNotEmpty() && result.entries.any { it.date !in dates }) {
            throw AiException("AI did not preserve the dates you supplied. Nothing was saved. Please separate the entries or enter them manually.")
        }
        return result
    }

    private fun isInfrastructure(e: AiException): Boolean {
        val m = e.message.orEmpty()
        return listOf("[AI_NETWORK]", "[AI_LIMIT]", "[AI_TIMEOUT]", "[AI_DEVICE_LIMIT]", "[AI_KEY]", "[AI_RESPONSE]", "[AI_REQUEST]").any { it in m }
    }

    suspend fun insights(snapshot: SpendingSnapshot): List<SpendingTip> {
        val request = AiPrompts.insights(snapshot, CategoryCatalog.load(context))
        // Same decode-retry as drafts(): qwen's first sample often fails to
        // decode (truncated or variant JSON); a fresh sample usually lands.
        // One extra call ONLY when the first answer failed to decode, zero
        // on success. Infrastructure failures are never retried here.
        return try {
            AiProtocol.decodeInsights(call(request))
        } catch (e: AiDecodeException) {
            AiProtocol.decodeInsights(call(request))
        }
    }

    /** Free-text AI answer (chat, reports, plans, reminder drafts). Nothing is saved by this call. */
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

