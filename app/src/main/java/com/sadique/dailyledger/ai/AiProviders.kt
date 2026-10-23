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
