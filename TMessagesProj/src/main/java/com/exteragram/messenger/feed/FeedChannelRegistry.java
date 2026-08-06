package com.exteragram.messenger.feed;

import androidx.collection.LongSparseArray;
import java.util.ArrayList;
import java.util.HashSet;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

public class FeedChannelRegistry implements NotificationCenter.NotificationCenterDelegate {

    private static final FeedChannelRegistry[] instances = new FeedChannelRegistry[16];
    private static final Object[] locks = new Object[16];

    static {
        for (int i = 0; i < 16; i++) {
            locks[i] = new Object();
        }
    }

    public final int currentAccount;
    private boolean built;
    private boolean rebuildScheduled;
    private final HashSet<Long> channelIds = new HashSet<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();

    private final Runnable rebuildRunnable = () -> {
        rebuildScheduled = false;
        rebuild(true);
    };

    public interface Listener {
        void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed);
    }

    public static FeedChannelRegistry getInstance(int account) {
        if (account < 0 || account >= 16) {
            account = org.telegram.messenger.UserConfig.selectedAccount;
            if (account < 0 || account >= 16) account = 0;
        }
        FeedChannelRegistry registry = instances[account];
        if (registry != null) {
            return registry;
        }
        synchronized (locks[account]) {
            registry = instances[account];
            if (registry == null) {
                registry = new FeedChannelRegistry(account);
                instances[account] = registry;
            }
        }
        return registry;
    }

    private FeedChannelRegistry(final int account) {
        currentAccount = account;
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.dialogsNeedReload));
    }

    public void addListener(Listener listener) {
        ensureBuilt();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;
        rebuild(false);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            ensureBuilt();
            if (rebuildScheduled) {
                return;
            }
            rebuildScheduled = true;
            AndroidUtilities.runOnUIThread(rebuildRunnable, 500L);
        }
    }

    private void rebuild(boolean notify) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        LongSparseArray<TLRPC.Dialog> dialogs = messagesController.dialogs_dict;
        HashSet<Long> currentList = new HashSet<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.valueAt(i);
            if (dialog != null && DialogObject.isChatDialog(dialog.id) && FeedController.isEligibleChannel(messagesController.getChat(-dialog.id))) {
                currentList.add(dialog.id);
            }
        }
        HashSet<Long> removed = null;
        HashSet<Long> added = null;
        for (Long id : currentList) {
            if (!channelIds.contains(id)) {
                if (added == null) {
                    added = new HashSet<>();
                }
                added.add(id);
            }
        }
        for (Long id : channelIds) {
            if (!currentList.contains(id)) {
                if (removed == null) {
                    removed = new HashSet<>();
                }
                removed.add(id);
            }
        }
        if (added == null && removed == null) {
            return;
        }
        channelIds.clear();
        channelIds.addAll(currentList);
        if (notify) {
            if (added == null) {
                added = new HashSet<>();
            }
            if (removed == null) {
                removed = new HashSet<>();
            }
            for (int i = listeners.size() - 1; i >= 0; i--) {
                listeners.get(i).onFeedChannelsChanged(added, removed);
            }
        }
    }
}
