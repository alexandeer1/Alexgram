package com.exteragram.messenger.feed;

import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.HashSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;

public class FeedChatIntegration {

    private static final int PAGEDOWN_SCROLL_THRESHOLD = AndroidUtilities.dp(100.0f);
    private static final int NEAR_NEWEST_THRESHOLD = AndroidUtilities.dp(160.0f);

    private final int currentAccount;
    private final Host host;
    private final boolean restoreDrawerScrollPosition;

    private Runnable channelsChangedCallback;
    private boolean destroyed;
    private boolean initialScrollApplied;
    private boolean pagedownShownByScroll;
    private boolean pendingDividerScroll;
    private long pendingHideDialogId;
    private ScrollAnchor pendingInitialScrollRestore;
    private boolean reactionsRefreshScheduled;
    private boolean readyToMarkAsRead;
    private boolean scrollPreservedNewerToUnread;
    private boolean settleAtNewestScheduled;
    private int totalScrollDy;
    private MessageObject unreadDivider;
    private boolean viewportActive;
    private int preserveScrollLoadIndex = -1;
    private int lastPagedownCount = -1;

    private final Runnable settleAtNewestRunnable = this::settleAtNewestNow;
    private final int reactionsRequestGuid = ConnectionsManager.generateClassGuid();
    private final LongSparseArray<Long> reactionsLastCheckTimes = new LongSparseArray<>();
    private final LongSparseArray<ArrayList<Integer>> pendingReactionIds = new LongSparseArray<>();
    private final Runnable reactionsRefreshRunnable = this::flushReactionsRefresh;

    public interface Host {
        boolean canScrollToNewer();
        ScrollAnchor captureScrollAnchor();
        void deleteRows(ArrayList<Integer> arrayList);
        int getDistanceToNewerPx();
        BaseFragment getFragment();
        int getLastVisibleMessageIndex();
        ArrayList<MessageObject> getMessages();
        int getNewestVisibleMessageIndex();
        void invalidateVisiblePart();
        boolean isFirstLoadComplete();
        boolean isListReady();
        boolean isListScrollIdle();
        boolean isPagedownButtonVisible();
        boolean isScrollAnimationRunning();
        void materializeRow(MessageObject messageObject);
        int nextStableId();
        void notifyAllMessagesChanged();
        void notifyMessageInserted(int index);
        void notifyMessageRemoved(int index);
        void onFeedListChanged();
        void reloadFeed();
        void requestOlderFeedPage();
        void restoreScrollAnchor(ScrollAnchor scrollAnchor);
        void scrollToMessage(int index, int offset);
        void scrollToMessageAnimated(int index, int offset);
        void setPagedownButtonVisible(boolean visible);
        void setPagedownCount(int count);
        void showEmptyFeedProgress();
        void showEmptyFeedState();
        int stableIdForDateHeader(int dateKeyInt);
    }

    public static final class ScrollAnchor {
        public final int offsetTop;
        public final MessageObject row;

        public ScrollAnchor(MessageObject messageObject, int offsetTop) {
            this.row = messageObject;
            this.offsetTop = offsetTop;
        }
    }

    public FeedChatIntegration(int account, Host host, boolean restoreScroll) {
        this.currentAccount = account;
        this.host = host;
        this.restoreDrawerScrollPosition = restoreScroll;
    }

    private void settleAtNewestNow() {
        this.settleAtNewestScheduled = false;
        if (this.destroyed || !this.viewportActive || !this.host.isListReady() || this.host.isScrollAnimationRunning() || this.host.canScrollToNewer()) {
            return;
        }
        settleUnreadDivider();
    }

    public void refreshAds() {
        requestPendingInitialPosition();
    }

    public void setChannelsChangedCallback(Runnable runnable) {
        this.channelsChangedCallback = runnable;
    }

    public void notifyChannelsChanged() {
        if (channelsChangedCallback != null) {
            channelsChangedCallback.run();
        }
    }

