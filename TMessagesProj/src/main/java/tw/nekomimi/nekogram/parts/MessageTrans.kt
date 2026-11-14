package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.ChatActivityInterface
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.helpers.MessageHelper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.translate.locale2code
import tw.nekomimi.nekogram.translate.source.LLMTranslator
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.AppScope
import xyz.nextalone.nagram.NaConfig
import java.util.Locale
import java.util.WeakHashMap

const val TRANSLATE_MODE_WITH_ORIGINAL_MANUAL_ONLY = 1
const val TRANSLATE_MODE_WITH_ORIGINAL_ALL = 2

const val TRANSLATION_SEPARATOR = "\n\n--------\n\n"

private val ChatActivityInterface.translateController: TranslateController
    get() = messagesController.translateController

private val runningJobs = WeakHashMap<ChatActivityInterface, MutableList<Job>>()

fun ChatActivityInterface.cancelTranslations() {
    synchronized(runningJobs) {
        runningJobs.remove(this)?.forEach { it.cancel() }
    }
}

fun ChatActivityInterface.translateMessagesWithProvider(provider: Int) =
    translateMessages(
        targetLocale = NekoConfig.translateToLang.String()?.code2Locale ?: Locale.getDefault(),
        provider = provider
    )

fun ChatActivityInterface.translateMessagesWithMessages(messages: List<MessageObject>) =
    translateMessages(messages = messages)

fun ChatActivityInterface.translateMessages(provider: Int) =
    translateMessages(
        targetLocale = NekoConfig.translateToLang.String()?.code2Locale ?: Locale.getDefault(),
        provider = provider
    )

fun ChatActivityInterface.translateMessages(messages: List<MessageObject>) =
    translateMessages(
        targetLocale = NekoConfig.translateToLang.String()?.code2Locale ?: Locale.getDefault(),
        provider = 0,
        messages = messages
    )

fun ChatActivityInterface.translateMessages(messages: List<MessageObject>, autoTranslate: Boolean) =
    translateMessages(
        targetLocale = NekoConfig.translateToLang.String()?.code2Locale ?: Locale.getDefault(),
        provider = 0,
        messages = messages
    )

@JvmOverloads
fun ChatActivityInterface.translateMessages(
    targetLocale: Locale = NekoConfig.translateToLang.String()?.code2Locale ?: Locale.getDefault(),
    provider: Int = 0,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages
        ?: emptyList()
) {
    if (messages.any { translateController.isTranslating(it) }) return

    val targetLanguage = targetLocale.toLanguageTag()
    val translatorMode = NaConfig.translatorMode.Int()
    val canReuseCache = provider == 0

    // Check if all messages are already translated, hide translation if so
    val allTranslated = messages.all { msg ->
        if (msg.isRich) {
            RichMessageTransHelper.hasActiveTranslation(msg)
        } else {
            msg.translatedLocally || msg.isTranslated || msg.messageOwner?.translated == true
        }
    }
    if (allTranslated) {
        messages.forEach { msg ->
            hideTranslation(msg, translatorMode)
        }
        return
    }

    // Try to use cached translation if available
    if (canReuseCache) {
        applyCachedTranslations(messages, targetLanguage, translatorMode)
    }

    // Filter messages that actually need translation
    val messagesToTranslate = messages.filter { msg ->
        msg.needsTranslation(canReuseCache, targetLanguage, translateController)
    }

    if (messagesToTranslate.isEmpty()) return

    // Mark messages as translating -> triggers native shimmering text selection highlight
    messagesToTranslate.forEach { msg ->
        getMessageHelper().detectLanguageNow(msg)
        translateController.addAsTranslatingItem(msg)
        translateController.addAsManualTranslate(msg)
        getNotificationCenter().postNotificationName(NotificationCenter.messageTranslating, msg)
    }

    // Perform translations concurrently
    val dispatcher = Dispatchers.IO.limitedParallelism(5)

    val job = AppScope.io.launch {
        supervisorScope {
            messagesToTranslate.map { msg ->
                launch(dispatcher) {
                    if (!isActive) return@launch
                    translateSingleMessage(
                        msg,
                        provider,
                        targetLocale,
                        targetLanguage,
                        translatorMode,
                        canReuseCache
                    )
                }
            }.joinAll()
        }
    }

    synchronized(runningJobs) {
        runningJobs.getOrPut(this) { ArrayList() }.add(job)
    }
}

