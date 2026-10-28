package com.sadique.dailyledger.ai

/**
 * A selectable AI provider for the OPTIONAL personal API key in Settings.
 * Gemini uses its native API; every other preset speaks the OpenAI-compatible
 * chat-completions API, so one personal-key option covers many services.
 * The built-in proxy (Gemini) remains the default when no personal key is saved.
 */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val geminiNative: Boolean = false,
)

object AiProviders {
    val GEMINI = AiProvider("gemini", "Google Gemini", GeminiGateway.DEFAULT_BASE, GeminiGateway.MODEL, geminiNative = true)

    val ALL: List<AiProvider> = listOf(
        GEMINI,
        AiProvider("openai", "OpenAI (ChatGPT)", "https://api.openai.com/v1", "gpt-4o-mini"),
        AiProvider("groq", "Groq (free tier)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        AiProvider("mistral", "Mistral AI", "https://api.mistral.ai/v1", "mistral-small-latest"),
        AiProvider("custom", "Custom (OpenAI-compatible)", "", ""),
    )

    fun byId(id: String): AiProvider = ALL.firstOrNull { it.id == id } ?: GEMINI
}

/**
 * Verified model lists for the provider dropdowns (checked 2026-10-28 against
 * each provider's docs/site). The preset default stays first in every list.
 * "Custom model..." is always offered last, so new or org-specific models keep
 * working through free text. The "custom" provider (incl. Cerebras) has no
 * fixed list: its available models depend on the user's own account/org, so it
 * stays free-text only. Gemini uses the native path with a fixed model, so it
 * has no dropdown either.
 */
object AiProviderModels {
    const val CUSTOM = "Custom model..."

    fun forProvider(providerId: String): List<String> = when (providerId) {
        // OpenAI: gpt-4o / gpt-4.1 / gpt-4o-mini are deprecated by OpenAI
        // (API shutdown 2027-01-14); kept because the preset default is
        // gpt-4o-mini, and gpt-6-luna is OpenAI's named replacement.
        "openai" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-6-luna")
        // Groq docs (console.groq.com/docs/models). gpt-oss models are
        // reasoning models; the gateway already sends reasoning_effort "low"
        // for groq + gpt-oss.
        "groq" -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "meta-llama/llama-4-scout-17b-16e-instruct", "meta-llama/llama-4-maverick-17b-128e-instruct", "qwen/qwen3-32b", "moonshotai/kimi-k2-instruct")
        // OpenRouter: catalog is 500+ models and changes fast; these are
        // commonly documented IDs — check openrouter.ai/models for the full
        // list, and use "Custom model..." for anything else.
        "openrouter" -> listOf("openai/gpt-4o-mini", "openai/gpt-4o", "anthropic/claude-sonnet-4.5", "anthropic/claude-opus-4", "google/gemini-3-pro-preview", "meta-llama/llama-4-scout", "meta-llama/llama-4-maverick", "mistralai/mistral-large")
        // DeepSeek API exposes exactly two model IDs (api-docs.deepseek.com).
        // deepseek-reasoner thinks before answering, so it spends more tokens.
        "deepseek" -> listOf("deepseek-chat", "deepseek-reasoner")
        // Mistral docs (docs.mistral.ai).
        "mistral" -> listOf("mistral-small-latest", "mistral-medium-latest", "mistral-large-latest", "codestral-latest", "ministral-8b-latest", "ministral-3b-latest", "open-mistral-nemo")
        else -> emptyList()
    }
}
