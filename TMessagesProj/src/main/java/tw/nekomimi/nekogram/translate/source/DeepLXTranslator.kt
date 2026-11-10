package tw.nekomimi.nekogram.translate.source

import android.text.TextUtils
import org.json.JSONObject
import xyz.nextalone.nagram.NaConfig
import xyz.nextalone.nagram.network.ContentType
import xyz.nextalone.nagram.network.NetworkRequestBuilder

/**
 * Translator implementation backed by a self-hosted DeepLX HTTP service.
 */
object DeepLXTranslator : AbstractDeepLTranslator() {

    @Suppress("unused")
    const val FORMALITY_DEFAULT = AbstractDeepLTranslator.FORMALITY_DEFAULT
    @Suppress("unused")
    const val FORMALITY_MORE = AbstractDeepLTranslator.FORMALITY_MORE
    @Suppress("unused")
    const val FORMALITY_LESS = AbstractDeepLTranslator.FORMALITY_LESS

    override val logTag: String = "DeepLX"

    @JvmStatic
    fun convertLanguageCode(language: String, country: String): String =
        AbstractDeepLTranslator.convertLanguageCode(language, country)

    override suspend fun performRequest(from: String, to: String, query: String): String {
        val translateApi = NaConfig.deepLxCustomApi.String()
        if (TextUtils.isEmpty(translateApi)) error("Missing DeepLx Translate Api")

        val response = NetworkRequestBuilder.post(translateApi) {
            contentType(ContentType.Application.Json)
            setBody(JSONObject().apply {
                put("text", query)
                put("source_lang", from)
                put("target_lang", to)
                put("formality", getFormalityString())
            }.toString())
        }.execute()

        if (response.statusCode != 200) {
            error("HTTP ${response.statusCode} : ${response.body}")
        }

        val jsonObject = JSONObject(response.body)
        if (jsonObject.has("error")) {
            error(jsonObject.getString("message"))
        }
        return jsonObject.getString("data")
    }
}