private suspend fun ChatActivityInterface.translateSingleMessage(
    msg: MessageObject,
    provider: Int,
    targetLocale: Locale,
    targetLanguage: String,
    translatorMode: Int,
    canReuseCache: Boolean,
) {
    val needsSummary = msg.needsSummaryTranslation(canReuseCache, targetLanguage)
    val needsOriginal = msg.needsOriginalTranslation(canReuseCache, targetLanguage)

    val shouldUseContext = shouldUseLlmContext(provider)
    val llmContext = if (shouldUseContext) {
        buildLlmContext(this@translateSingleMessage, msg)
    } else null

    // Translate summary if needed
    if (needsSummary) {
        val success = translateSummary(msg, targetLocale, provider, llmContext)
        if (!success) return
    }

    // Translate original content if needed
    if (needsOriginal) {
        val success = when {
            msg.isRich -> translateRichMessageContent(msg, targetLocale)
            msg.isPoll -> translatePoll(msg, targetLocale, provider)
            else -> translateMessageContent(
                msg,
                targetLocale,
                provider,
                translatorMode,
                llmContext,
            )
        }
        if (!success) return

        msg.messageOwner.translatedToLanguage =
            targetLocale.locale2code.lowercase(Locale.getDefault())
    }

    // Mark translation as complete
    finalizeTranslation(msg, translatorMode)
}

private suspend fun ChatActivityInterface.translateSummary(
    msg: MessageObject,
    targetLocale: Locale,
    provider: Int,
    llmContext: String?,
): Boolean {
    val summaryText = msg.messageOwner.summaryText ?: return false

    // Translate summary
    val translatedSummary = runCatching {
        translateText(targetLocale, summaryText.text, summaryText.entities, provider, llmContext)
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) { newProvider ->
            val retryProvider = if (newProvider != 0) newProvider else provider
            translateMessages(targetLocale, retryProvider, listOf(msg))
        }
        return false
    }

    // Store translated summary
    msg.messageOwner.translatedSummaryText = translatedSummary
    msg.messageOwner.translatedSummaryLanguage = targetLocale.locale2code.lowercase(Locale.getDefault())

    return true
}

private suspend fun ChatActivityInterface.translateRichMessageContent(
    msg: MessageObject,
    target: Locale
): Boolean {
    val richMessage = msg.messageOwner.rich_message ?: return false
    val targetLanguage = target.locale2code
    val texts = RichMessageTransHelper.collectPlainTexts(richMessage)
        .filter { RichMessageTransHelper.getCachedTranslation(richMessage, targetLanguage, it) == null }

    val dispatcher = Dispatchers.IO.limitedParallelism(5)
    return runCatching {
        supervisorScope {
            texts.map { text ->
                async(dispatcher) {
                    if (!isActive) return@async
                    val translated = Translator.translate(target, text, Translator.providerGoogle)
                    RichMessageTransHelper.putCachedTranslation(richMessage, targetLanguage, text, translated)
                }
            }.awaitAll()
        }
        RichMessageTransHelper.hasFullCache(richMessage, targetLanguage)
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) { newProvider ->
            val retryProvider = if (newProvider != 0) newProvider else 0
            translateMessages(target, retryProvider, listOf(msg))
        }
        false
    }
}

private suspend fun ChatActivityInterface.translatePoll(
    msg: MessageObject,
    target: Locale,
    provider: Int
): Boolean {
    val poll = (msg.messageOwner.media as TLRPC.TL_messageMediaPoll).poll

    // Translate question
    val translatedQuestion = runCatching {
        Translator.translate(target, poll.question.text, provider)
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) { newProvider ->
            val retryProvider = if (newProvider != 0) newProvider else provider
            translateMessages(target, retryProvider, listOf(msg))
        }
        return false
    }
    poll.translatedQuestion = translatedQuestion

    // Translate answers
    for (answer in poll.answers) {
        val translatedAnswer = runCatching {
            Translator.translate(target, answer.text.text, provider)
        }.getOrElse { e ->
            handleTranslationError(parentActivity, e, msg, translateController) { newProvider ->
                val retryProvider = if (newProvider != 0) newProvider else provider
                translateMessages(target, retryProvider, listOf(msg))
            }
            return false
        }
        answer.translatedText = translatedAnswer
    }

    return true
}

