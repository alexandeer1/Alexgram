package tw.nekomimi.nekogram.parts

import org.telegram.messenger.MessageObject
import org.telegram.tgnet.tl.TL_iv
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.translate.transDb
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.WeakHashMap

object RichMessageTransHelper {
    private const val MAX_CACHE_SIZE = 2048

    private data class CacheKey(val language: String, val text: String)

    private val cache = object : LinkedHashMap<CacheKey, String>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, String>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val messageCache = WeakHashMap<TL_iv.RichMessage, MutableMap<CacheKey, String>>()

    @JvmStatic
    fun isTranslated(messageObject: MessageObject?): Boolean {
        val message = messageObject?.messageOwner ?: return false
        return (message.rich_message != null &&
                message.translated &&
                !message.translatedToLanguage.isNullOrBlank()) ||
                (messageObject.translatedLocally && message.rich_message != null)
    }

    @JvmStatic
    fun getTranslatedLanguage(messageObject: MessageObject?): String? {
        return if (isTranslated(messageObject)) {
            val lang = messageObject?.messageOwner?.translatedToLanguage
            if (!lang.isNullOrBlank()) {
                normalizeLanguage(lang)
            } else {
                normalizeLanguage(NekoConfig.translateToLang.String())
            }
        } else {
            null
        }
    }

    @JvmStatic
    fun getCachedTranslation(messageObject: MessageObject?, text: String?): String? {
        val language = getTranslatedLanguage(messageObject) ?: return null
        val richMessage = messageObject?.messageOwner?.rich_message
        if (text.isNullOrEmpty()) return null
        return getCachedTranslation(richMessage, language, text)
    }

    @JvmStatic
    fun getCachedTranslation(richMessage: TL_iv.RichMessage?, language: String, text: String): String? {
        val normalizedLanguage = normalizeLanguage(language)
        synchronized(cache) {
            val key = CacheKey(normalizedLanguage, text)
            val cached = richMessage?.let { messageCache[it]?.get(key) } ?: cache[key]
            if (cached != null) return cached
        }
        return try {
            normalizedLanguage.code2Locale.transDb.query(text)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun putCachedTranslation(richMessage: TL_iv.RichMessage?, language: String, text: String, translated: String) {
        val normalizedLanguage = normalizeLanguage(language)
        synchronized(cache) {
            val key = CacheKey(normalizedLanguage, text)
            cache[key] = translated
            if (richMessage != null) {
                messageCache.getOrPut(richMessage) { HashMap() }[key] = translated
            }
        }
        try {
            normalizedLanguage.code2Locale.transDb.save(text, translated)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun hasFullCache(richMessage: TL_iv.RichMessage?, language: String): Boolean {
        if (richMessage == null) return false
        val texts = collectPlainTexts(richMessage)
        if (texts.isEmpty()) return false
        val normalizedLanguage = normalizeLanguage(language)
        val db = try { normalizedLanguage.code2Locale.transDb } catch (_: Exception) { null }
        synchronized(cache) {
            val currentMessageCache = messageCache.getOrPut(richMessage) { HashMap() }
            texts.forEach { text ->
                val key = CacheKey(normalizedLanguage, text)
                val translated = currentMessageCache[key] ?: cache[key] ?: db?.query(text) ?: return false
                currentMessageCache[key] = translated
            }
            return true
        }
    }

    @JvmStatic
    fun hasActiveTranslation(messageObject: MessageObject?): Boolean {
        val language = getTranslatedLanguage(messageObject) ?: return false
        return hasFullCache(messageObject?.messageOwner?.rich_message, language)
    }

    @JvmStatic
    fun collectPlainTexts(richMessage: TL_iv.RichMessage?): LinkedHashSet<String> {
        val texts = LinkedHashSet<String>()
        richMessage?.blocks?.forEach { collectBlock(it, texts) }
        return texts
    }

    @JvmStatic
    fun normalizeLanguage(language: String?): String {
        return language.orEmpty().lowercase()
    }

    private fun collectCaption(caption: TL_iv.PageCaption?, out: MutableSet<String>) {
        collectRichText(caption?.text, out)
        collectRichText(caption?.credit, out)
    }

    private fun collectBlock(block: TL_iv.PageBlock?, out: MutableSet<String>) {
        if (block == null) return
        when (block) {
            is TL_iv.pageBlockPreformatted -> return
            is TL_iv.pageBlockBlockquote -> {
                collectRichText(block.text, out)
                return
            }
            is TL_iv.pageBlockBlockquoteBlocks -> {
                block.blocks.forEach { collectBlock(it, out) }
                return
            }
        }

        collectRichText(block.text, out)
        when (block) {
            is TL_iv.pageBlockAuthorDate -> collectRichText(block.author, out)
            is TL_iv.pageBlockList -> block.items.forEach { item ->
                when (item) {
                    is TL_iv.TL_pageListItemText -> collectRichText(item.text, out)
                    is TL_iv.TL_pageListItemBlocks -> item.blocks.forEach { collectBlock(it, out) }
                }
            }
            is TL_iv.pageBlockPullquote -> collectRichText(block.caption, out)
            is TL_iv.pageBlockCover -> collectBlock(block.cover, out)
            is TL_iv.pageBlockEmbedPost -> {
                block.blocks.forEach { collectBlock(it, out) }
                collectCaption(block.caption, out)
            }
            is TL_iv.pageBlockCollage -> {
                block.items.forEach { collectBlock(it, out) }
                collectCaption(block.caption, out)
            }
            is TL_iv.pageBlockSlideshow -> {
                block.items.forEach { collectBlock(it, out) }
                collectCaption(block.caption, out)
            }
            is TL_iv.pageBlockTable -> {
                collectRichText(block.title, out)
                block.rows.forEach { row ->
                    row.cells.forEach { cell ->
                        collectRichText(cell.text, out)
                    }
                }
            }
            is TL_iv.pageBlockOrderedList -> block.items.forEach { item ->
                when (item) {
                    is TL_iv.TL_pageListOrderedItemText -> collectRichText(item.text, out)
                    is TL_iv.TL_pageListOrderedItemBlocks -> item.blocks.forEach { collectBlock(it, out) }
                }
            }
            is TL_iv.pageBlockDetails -> {
                block.blocks.forEach { collectBlock(it, out) }
                collectRichText(block.title, out)
            }
            is TL_iv.pageBlockRelatedArticles -> collectRichText(block.title, out)
            else -> collectCaption(block.caption, out)
        }
    }

    private fun collectRichText(richText: TL_iv.RichText?, out: MutableSet<String>) {
        when (richText) {
            null,
            is TL_iv.textEmpty,
            is TL_iv.textEmail,
            is TL_iv.textPhone,
            is TL_iv.textMention,
            is TL_iv.textHashtag,
            is TL_iv.textBotCommand,
            is TL_iv.textAutoUrl,
            is TL_iv.textAutoEmail,
            is TL_iv.textAutoPhone,
            is TL_iv.textBankCard,
            is TL_iv.textMentionName -> return
            is TL_iv.textPlain -> {
                val text = richText.text
                if (!text.isNullOrBlank()) {
                    out.add(text)
                }
            }
            is TL_iv.textConcat -> richText.texts.forEach { collectRichText(it, out) }
            else -> collectRichText(richText.text, out)
        }
    }
}
