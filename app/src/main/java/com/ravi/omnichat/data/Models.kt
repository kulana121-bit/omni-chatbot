package com.ravi.omnichat.data

import java.util.UUID

enum class Protocol(val label: String) {
    OPENAI("OpenAI-compatible"),
    GEMINI("Google Gemini"),
    ANTHROPIC("Anthropic Claude")
}

data class Provider(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val defaultBaseUrl: String,
    val keyUrl: String = "",
    val keyHint: String = "API key"
)

object Providers {
    val all = listOf(
        Provider("openai", "OpenAI", Protocol.OPENAI, "https://api.openai.com/v1", "https://platform.openai.com/api-keys", "sk-..."),
        Provider("gemini", "Gemini", Protocol.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "https://aistudio.google.com/app/apikey", "AIza..."),
        Provider("anthropic", "Claude", Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "https://console.anthropic.com/settings/keys", "sk-ant-..."),
        Provider("openrouter", "OpenRouter", Protocol.OPENAI, "https://openrouter.ai/api/v1", "https://openrouter.ai/keys", "sk-or-..."),
        Provider("groq", "Groq", Protocol.OPENAI, "https://api.groq.com/openai/v1", "https://console.groq.com/keys", "gsk_..."),
        Provider("deepseek", "DeepSeek", Protocol.OPENAI, "https://api.deepseek.com/v1", "https://platform.deepseek.com/api_keys", "sk-..."),
        Provider("mistral", "Mistral", Protocol.OPENAI, "https://api.mistral.ai/v1", "https://console.mistral.ai/api-keys"),
        Provider("xai", "xAI Grok", Protocol.OPENAI, "https://api.x.ai/v1", "https://console.x.ai", "xai-..."),
        Provider("together", "Together", Protocol.OPENAI, "https://api.together.xyz/v1", "https://api.together.ai/settings/api-keys"),
        Provider("cerebras", "Cerebras", Protocol.OPENAI, "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai"),
        Provider("fireworks", "Fireworks", Protocol.OPENAI, "https://api.fireworks.ai/inference/v1", "https://fireworks.ai/account/api-keys"),
        Provider("nvidia", "NVIDIA NIM", Protocol.OPENAI, "https://integrate.api.nvidia.com/v1", "https://build.nvidia.com", "nvapi-..."),
        Provider("huggingface", "Hugging Face", Protocol.OPENAI, "https://router.huggingface.co/v1", "https://huggingface.co/settings/tokens", "hf_..."),
        Provider("perplexity", "Perplexity", Protocol.OPENAI, "https://api.perplexity.ai", "https://www.perplexity.ai/settings/api", "pplx-..."),
        Provider("ollama", "Ollama (local)", Protocol.OPENAI, "http://192.168.1.10:11434/v1", "", "optional"),
        Provider("custom", "Custom", Protocol.OPENAI, "", "", "API key")
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

enum class ThinkMode(val label: String, val budget: Int, val effort: String) {
    AUTO("Auto", 0, ""),
    OFF("Off", 0, ""),
    LOW("Low", 2048, "low"),
    MEDIUM("Medium", 8192, "medium"),
    HIGH("High", 24576, "high")
}

data class ModelInfo(val id: String, val displayName: String = id)

data class Attachment(
    val name: String,
    val mime: String,
    val base64: String? = null,
    val text: String? = null,
    /** For videos: a few JPEG keyframes (base64) for models that can't read video directly. */
    val frames: List<String> = emptyList()
) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = mime.startsWith("video/")
    val isPdf get() = mime == "application/pdf"
}

data class Source(val title: String, val url: String)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val sources: List<Source> = emptyList(),
    val isError: Boolean = false,
    val model: String = "",
    val thinking: String = "",
    val memoryNote: String = "",
    val thinkSecs: Int = 0,
    val appNote: String = ""
)

data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val instructions: String
) {
    /** What the user types after "/" to call it. */
    val slug get() = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "skill" }
}

data class Memory(val id: String = UUID.randomUUID().toString(), val text: String, val created: Long = System.currentTimeMillis())

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val updated: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

/** One API key + endpoint + model used by the auto-fallback chain. */
data class Endpoint(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val protocol: Protocol,
    val baseUrl: String,
    val key: String,
    val model: String,
    val label: String = "",
    val enabled: Boolean = true
) {
    val title get() = label.ifBlank { Providers.byId(providerId).name }
}