private suspend fun ChatActivityInterface.translateMessageContent(
    msg: MessageObject,
    target: Locale,
    provider: Int,
    translatorMode: Int,
    llmContext: String?
): Boolean {
    val buttonsToTranslate = ArrayList<String>()
    if (msg.messageOwner.reply_markup is TLRPC.TL_replyInlineMarkup) {
        val inlineMarkup = msg.messageOwner.reply_markup as TLRPC.TL_replyInlineMarkup
        for (i in inlineMarkup.rows.indices) {
            val row = inlineMarkup.rows[i]
            for (j in row.buttons.indices) {
                val button = row.buttons[j]
                if (!button.text.isNullOrEmpty()) {
                    buttonsToTranslate.add(button.text)
                }
            }
        }
    }

    val baseText = if (!msg.messageOwner.message.isNullOrEmpty()) {
        msg.messageOwner.message
    } else {
        msg.voiceTranscription?.toString() ?: ""
    }

    val query = if (buttonsToTranslate.isEmpty()) {
        baseText
    } else {
        buildString {
            append(baseText)
            for (btn in buttonsToTranslate) {
                append("\n<<<<\n").append(btn)
            }
        }
    }

    val result = runCatching {
        translateText(
            target,
            query,
            msg.messageOwner.entities,
            provider,
            llmContext
        )
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) { newProvider ->
            val retryProvider = if (newProvider != 0) newProvider else provider
            translateMessages(target, retryProvider, listOf(msg))
        }
        return false
    }

    if (buttonsToTranslate.isNotEmpty() && result.text != null) {
        val parts = result.text.toString().split("\n<<<<\n")
        val mainText = parts[0]
        result.text = mainText
        msg.messageOwner.translatedButtons = HashMap()
        msg.messageOwner.translatedButtonsLanguage = target.locale2code.lowercase(Locale.getDefault())
        for (i in 0 until minOf(buttonsToTranslate.size, parts.size - 1)) {
            msg.messageOwner.translatedButtons[buttonsToTranslate[i]] = parts[i + 1].trim()
        }
    }

    val keepOriginal = MessageHelper.shouldKeepOriginalForManualTranslation(translatorMode)
    msg.messageOwner.translatedMessage = MessageHelper.buildTranslatedDisplayText(
        baseText,
        result,
        keepOriginal
    )
    if (msg.isVoice || !msg.messageOwner.voiceTranscription.isNullOrEmpty()) {
        msg.messageOwner.translatedVoiceTranscription = result
    }
    msg.messageOwner.translatedText = result

    return true
}

private suspend fun ChatActivityInterface.finalizeTranslation(
    msg: MessageObject,
    translatorMode: Int
) {
    translateController.removeAsTranslatingItem(msg)
    translateController.addAsManualTranslate(msg)
    msg.messageOwner.translated = true
    msg.translated = true
    msg.translatedLocally = true

    // Persist translation to storage
    if (!msg.isRich && !BuildVars.LOGS_ENABLED) {
        MessagesStorage.getInstance(getCurrentAccount()).updateMessageCustomParams(
            msg.dialogId, msg.messageOwner
        )
    }

    // Update UI
    val keepOriginal = MessageHelper.shouldKeepOriginalForManualTranslation(translatorMode)
    if (msg.isRich) {
        withContext(Dispatchers.Main) {
            getMessageHelper().resetMessageContent(getDialogId(), msg)
        }
    } else if (msg.messageOwner.summarizedOpen) {
        AndroidUtilities.runOnUIThread {
            postTranslatedNotification(msg)
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0)
        }
    } else if (msg.messageOwner.translatedText != null && !keepOriginal) {
        AndroidUtilities.runOnUIThread {
            postTranslatedNotification(msg)
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0)
        }
    } else {
        withContext(Dispatchers.Main) {
            clearTranslated(msg, getCurrentAccount(), false)
            getMessageHelper().resetMessageContent(getDialogId(), msg)
        }
    }
}

private fun ChatActivityInterface.postTranslatedNotification(msg: MessageObject) {
    if (msg.messageOwner.summarizedOpen) {
        getNotificationCenter().postNotificationName(
            NotificationCenter.messageTranslated, msg, true
        )
    } else {
        getNotificationCenter().postNotificationName(
            NotificationCenter.messageTranslated, msg
        )
    }
}

