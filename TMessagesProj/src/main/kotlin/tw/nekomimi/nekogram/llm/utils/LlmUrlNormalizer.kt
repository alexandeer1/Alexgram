package tw.nekomimi.nekogram.llm.utils

object LlmUrlNormalizer {

    @JvmStatic
    fun normalizeBaseUrl(url: String?): String {
        if (url == null) {
            return ""
        }

        var normalized = url.trim()
        if (normalized.isEmpty()) {
            return ""
        }

        if (normalized.contains("generativelanguage.googleapis.com")) {
            val googleRegex = "(https://generativelanguage\\.googleapis\\.com/v[^/]+)/.*".toRegex()
            val match = googleRegex.find(normalized)
            if (match != null) {
                return match.groupValues[1] + "/openai"
            }
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        val completionsSuffix = "/chat/completions"
        if (normalized.endsWith(completionsSuffix, ignoreCase = true)) {
            normalized = normalized.dropLast(completionsSuffix.length)
            while (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
        }

        return normalized
    }

    @JvmStatic
    fun extractModelNameFromUrl(url: String?): String? {
        if (url == null) return null
        val normalized = url.trim()
        if (!normalized.contains("generativelanguage.googleapis.com")) return null
        
        val regex = "models/([^/:]+)".toRegex()
        val matchResult = regex.find(normalized)
        return matchResult?.groups?.get(1)?.value
    }
}

