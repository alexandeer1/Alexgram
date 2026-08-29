package tw.nekomimi.nekogram.llm.utils

import tw.nekomimi.nekogram.llm.preset.PresetRegistry
import java.util.Locale

object ModelUtil {

    private val gemma4ThoughtTagRegex = Regex(
        "<thought>.*?</thought>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val nonTextGenerationModelKeywords = listOf(
        "-live-",
        "-research",
        "-search",
        "antigravity-",
        "aqa",
        "asr-",
        "audio",
        "bge-",
        "chirp-",
        "computer-use",
        "csm-",
        "deepgram", // provider
        "e5-",
        "embed",
        "embedding",
        "flux",
        "gemini-omni",
        "gte-",
        "hailuo",
        "happyhorse",
        "i2v",
        "image",
        "imagen",
        "imagine",
        "kling-v",
        "kokoro-",
        "krea-",
        "lyria",
        "minilm-",
        "minimax-h3",
        "moderation",
        "nano-banana",
        "orpheus-",
        "parakeet-",
        "perplexity", // provider
        "quiverai", // provider
        "r2v",
        "realtime",
        "recraft",
        "rerank",
        "riverflow",
        "robotics",
        "runway", // provider
        "seedance",
        "seedream",
        "sentence-transformers", // provider
        "sora",
        "speech",
        "stt",
        "t2v",
        "transcri",
        "tts",
        "veo-",
        "video",
        "voice",
        "voyage",
        "wan-",
        "whisper",
        "zonos"
    )

    @JvmStatic
    fun getBaseModelName(model: String?): String {
        if (model.isNullOrBlank()) {
            return ""
        }
        return model.trim().substringAfterLast('/')
    }

    @JvmStatic
    fun isTextGenerationModel(model: String?): Boolean {
        if (model.isNullOrBlank()) {
            return true
        }
        val normalized = model.trim().lowercase(Locale.ROOT)
        return nonTextGenerationModelKeywords.none { normalized.contains(it) }
    }

    @JvmStatic
    fun isGPT5(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return !base.startsWith("gpt-5.") && base.startsWith("gpt-5") && !base.contains("instant") && !base.contains("chat")
    }

    @JvmStatic
    fun isGemma4(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return base.contains("gemma4") || base.contains("gemma-4")
    }

    @JvmStatic
    fun isGemini3(model: String?): Boolean {
        return getBaseModelName(model).lowercase().startsWith("gemini-3")
    }

    @JvmStatic
    fun isGeminiLegacy(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return base.startsWith("gemini-2") || base.startsWith("gemini-3-") || base.startsWith("gemini-3.1")
    }

    @JvmStatic
    fun isDeepSeekV4(model: String?): Boolean {
        return getBaseModelName(model).lowercase().startsWith("deepseek-v4")
    }

    @JvmStatic
    fun isCerebrasGlm(url: String?, model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return url == PresetRegistry.getPresetBaseUrl(PresetRegistry.CEREBRAS) && base == "zai-glm-4.7"
    }

    @JvmStatic
    fun isReasoning(model: String?): Boolean {
        return isOpenaiCompatibleReasoning(model) || isGemma4(model) || isDeepSeekV4(model)
    }

    @JvmStatic
    fun isOpenaiCompatibleReasoning(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return base.contains("gemini") && base.contains("flash")
                || (base.startsWith("gpt-5") && !base.contains("instant") && !base.contains("chat"))
                || base.startsWith("gpt-oss")
                || base.startsWith("grok-4.3")
                || base.startsWith("glm-5")
                || base.startsWith("hy3")
                || base.startsWith("inkling")
                || base.startsWith("kimi-k2.5") || base.startsWith("kimi-k2.6") || base.startsWith("kimi-k3")
                || base.startsWith("nemotron-3")
                || base.startsWith("qwen3")
    }

    @JvmStatic
    fun getReasoningEffort(model: String?): String {
        val base = getBaseModelName(model).lowercase()
        return when {
            base.startsWith("gpt-oss") -> "low"
            base.startsWith("gpt-5.") -> "none"
            base.startsWith("gpt-5") -> "low"
            base.startsWith("gemini") && (base.endsWith("latest") || !isGeminiLegacy(model)) -> "low"
            isGemma4(model) -> "low"
            else -> "none"
        }
    }

    @JvmStatic
    fun applyReasoningParameters(requestJson: org.json.JSONObject, url: String?, model: String?) {
        if (!isReasoning(model)) {
            return
        }
        val providerPreset = when (url) {
            PresetRegistry.getPresetBaseUrl(PresetRegistry.GEMINI) -> PresetRegistry.GEMINI
            PresetRegistry.getPresetBaseUrl(PresetRegistry.OPENROUTER) -> PresetRegistry.OPENROUTER
            PresetRegistry.getPresetBaseUrl(PresetRegistry.VERCEL_AI_GATEWAY) -> PresetRegistry.VERCEL_AI_GATEWAY
            else -> null
        }
        if (isGemma4(model) && providerPreset != PresetRegistry.GEMINI) {
            return
        }
        applyReasoningParametersInternal(requestJson, providerPreset, model)
    }