private fun ChatActivityInterface.hideTranslation(
    msg: MessageObject,
    translatorMode: Int,
) {
    translateController.removeAsTranslatingItem(msg)
    translateController.removeAsManualTranslate(msg)

    val owner = msg.messageOwner
    if (owner != null) {
        owner.translated = false
        owner.translatedMessage = null
        owner.translatedText = null
        owner.translatedPoll = null
        owner.translatedButtons = null
        owner.translatedButtonsLanguage = null
        owner.translatedVoiceTranscription = null
        owner.translatedSummaryText = null
        owner.translatedSummaryLanguage = null
        owner.translatedToLanguage = null
        if (msg.isPoll) {
            val media = MessageObject.getMedia(owner) as? TLRPC.TL_messageMediaPoll
            media?.poll?.translatedQuestion = null
            media?.poll?.answers?.forEach { it.translatedText = null }
        }
    }
    msg.translated = false
    msg.translatedLocally = false
    msg.caption = null

    // Persist cleared state to storage
    if (!msg.isRich && !BuildVars.LOGS_ENABLED && owner != null) {
        MessagesStorage.getInstance(getCurrentAccount()).updateMessageCustomParams(
            msg.dialogId, owner
        )
    }

    if (msg.isRich) {
        AndroidUtilities.runOnUIThread {
            getMessageHelper().resetMessageContent(getDialogId(), msg)
            postTranslatedNotification(msg)
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0)
        }
        return
    }

    AndroidUtilities.runOnUIThread {
        if (owner != null && !owner.message.isNullOrEmpty()) {
            msg.applyNewText(owner.message)
            msg.generateCaption()
        }
        msg.forceUpdate = true
        msg.updateTranslation(true)
        getMessageHelper().resetMessageContent(getDialogId(), msg)
        postTranslatedNotification(msg)
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, 0)
        (this as? org.telegram.ui.ChatActivity)?.updateVisibleRows()
    }
}

private fun ChatActivityInterface.applyCachedTranslations(
    messages: List<MessageObject>,
    targetLanguage: String,
    translatorMode: Int,
) {
    val hasCachedTranslation = messages.any { msg ->
        msg.isTranslatedPoll() ||
            (msg.isRich && RichMessageTransHelper.hasFullCache(msg.messageOwner.rich_message, targetLanguage)) ||
            (msg.messageOwner.translatedText?.text?.isNotEmpty() == true) ||
            (
                msg.messageOwner.summarizedOpen &&
                    msg.messageOwner.translatedSummaryText?.text?.isNotEmpty() == true &&
                    !MessageHelper.isLegacyTranslatedSummary(msg.messageOwner.summaryText, msg.messageOwner.translatedSummaryText)
            )
    }
    if (!hasCachedTranslation) return

    messages.forEach { msg ->
        if (msg.isRich) {
            if (!RichMessageTransHelper.hasFullCache(msg.messageOwner.rich_message, targetLanguage)) {
                return@forEach
            }
            translateController.removeAsTranslatingItem(msg)
            translateController.addAsManualTranslate(msg)
            msg.messageOwner.translated = true
            msg.translated = true
            msg.translatedLocally = true
            msg.messageOwner.translatedToLanguage = targetLanguage
            AndroidUtilities.runOnUIThread {
                getMessageHelper().resetMessageContent(getDialogId(), msg)
            }
            return@forEach
        }

        if (!msg.matchesCachedLanguage(targetLanguage)) return@forEach

        translateController.removeAsTranslatingItem(msg)
        translateController.addAsManualTranslate(msg)
        msg.messageOwner.translated = true
        msg.translated = true
        msg.translatedLocally = true
        msg.messageOwner.translatedMessage = if (!msg.messageOwner.summarizedOpen && msg.messageOwner.translatedText != null) {
            MessageHelper.buildTranslatedDisplayText(
                msg.messageOwner.message,
                msg.messageOwner.translatedText,
                MessageHelper.shouldKeepOriginalForManualTranslation(translatorMode)
            )
        } else {
            null
        }

        AndroidUtilities.runOnUIThread {
            when {
                msg.messageOwner.summarizedOpen -> {
                    postTranslatedNotification(msg)
                    getNotificationCenter().postNotificationName(
                        NotificationCenter.updateInterfaces, 0
                    )
                }

                msg.isPoll -> {
                    getMessageHelper().resetMessageContent(getDialogId(), msg)
                }

                MessageHelper.shouldKeepOriginalForManualTranslation(translatorMode) -> {
                    getMessageHelper().resetMessageContent(getDialogId(), msg)
                }

                else -> {
                    getNotificationCenter().postNotificationName(
                        NotificationCenter.messageTranslating, msg
                    )
                    postTranslatedNotification(msg)
                }
            }
        }
    }
}

