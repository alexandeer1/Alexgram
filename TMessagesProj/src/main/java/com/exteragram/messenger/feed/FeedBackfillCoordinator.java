package com.exteragram.messenger.feed;

import java.util.ArrayList;
import java.util.HashSet;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

final class FeedBackfillCoordinator {
    private final int currentAccount;
    private final Runnable onRoundFinished;
    private final int guid = ConnectionsManager.generateClassGuid();
    private final HashSet<Long> pending = new HashSet<>();
    private final HashSet<Long> exhausted = new HashSet<>();
    private int loadIndex;
    private int roundId;
    private boolean running;

    public FeedBackfillCoordinator(int account, Runnable onRoundFinished) {
        this.currentAccount = account;
        this.onRoundFinished = onRoundFinished;
    }

    public HashSet<Long> getExhaustedSnapshot() {
        return new HashSet<>(exhausted);
    }

    public void clearExhausted() {
        exhausted.clear();
    }

    public void cancel() {
        running = false;
        roundId++;
        pending.clear();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(guid);
    }

    public void startRound(ArrayList<long[]> list) {
        running = true;
        final int currentRound = ++roundId;
        pending.clear();
        int count = Math.min(4, list.size());
        for (int i = 0; i < count; i++) {
            pending.add(list.get(i)[0]);
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        for (int i = 0; i < count; i++) {
            long dialogId = list.get(i)[0];
            int maxId = (int) list.get(i)[1];
            int loadIdx = loadIndex++;
            messagesController.loadMessages(dialogId, 0L, false, 20, maxId, 0, false, 0, guid, 0, 0, 0, 0L, 0, loadIdx, false);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (currentRound == roundId && running) {
                exhausted.addAll(pending);
                finishRound();
            }
        }, 10000L);
    }

    public void onMessagesDidLoad(Object... args) {
        if (((Integer) args[10]) != guid) {
            return;
        }
        Long dialogIdObj = (Long) args[0];
        long dialogId = dialogIdObj;
        if (((ArrayList<?>) args[2]).size() < 20) {
            exhausted.add(dialogIdObj);
        }
        onResult(dialogId);
    }

    public void onLoadingMessagesFailed(Object... args) {
        if (((Integer) args[0]) != guid) {
            return;
        }
        long dialogId = 0;
        Object req = args[1];
        if (req instanceof TLRPC.TL_messages_getHistory) {
            TLRPC.InputPeer peer = ((TLRPC.TL_messages_getHistory) req).peer;
            if (peer != null) {
                long channelId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
                dialogId = -channelId;
            }
        }
        if (dialogId != 0) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    private void onResult(long dialogId) {
        if (running && pending.remove(dialogId) && pending.isEmpty()) {
            finishRound();
        }
    }

    private void finishRound() {
        running = false;
        roundId++;
        pending.clear();
        if (onRoundFinished != null) {
            onRoundFinished.run();
        }
    }
}
