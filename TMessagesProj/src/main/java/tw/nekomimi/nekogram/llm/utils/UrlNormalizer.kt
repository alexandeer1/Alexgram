package tw.nekomimi.nekogram.llm.utils

object UrlNormalizer {
    @JvmStatic
    fun normalizeBaseUrl(url: String?): String = LlmUrlNormalizer.normalizeBaseUrl(url)

    @JvmStatic
    fun isValidBaseUrl(url: String?): Boolean = LlmUrlNormalizer.isValidBaseUrl(url)

    @JvmStatic
    fun isValidNormalizedBaseUrl(url: String): Boolean = LlmUrlNormalizer.isValidNormalizedBaseUrl(url)
}