private fun MessageObject.needsTranslation(
    canReuseCache: Boolean,
    targetLanguage: String,
    controller: TranslateController
): Boolean {
    if (controller.isTranslating(this)) return false

    val hasRichText = isRich && RichMessageTransHelper.collectPlainTexts(messageOwner.rich_message).isNotEmpty()
    val hasVoiceTranscription = !voiceTranscription.isNullOrEmpty()

    if (!(hasRichText || isPoll || messageOwner.message.isNotEmpty() || hasVoiceTranscription)) return false

    val needsSummary = needsSummaryTranslation(canReuseCache, targetLanguage)
    val needsOriginal = needsOriginalTranslation(canReuseCache, targetLanguage)

    return needsSummary || needsOriginal
}

private fun MessageObject.needsSummaryTranslation(
    canReuseCache: Boolean,
    targetLanguage: String
): Boolean {
    if (!messageOwner.summarizedOpen) return false

    return !canReuseCache ||
            messageOwner.translatedSummaryText?.text.isNullOrEmpty() ||
            MessageHelper.isLegacyTranslatedSummary(messageOwner.summaryText, messageOwner.translatedSummaryText) ||
            !messageOwner.translatedSummaryLanguage.equals(targetLanguage, ignoreCase = true)
}

private fun MessageObject.needsOriginalTranslation(
    canReuseCache: Boolean,
    targetLanguage: String
): Boolean {
    if (!canReuseCache) return true

    val languageMatches =
        messageOwner.translatedToLanguage.equals(targetLanguage, ignoreCase = true)

    return when {
        isRich -> {
            !RichMessageTransHelper.isTranslated(this) || !languageMatches
                    || !RichMessageTransHelper.hasFullCache(messageOwner.rich_message, targetLanguage)
        }
        isPoll -> !isTranslatedPoll() || !languageMatches

        else -> messageOwner.translatedText?.text.isNullOrEmpty() || !languageMatches
    }
}

private fun MessageObject.matchesCachedLanguage(targetLanguage: String): Boolean {
    return if (messageOwner.summarizedOpen) {
        messageOwner.translatedSummaryText?.text?.isNotEmpty() == true &&
                !MessageHelper.isLegacyTranslatedSummary(messageOwner.summaryText, messageOwner.translatedSummaryText) &&
                messageOwner.translatedSummaryLanguage.equals(targetLanguage, ignoreCase = true)
    } else {
        messageOwner.translatedToLanguage.equals(targetLanguage, ignoreCase = true)
    }
}

private fun MessageObject.isTranslatedPoll() =
    isPoll && (messageOwner.media as? TLRPC.TL_messageMediaPoll)?.poll?.let { poll ->
        poll.translatedQuestion?.isNotEmpty() == true && poll.answers.all { it.translatedText?.isNotEmpty() == true }
    } ?: false

private suspend fun translateText(
    target: Locale,
    text: String,
    entities: ArrayList<TLRPC.MessageEntity>?,
    provider: Int,
    llmContext: String?
): TLRPC.TL_textWithEntities {
    val safeEntities = entities ?: ArrayList()
    val contextList = if (!llmContext.isNullOrBlank()) listOf(llmContext) else emptyList()
    return Translator.translate(target, text, safeEntities, provider, contextList)
}

private fun ChatActivityInterface.handleTranslationError(
    parentActivity: Context?,
    throwable: Throwable,
    messageObject: MessageObject,
    controller: TranslateController,
    retryAction: (newProvider: Int) -> Unit
) {
    controller.removeAsTranslatingItem(messageObject)
    controller.removeAsManualTranslate(messageObject)
    AndroidUtilities.runOnUIThread {
        getMessageHelper().resetMessageContent(getDialogId(), messageObject)
    }

    if (parentActivity != null) {
        AlertUtil.showTransFailedDialog(
            parentActivity,
            throwable is UnsupportedOperationException,
            throwable.message ?: throwable.javaClass.simpleName
        ) { newProvider ->
            retryAction(newProvider)
        }
    }
}

