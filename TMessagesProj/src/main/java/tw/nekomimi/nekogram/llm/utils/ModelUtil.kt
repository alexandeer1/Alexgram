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
        return base.contains("gemma") && base.contains("4")
    }

    @JvmStatic
    fun isCerebrasGlm(url: String?, model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return url == PresetRegistry.getPresetBaseUrl(PresetRegistry.CEREBRAS) && base == "zai-glm-4.7"
    }

    @JvmStatic
    fun isReasoning(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return base.contains("gemini") && base.contains("flash")
                || base.startsWith("gpt-oss")
                || (base.startsWith("gpt-5") && !base.contains("instant") && !base.contains("chat"))
    }

    @JvmStatic
    fun getReasoningEffort(model: String?): String {
        val base = getBaseModelName(model).lowercase()
        return when {
            base.startsWith("gpt-oss") -> "low"
            base.startsWith("gpt-5.") -> "none"
            base.startsWith("gpt-5") -> "minimal"
            else -> "none"
        }
    }

    @JvmStatic
    fun supportsTemperature(model: String?): Boolean {
        val base = getBaseModelName(model).lowercase()
        return !base.startsWith("gpt-5")
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
