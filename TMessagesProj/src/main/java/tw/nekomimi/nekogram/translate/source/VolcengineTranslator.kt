package tw.nekomimi.nekogram.translate.source

import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.translate.Translator
import xyz.nextalone.nagram.network.ContentType
import xyz.nextalone.nagram.network.NetworkRequestBuilder
import java.util.Arrays

object VolcengineTranslator : Translator {

    private val targetLanguages = Arrays.asList(
        "ar", "as", "bn", "bs", "bg", "yue", "ca", "zh", "zh-Hans", "zh-Hant",
        "hr", "cs", "da", "prs", "nl", "en", "et", "fj", "fil", "fi",
        "fr", "de", "el", "gu", "ht", "he", "hi", "mww", "hu", "is",
        "id", "ga", "it", "ja", "kn", "kk", "tlh", "ko", "ku", "kmr",
        "lv", "lt", "mg", "ms", "ml", "mt", "mi", "mr", "nb", "or", "ps",
        "fa", "pl", "pt", "pa", "otq", "ro", "ru", "sm", "sr", "sk", "sl",
        "es", "sw", "sv", "ty", "ta", "te", "th", "to", "tr", "uk", "ur",
        "vi", "cy", "yua"
    )

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        var targetLang = to
        if (targetLang !in targetLanguages) {
            val base = targetLang.split("-", "_")[0]
            if (base in targetLanguages) {
                targetLang = base
            } else {
                throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported))
            }
        }

        val jsonBody = JSONObject().apply {
            put("target_language", targetLang)
            put("text", query)
        }

        val response = NetworkRequestBuilder.post("https://translate.volcengine.com/crx/translate/v1/") {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json, text/plain, */*")
            header("Origin", "chrome-extension://klgfhbiooeogfpknjdcbablpceialkdj")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header("Sec-Fetch-Site", "none")
            header("Sec-Fetch-Mode", "cors")
            header("Sec-Fetch-Dest", "empty")
            setBody(jsonBody.toString())
        }.execute()

        if (response.statusCode != 200) {
            error("HTTP ${response.statusCode} : ${response.body}")
        }

        val respArr = JSONObject(response.body)
        val result = respArr.getString("translation")

        if (result.isEmpty()) error("Empty translation result")

        return result
    }
}
