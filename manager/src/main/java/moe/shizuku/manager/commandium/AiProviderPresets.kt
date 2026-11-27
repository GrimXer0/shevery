package moe.shizuku.manager.commandium

import moe.shizuku.manager.R

/**
 * Built-in provider presets shown in the provider picker.
 * Moved byte-identical out of AiProviderDialog so the future AI manager
 * screen and the dialog share one source of truth.
 */
data class AiProviderPreset(
    val name: String,
    val baseUrl: String,
    val nameRes: Int? = null,
)

const val GEMINI_NANO_BASE_URL = "local://gemini-nano"
const val GEMINI_NANO_DEFAULT_MODEL = "gemini-nano"
const val GEMINI_NANO_PRESET_NAME = "Gemini Nano (On-device)"

fun isGeminiNano(baseUrl: String): Boolean =
    baseUrl.trim().equals(GEMINI_NANO_BASE_URL, ignoreCase = true)

val aiProviderPresets = listOf(
    AiProviderPreset(GEMINI_NANO_PRESET_NAME, GEMINI_NANO_BASE_URL),
    AiProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    AiProviderPreset("OpenAI", "https://api.openai.com/v1"),
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
    AiProviderPreset("Groq", "https://api.groq.com/openai/v1"),
    AiProviderPreset("Mistral", "https://api.mistral.ai/v1"),
    AiProviderPreset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    AiProviderPreset("xAI", "https://api.x.ai/v1"),
    AiProviderPreset("", "", R.string.comput_ai_preset_custom),
)

fun urlIsValid(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://") || isGeminiNano(url)

fun matchPresetName(currentName: String, currentBaseUrl: String): String =
    when {
        isGeminiNano(currentBaseUrl) -> GEMINI_NANO_PRESET_NAME
        currentName.isBlank() -> "OpenRouter"
        else ->
            aiProviderPresets.firstOrNull { it.name == currentName && it.baseUrl == currentBaseUrl.trim() }?.name ?: "Custom"
    }