private fun clearTranslated(
    messageObject: MessageObject,
    currentAccount: Int,
    clearTranslatedText: Boolean
) {
    if (clearTranslatedText) {
        messageObject.messageOwner.translatedText = null
    }
    messageObject.messageOwner.translatedPoll = null
    MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
        messageObject.dialogId, messageObject.messageOwner
    )
}

private fun shouldUseLlmContext(provider: Int): Boolean {
    val effectiveProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
    return effectiveProvider == Translator.providerLLMTranslator && NaConfig.llmUseContext.Bool()
}

private fun extractLlmContextText(message: MessageObject): String? {
    val text = MessageHelper.getMessagePlainText(message, null)?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (MessageHelper.shouldSkipTranslation(text)) return null
    return text
}

private fun buildLlmContext(chatActivity: ChatActivityInterface, message: MessageObject): String? {
    val maxMessages = LLMTranslator.getContextMessageLimit()
    if (maxMessages <= 0) return null

    val seen = HashSet<String>(maxMessages * 2)

    fun extractWithDedup(candidate: MessageObject): String? {
        val key = "${candidate.dialogId}:${candidate.id}"
        if (!seen.add(key)) return null
        return extractLlmContextText(candidate)
    }

    // Reply chain
    val replyChainTexts = ArrayList<String>()
    var reply = message.replyMessageObject
    var isDirectReplyIncluded = false
    while (reply != null && replyChainTexts.size < maxMessages) {
        val text = extractWithDedup(reply)
        if (text != null) {
            replyChainTexts.add(text)
            if (reply === message.replyMessageObject) isDirectReplyIncluded = true
        }
        reply = reply.replyMessageObject
    }
    if (replyChainTexts.isNotEmpty()) {
        replyChainTexts.reverse() // oldest first
    }

    // Context messages
    val contextTexts = ArrayList<String>()
    val currentChat = chatActivity.getCurrentChat()
    if (currentChat == null || !ChatObject.isChannelAndNotMegaGroup(currentChat)) {
        val messages = chatActivity.getChatAdapterMessages()
        if (messages != null) {
            val index = messages.indexOf(message).takeIf { it >= 0 }
                ?: messages.indexOfFirst { it.dialogId == message.dialogId && it.id == message.id }
                    .takeIf { it >= 0 }
            if (index != null) {
                val remaining = maxMessages - replyChainTexts.size
                for (i in (index + 1) until messages.size) {
                    if (contextTexts.size >= remaining) break
                    val msg = messages[i]
                    if (msg.isAyuDeleted) continue
                    extractWithDedup(msg)?.let { contextTexts.add(it) }
                }
                if (contextTexts.isNotEmpty()) {
                    contextTexts.reverse() // oldest first
                }
            }
        }
    }

    if (replyChainTexts.isEmpty() && contextTexts.isEmpty()) return null

    return buildString {
        val dialogTitle = DialogObject.getName(chatActivity.getCurrentAccount(), message.dialogId).trim()
        if (dialogTitle.isNotEmpty()) {
            append("Chat: ").append(dialogTitle).append("\n\n")
        }

        if (replyChainTexts.isNotEmpty()) {
            append("Reply chain (oldest → newest):\n")
            replyChainTexts.forEachIndexed { index, text ->
                append("R").append(index + 1).append(": ").append(text).append('\n')
            }
            if (isDirectReplyIncluded) {
                append("\nMessage to translate replies to: R").append(replyChainTexts.size).append('\n')
            } else if (message.replyMessageObject != null) {
                append("\nMessage to translate is a reply, but the replied message text is unavailable.\n")
            }
            append('\n')
        }

        if (contextTexts.isNotEmpty()) {
            append("Other context messages (chronological):\n")
            contextTexts.forEachIndexed { index, text ->
                append("C").append(index + 1).append(": ").append(text).append('\n')
            }
        }
    }.trim().takeIf { it.isNotEmpty() }
}
