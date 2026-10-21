package tw.nekomimi.nekogram.translate.source

import xyz.nextalone.nagram.NaConfig

/**
 * Official DeepL Pro API translator (https://api.deepl.com).
 */
object DeepLTranslator : DeepLOfficialTranslatorBase() {

    override val logTag: String = "DeepL"

    override val baseUrl: String = "https://api.deepl.com"

    override val displayName: String = "DeepL"

    override val apiKey: String?
        get() = NaConfig.deepLApiKey.String()
}
