package tw.nekomimi.nekogram.llm.preset

object PresetRegistry {
    const val CUSTOM = LlmPresetRegistry.CUSTOM
    const val OPENAI = LlmPresetRegistry.OPENAI
    const val GEMINI = LlmPresetRegistry.GEMINI
    const val GROQ = LlmPresetRegistry.GROQ
    const val DEEPSEEK = LlmPresetRegistry.DEEPSEEK
    const val XAI = LlmPresetRegistry.XAI
    const val CEREBRAS = LlmPresetRegistry.CEREBRAS
    const val OLLAMA_CLOUD = LlmPresetRegistry.OLLAMA_CLOUD
    const val OPENROUTER = LlmPresetRegistry.OPENROUTER
    const val VERCEL_AI_GATEWAY = LlmPresetRegistry.VERCEL_AI_GATEWAY

    @JvmStatic
    fun getPresetBaseUrl(preset: Int): String? = LlmPresetRegistry.getPresetBaseUrl(preset)

    @JvmStatic
    fun getDefaultModelResId(preset: Int): Int = LlmPresetRegistry.getDefaultModelResId(preset)
}
