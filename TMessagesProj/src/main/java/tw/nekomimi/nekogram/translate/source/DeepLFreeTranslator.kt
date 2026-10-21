package tw.nekomimi.nekogram.translate.source

import xyz.nextalone.nagram.NaConfig

/**
 * Official DeepL Free API translator (https://api-free.deepl.com).
 */
object DeepLFreeTranslator : DeepLOfficialTranslatorBase() {

    override val logTag: String = "DeepLFree"

    override val baseUrl: String = "https://api-free.deepl.com"

    override val displayName: String = "DeepL Free"

    override val apiKey: String?
        get() = NaConfig.deepLFreeApiKey.String()
}
