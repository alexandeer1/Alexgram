package tw.nekomimi.nekogram.translate

import android.content.Context
import android.view.View
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.parts.MessageTransText
import tw.nekomimi.nekogram.translate.TranslateDb.Companion.currentTarget
import tw.nekomimi.nekogram.translate.source.*
import tw.nekomimi.nekogram.ui.PopupBuilder
import tw.nekomimi.nekogram.utils.AlertUtil.showCopyAlert
import tw.nekomimi.nekogram.utils.AlertUtil.showProgress
import tw.nekomimi.nekogram.utils.AlertUtil.showTransFailedDialog
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.receive
import tw.nekomimi.nekogram.utils.receiveLazy
import xyz.nextalone.nagram.NaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Arrays
import java.util.Locale

val String.code2Locale: Locale by receiveLazy<String, Locale> {
    var ret: Locale
    if (this == null || this.isBlank()) {
        ret = LocaleController.getInstance().currentLocale
    } else {
        val args = replace('-', '_').split('_')
        if (args.size == 1) {
            ret = Locale(args[0])
        } else {
            ret = Locale(args[0], args[1])
        }
    }
    ret
}

val Locale.locale2code by receiveLazy<Locale, String> {
    if (country.isNullOrBlank()) {
        language
    } else {
        "$language-$country"
    }
}

val LocaleController.LocaleInfo.locale by receiveLazy<LocaleController.LocaleInfo, Locale> { pluralLangCode.code2Locale }

interface Translator {

    suspend fun doTranslate(from: String, to: String, query: String): String

    suspend fun doTranslate(from: String, to: String, query: String, context: List<String>): String {
        return doTranslate(from, to, query)
    }

    companion object {

        @Throws(Exception::class)
        suspend fun translate(query: String) = translate(
            NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale, query
        )

        @Throws(Exception::class)
        suspend fun translateArticle(query: String) = translate(query)

        const val providerGoogle = 1
        const val providerGoogleCN = 2
        const val providerGoogle2 = 3
        const val providerLingo = 4
        const val providerMicrosoft = 5
        const val providerVolcengine = 6
        const val providerDeepL = 7 // DeepLX
        const val providerTelegram = 8
        const val providerTranSmart = 9
        const val providerLLM = 10
        const val providerDeepLOfficial = 11
        const val providerDeepLFree = 12

        // Backwards compatibility aliases
        const val providerLLMTranslator = providerLLM
        const val providerRealMicrosoft = providerMicrosoft
        const val providerYandex = providerGoogle

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String): String = translate(to, query, emptyList())

