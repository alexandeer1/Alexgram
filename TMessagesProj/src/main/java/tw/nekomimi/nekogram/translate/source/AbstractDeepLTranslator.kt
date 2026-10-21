package tw.nekomimi.nekogram.translate.source

import android.text.TextUtils
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.translate.Translator
import xyz.nextalone.nagram.NaConfig
import java.util.Locale

/**
 * Common base translator for DeepL-family services.
 */
abstract class AbstractDeepLTranslator : Translator {

    companion object {
        const val FORMALITY_DEFAULT = 0
        const val FORMALITY_MORE = 1
        const val FORMALITY_LESS = 2

        @JvmStatic
        protected val SUPPORTED_TARGET_LANGUAGES: List<String> = listOf(
            "bg", "cs", "da", "de", "el", "en-GB", "en-US", "en", "es", "et",
            "fi", "fr", "hu", "id", "it", "ja", "lt", "lv", "nl", "pl", "pt-BR",
            "pt-PT", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "zh"
        )

        @JvmStatic
        fun convertLanguageCode(language: String, country: String): String {
            val languageLowerCase: String = language.lowercase(Locale.getDefault())
            return if (!TextUtils.isEmpty(country)) {
                val countryUpperCase: String = country.uppercase(Locale.getDefault())
                if (SUPPORTED_TARGET_LANGUAGES.contains("$languageLowerCase-$countryUpperCase")) {
                    "$languageLowerCase-$countryUpperCase"
                } else {
                    languageLowerCase
                }
            } else {
                languageLowerCase
            }
        }
    }

    protected open val logTag: String = "DeepL"

    protected fun getFormalityString(): String {
        return when (NaConfig.deepLFormality.Int()) {
            FORMALITY_MORE -> "more"
            FORMALITY_LESS -> "less"
            else -> "default"
        }
    }

    @Throws(Exception::class)
    protected abstract suspend fun performRequest(from: String, to: String, query: String): String

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        if (from == to) {
            return query
        }
        var targetLang = to
        if (targetLang !in SUPPORTED_TARGET_LANGUAGES) {
            val base = targetLang.split("-", "_")[0]
            if (base in SUPPORTED_TARGET_LANGUAGES) {
                targetLang = base
            } else {
                throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported))
            }
        }

        return try {
            performRequest(from, targetLang, query)
        } catch (e: UnsupportedOperationException) {
            throw e
        } catch (e: Exception) {
            FileLog.e("[$logTag] translate failed: ${e.message}")
            throw e
        }
    }
}
