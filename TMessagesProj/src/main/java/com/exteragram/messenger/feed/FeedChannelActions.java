package com.exteragram.messenger.feed;

import androidx.core.util.Consumer;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;

public abstract class FeedChannelActions {

    public static boolean canLeave(TLRPC.Chat chat) {
        return chat != null && !chat.creator && !ChatObject.isNotInChat(chat);
    }

    public static void showAvatarMenu(final ChatActivity chatActivity, ChatMessageCell chatMessageCell, final TLRPC.Chat chat, Runnable openRunnable, final Runnable leaveRunnable, final Consumer<ArrayList<Integer>> consumer) {
        if (chatActivity == null || chatMessageCell == null || chat == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(chatActivity, chatMessageCell);
        boolean isBroadcast = chat.broadcast;
        options.add(isBroadcast ? R.drawable.msg_channel : R.drawable.msg_discussion, LocaleController.getString(isBroadcast ? R.string.OpenChannel2 : R.string.OpenGroup2), openRunnable)
                .add(R.drawable.menu_hide_gift, LocaleController.getString(R.string.FeedHideChannel), () -> chatActivity.hideFeedChannelWithUndo(-chat.id, chat.title))
                .addIf(canLeave(chat), R.drawable.msg_leave, LocaleController.getString(chat.broadcast ? R.string.LeaveChannelMenu : R.string.LeaveMegaMenu), true, () -> leaveChannel(chatActivity, chat, leaveRunnable, consumer))
                .setDrawScrim(false)
                .setGravity(3)
                .forceBottom(true)
                .show();
    }

    public static void leaveChannel(final BaseFragment baseFragment, final TLRPC.Chat chat, final Runnable runnable, final Consumer<ArrayList<Integer>> consumer) {
        if (baseFragment == null || chat == null || baseFragment.getParentActivity() == null) {
            return;
        }
        AlertsCreator.createClearOrDeleteDialogAlert(baseFragment, false, chat, null, false, true, false, false, (MessagesStorage.BooleanCallback) param -> {
            long dialogId = -chat.id;
            if (ChatObject.isNotInChat(chat)) {
                baseFragment.getMessagesController().deleteDialog(dialogId, 0, param);
            } else {
                baseFragment.getMessagesController().deleteParticipantFromChat(chat.id, baseFragment.getMessagesController().getUser(baseFragment.getUserConfig().getClientUserId()), (TLRPC.Chat) null, param, param);
            }
            deleteFeedRows(baseFragment, dialogId, consumer);
            if (runnable != null) {
                runnable.run();
            }
        });
    }

    private static void deleteFeedRows(BaseFragment baseFragment, long dialogId, Consumer<ArrayList<Integer>> consumer) {
        ArrayList<Integer> arrayList = FeedController.getInstance(baseFragment.getCurrentAccount()).deleteHistory(dialogId, Integer.MAX_VALUE);
        if (consumer != null) {
            consumer.accept(arrayList);
        }
    }
}