    private fun applyReasoningParametersInternal(requestJson: org.json.JSONObject, providerPreset: Int?, model: String?) {
        if (providerPreset != null && applyReasoningParametersRouter(requestJson, providerPreset, model)) {
            return
        }
        applyReasoningParametersOriginal(requestJson, model)
    }

    private fun applyReasoningParametersOriginal(requestJson: org.json.JSONObject, model: String?) {
        if (isOpenaiCompatibleReasoning(model) || isGemma4(model)) {
            val effort = getReasoningEffort(model)
            if (effort.isNotEmpty() && effort != "none") {
                requestJson.put("reasoning_effort", effort)
            }
        } else if (isDeepSeekV4(model)) {
            requestJson.put("thinking", org.json.JSONObject().put("type", "disabled"))
        }
    }

    private fun applyReasoningParametersRouter(requestJson: org.json.JSONObject, providerPreset: Int, model: String?): Boolean {
        val routerProvider = getRouterModelProvider(model) ?: return false
        return when (providerPreset) {
            PresetRegistry.OPENROUTER -> {
                when (routerProvider) {
                    "google" -> {
                        val effort = getReasoningEffort(model)
                        if (effort.isNotEmpty() && effort != "none") {
                            requestJson.put("reasoning", org.json.JSONObject().put("effort", effort))
                        }
                        return true
                    }
                    "openai" -> {
                        if (model?.contains("gpt-oss") ?: return false) {
                            requestJson.put("reasoning", org.json.JSONObject().put("effort", "low"))
                            return true
                        }
                    }
                }
                requestJson.put("reasoning", org.json.JSONObject().put("effort", "none"))
                true
            }
            PresetRegistry.VERCEL_AI_GATEWAY -> {
                when (routerProvider) {
                    "google" -> {
                        val thinkingConfig = if (isGemini3(model)) {
                            org.json.JSONObject().put("thinkingLevel", "low")
                        } else {
                            org.json.JSONObject().put("thinkingBudget", 0)
                        }
                        putProviderOptions(
                            requestJson,
                            "google",
                            org.json.JSONObject().put("thinkingConfig", thinkingConfig)
                        )
                        return true
                    }
                    "deepseek" -> {
                        if (isDeepSeekV4(model)) {
                            putProviderOptions(
                                requestJson,
                                "deepseek",
                                org.json.JSONObject().put("thinking", org.json.JSONObject().put("type", "disabled"))
                            )
                            return true
                        }
                    }
                }
                putProviderOptions(
                    requestJson,
                    routerProvider,
                    org.json.JSONObject().put("reasoning", org.json.JSONObject().put("effort", "none"))
                )
                true
            }
            else -> false
        }
    }

    private fun getRouterModelProvider(model: String?): String? {
        if (model.isNullOrBlank() || !model.contains('/')) {
            return null
        }
        return model.trim().substringBefore('/').lowercase()
    }

    private fun putProviderOptions(requestJson: org.json.JSONObject, provider: String, options: org.json.JSONObject) {
        val providerOptions = requestJson.optJSONObject("providerOptions") ?: org.json.JSONObject().also {
            requestJson.put("providerOptions", it)
        }
        providerOptions.put(provider, options)
    }

    @JvmStatic
    fun supportsTemperature(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return !base.startsWith("gpt-5") && (!base.startsWith("gemini") || isGeminiLegacy(model))
    }

    @JvmStatic
    fun stripModelsPrefix(models: List<String?>?): List<String> {
        if (models.isNullOrEmpty()) {
            return emptyList()
        }
        val out = LinkedHashSet<String>()
        for (model in models) {
            if (model == null) {
                continue
            }
            var id = model.trim()
            if (id.startsWith("models/")) {
                id = id.substring("models/".length)
            }
            if (id.isNotEmpty()) {
                out.add(id)
            }
        }
        return out.toList()
    }

    @JvmStatic
    fun isOpenRouterFreeModel(modelId: String?): Boolean {
        if (modelId.isNullOrBlank()) {
            return false
        }
        return modelId.trim().endsWith(":free", ignoreCase = true)
    }

    @JvmStatic
    fun sanitizeResponse(model: String?, content: String?): String {
        if (content.isNullOrBlank()) {
            return ""
        }
        var sanitized = content.trim()
        if (isGemma4(model)) {
            sanitized = gemma4ThoughtTagRegex.replace(sanitized, "").trim()
        }
        return sanitized
    }
}
