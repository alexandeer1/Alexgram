package tw.nekomimi.nekogram.llm.utils

object ModelUtil {
    @JvmStatic
    fun getBaseModelName(model: String?): String = LlmModelUtil.getBaseModelName(model)

    @JvmStatic
    fun isTextGenerationModel(model: String?): Boolean = LlmModelUtil.isTextGenerationModel(model)

    @JvmStatic
    fun isCerebrasGlm(url: String?, model: String?): Boolean = LlmModelUtil.isCerebrasGlm(url, model)

    @JvmStatic
    fun isGPT5(model: String?): Boolean = LlmModelUtil.isGPT5(model)

    @JvmStatic
    fun isGemma4(model: String?): Boolean = LlmModelUtil.isGemma4(model)

    @JvmStatic
    fun isGemini3(model: String?): Boolean = LlmModelUtil.isGemini3(model)

    @JvmStatic
    fun isGeminiLegacy(model: String?): Boolean = LlmModelUtil.isGeminiLegacy(model)

    @JvmStatic
    fun isDeepSeekV4(model: String?): Boolean = LlmModelUtil.isDeepSeekV4(model)

    @JvmStatic
    fun isReasoning(model: String?): Boolean = LlmModelUtil.isReasoning(model)

    @JvmStatic
    fun isOpenaiCompatibleReasoning(model: String?): Boolean = LlmModelUtil.isOpenaiCompatibleReasoning(model)

    @JvmStatic
    fun getReasoningEffort(model: String?): String = LlmModelUtil.getReasoningEffort(model)

    @JvmStatic
    fun applyReasoningParameters(requestJson: org.json.JSONObject, url: String?, model: String?) {
        LlmModelUtil.applyReasoningParameters(requestJson, url, model)
    }

    @JvmStatic
    fun supportsTemperature(model: String?): Boolean = LlmModelUtil.supportsTemperature(model)

    @JvmStatic
    fun stripModelsPrefix(models: List<String?>?): List<String> = LlmModelUtil.stripModelsPrefix(models)

    @JvmStatic
    fun isOpenRouterFreeModel(modelId: String?): Boolean = LlmModelUtil.isOpenRouterFreeModel(modelId)

    @JvmStatic
    fun sanitizeResponse(model: String?, content: String?): String = LlmModelUtil.sanitizeResponse(model, content)
}