        @Throws(Exception::class)
        suspend fun translate(to: Locale, provider: Int, query: String, context: List<String> = emptyList()): String {
            val oldProvider = NekoConfig.translationProvider.Int()
            val effectiveProvider = if (provider in 1..12) provider else oldProvider
            return translateWithProvider(to, query, context, effectiveProvider)
        }

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String, context: List<String>): String {
            val provider = NekoConfig.translationProvider.Int()
            return translateWithProvider(to, query, context, provider)
        }

        @Throws(Exception::class)
        private suspend fun translateWithProvider(to: Locale, query: String, context: List<String>, provider: Int): String {
            var language = to.language
            var country = to.country

            if (language == "in") language = "id"
            if (country.lowercase(Locale.getDefault()) == "duang") country = "CN"

            when (provider) {
                providerDeepL,
                providerDeepLOfficial,
                providerDeepLFree -> language = AbstractDeepLTranslator.convertLanguageCode(language, country)
                providerVolcengine,
                providerMicrosoft,
                providerGoogle,
                providerGoogle2,
                providerGoogleCN -> if (language == "zh") {
                    val countryUpperCase = country.uppercase(Locale.getDefault())
                    if (countryUpperCase == "CN" || countryUpperCase == "DUANG") {
                        language = if (provider == providerMicrosoft || provider == providerVolcengine) "zh-Hans" else "zh-CN"
                    } else if (countryUpperCase == "TW" || countryUpperCase == "HK") {
                        language = if (provider == providerMicrosoft || provider == providerVolcengine) "zh-Hant" else "zh-TW"
                    }
                }
                providerTelegram -> language = TelegramAPITranslator.convertLanguageCode(language, country)
            }

            val translator: Translator = when (provider) {
                providerGoogle, providerGoogleCN -> GoogleAppTranslator
                providerGoogle2 -> GoogleCloud2Translator
                providerLingo -> LingoTranslator
                providerMicrosoft -> MicrosoftTranslator
                providerVolcengine -> VolcengineTranslator
                providerDeepL -> DeepLXTranslator
                providerDeepLOfficial -> DeepLTranslator
                providerDeepLFree -> DeepLFreeTranslator
                providerTelegram -> TelegramAPITranslator
                providerTranSmart -> TranSmartTranslator
                providerLLM -> LLMTranslator
                else -> GoogleAppTranslator
            }

            val result = translator.doTranslate("auto", language, query, context).also {
                if (context.isEmpty()) {
                    to.transDb.save(query, it)
                }
            }

            return result
        }

        val availableLocaleList: Array<Locale> = Locale.getAvailableLocales().also {
            Arrays.sort(it, Comparator.comparing(Locale::toString))
        }

        @JvmStatic
        @JvmOverloads
        fun showTargetLangSelect(anchor: View, input: Boolean = false, full: Boolean = false, callback: (Locale) -> Unit) {
            val builder = PopupBuilder(anchor)

            val locales: MutableList<Locale> = if (full) {
                availableLocaleList.filter { it.variant.isBlank() }.toMutableList()
            } else {
                LocaleController.getInstance().languages.asSequence().map { it.pluralLangCode }.toSet()
                    .filter { !it.lowercase(Locale.getDefault()).contains("duang") }.map { it.code2Locale }
                    .toMutableList()
            }

            val firstLocale = if (!input) {
                LocaleController.getInstance().currentLocale
            } else {
                Locale.ENGLISH
            }

            locales.remove(firstLocale)
            locales.add(0, firstLocale)

            val preferredLocales = NaConfig.preferredTranslateTargetLangList.mapNotNull { lang ->
                try {
                    lang.code2Locale
                } catch (e: Exception) {
                    null
                }
            }

            if (preferredLocales.isNotEmpty()) {
                locales.removeAll(preferredLocales.toSet())
                locales.addAll(1, preferredLocales)
            }

            val currLocale = LocaleController.getInstance().currentLocale
            val localeNames = arrayOfNulls<String>(if (full) locales.size else locales.size + 1)

            for (i in locales.indices) {
                localeNames[i] = if (i == 0) {
                    LocaleController.getString(R.string.Default) + " ( " + locales[i].getDisplayName(currLocale) + " )"
                } else if (i <= preferredLocales.size) {
                    "⭐ " + locales[i].getDisplayName(currLocale)
                } else {
                    locales[i].getDisplayName(currLocale)
                }
            }

            if (!full) {
                localeNames[localeNames.size - 1] = LocaleController.getString(R.string.More)
            }

            builder.setItems(
                localeNames.filterIsInstance<CharSequence>().toTypedArray()
            ) { index: Int, _ ->
                if (!full && index == locales.size) {
                    showTargetLangSelect(anchor, input, true, callback)
                } else {
                    callback(locales[index])
                }
            }

            builder.show()
        }

        @JvmStatic
        fun showProviderSelect(anchor: View, callback: (Int) -> Unit) {
            val builder = PopupBuilder(anchor)
            val providers = listOf(
                Pair(providerGoogle, LocaleController.getString(R.string.ProviderGoogleTranslate)),
                Pair(providerGoogleCN, LocaleController.getString(R.string.ProviderGoogleTranslateCN)),
                Pair(providerGoogle2, LocaleController.getString(R.string.ProviderGoogleTranslate) + " 2"),
                Pair(providerLingo, LocaleController.getString(R.string.ProviderLingocloud)),
                Pair(providerMicrosoft, LocaleController.getString(R.string.ProviderMicrosoftTranslator)),
                Pair(providerVolcengine, LocaleController.getString(R.string.ProviderVolcengineTranslate)),
                Pair(providerDeepL, LocaleController.getString(R.string.ProviderDeepLxTranslate)),
                Pair(providerTelegram, LocaleController.getString(R.string.ProviderTelegramAPI)),
                Pair(providerTranSmart, LocaleController.getString(R.string.ProviderTranSmartTranslate)),
                Pair(providerLLM, LocaleController.getString(R.string.ProviderLLMTranslate)),
                Pair(providerDeepLOfficial, LocaleController.getString(R.string.ProviderDeepLTranslate)),
                Pair(providerDeepLFree, LocaleController.getString(R.string.ProviderDeepLFreeTranslate))
            )
            val names: List<CharSequence?> = providers.map { it.second }
            builder.setItems(names) { index, _ ->
                callback(providers[index].first)
            }
            builder.show()
        }

        @JvmStatic
        fun getInputTranslateLangForChat(chatId: Long): String {
            val key = "translateInputLang_$chatId"
            return NekoConfig.getPreferences().getString(key, null)
                ?: NekoConfig.translateInputLang.String()
        }

        @JvmStatic
        fun setInputTranslateLangForChat(chatId: Long, langCode: String) {
            val key = "translateInputLang_$chatId"
            NekoConfig.getPreferences().edit().putString(key, langCode).apply()
        }

        @JvmStatic
        fun getInputTranslateLangLocaleForChat(chatId: Long): Locale {
            return getInputTranslateLangForChat(chatId).code2Locale
        }

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String, provider: Int): String =
            translate(to, provider, query)

        @Throws(Exception::class)
        @JvmOverloads
        suspend fun translate(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            provider: Int = 0,
            context: List<String> = emptyList()
        ): TLRPC.TL_textWithEntities {
            val source = TLRPC.TL_textWithEntities().also {
                it.text = query
                it.entities = entities ?: ArrayList()
            }
            val queryText = MessageTransText.queryTextOf(source)
            val effectiveProvider = if (provider in 1..12) provider else NekoConfig.translationProvider.Int()
            val translatedText = translateWithProvider(to, queryText, context, effectiveProvider)
            return MessageTransText.merge(source, translatedText, "")
        }

        @JvmStatic
        fun translate(
            to: Locale,
            query: String,
            provider: Int,
            translateCallBack: TranslateCallBack
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val result = if (provider in 1..12) translate(to, provider, query) else translate(to, query)
                    UIUtil.runOnUIThread {
                        translateCallBack.onSuccess(result)
                    }
                }.onFailure {
                    translateCallBack.onFailed(
                        it is UnsupportedOperationException,
                        it.message ?: it.javaClass.simpleName
                    )
                }
            }
        }

        @JvmStatic
        fun translate(
            to: Locale,
            query: String,
            translateCallBack: TranslateCallBack
        ) {
            translate(to, query, 0, translateCallBack)
        }

        @JvmStatic
        fun translate(
            query: String,
            translateCallBack: TranslateCallBack
        ) {
            translate(
                to = NekoConfig.translateToLang.String()?.code2Locale
                    ?: LocaleController.getInstance().currentLocale,
                query = query,
                provider = 0,
                translateCallBack = translateCallBack
            )
        }

        @JvmStatic
        fun translate(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            provider: Int,
            translateCallBack: TranslateCallBack2
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val result = translate(to, query, entities, provider)
                    UIUtil.runOnUIThread {
                        translateCallBack.onSuccess(result)
                    }
                }.onFailure {
                    UIUtil.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        fun translate(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            translateCallBack: TranslateCallBack2
        ) {
            translate(to, query, entities, 0, translateCallBack)
        }

        @JvmStatic
        fun translate(
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            translateCallBack: TranslateCallBack2
        ) {
            translate(
                to = NekoConfig.translateToLang.String()?.code2Locale
                    ?: LocaleController.getInstance().currentLocale,
                query = query,
                entities = entities,
                provider = 0,
                translateCallBack = translateCallBack
            )
        }

        @JvmStatic
        fun translateWithContext(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            context: String?,
            translateCallBack: TranslateCallBack2
        ) {
            translateWithContext(to, query, entities, context, 0, translateCallBack)
        }

        @JvmStatic
        fun translateWithContext(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>?,
            context: String?,
            provider: Int,
            translateCallBack: TranslateCallBack2
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val source = TLRPC.TL_textWithEntities().also {
                        it.text = query
                        it.entities = entities ?: ArrayList()
                    }
                    val queryText = MessageTransText.queryTextOf(source)
                    val effectiveProvider = if (provider in 1..12) provider else NekoConfig.translationProvider.Int()
                    val contextList = if (!context.isNullOrBlank()) listOf(context) else emptyList()
                    val translatedText = translateWithProvider(to, queryText, contextList, effectiveProvider)
                    val result = MessageTransText.merge(source, translatedText, "")
                    UIUtil.runOnUIThread {
                        translateCallBack.onSuccess(result)
                    }
                }.onFailure {
                    UIUtil.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun translatePoll(
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale,
            query: TranslateController.PollText,
            translateCallBack: TranslateCallBack3
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val provider = NekoConfig.translationProvider.Int()
                    val translatedPoll = TranslateController.PollText()
                    if (query.question != null) {
                        val questionSource = query.question
                        val qText = MessageTransText.queryTextOf(questionSource)
                        val qTrans = translateWithProvider(to, qText, emptyList(), provider)
                        translatedPoll.question = MessageTransText.merge(questionSource, qTrans, "")
                    }
                    for (answer in query.answers) {
                        val answerSource = answer.text
                        val aText = MessageTransText.queryTextOf(answerSource)
                        val aTrans = translateWithProvider(to, aText, emptyList(), provider)
                        val translatedAnswer = TLRPC.TL_pollAnswer().apply {
                            text = MessageTransText.merge(answerSource, aTrans, "")
                            option = answer.option
                        }
                        translatedPoll.answers.add(translatedAnswer)
                    }
                    if (query.solution != null) {
                        val solutionSource = query.solution
                        val sText = MessageTransText.queryTextOf(solutionSource)
                        val sTrans = translateWithProvider(to, sText, emptyList(), provider)
                        translatedPoll.solution = MessageTransText.merge(solutionSource, sTrans, "")
                    }

                    UIUtil.runOnUIThread { translateCallBack.onSuccess(translatedPoll) }
                }.onFailure {
                    UIUtil.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun translateShowAlert(
            ctx: Context,
            query: String,
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale
        ) {
            val db = currentTarget()
            if (db.contains(query)) {
                showCopyAlert(ctx, db.query(query)!!)
            } else {
                val pro = showProgress(ctx)
                pro.show()
                translate(to, query, object : TranslateCallBack {
                    override fun onSuccess(translation: String) {
                        pro.dismiss()
                        showCopyAlert(ctx, translation)
                    }

                    override fun onFailed(unsupported: Boolean, message: String) {
                        pro.dismiss()
                        showTransFailedDialog(ctx, unsupported, message) {
                            pro.show()
                            translate(to, query, this)
                        }
                    }
                })
            }
        }

        interface TranslateCallBack {
            fun onSuccess(translation: String)
            fun onFailed(unsupported: Boolean, message: String)
        }

        interface TranslateCallBack2 {
            fun onSuccess(finalText: org.telegram.tgnet.TLRPC.TL_textWithEntities)
            fun onFailed(unsupported: Boolean, message: String)
        }

        interface TranslateCallBack3 {
            fun onSuccess(pollText: org.telegram.messenger.TranslateController.PollText)
            fun onFailed(unsupported: Boolean, message: String)
        }
    }
}
