package com.exteragram.messenger.feed;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import androidx.collection.LongSparseArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class FeedController implements NotificationCenter.NotificationCenterDelegate {
    private static final String TAG = "FeedPagination";
    private static final FeedController[] Instance = new FeedController[16];
    private static final Object[] lockObjects = new Object[16];

    static {
        for (int i = 0; i < 16; i++) {
            lockObjects[i] = new Object();
        }
    }

    public final int currentAccount;
    private final FeedStore store;
    private final FeedTimelineLoader loader;
    private final FeedUnreadTracker unreadTracker;
    private final FeedBackfillCoordinator backfill;

    private int attemptRounds;
    private int cachedIncludedChannelCount;
    private final int closedRefreshGuid;
    private final Runnable closedRefreshRunnable;
    private boolean closedRefreshScheduled;
    private int configGeneration;
    private SavedScrollPosition drawerScrollPosition;
    private boolean hasChannels;
    private boolean hasIncludedChannels;
    private int heldGuid;
    private int heldLoadIndex;
    private final ArrayList<int[]> initialLoadWaiters;
    private boolean initialUnreadScrollPending;
    private boolean loading;
    private boolean loadingNewer;
    private boolean newerPagingBoundsDirty;
    private boolean olderPagingBoundsDirty;
    private int resumedUiClients;
    private int sessionGeneration;
    private int staleEnumerationRetries;
    private int uiActiveClients;

    public interface ChannelsCallback {
        void onChannels(ArrayList<TLRPC.Chat> channels, int count, boolean failed, int generation);
    }

    public static final class SavedScrollPosition {
        public final long dialogId;
        public final int messageId;
        public final int offsetTop;

        public SavedScrollPosition(long dialogId, int messageId, int offsetTop) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.offsetTop = offsetTop;
        }
    }

    public static FeedController peekInstance(int account) {
        if (account < 0 || account >= 16) {
            account = UserConfig.selectedAccount;
            if (account < 0 || account >= 16) account = 0;
        }
        return Instance[account];
    }

    public static FeedController getInstance(int account) {
        if (account < 0 || account >= 16) {
            account = UserConfig.selectedAccount;
            if (account < 0 || account >= 16) account = 0;
        }
        FeedController controller = Instance[account];
        if (controller != null) {
            return controller;
        }
        synchronized (lockObjects[account]) {
            controller = Instance[account];
            if (controller == null) {
                controller = new FeedController(account);
                Instance[account] = controller;
            }
        }
        return controller;
    }

    private FeedController(final int account) {
        this.currentAccount = account;
        this.store = new FeedStore();
        this.initialUnreadScrollPending = true;
        this.initialLoadWaiters = new ArrayList<>();
        this.closedRefreshGuid = ConnectionsManager.generateClassGuid();
        this.closedRefreshRunnable = this::runClosedRefresh;
        this.unreadTracker = new FeedUnreadTracker(account, store.getMessages());
        this.loader = new FeedTimelineLoader(account);
        this.backfill = new FeedBackfillCoordinator(account, this::onBackfillRoundFinished);

        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagesDidLoad);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.loadingMessagesFailed);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagesDeleted);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.historyCleared);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.didReceiveNewMessages);
            FeedChannelRegistry.getInstance(account).addListener(this::onFeedChannelsChanged);
        });
    }

    private void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed) {
        loader.invalidateChannelCache();
        for (Long id : removed) {
            deleteHistory(id, Integer.MAX_VALUE);
        }
        if (added.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, Boolean.FALSE);
        } else {
            reconcileChannelSet(res -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, res));
        }
    }

    public FeedStore getStore() {
        return store;
    }

    public ArrayList<MessageObject> getMessages() {
        return store.getMessages();
    }

    public boolean isLoading() {
        return loading || loadingNewer;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        return store.hasMessagesForDialog(dialogId);
    }

    public boolean hasChannels() {
        return hasChannels;
    }

    public boolean hasIncludedChannels() {
        return hasIncludedChannels;
    }

    public int getIncludedChannelCount() {
        return cachedIncludedChannelCount;
    }

    public void setUiActive(boolean active) {
        if (!active) {
            if (uiActiveClients == 0) return;
            uiActiveClients--;
            if (uiActiveClients == 0) {
                cancelLoads();
                trimForInactiveCache();
            }
        } else {
            uiActiveClients++;
            if (uiActiveClients > 1) return;
            if (closedRefreshScheduled) {
                AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
                closedRefreshScheduled = false;
            }
            if (loadingNewer) {
                cancelLoads();
            }
        }
    }

    private boolean isUiActive() {
        return uiActiveClients > 0;
    }

    public void setUiResumed(boolean resumed) {
        if (resumed) {
            resumedUiClients++;
        } else if (resumedUiClients > 0) {
            resumedUiClients--;
        }
    }

    public void clear() {
        sessionGeneration++;
        configGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
        unreadTracker.clear();
        drawerScrollPosition = null;
        store.clear();
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
        backfill.clearExhausted();
        if (closedRefreshScheduled) {
            AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
            closedRefreshScheduled = false;
        }
    }

    public void cancelLoads() {
        sessionGeneration++;
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
    }

    private static int getInactiveCacheCap() {
        int devicePerformanceClass = SharedConfig.getDevicePerformanceClass();
        if (devicePerformanceClass == 0) {
            return 300;
        }
        if (devicePerformanceClass != 2) {
            return 500;
        }
        return 1000;
    }

    public void trimForInactiveCache() {
        if (isUiActive() || store.isEmpty()) {
            return;
        }
        store.trim(getInactiveCacheCap());
    }

    public boolean isIncludedChannelPost(long dialogId) {
        if (!DialogObject.isChatDialog(dialogId) || FeedConfig.getInstance(currentAccount).isExcluded(dialogId)) {
            return false;
        }
        return isEligibleChannel(MessagesController.getInstance(currentAccount).getChat(-dialogId));
    }

    public static boolean isEligibleChannel(TLRPC.Chat chat) {
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isCommunity(chat) && !ChatObject.isNotInChat(chat);
    }

    public boolean consumeInitialUnreadScroll() {
        boolean res = initialUnreadScrollPending;
        initialUnreadScrollPending = false;
        return res;
    }

    public int getUnreadCount() {
        return unreadTracker.getUnreadCount();
    }

    public void onPostSeen(long dialogId, int messageId) {
        unreadTracker.onPostSeen(dialogId, messageId);
    }

    public void markAllRead() {
        unreadTracker.markAllRead();
    }

    public int findFirstUnreadIndex(ArrayList<MessageObject> arrayList) {
        return unreadTracker.findFirstUnreadIndex(arrayList);
    }

    public int countUnreadBelow(ArrayList<MessageObject> arrayList, int index) {
        return unreadTracker.countUnreadBelow(arrayList, index);
    }

    public void saveDrawerScrollPosition(long dialogId, int messageId, int offsetTop) {
        if (dialogId == 0 || messageId <= 0) {
            return;
        }
        drawerScrollPosition = new SavedScrollPosition(dialogId, messageId, offsetTop);
    }

    public SavedScrollPosition getDrawerScrollPosition() {
        return drawerScrollPosition;
    }

    public boolean hasNoSyntheticIds() {
        return store.hasNoSyntheticIds();
    }

    public MessageObject getMessage(long dialogId, int messageId) {
        return store.getMessage(dialogId, messageId);
    }

    public int resolveRealMessageId(long dialogId, int messageId) {
        return store.resolveRealMessageId(dialogId, messageId);
    }

    public long resolveRealDialogId(int messageId) {
        return store.resolveRealDialogId(messageId);
    }

    public boolean loadInitial(final int guid, final int loadIndex) {
        ensureCurrentConfig();
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            if (!loadMore(guid, loadIndex)) {
                initialLoadWaiters.add(new int[]{guid, loadIndex});
            }
            return false;
        }
        final ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
        for (int i = 0; i < visibleMessages.size(); i++) {
            visibleMessages.get(i).viewsReloaded = false;
        }
        if (visibleMessages.isEmpty() && !store.isEndReached()) {
            if (!loadMore(guid, loadIndex)) {
                initialLoadWaiters.add(new int[]{guid, loadIndex});
            }
            return false;
        }
        final int session = sessionGeneration;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, config, gen, epoch)) {
                    postFeedResults(guid, loadIndex, new ArrayList<>(), 0, false, true);
                    postFeedCount(guid);
                } else {
                    applyEnumeration(enumeration);
                    postFeedResults(guid, loadIndex, visibleMessages, 0, false, enumeration.failed);
                    postFeedCount(guid);
                }
            });
        });
        return true;
    }

    private void ensureCurrentConfig() {
        if (configGeneration != FeedConfig.getInstance(currentAccount).getGeneration()) {
            applyConfigChange(res -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, res));
        }
    }

    public void markConfigApplied() {
        configGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
    }

    public void applyConfigChange(Utilities.Callback<Boolean> callback) {
        reconcileChannelSet(callback);
    }

    private void reconcileChannelSet(final Utilities.Callback<Boolean> callback) {
        final int session = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            loadChannels((arrayList, count, failed, generation) -> {
                if (!failed) {
                    configGeneration = generation;
                }
                if (callback != null) {
                    callback.run(Boolean.FALSE);
                }
            });
            return;
        }
        final HashSet<Long> loadedDialogIds = store.getLoadedDialogIds();
        final HashSet<Long> hiddenSnapshot = store.getHiddenSnapshot();
        final FeedTimelineLoader.Cursor newCursor = new FeedTimelineLoader.Cursor();
        final FeedTimelineLoader.Cursor oldCursor = new FeedTimelineLoader.Cursor();
        newCursor.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);
        oldCursor.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, true);
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        reconcileChannelSet(callback);
                    } else if (callback != null) {
                        callback.run(Boolean.FALSE);
                    }
                });
                return;
            }
            ArrayList<Long> missingChannels = new ArrayList<>();
            for (FeedTimelineLoader.ChannelSnapshot snapshot : enumeration.included) {
                if (!loadedDialogIds.contains(snapshot.dialogId) || hiddenSnapshot.contains(snapshot.dialogId)) {
                    missingChannels.add(snapshot.dialogId);
                }
            }
            FeedTimelineLoader.WindowPage page = missingChannels.isEmpty() ? null : loader.loadChannelWindow(missingChannels, newCursor, oldCursor);
            if (page != null && page.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        reconcileChannelSet(callback);
                    } else if (callback != null) {
                        callback.run(Boolean.FALSE);
                    }
                });
                return;
            }
            ArrayList<MessageObject> createdObjects = page != null ? createMessageObjects(page.messages, page.users, page.chats) : null;
            boolean hasMissing = !missingChannels.isEmpty();
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) return;
                if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                    reconcileChannelSet(callback);
                    return;
                }
                applyEnumeration(enumeration);
                configGeneration = enumeration.configGeneration;
                HashSet<Long> includedSet = new HashSet<>();
                for (FeedTimelineLoader.ChannelSnapshot snapshot : enumeration.included) {
                    includedSet.add(snapshot.dialogId);
                }
                store.applyIncludedDialogs(includedSet);
                boolean truncated = page != null && page.truncated;
                if (page != null && !truncated && !createdObjects.isEmpty()) {
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    messagesController.putUsers(page.users, true);
                    messagesController.putChats(page.chats, true);
                    store.mergeRows(createdObjects);
                }
                if (hasMissing) {
                    store.setEndReached(false);
                    if (loading) olderPagingBoundsDirty = true;
                    if (loadingNewer) newerPagingBoundsDirty = true;
                }
                if (callback != null) {
                    callback.run(truncated);
                }
            });
        });
    }

    public void refreshReadState(final Runnable runnable) {
        final int session = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) return;
                if (isEnumerationCurrent(enumeration, config, gen, epoch)) {
                    applyEnumeration(enumeration);
                }
                if (runnable != null) runnable.run();
            });
        });
    }

    public boolean loadMore(int guid, int loadIndex) {
        ensureCurrentConfig();
        Log.d(TAG, "loadMore called: guid=" + guid + " loadIndex=" + loadIndex + " loading=" + loading + " endReached=" + store.isEndReached());
        if (loading) {
            Log.d(TAG, "loadMore BLOCKED - already loading");
            return false;
        }
        store.setEndReached(false);
        loading = true;
        heldGuid = guid;
        heldLoadIndex = loadIndex;
        attemptRounds = 0;
        Log.d(TAG, "loadMore STARTED - running attempt");
        runAttempt();
        return true;
    }

    private void runAttempt() {
        final int guid = heldGuid;
        final int loadIndex = heldLoadIndex;
        final int session = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();
        final boolean emptyStore = store.getOldestCursor().isEmpty();
        final FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);
        final HashSet<Long> exhaustedSnapshot = backfill.getExhaustedSnapshot();

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, false);
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        attemptRounds = 0;
                        runAttempt();
                    } else {
                        loading = false;
                        postFeedResults(guid, loadIndex, new ArrayList<>(), 2, false, true);
                        postFeedCount(guid);
                        flushInitialLoadWaiters(true);
                    }
                });

            } else if (enumeration.included.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch)) {
                        if (canRetryStaleEnumeration()) {
                            olderPagingBoundsDirty = false;
                            attemptRounds = 0;
                            runAttempt();
                        } else {
                            loading = false;
                            postFeedResults(guid, loadIndex, new ArrayList<>(), 2, false, true);
                            postFeedCount(guid);
                            flushInitialLoadWaiters(true);
                        }
                        return;
                    }
                    applyEnumeration(enumeration);
                    olderPagingBoundsDirty = false;
                    unreadTracker.clear();
                    loading = false;
                    store.setEndReached(true);
                    postFeedResults(guid, loadIndex, new ArrayList<>(), 2);
                    postFeedCount(guid);
                    flushInitialLoadWaiters();
                });

            } else {
                FeedTimelineLoader.OlderPage page = loader.loadOlderPage(enumeration.included, cursor, exhaustedSnapshot);
                ArrayList<MessageObject> createdObjects = createMessageObjects(page.messages, page.users, page.chats);
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        olderPagingBoundsDirty = false;
                        attemptRounds = 0;
                        runAttempt();
                        return;
                    }
                    if (olderPagingBoundsDirty) {
                        olderPagingBoundsDirty = false;
                        attemptRounds = 0;
                        runAttempt();
                        return;
                    }
                    applyEnumeration(enumeration);
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    pruneStaleExclusions(FeedConfig.getInstance(currentAccount), messagesController);

                    if (page.failed) {
                        Log.d(TAG, "runAttempt: page FAILED - clearing loading");
                        loading = false;
                        postFeedResults(guid, loadIndex, new ArrayList<>(), 2, false, true);
                        postFeedCount(guid);
                        flushInitialLoadWaiters(true);
                        return;
                    }
                    store.getOldestCursor().set(page.last.date, page.last.uid, page.last.mid);
                    if (emptyStore && !page.first.isEmpty()) {
                        store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
                    }
                    messagesController.putUsers(page.users, true);
                    messagesController.putChats(page.chats, true);
                    ArrayList<MessageObject> appended = store.appendMessages(createdObjects, false);
                    Log.d(TAG, "runAttempt: page loaded - appended=" + appended.size() + " lastChunkRowCount=" + page.lastChunkRowCount + " hasIncomplete=" + page.hasIncomplete + " backfillCandidates=" + page.backfillCandidates.size() + " attemptRounds=" + attemptRounds);
                    if (appended.isEmpty() && page.lastChunkRowCount == 30) {
                        Log.d(TAG, "runAttempt: full chunk but nothing appended (all duplicates?) - retrying");
                        runAttempt();
                        return;
                    }
                    boolean endReached = !page.hasIncomplete && page.lastChunkRowCount < 30 && page.backfillCandidates.isEmpty();
                    if (!appended.isEmpty() || endReached || page.backfillCandidates.isEmpty() || attemptRounds >= 10) {
                        Log.d(TAG, "runAttempt: DONE - endReached=" + endReached + " appended=" + appended.size() + " postingResults loadIndex=" + loadIndex);
                        loading = false;
                        store.setEndReached(endReached);
                        postFeedResults(guid, loadIndex, appended, 2);
                        postFeedCount(guid);
                        flushInitialLoadWaiters();
                        return;
                    }
                    Log.d(TAG, "runAttempt: starting backfill round=" + attemptRounds + " candidates=" + page.backfillCandidates.size());
                    attemptRounds++;
                    backfill.startRound(page.backfillCandidates);
                });
            }
        });
    }

    private void postFeedResults(int guid, int loadIndex, ArrayList<MessageObject> arrayList, int mode) {
        postFeedResults(guid, loadIndex, arrayList, mode, false, false);
    }

    private void postFeedResults(int guid, int loadIndex, ArrayList<MessageObject> arrayList, int mode, boolean z, boolean failed) {
        Log.d(TAG, "postFeedResults: guid=" + guid + " loadIndex=" + loadIndex + " count=" + arrayList.size() + " mode=" + mode + " failed=" + failed);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoad, 0L, arrayList.size(), arrayList, Boolean.FALSE, 0, 0, 0, 0, mode, Boolean.TRUE, guid, loadIndex, 0, 0, 7, z, failed);
    }

    private void postFeedCount(int guid) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, guid, store.getCount(), store.isEndReached(), 0, 0, 0);
    }

    private boolean isEnumerationCurrent(FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig config, int gen, int epoch) {
        boolean valid = loader.isEnumerationCurrent(enumeration) && enumeration.configGeneration == config.getGeneration() && enumeration.configGeneration == gen && enumeration.cacheEpoch == epoch;
        if (valid) {
            staleEnumerationRetries = 0;
        }
        return valid;
    }

    private boolean canRetryStaleEnumeration() {
        if (staleEnumerationRetries >= 3) {
            staleEnumerationRetries = 0;
            return false;
        }
        staleEnumerationRetries++;
        return true;
    }

    private void applyEnumeration(FeedTimelineLoader.ChannelEnumeration enumeration) {
        if (enumeration.failed) return;
        hasChannels = enumeration.hasChannels;
        hasIncludedChannels = !enumeration.included.isEmpty();
        cachedIncludedChannelCount = enumeration.included.size();
        for (FeedTimelineLoader.ChannelSnapshot snapshot : enumeration.included) {
            int readMax = snapshot.readInboxMax;
            if (readMax <= 0 && snapshot.unreadCount <= 0) {
                readMax = snapshot.topMessage;
            }
            unreadTracker.applyReadInboxMax(snapshot.dialogId, readMax);
        }
    }

    private void flushInitialLoadWaiters() {
        flushInitialLoadWaiters(false);
    }

    private void flushInitialLoadWaiters(boolean failed) {
        if (initialLoadWaiters.isEmpty()) return;
        ArrayList<int[]> waiters = new ArrayList<>(initialLoadWaiters);
        initialLoadWaiters.clear();
        ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
        for (int[] waiter : waiters) {
            postFeedResults(waiter[0], waiter[1], visibleMessages, 0, false, failed);
            postFeedCount(waiter[0]);
        }
    }

    private void onBackfillRoundFinished() {
        if (loading) {
            runAttempt();
        }
    }

    public boolean loadNewer(int guid, int loadIndex) {
        ensureCurrentConfig();
        if (loadingNewer || store.getNewestCursor().isEmpty()) {
            return false;
        }
        loadingNewer = true;
        runLoadNewer(guid, loadIndex);
        return true;
    }

    private void runLoadNewer(final int guid, final int loadIndex) {
        final int session = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();
        final FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, false);
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        runLoadNewer(guid, loadIndex);
                    } else {
                        loadingNewer = false;
                        postNewerMessagesLoaded(guid, loadIndex, null, false, true);
                    }
                });
            } else if (enumeration.included.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        newerPagingBoundsDirty = false;
                        runLoadNewer(guid, loadIndex);
                    } else {
                        newerPagingBoundsDirty = false;
                        loadingNewer = false;
                        postNewerMessagesLoaded(guid, loadIndex, null, false);
                        postFeedCount(guid);
                    }
                });
            } else {
                FeedTimelineLoader.NewerPage page = loader.loadNewerPage(enumeration.included, cursor);
                ArrayList<MessageObject> createdObjects = createMessageObjects(page.messages, page.users, page.chats);
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) return;
                    if (!isEnumerationCurrent(enumeration, config, gen, epoch) && canRetryStaleEnumeration()) {
                        newerPagingBoundsDirty = false;
                        runLoadNewer(guid, loadIndex);
                        return;
                    }
                    if (newerPagingBoundsDirty) {
                        newerPagingBoundsDirty = false;
                        if (store.getNewestCursor().isEmpty()) {
                            loadingNewer = false;
                            postNewerMessagesLoaded(guid, loadIndex, null, false);
                            postFeedCount(guid);
                            return;
                        }
                        runLoadNewer(guid, loadIndex);
                        return;
                    }
                    loadingNewer = false;
                    applyEnumeration(enumeration);
                    if (page.failed) {
                        postNewerMessagesLoaded(guid, loadIndex, null, false, true);
                        return;
                    }
                    store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
                    if (page.messages.isEmpty()) {
                        postNewerMessagesLoaded(guid, loadIndex, null, page.hasMore);
                        if (!page.hasMore) {
                            postFeedCount(guid);
                        }
                        return;
                    }
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    messagesController.putUsers(page.users, true);
                    messagesController.putChats(page.chats, true);
                    postNewerMessagesLoaded(guid, loadIndex, store.appendMessages(createdObjects, true), page.hasMore);
                    if (!page.hasMore) {
                        postFeedCount(guid);
                    }
                    trimForInactiveCache();
                });
            }
        });
    }

    private void postNewerMessagesLoaded(int guid, int loadIndex, ArrayList<MessageObject> arrayList, boolean hasMore) {
        postNewerMessagesLoaded(guid, loadIndex, arrayList, hasMore, false);
    }

    private void postNewerMessagesLoaded(int guid, int loadIndex, ArrayList<MessageObject> arrayList, boolean hasMore, boolean failed) {
        int mode = 0;
        ArrayList<MessageObject> reversed = new ArrayList<>();
        if (arrayList != null && !arrayList.isEmpty()) {
            reversed.addAll(arrayList);
            Collections.reverse(reversed);
            mode = 1;
        }
        postFeedResults(guid, loadIndex, reversed, mode, hasMore, failed);
    }

    private ArrayList<MessageObject> createMessageObjects(ArrayList<TLRPC.Message> messages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        HashMap<Long, TLRPC.User> usersMap = new HashMap<>();
        HashMap<Long, TLRPC.Chat> chatsMap = new HashMap<>();
        for (TLRPC.User user : users) {
            usersMap.put(user.id, user);
        }
        for (TLRPC.Chat chat : chats) {
            chatsMap.put(chat.id, chat);
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>(messages.size());
        for (TLRPC.Message message : messages) {
            messageObjects.add(new MessageObject(currentAccount, message, null, usersMap, chatsMap, null, null, true, true, 0L, false, false, false, 4));
        }
        return messageObjects;
    }

    public void loadChannels(ChannelsCallback callback) {
        loadChannels(false, callback);
    }

    public void loadChannels(final boolean force, final ChannelsCallback callback) {
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int session = sessionGeneration;
        final int gen = config.getGeneration();
        final int epoch = loader.getChannelCacheEpoch();

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, session, force);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) return;
                if (!isEnumerationCurrent(enumeration, config, gen, epoch)) {
                    if (callback != null) {
                        callback.onChannels(new ArrayList<>(), 0, true, enumeration.configGeneration);
                    }
                } else {
                    applyEnumeration(enumeration);
                    if (!enumeration.failed) {
                        MessagesController.getInstance(currentAccount).putChats(enumeration.channels, true);
                    }
                    if (callback != null) {
                        callback.onChannels(enumeration.channels, enumeration.included.size(), enumeration.failed, enumeration.configGeneration);
                    }
                }
            });
        });
    }

    private void pruneStaleExclusions(FeedConfig config, MessagesController messagesController) {
        HashSet<Long> toRemove = null;
        for (Long id : config.getExcludedSnapshot()) {
            TLRPC.Chat chat = messagesController.getChat(-id);
            if (chat != null && !isEligibleChannel(chat)) {
                if (toRemove == null) toRemove = new HashSet<>();
                toRemove.add(id);
            }
        }
        if (toRemove != null) {
            config.removeExcluded(toRemove);
            markConfigApplied();
        }
    }

    public void replaceMessage(MessageObject oldMsg, MessageObject newMsg) {
        store.replaceMessage(oldMsg, newMsg);
    }

    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> ids) {
        boolean[] removed = new boolean[1];
        ArrayList<Integer> result = store.deleteMessages(dialogId, ids, removed);
        if (removed[0]) {
            onFeedRowsRemoved();
        }
        return result;
    }

    public ArrayList<Integer> deleteHistory(long dialogId, int maxId) {
        boolean[] removed = new boolean[1];
        ArrayList<Integer> result = store.deleteHistory(dialogId, maxId, removed);
        if (removed[0]) {
            onFeedRowsRemoved();
        }
        return result;
    }

    private void onFeedRowsRemoved() {
        if (loading) olderPagingBoundsDirty = true;
        if (loadingNewer) newerPagingBoundsDirty = true;
    }

    public ArrayList<MessageObject> updateViews(LongSparseArray<SparseIntArray> views, LongSparseArray<SparseIntArray> forwards, LongSparseArray<SparseArray<TLRPC.MessageReplies>> replies, boolean animated) {
        ArrayList<MessageObject> updated = new ArrayList<>();
        updateCounters(views, true, updated);
        updateCounters(forwards, false, updated);
        updateReplies(replies, animated, updated);
        return updated;
    }

    private void updateCounters(LongSparseArray<SparseIntArray> sparseArray, boolean views, ArrayList<MessageObject> updated) {
        if (sparseArray == null) return;
        for (int i = 0; i < sparseArray.size(); i++) {
            long dialogId = sparseArray.keyAt(i);
            SparseIntArray items = sparseArray.valueAt(i);
            for (int k = 0; k < items.size(); k++) {
                MessageObject message = getMessage(dialogId, items.keyAt(k));
                if (message != null) {
                    int val = items.valueAt(k);
                    TLRPC.Message msg = message.messageOwner;
                    if (views) {
                        if (val > msg.views) {
                            msg.views = val;
                            addUpdated(updated, message);
                        }
                    } else if (val > msg.forwards) {
                        msg.forwards = val;
                        addUpdated(updated, message);
                    }
                }
            }
        }
    }

    private void updateReplies(LongSparseArray<SparseArray<TLRPC.MessageReplies>> sparseArray, boolean animated, ArrayList<MessageObject> updated) {
        if (sparseArray == null) return;
        for (int i = 0; i < sparseArray.size(); i++) {
            long dialogId = sparseArray.keyAt(i);
            SparseArray<TLRPC.MessageReplies> items = sparseArray.valueAt(i);
            for (int k = 0; k < items.size(); k++) {
                MessageObject message = getMessage(dialogId, items.keyAt(k));
                TLRPC.MessageReplies replies = items.valueAt(k);
                if (message != null && replies != null) {
                    TLRPC.Message msg = message.messageOwner;
                    if (animated) {
                        if (msg.replies == null) {
                            msg.replies = new TLRPC.TL_messageReplies();
                        }
                        msg.replies.replies += replies.replies;
                        for (int j = 0; j < replies.recent_repliers.size(); j++) {
                            msg.replies.recent_repliers.remove(replies.recent_repliers.get(j));
                        }
                        msg.replies.recent_repliers.addAll(0, replies.recent_repliers);
                        while (msg.replies.recent_repliers.size() > 3) {
                            msg.replies.recent_repliers.remove(0);
                        }
                    } else {
                        if (msg.replies == null || replies.replies_pts > msg.replies.replies_pts || replies.read_max_id > msg.replies.read_max_id || replies.max_id > msg.replies.max_id) {
                            msg.replies = replies;
                        }
                    }
                    message.animateComments = true;
                    addUpdated(updated, message);
                }
            }
        }
    }

    private static void addUpdated(ArrayList<MessageObject> list, MessageObject item) {
        if (!list.contains(item)) {
            list.add(item);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            backfill.onMessagesDidLoad(args);
            return;
        }
        if (id == NotificationCenter.loadingMessagesFailed) {
            backfill.onLoadingMessagesFailed(args);
            return;
        }
        if (id == NotificationCenter.messagesDeleted) {
            if (isUiActive() || ((Boolean) args[2])) return;
            long dialogId = (Long) args[1];
            if (dialogId == 0) return;
            if (dialogId > 0) dialogId = -dialogId;
            deleteMessages(dialogId, (ArrayList<Integer>) args[0]);
            return;
        }
        if (id == NotificationCenter.historyCleared) {
            if (isUiActive()) return;
            long dialogId = (Long) args[0];
            if (DialogObject.isChatDialog(dialogId)) {
                deleteHistory(dialogId, (Integer) args[1]);
            }
            return;
        }
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (isUiActive() || ((Boolean) args[2]) || store.isEmpty() || store.getNewestCursor().isEmpty()) return;
            long dialogId = (Long) args[0];
            if (isIncludedChannelPost(dialogId)) {
                scheduleClosedRefresh();
            }
        }
    }

    private void scheduleClosedRefresh() {
        if (closedRefreshScheduled) return;
        closedRefreshScheduled = true;
        AndroidUtilities.runOnUIThread(closedRefreshRunnable, 1000L);
    }

    private void runClosedRefresh() {
        closedRefreshScheduled = false;
        if (isUiActive() || loadingNewer || store.isEmpty() || store.getNewestCursor().isEmpty()) {
            return;
        }
        loadNewer(closedRefreshGuid, 0);
    }
}
