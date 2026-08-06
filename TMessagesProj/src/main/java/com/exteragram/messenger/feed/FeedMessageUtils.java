package com.exteragram.messenger.feed;

import java.util.ArrayList;
import java.util.Calendar;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

public abstract class FeedMessageUtils {

    public static boolean isAllowedDoubleTapAction(int action) {
        return action == 2 || action == 3 || action == 4 || action == 6 || action == 9;
    }

    public static boolean isAllowedFeedOption(int option) {
        return option == 2 || option == 3 || option == 4 || option == 6 || option == 7 || option == 8 || option == 10 || option == 16 || option == 22 || option == 29 || option == 36 || option == 200 || option == 203 || option == 206;
    }

    public static boolean isPostRow(MessageObject messageObject) {
        return messageObject != null && !messageObject.isDateObject && messageObject.type != 6 && !messageObject.isSponsored();
    }

    public static MessageObject createUnreadDivider(int account, int stableId) {
        TLRPC.TL_message msg = new TLRPC.TL_message();
        msg.message = "";
        msg.id = 0;
        MessageObject messageObject = new MessageObject(account, msg, false, false);
        messageObject.type = 6;
        messageObject.contentType = 2;
        messageObject.stableId = stableId;
        return messageObject;
    }

    public static MessageObject createDateHeader(int account, MessageObject sourceMessage, int stableId) {
        TLRPC.TL_message msg = new TLRPC.TL_message();
        msg.message = LocaleController.formatDateChat(sourceMessage.messageOwner.date);
        msg.id = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(((long) sourceMessage.messageOwner.date) * 1000);
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        msg.date = (int) (calendar.getTimeInMillis() / 1000);
        MessageObject messageObject = new MessageObject(account, msg, false, false);
        messageObject.type = 10;
        messageObject.contentType = 1;
        messageObject.isDateObject = true;
        messageObject.stableId = stableId;
        return messageObject;
    }

    public static TLRPC.InputPeer getInputPeerForMessageRequest(MessagesController messagesController, long peerId, boolean isFeed, MessageObject messageObject) {
        if (isFeed && messageObject != null) {
            peerId = messageObject.getDialogId();
        }
        return messagesController.getInputPeer(peerId);
    }

