package tw.nekomimi.nekogram.translate.source

import org.json.JSONArray
import org.json.JSONObject
import xyz.nextalone.nagram.network.ContentType
import xyz.nextalone.nagram.network.NetworkRequestBuilder

/**
 * Shared implementation for the two official DeepL endpoints:
 *   - DeepL Pro:  https://api.deepl.com
 *   - DeepL Free: https://api-free.deepl.com
 */
abstract class DeepLOfficialTranslatorBase : AbstractDeepLTranslator() {

    protected abstract val baseUrl: String
    protected abstract val apiKey: String?
    protected abstract val displayName: String

    override suspend fun performRequest(from: String, to: String, query: String): String {
        val key = apiKey
        if (key.isNullOrBlank()) {
            error("Missing $displayName API Key")
        }

        val endpoint = "${baseUrl.trimEnd('/')}/v2/translate"

        val body = JSONObject().apply {
            put("text", JSONArray().apply { put(query) })
            if (from.isNotBlank() && from != "auto") {
                put("source_lang", from.uppercase())
            }
            put("target_lang", to.uppercase())
            put("formality", getFormalityString())
        }.toString()

        val response = NetworkRequestBuilder.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "DeepL-Auth-Key $key")
            setBody(body)
        }.execute()

        if (response.statusCode != 200) {
            error("HTTP ${response.statusCode} : ${response.body}")
        }

        val json = JSONObject(response.body)
        if (json.has("message") && !json.has("translations")) {
            error(json.getString("message"))
        }

        val translations = json.optJSONArray("translations")
            ?: error("Malformed DeepL response: ${response.body}")
        if (translations.length() == 0) {
            error("Empty DeepL response")
        }
        return translations.getJSONObject(0).getString("text")
    }
}
