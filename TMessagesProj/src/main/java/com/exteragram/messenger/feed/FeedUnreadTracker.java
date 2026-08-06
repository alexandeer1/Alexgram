package com.exteragram.messenger.feed;

import androidx.collection.LongSparseArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

final class FeedUnreadTracker {
    private final int currentAccount;
    private boolean flushScheduled;
    private final ArrayList<MessageObject> timeline;
    private final LongSparseArray<Integer> readInboxMaxByDialog = new LongSparseArray<>();
    private final LongSparseArray<Integer> pendingMaxReadId = new LongSparseArray<>();
    private final Runnable flushRunnable = this::flush;

    public FeedUnreadTracker(int account, ArrayList<MessageObject> timeline) {
        this.currentAccount = account;
        this.timeline = timeline;
    }

    public void clear() {
        if (this.flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(this.flushRunnable);
            this.flushScheduled = false;
        }
        flush();
        this.readInboxMaxByDialog.clear();
    }

    public void applyReadInboxMax(long dialogId, int maxId) {
        if (maxId > this.readInboxMaxByDialog.get(dialogId, 0)) {
            this.readInboxMaxByDialog.put(dialogId, maxId);
        }
    }

    public boolean isUnread(MessageObject messageObject) {
        return messageObject != null && !messageObject.isSponsored() && messageObject.getRealId() > getEffectiveReadInboxMax(messageObject.getDialogId());
    }

    private int getEffectiveReadInboxMax(long dialogId) {
        return Math.max(this.readInboxMaxByDialog.get(dialogId, 0), this.pendingMaxReadId.get(dialogId, 0));
    }

    public int findFirstUnreadIndex(ArrayList<MessageObject> arrayList) {
        if (arrayList != null && !this.readInboxMaxByDialog.isEmpty()) {
            for (int size = arrayList.size() - 1; size >= 0; size--) {
                if (isUnread(arrayList.get(size))) {
                    return size;
                }
            }
        }
        return -1;
    }

    public int countUnreadBelow(ArrayList<MessageObject> arrayList, int index) {
        if (arrayList == null || this.readInboxMaxByDialog.isEmpty()) {
            return 0;
        }
        int iMin = Math.min(index, arrayList.size());
        int count = 0;
        for (int i = 0; i < iMin; i++) {
            MessageObject messageObject = arrayList.get(i);
            if (messageObject != null && !messageObject.isDateObject && messageObject.type != 6 && !messageObject.isSponsored() && isUnread(messageObject)) {
                count++;
            }
        }
        return count;
    }

    public void onPostSeen(long dialogId, int realId) {
        if (dialogId == 0 || realId <= 0 || realId <= getEffectiveReadInboxMax(dialogId)) {
            return;
        }
        Integer num = this.pendingMaxReadId.get(dialogId);
        if (num == null || num < realId) {
            this.pendingMaxReadId.put(dialogId, realId);
            if (this.flushScheduled) {
                return;
            }
            this.flushScheduled = true;
            AndroidUtilities.runOnUIThread(this.flushRunnable, 1000L);
        }
    }

    private void flush() {
        this.flushScheduled = false;
        if (this.pendingMaxReadId.isEmpty()) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(this.currentAccount);
        int currentTime = ConnectionsManager.getInstance(this.currentAccount).getCurrentTime();
        for (int i = 0; i < this.pendingMaxReadId.size(); i++) {
            long dialogId = this.pendingMaxReadId.keyAt(i);
            int maxReadId = this.pendingMaxReadId.valueAt(i);
            int currentMax = this.readInboxMaxByDialog.get(dialogId, 0);
            if (maxReadId > currentMax) {
                this.readInboxMaxByDialog.put(dialogId, maxReadId);
                messagesController.markDialogAsRead(dialogId, maxReadId, 0, currentTime, false, 0L, Math.max(countTimelineRows(dialogId, currentMax, maxReadId), 1), true, 0);
            }
        }
        this.pendingMaxReadId.clear();
    }

    private int countTimelineRows(long dialogId, int minId, int maxId) {
        int count = 0;
        for (int i = 0; i < this.timeline.size(); i++) {
            MessageObject messageObject = this.timeline.get(i);
            if (messageObject != null && messageObject.getDialogId() == dialogId) {
                int realId = messageObject.getRealId();
                if (realId > minId && realId <= maxId) {
                    count++;
                }
            }
        }
        return count;
    }

    public void markAllRead() {
        MessagesController messagesController = MessagesController.getInstance(this.currentAccount);
        HashSet<Long> processedDialogs = new HashSet<>();
        ArrayList<TLRPC.Dialog> unreadDialogs = collectUnreadFeedDialogs();
        for (int i = 0; i < unreadDialogs.size(); i++) {
            TLRPC.Dialog dialog = unreadDialogs.get(i);
            messagesController.markMentionsAsRead(dialog.id, 0L);
            messagesController.markDialogAsRead(dialog.id, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0L, 0, true, 0);
            this.readInboxMaxByDialog.put(dialog.id, dialog.top_message);
            processedDialogs.add(dialog.id);
        }
        FeedConfig feedConfig = FeedConfig.getInstance(this.currentAccount);
        boolean includeArchived = feedConfig.getIncludeArchived();
        for (int i = 0; i < this.timeline.size(); i++) {
            MessageObject messageObject = this.timeline.get(i);
            if (messageObject != null) {
                long dialogId = messageObject.getDialogId();
                if (!feedConfig.isExcluded(dialogId)) {
                    TLRPC.Dialog dialog = messagesController.dialogs_dict.get(dialogId);
                    if (includeArchived || dialog == null || dialog.folder_id != 1) {
                        processedDialogs.add(dialogId);
                        int realId = messageObject.getRealId();
                        if (realId > this.readInboxMaxByDialog.get(dialogId, 0)) {
                            this.readInboxMaxByDialog.put(dialogId, realId);
                        }
                    }
                }
            }
        }
        for (Long dialogId : processedDialogs) {
            this.pendingMaxReadId.remove(dialogId);
        }
        if (this.pendingMaxReadId.isEmpty() && this.flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(this.flushRunnable);
            this.flushScheduled = false;
        }
    }

    public int getUnreadCount() {
        ArrayList<TLRPC.Dialog> unreadDialogs = collectUnreadFeedDialogs();
        int count = 0;
        for (int i = 0; i < unreadDialogs.size(); i++) {
            count += unreadDialogs.get(i).unread_count;
        }
        return count;
    }

    private ArrayList<TLRPC.Dialog> collectUnreadFeedDialogs() {
        MessagesController messagesController = MessagesController.getInstance(this.currentAccount);
        FeedConfig feedConfig = FeedConfig.getInstance(this.currentAccount);
        boolean includeArchived = feedConfig.getIncludeArchived();
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog != null && dialog.unread_count > 0 && DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = messagesController.getChat(-dialog.id);
                if (FeedController.isEligibleChannel(chat) && !feedConfig.isExcluded(dialog.id)) {
                    if (includeArchived || dialog.folder_id != 1) {
                        result.add(dialog);
                    }
                }
            }
        }
        return result;
    }
}