    public static boolean matchesPlaybackNotification(int account, MessageObject messageObject, int messageId) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.getId() == messageId) {
            return true;
        }
        FeedController controller = FeedController.peekInstance(account);
        if (controller == null) {
            return false;
        }
        long realDialogId = controller.resolveRealDialogId(messageId);
        return realDialogId != 0 && realDialogId == messageObject.getDialogId() && controller.resolveRealMessageId(realDialogId, messageId) == messageObject.getRealId();
    }

    public static int getPlaybackScrollMessageId(boolean isFeed, long dialogId, MessageObject messageObject) {
        if (messageObject != null && messageObject.searchType == 4 && !isFeed && messageObject.getDialogId() == dialogId) {
            return messageObject.getRealId();
        }
        if (messageObject != null) {
            return messageObject.getId();
        }
        return 0;
    }

    public static MessageObject getForwardingMessageObject(int account, boolean isFeed, MessageObject messageObject) {
        if (!isFeed || messageObject == null || messageObject.getId() == messageObject.getRealId()) {
            return messageObject;
        }
        TLRPC.TL_message copied = copyMessage(messageObject.messageOwner);
        copied.id = messageObject.getRealId();
        copied.realId = 0;
        copied.dialog_id = messageObject.getDialogId();
        MessageObject newMsg = new MessageObject(account, copied, messageObject.replyMessageObject, null, null, null, null, false, true, 0L, false, false, false);
        newMsg.isPrimaryGroupMessage = messageObject.isPrimaryGroupMessage;
        newMsg.localGroupId = messageObject.localGroupId;
        newMsg.copyStableParams(messageObject);
        return newMsg;
    }

    public static MessageObject createReplacement(int account, long dialogId, MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        FeedController controller = FeedController.getInstance(account);
        MessageObject cached = controller.getMessage(dialogId, messageObject.getRealId());
        if (cached == null) {
            return null;
        }
        TLRPC.TL_message copied = copyMessage(messageObject.messageOwner);
        copied.id = cached.getId();
        copied.realId = cached.getRealId();
        copied.dialog_id = cached.getDialogId();
        MessageObject newMsg = new MessageObject(account, copied, cached.replyMessageObject, null, null, null, null, true, true, 0L, false, false, false, 4);
        newMsg.isPrimaryGroupMessage = cached.isPrimaryGroupMessage;
        newMsg.localGroupId = cached.localGroupId;
        newMsg.copyStableParams(cached);
        controller.replaceMessage(cached, newMsg);
        return newMsg;
    }

    public static ArrayList<MessageObject> createReplacements(int account, long dialogId, ArrayList<MessageObject> list) {
        ArrayList<MessageObject> replacements = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            MessageObject replacement = createReplacement(account, dialogId, list.get(i));
            if (replacement != null) {
                replacements.add(replacement);
            }
        }
        return replacements;
    }

    public static void filterAllowedOptions(ArrayList<CharSequence> titles, ArrayList<Integer> icons, ArrayList<Integer> ids) {
        for (int i = icons.size() - 1; i >= 0; i--) {
            if (!isAllowedFeedOption(icons.get(i))) {
                ids.remove(i);
                titles.remove(i);
                icons.remove(i);
            }
        }
    }

    public static void copyFeedPostLink(final ChatActivity chatActivity, MessageObject messageObject) {
        if (chatActivity == null || messageObject == null) {
            return;
        }
        TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-messageObject.getDialogId());
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messageObject.getRealId();
            req.channel = MessagesController.getInputChannel(chat);
            chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_exportedMessageLink) {
                    String link = ((TLRPC.TL_exportedMessageLink) response).link;
                    if (AndroidUtilities.addToClipboard(link) && BulletinFactory.canShowBulletin(chatActivity)) {
                        BulletinFactory.of(chatActivity).createCopyLinkBulletin(link.contains("/c/")).show();
                    }
                }
            }));
        }
    }

    public static void copyTranslationState(MessageObject source, MessageObject target) {
        if (source == null || target == null || source == target || source.messageOwner == null || target.messageOwner == null) {
            return;
        }
        target.messageOwner.translatedText = source.messageOwner.translatedText;
        target.messageOwner.translatedToLanguage = source.messageOwner.translatedToLanguage;
        target.messageOwner.translatedVoiceTranscription = source.messageOwner.translatedVoiceTranscription;
        target.messageOwner.translatedPoll = source.messageOwner.translatedPoll;
        target.messageOwner.summaryText = source.messageOwner.summaryText;
        target.messageOwner.summarizedOpen = source.messageOwner.summarizedOpen;
        target.messageOwner.translatedSummaryText = source.messageOwner.translatedSummaryText;
        target.messageOwner.translatedSummaryLanguage = source.messageOwner.translatedSummaryLanguage;
    }

    private static TLRPC.TL_message copyMessage(TLRPC.Message msg) {
        TLRPC.TL_message copy = new TLRPC.TL_message();
        copy.id = msg.id;
        copy.from_id = msg.from_id;
        copy.from_boosts_applied = msg.from_boosts_applied;
        copy.peer_id = msg.peer_id;
        copy.saved_peer_id = msg.saved_peer_id;
        copy.date = msg.date;
        copy.expire_date = msg.expire_date;
        copy.action = msg.action;
        copy.message = msg.message;
        copy.media = msg.media;
        copy.flags = msg.flags;
        copy.flags2 = msg.flags2;
        copy.mentioned = msg.mentioned;
        copy.media_unread = msg.media_unread;
        copy.out = msg.out;
        copy.unread = msg.unread;
        copy.entities = msg.entities;
        copy.via_bot_name = msg.via_bot_name;
        copy.reply_markup = msg.reply_markup;
        copy.views = msg.views;
        copy.forwards = msg.forwards;
        copy.replies = msg.replies;
        copy.edit_date = msg.edit_date;
        copy.silent = msg.silent;
        copy.post = msg.post;
        copy.from_scheduled = msg.from_scheduled;
        copy.legacy = msg.legacy;
        copy.edit_hide = msg.edit_hide;
        copy.pinned = msg.pinned;
        copy.fwd_from = msg.fwd_from;
        copy.via_bot_id = msg.via_bot_id;
        copy.via_business_bot_id = msg.via_business_bot_id;
        copy.reply_to = msg.reply_to;
        copy.post_author = msg.post_author;
        copy.grouped_id = msg.grouped_id;
        copy.reactions = msg.reactions;
        copy.restriction_reason = msg.restriction_reason;
        copy.ttl_period = msg.ttl_period;
        copy.quick_reply_shortcut_id = msg.quick_reply_shortcut_id;
        copy.effect = msg.effect;
        copy.noforwards = msg.noforwards;
        copy.invert_media = msg.invert_media;
        copy.offline = msg.offline;
        copy.factcheck = msg.factcheck;
        copy.send_state = msg.send_state;
        copy.fwd_msg_id = msg.fwd_msg_id;
        copy.params = msg.params;
        copy.random_id = msg.random_id;
        copy.local_id = msg.local_id;
        copy.attachPath = msg.attachPath;
        copy.dialog_id = msg.dialog_id;
        copy.ttl = msg.ttl;
        copy.destroyTime = msg.destroyTime;
        copy.destroyTimeMillis = msg.destroyTimeMillis;
        copy.layer = msg.layer;
        copy.seq_in = msg.seq_in;
        copy.seq_out = msg.seq_out;
        copy.with_my_score = msg.with_my_score;
        copy.replyMessage = msg.replyMessage;
        copy.reqId = msg.reqId;
        copy.realId = msg.realId;
        copy.stickerVerified = msg.stickerVerified;
        copy.isThreadMessage = msg.isThreadMessage;
        copy.voiceTranscription = msg.voiceTranscription;
        copy.voiceTranscriptionOpen = msg.voiceTranscriptionOpen;
        copy.voiceTranscriptionRated = msg.voiceTranscriptionRated;
        copy.voiceTranscriptionFinal = msg.voiceTranscriptionFinal;
        copy.voiceTranscriptionForce = msg.voiceTranscriptionForce;
        copy.voiceTranscriptionId = msg.voiceTranscriptionId;
        copy.premiumEffectWasPlayed = msg.premiumEffectWasPlayed;
        copy.originalLanguage = msg.originalLanguage;
        copy.translatedToLanguage = msg.translatedToLanguage;
        copy.translatedText = msg.translatedText;
        copy.replyStory = msg.replyStory;
        copy.quick_reply_shortcut = msg.quick_reply_shortcut;
        return copy;
    }
}