    public void resetUiState() {
        resetMetadataRefresh();
        this.initialScrollApplied = false;
        this.readyToMarkAsRead = false;
        this.pendingDividerScroll = false;
        this.pendingInitialScrollRestore = null;
        this.scrollPreservedNewerToUnread = false;
        this.preserveScrollLoadIndex = -1;
        this.unreadDivider = null;
        this.lastPagedownCount = -1;
        this.pagedownShownByScroll = false;
        this.totalScrollDy = 0;
        this.pendingHideDialogId = 0L;
        if (this.settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(this.settleAtNewestRunnable);
            this.settleAtNewestScheduled = false;
        }
    }

    private boolean hasMaterializedPostRows() {
        ArrayList<MessageObject> messages = this.host.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void onMessagesLoaded() {
        if (!this.host.getFragment().isPaused() && this.host.isListReady() && hasMaterializedPostRows()) {
            if (!this.initialScrollApplied) {
                this.initialScrollApplied = true;
                FeedController feedController = FeedController.getInstance(this.currentAccount);
                boolean zConsumeInitialUnreadScroll = feedController.consumeInitialUnreadScroll();
                FeedController.SavedScrollPosition drawerScrollPosition = this.restoreDrawerScrollPosition ? feedController.getDrawerScrollPosition() : null;
                MessageObject message = drawerScrollPosition != null ? feedController.getMessage(drawerScrollPosition.dialogId, drawerScrollPosition.messageId) : null;
                if (message != null && this.host.getMessages().contains(message)) {
                    applyUnreadDivider(false);
                    refreshAds();
                    this.pendingInitialScrollRestore = new ScrollAnchor(message, drawerScrollPosition.offsetTop);
                    requestPendingInitialPosition();
                } else {
                    applyUnreadDivider((zConsumeInitialUnreadScroll || this.host.getDistanceToNewerPx() > NEAR_NEWEST_THRESHOLD) ? zConsumeInitialUnreadScroll : true);
                }
            }
        }
    }

    public void onHostResumed() {
        if (!this.host.getMessages().isEmpty()) {
            onMessagesLoaded();
        }
        requestPendingInitialPosition();
    }

    private boolean hasPendingInitialPosition() {
        return this.pendingInitialScrollRestore != null || this.pendingDividerScroll;
    }

    private void requestPendingInitialPosition() {
        if (this.host.getFragment().isPaused() || !this.host.isListReady()) {
            return;
        }
        ScrollAnchor scrollAnchor = this.pendingInitialScrollRestore;
        if (scrollAnchor != null) {
            this.host.restoreScrollAnchor(scrollAnchor);
            return;
        }
        if (this.pendingDividerScroll) {
            int iIndexOf = this.unreadDivider == null ? -1 : this.host.getMessages().indexOf(this.unreadDivider);
            if (iIndexOf < 0) {
                this.pendingDividerScroll = false;
                this.readyToMarkAsRead = true;
            } else {
                this.host.scrollToMessage(iIndexOf, AndroidUtilities.dp(48.0f));
            }
        }
    }

    public void setViewportActive(boolean z) {
        if (this.viewportActive == z) {
            return;
        }
        this.viewportActive = z;
        if (!z) {
            if (this.settleAtNewestScheduled) {
                AndroidUtilities.cancelRunOnUIThread(this.settleAtNewestRunnable);
                this.settleAtNewestScheduled = false;
            }
            cancelPendingReactionsRefresh();
            return;
        }
        onHostResumed();
        if (this.host.getMessages().isEmpty()) {
            return;
        }
        onVisiblePartInvalidated();
    }

    public boolean canMarkVisibleAsRead() {
        return this.viewportActive && !this.host.getFragment().isPaused() && this.initialScrollApplied && this.readyToMarkAsRead && !this.pendingDividerScroll && this.pendingInitialScrollRestore == null && !BaseFragment.hasSheets(this.host.getFragment());
    }

    public void onPostCellVisible(MessageObject messageObject, boolean z, boolean z2) {
        if (messageObject == null || messageObject.isSponsored()) {
            return;
        }
        requestReactionsRefresh(messageObject);
        if (canMarkVisibleAsRead()) {
            if (z || z2) {
                FeedController.getInstance(this.currentAccount).onPostSeen(messageObject.getDialogId(), messageObject.getRealId());
            }
        }
    }

    private void requestReactionsRefresh(MessageObject messageObject) {
        if (this.destroyed || !this.viewportActive || messageObject.messageOwner == null) {
            return;
        }
        int realId = messageObject.getRealId();
        long dialogId = messageObject.getDialogId();
        if (realId <= 0 || dialogId == 0) {
            return;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (jCurrentTimeMillis - this.reactionsLastCheckTimes.get(realId, 0L).longValue() <= 15000) {
            return;
        }
        this.reactionsLastCheckTimes.put(realId, Long.valueOf(jCurrentTimeMillis));
        ArrayList<Integer> arrayList = this.pendingReactionIds.get(dialogId);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            this.pendingReactionIds.put(dialogId, arrayList);
        }
        arrayList.add(Integer.valueOf(realId));
        if (this.reactionsRefreshScheduled) {
            return;
        }
        this.reactionsRefreshScheduled = true;
        AndroidUtilities.runOnUIThread(this.reactionsRefreshRunnable);
    }

    private void flushReactionsRefresh() {
        this.reactionsRefreshScheduled = false;
        if (this.destroyed || !this.viewportActive) {
            this.pendingReactionIds.clear();
            return;
        }
        for (int i = 0; i < this.pendingReactionIds.size(); i++) {
            TLRPC.TL_messages_getMessagesReactions req = new TLRPC.TL_messages_getMessagesReactions();
            req.peer = MessagesController.getInstance(this.currentAccount).getInputPeer(this.pendingReactionIds.keyAt(i));
            req.id.addAll(this.pendingReactionIds.valueAt(i));
            ConnectionsManager.getInstance(this.currentAccount).bindRequestToGuid(ConnectionsManager.getInstance(this.currentAccount).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    for (int j = 0; j < updates.updates.size(); j++) {
                        TLRPC.Update update = updates.updates.get(j);
                        if (update instanceof TL_update.TL_updateMessageReactions) {
                            ((TL_update.TL_updateMessageReactions) update).updateUnreadState = false;
                        }
                    }
                    MessagesController.getInstance(this.currentAccount).processUpdates(updates, false);
                }
            }), this.reactionsRequestGuid);
        }
        this.pendingReactionIds.clear();
    }

    private void cancelPendingReactionsRefresh() {
        AndroidUtilities.cancelRunOnUIThread(this.reactionsRefreshRunnable);
        this.reactionsRefreshScheduled = false;
        this.pendingReactionIds.clear();
    }

    private void resetMetadataRefresh() {
        cancelPendingReactionsRefresh();
        this.reactionsLastCheckTimes.clear();
        ConnectionsManager.getInstance(this.currentAccount).cancelRequestsForGuid(this.reactionsRequestGuid);
    }

    public void markAllRead() {
        FeedController.getInstance(this.currentAccount).markAllRead();
        applyUnreadDivider(false);
        refreshAds();
        this.host.invalidateVisiblePart();
    }

    public void settleUnreadDivider() {
        int lastVisibleMessageIndex;
        if (canMarkVisibleAsRead() && this.host.isListReady() && (lastVisibleMessageIndex = this.host.getLastVisibleMessageIndex()) != Integer.MIN_VALUE) {
            ArrayList<MessageObject> messages = this.host.getMessages();
            int iMin = this.host.canScrollToNewer() ? Math.min(lastVisibleMessageIndex, messages.size() - 1) : messages.size() - 1;
            if (iMin >= 0) {
                FeedController feedController = FeedController.getInstance(this.currentAccount);
                for (int i = 0; i <= iMin; i++) {
                    MessageObject messageObject = messages.get(i);
                    if (messageObject != null && !messageObject.isDateObject && messageObject.type != 6 && !messageObject.isSponsored()) {
                        feedController.onPostSeen(messageObject.getDialogId(), messageObject.getRealId());
                    }
                }
            }
            applyUnreadDivider(false);
            refreshAds();
            updatePagedownCounter();
        }
    }

    public void onScrollAnimationFinished() {
        if (this.destroyed || !this.viewportActive || this.settleAtNewestScheduled) {
            return;
        }
        this.settleAtNewestScheduled = true;
        AndroidUtilities.runOnUIThread(this.settleAtNewestRunnable);
    }

    public void destroy() {
        this.destroyed = true;
        resetMetadataRefresh();
        if (this.settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(this.settleAtNewestRunnable);
            this.settleAtNewestScheduled = false;
        }
        this.pendingInitialScrollRestore = null;
    }

    public void saveDrawerScrollPosition() {
        ScrollAnchor scrollAnchorCaptureScrollAnchor = this.host.captureScrollAnchor();
        if (scrollAnchorCaptureScrollAnchor == null || scrollAnchorCaptureScrollAnchor.row == null) {
            return;
        }
        FeedController.getInstance(this.currentAccount).saveDrawerScrollPosition(scrollAnchorCaptureScrollAnchor.row.getDialogId(), scrollAnchorCaptureScrollAnchor.row.getRealId(), scrollAnchorCaptureScrollAnchor.offsetTop);
    }

    public void onReadStateRefreshed() {
        ScrollAnchor scrollAnchorCaptureScrollAnchor = this.host.captureScrollAnchor();
        boolean zHasPendingInitialPosition = hasPendingInitialPosition();
        applyUnreadDivider(false);
        refreshAds();
        if (!zHasPendingInitialPosition || !hasPendingInitialPosition()) {
            this.host.restoreScrollAnchor(scrollAnchorCaptureScrollAnchor);
        }
        this.lastPagedownCount = -1;
        updatePagedownCounter();
        this.host.invalidateVisiblePart();
    }

    public void onPreserveScrollLoadStarted(int i) {
        this.preserveScrollLoadIndex = i;
    }

    public boolean consumePreserveScrollLoad(int i) {
        if (this.preserveScrollLoadIndex != i) {
            return false;
        }
        this.preserveScrollLoadIndex = -1;
        return true;
    }

    public void beforePreservedNewerMessagesInserted() {
        this.scrollPreservedNewerToUnread = this.host.isListScrollIdle() && !this.host.isScrollAnimationRunning() && this.host.getDistanceToNewerPx() <= NEAR_NEWEST_THRESHOLD;
    }

    public boolean afterPreservedNewerMessagesInserted() {
        boolean z = this.scrollPreservedNewerToUnread;
        applyUnreadDivider(z, true);
        this.scrollPreservedNewerToUnread = false;
        return z;
    }

    public void applyUnreadDivider(boolean z) {
        applyUnreadDivider(z, false);
    }

    private void applyUnreadDivider(boolean z, boolean z2) {
        if (this.host.isListReady()) {
            ArrayList<MessageObject> messages = this.host.getMessages();
            if (messages.isEmpty()) {
                this.unreadDivider = null;
                this.readyToMarkAsRead = false;
                return;
            }
            MessageObject messageObject = this.unreadDivider;
            int iIndexOf = messageObject == null ? -1 : messages.indexOf(messageObject);
            MessageObject messageObjectCreateUnreadDivider = iIndexOf >= 0 ? this.unreadDivider : null;
            if (iIndexOf >= 0) {
                messages.remove(iIndexOf);
            }
            this.unreadDivider = null;
            int iFindFirstUnreadIndex = FeedController.getInstance(this.currentAccount).findFirstUnreadIndex(messages);
            if (iFindFirstUnreadIndex < 0) {
                this.pendingDividerScroll = false;
                if (iIndexOf >= 0) {
                    if (z && !z2) {
                        this.host.notifyAllMessagesChanged();
                    } else {
                        this.host.notifyMessageRemoved(iIndexOf);
                        this.host.invalidateVisiblePart();
                    }
                }
                this.readyToMarkAsRead = true;
                return;
            }
            int iFindDividerInsertIndex = findDividerInsertIndex(messages, iFindFirstUnreadIndex);
            if (messageObjectCreateUnreadDivider == null) {
                messageObjectCreateUnreadDivider = FeedMessageUtils.createUnreadDivider(this.currentAccount, this.host.nextStableId());
            }
            messages.add(iFindDividerInsertIndex, messageObjectCreateUnreadDivider);
            this.unreadDivider = messageObjectCreateUnreadDivider;
            if (z) {
                this.readyToMarkAsRead = false;
                if (!z2) {
                    this.host.notifyAllMessagesChanged();
                } else if (iIndexOf < 0) {
                    this.host.notifyMessageInserted(iFindDividerInsertIndex);
                } else if (iIndexOf != iFindDividerInsertIndex) {
                    this.host.notifyMessageRemoved(iIndexOf);
                    this.host.notifyMessageInserted(iFindDividerInsertIndex);
                }
                this.pendingDividerScroll = true;
                requestPendingInitialPosition();
                this.host.invalidateVisiblePart();
                return;
            }
            this.readyToMarkAsRead = true;
            if (iIndexOf < 0) {
                this.host.notifyMessageInserted(iFindDividerInsertIndex);
                this.host.invalidateVisiblePart();
            } else if (iIndexOf != iFindDividerInsertIndex) {
                this.host.notifyMessageRemoved(iIndexOf);
                this.host.notifyMessageInserted(iFindDividerInsertIndex);
                this.host.invalidateVisiblePart();
            }
        }
    }

    public boolean scrollToUnreadDividerIfAbove() {
        int newestVisibleMessageIndex;
        if (!this.host.isListReady()) {
            return false;
        }
        ArrayList<MessageObject> messages = this.host.getMessages();
        MessageObject messageObject = this.unreadDivider;
        if (messageObject == null || !messages.contains(messageObject)) {
            applyUnreadDivider(false);
            refreshAds();
            messages = this.host.getMessages();
        }
        int iIndexOf = messages.indexOf(this.unreadDivider);
        if (iIndexOf < 0 || (newestVisibleMessageIndex = this.host.getNewestVisibleMessageIndex()) == Integer.MIN_VALUE || iIndexOf >= newestVisibleMessageIndex) {
            return false;
        }
        this.host.scrollToMessageAnimated(iIndexOf, AndroidUtilities.dp(48.0f));
        this.host.invalidateVisiblePart();
        return true;
    }

    private static int findDividerInsertIndex(ArrayList<MessageObject> arrayList, int i) {
        MessageObject messageObject = arrayList.get(i);
        long groupId = messageObject.getGroupId();
        if (groupId == 0) {
            return i + 1;
        }
        long dialogId = messageObject.getDialogId();
        int iMax = i + 1;
        for (int i2 = 0; i2 < arrayList.size(); i2++) {
            MessageObject messageObject2 = arrayList.get(i2);
            if (messageObject2 != null && messageObject2.getGroupId() == groupId && messageObject2.getDialogId() == dialogId) {
                iMax = Math.max(iMax, i2 + 1);
            }
        }
        return iMax;
    }

    public void onVisiblePartInvalidated() {
        if (!this.viewportActive || this.host.getFragment().isPaused()) {
            return;
        }
        ScrollAnchor scrollAnchor = this.pendingInitialScrollRestore;
        if (scrollAnchor != null) {
            this.host.restoreScrollAnchor(scrollAnchor);
            this.pendingInitialScrollRestore = null;
        }
        maybeScrollToDivider();
        updatePagedownCounter();
    }

    private void maybeScrollToDivider() {
        if (this.pendingDividerScroll) {
            if (!this.host.isListReady() || this.unreadDivider == null) {
                this.pendingDividerScroll = false;
                this.readyToMarkAsRead = true;
                return;
            }
            int lastVisibleMessageIndex = this.host.getLastVisibleMessageIndex();
            if (lastVisibleMessageIndex == Integer.MIN_VALUE) {
                return;
            }
            int iIndexOf = this.host.getMessages().indexOf(this.unreadDivider);
            if (iIndexOf >= 0 && iIndexOf > lastVisibleMessageIndex) {
                this.host.scrollToMessage(iIndexOf, AndroidUtilities.dp(48.0f));
            }
            this.pendingDividerScroll = false;
            this.readyToMarkAsRead = true;
        }
    }

    private void updatePagedownCounter() {
        if (this.host.isListReady() && !this.host.isScrollAnimationRunning()) {
            int newestVisibleMessageIndex = this.host.getNewestVisibleMessageIndex();
            int iCountUnreadBelow = newestVisibleMessageIndex == Integer.MIN_VALUE ? 0 : FeedController.getInstance(this.currentAccount).countUnreadBelow(this.host.getMessages(), newestVisibleMessageIndex);
            if (iCountUnreadBelow != this.lastPagedownCount) {
                this.lastPagedownCount = iCountUnreadBelow;
                this.host.setPagedownCount(iCountUnreadBelow);
            }
            if (iCountUnreadBelow > 0) {
                this.pagedownShownByScroll = false;
                this.host.setPagedownButtonVisible(true);
            } else {
                if (this.host.canScrollToNewer()) {
                    return;
                }
                this.pagedownShownByScroll = false;
                this.host.setPagedownButtonVisible(false);
            }
        }
    }

    public void onScrolled(int i) {
        if (this.viewportActive && this.host.isListReady() && !this.host.isScrollAnimationRunning()) {
            if (!this.host.canScrollToNewer()) {
                this.totalScrollDy = 0;
                this.pagedownShownByScroll = false;
                this.host.setPagedownButtonVisible(false);
                if (i <= 0 || this.settleAtNewestScheduled) {
                    return;
                }
                this.settleAtNewestScheduled = true;
                AndroidUtilities.runOnUIThread(this.settleAtNewestRunnable);
                return;
            }
            if (this.lastPagedownCount > 0) {
                return;
            }
            boolean zIsPagedownButtonVisible = this.host.isPagedownButtonVisible();
            if (i > 0) {
                if (zIsPagedownButtonVisible) {
                    return;
                }
                int i2 = this.totalScrollDy + i;
                this.totalScrollDy = i2;
                if (i2 > PAGEDOWN_SCROLL_THRESHOLD) {
                    this.totalScrollDy = 0;
                    this.pagedownShownByScroll = true;
                    this.host.setPagedownButtonVisible(true);
                    return;
                }
                return;
            }
            if (i < 0 && this.pagedownShownByScroll && zIsPagedownButtonVisible) {
                int i3 = this.totalScrollDy + i;
                this.totalScrollDy = i3;
                if (i3 < (-PAGEDOWN_SCROLL_THRESHOLD)) {
                    this.totalScrollDy = 0;
                    this.host.setPagedownButtonVisible(false);
                }
            }
        }
    }

    public void onMessagesDeleted() {
        if (this.unreadDivider == null) {
            return;
        }
        ArrayList<MessageObject> messages = this.host.getMessages();
        int iIndexOf = messages.indexOf(this.unreadDivider);
        for (int i = 0; i < iIndexOf; i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                return;
            }
        }
        this.unreadDivider = null;
        if (iIndexOf < 0 || !this.host.isListReady()) {
            return;
        }
        messages.remove(iIndexOf);
        this.host.notifyMessageRemoved(iIndexOf);
    }

    public void loadReplyMessages(ArrayList<MessageObject> arrayList, int i, int i2) {
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        LongSparseArray<ArrayList<MessageObject>> longSparseArray = new LongSparseArray<>();
        for (int i3 = 0; i3 < arrayList.size(); i3++) {
            MessageObject messageObject = arrayList.get(i3);
            if (messageObject != null && !messageObject.isDateObject) {
                long dialogId = messageObject.getDialogId();
                if (dialogId != 0) {
                    ArrayList<MessageObject> arrayList2 = longSparseArray.get(dialogId);
                    if (arrayList2 == null) {
                        arrayList2 = new ArrayList<>();
                        longSparseArray.put(dialogId, arrayList2);
                    }
                    arrayList2.add(messageObject);
                }
            }
        }
        for (int i4 = 0; i4 < longSparseArray.size(); i4++) {
            MediaDataController.getInstance(this.currentAccount).loadReplyMessagesForMessages(longSparseArray.valueAt(i4), longSparseArray.keyAt(i4), i, 0L, null, i2, null);
        }
    }

    public void reconcileWithStore() {
        boolean z;
        if (this.host.isListReady()) {
            FeedStore store = FeedController.getInstance(this.currentAccount).getStore();
            ArrayList<MessageObject> messages = this.host.getMessages();
            int i = 0;
            for (int i2 = 0; i2 < messages.size(); i2++) {
                if (FeedMessageUtils.isPostRow(messages.get(i2))) {
                    i++;
                }
            }
            ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
            if (i == 0) {
                if (!visibleMessages.isEmpty() && this.host.isFirstLoadComplete()) {
                    this.host.reloadFeed();
                    return;
                } else {
                    if (store.isEmpty() || store.isEndReached()) {
                        return;
                    }
                    this.host.requestOlderFeedPage();
                    return;
                }
            }
            if (store.isEmpty()) {
                this.host.reloadFeed();
                return;
            }
            HashSet<MessageObject> hashSet = new HashSet<>(visibleMessages);
            ArrayList<Integer> arrayList = null;
            ArrayList<MessageObject> arrayList2 = null;
            for (int i3 = 0; i3 < messages.size(); i3++) {
                MessageObject messageObject = messages.get(i3);
                if (FeedMessageUtils.isPostRow(messageObject) && !hashSet.contains(messageObject)) {
                    if (store.getMessage(messageObject.getDialogId(), messageObject.getId()) == messageObject) {
                        if (arrayList2 == null) {
                            arrayList2 = new ArrayList<>();
                        }
                        arrayList2.add(messageObject);
                    } else {
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                        }
                        arrayList.add(Integer.valueOf(messageObject.getId()));
                    }
                }
            }
            int size = (arrayList == null ? 0 : arrayList.size()) + (arrayList2 == null ? 0 : arrayList2.size());
            if (size == 0 && i == visibleMessages.size()) {
                return;
            }
            ScrollAnchor scrollAnchorCaptureScrollAnchor = this.host.captureScrollAnchor();
            boolean zHasPendingInitialPosition = hasPendingInitialPosition();
            if (arrayList != null) {
                this.host.deleteRows(arrayList);
            }
            if (arrayList2 != null) {
                for (int i4 = 0; i4 < arrayList2.size(); i4++) {
                    int iIndexOf = messages.indexOf(arrayList2.get(i4));
                    if (iIndexOf >= 0) {
                        messages.remove(iIndexOf);
                        this.host.notifyMessageRemoved(iIndexOf);
                    }
                }
            }
            HashSet<MessageObject> hashSet2 = new HashSet<>();
            for (int i5 = 0; i5 < messages.size(); i5++) {
                if (FeedMessageUtils.isPostRow(messages.get(i5))) {
                    hashSet2.add(messages.get(i5));
                }
            }
            int i6 = 0;
            boolean z2 = false;
            int i7 = 0;
            while (true) {
                z = true;
                if (i6 >= visibleMessages.size()) {
                    break;
                }
                MessageObject messageObject2 = visibleMessages.get(i6);
                if (hashSet2.contains(messageObject2)) {
                    while (i7 < messages.size() && messages.get(i7) != messageObject2) {
                        i7++;
                    }
                    if (i7 < messages.size()) {
                        i7++;
                    }
                } else {
                    this.host.materializeRow(messageObject2);
                    int insertIndex = getInsertIndex(messages, messageObject2, visibleMessages, i6, i7);
                    messages.add(insertIndex, messageObject2);
                    this.host.notifyMessageInserted(insertIndex);
                    i7 = insertIndex + 1;
                    z2 = true;
                }
                i6++;
            }
            if (size <= 0 && !z2) {
                z = false;
            }
            if (!normalizeDateHeaders(messages) && !z) {
                return;
            }
            this.host.onFeedListChanged();
            applyUnreadDivider(false);
            refreshAds();
            onFeedExclusionsChanged();
            if (!zHasPendingInitialPosition || !hasPendingInitialPosition()) {
                this.host.restoreScrollAnchor(scrollAnchorCaptureScrollAnchor);
            }
            this.host.invalidateVisiblePart();
            if (store.getVisibleCount() == 0) {
                boolean zIsEndReached = store.isEndReached();
                Host host = this.host;
                if (zIsEndReached) {
                    host.showEmptyFeedState();
                } else {
                    host.showEmptyFeedProgress();
                    this.host.requestOlderFeedPage();
                }
            }
        }
    }

    private static int getInsertIndex(ArrayList<MessageObject> arrayList, MessageObject messageObject, ArrayList<MessageObject> arrayList2, int i, int i2) {
        int i3;
        int iMin = Math.min(i2, arrayList.size());
        if (i <= 0 || iMin >= arrayList.size()) {
            return iMin;
        }
        MessageObject messageObject2 = arrayList2.get(i - 1);
        MessageObject messageObject3 = arrayList.get(iMin);
        return (messageObject2 == null || messageObject3 == null || !messageObject3.isDateObject || (i3 = messageObject2.dateKeyInt) == messageObject.dateKeyInt || messageObject3.dateKeyInt != i3) ? iMin : iMin + 1;
    }

    private boolean normalizeDateHeaders(ArrayList<MessageObject> arrayList) {
        MessageObject lastMsg = null;
        boolean z = false;
        boolean z2 = false;
        for (int i = 0; i < arrayList.size(); i++) {
            MessageObject messageObject2 = arrayList.get(i);
            if (messageObject2 == null || messageObject2.type == 6 || messageObject2.isSponsored()) {
                continue;
            }
            if (messageObject2.isDateObject) {
                if (!z) {
                    arrayList.remove(i);
                    this.host.notifyMessageRemoved(i);
                    i--;
                } else if (lastMsg != null && lastMsg.dateKeyInt != messageObject2.dateKeyInt) {
                    arrayList.add(i, createDateHeader(lastMsg));
                    this.host.notifyMessageInserted(i);
                }
                z2 = true;
            } else if (lastMsg == null || lastMsg.dateKeyInt == messageObject2.dateKeyInt) {
                lastMsg = messageObject2;
                z = true;
            } else {
                arrayList.add(i, createDateHeader(lastMsg));
                this.host.notifyMessageInserted(i);
                z = false;
                lastMsg = null;
                z2 = true;
            }
        }
        if (!z || lastMsg == null) {
            return z2;
        }
        arrayList.add(createDateHeader(lastMsg));
        this.host.notifyMessageInserted(arrayList.size() - 1);
        return true;
    }

    private MessageObject createDateHeader(MessageObject messageObject) {
        return FeedMessageUtils.createDateHeader(this.currentAccount, messageObject, this.host.stableIdForDateHeader(messageObject.dateKeyInt));
    }

    public void hideChannelWithUndo(final long dialogId, CharSequence charSequence) {
        FeedConfig feedConfig = FeedConfig.getInstance(this.currentAccount);
        FeedController feedController = FeedController.getInstance(this.currentAccount);
        feedConfig.setExcluded(dialogId, true);
        feedController.markConfigApplied();
        feedController.getStore().setHidden(dialogId, true);
        this.pendingHideDialogId = dialogId;
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();
        BulletinFactory.of(this.host.getFragment()).createUndoBulletin(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FeedChannelHidden, charSequence)), this::undoHideChannel, () -> {
            if (this.pendingHideDialogId == dialogId) {
                this.pendingHideDialogId = 0L;
            }
        }).show();
    }

    private void undoHideChannel() {
        long j = this.pendingHideDialogId;
        if (j == 0) {
            return;
        }
        this.pendingHideDialogId = 0L;
        FeedController feedController = FeedController.getInstance(this.currentAccount);
        FeedConfig.getInstance(this.currentAccount).setExcluded(j, false);
        feedController.markConfigApplied();
        feedController.getStore().setHidden(j, false);
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();
    }

    public void onFeedExclusionsChanged() {
        this.lastPagedownCount = -1;
        updatePagedownCounter();
        NotificationCenter.getInstance(this.currentAccount).postNotificationName(NotificationCenter.updateInterfaces, Integer.valueOf(MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE));
    }

    public void onDestroy() {
        this.destroyed = true;
        resetMetadataRefresh();
        if (this.settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(this.settleAtNewestRunnable);
            this.settleAtNewestScheduled = false;
        }
        this.pendingInitialScrollRestore = null;
    }
}
