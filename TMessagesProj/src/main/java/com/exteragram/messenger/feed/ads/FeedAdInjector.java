package com.exteragram.messenger.feed.ads;

import com.exteragram.messenger.feed.FeedChatIntegration;
import com.exteragram.messenger.feed.FeedMessageUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.telegram.messenger.MessageObject;

public final class FeedAdInjector {
    private final int currentAccount;
    private final FeedChatIntegration.Host host;
    private final HashMap<MessageObject, MessageObject> adByAnchor = new HashMap<>();
    private final HashMap<MessageObject, Integer> slotOrdinalByAnchor = new HashMap<>();

    public FeedAdInjector(int account, FeedChatIntegration.Host host) {
        this.currentAccount = account;
        this.host = host;
    }

    public void clear() {
        this.adByAnchor.clear();
        this.slotOrdinalByAnchor.clear();
    }

    public void refresh(MessageObject focusMessage) {
        if (!host.isListReady()) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        FeedAdController controller = FeedAdController.getInstance(currentAccount);
        boolean enabled = controller.isEnabled();
        ArrayList<MessageObject> anchors = new ArrayList<>(adByAnchor.keySet());
        boolean listChanged = false;
        for (MessageObject anchor : anchors) {
            if (!enabled || !messages.contains(anchor)) {
                listChanged |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            }
        }
        if (enabled) {
            boolean keptRevalidated = revalidateKeptAds(messages, controller) | listChanged;
            for (Map.Entry<MessageObject, MessageObject> entry : adByAnchor.entrySet()) {
                MessageObject key = entry.getKey();
                MessageObject value = entry.getValue();
                int keyIdx = messages.indexOf(key);
                if (keyIdx >= 0) {
                    int targetIdx = keyIdx + 1;
                    int curIdx = messages.indexOf(value);
                    if (curIdx != targetIdx) {
                        if (curIdx >= 0) {
                            messages.remove(curIdx);
                            host.notifyMessageRemoved(curIdx);
                            targetIdx = messages.indexOf(key) + 1;
                        }
                        messages.add(targetIdx, value);
                        host.notifyMessageInserted(targetIdx);
                        keptRevalidated = true;
                    }
                }
            }
            ArrayList<AnchorSlot> newAnchors = computeNewAnchors(messages, focusMessage, controller);
            for (AnchorSlot slot : newAnchors) {
                MessageObject anchor = slot.anchor;
                FeedAd ad = controller.nextAd();
                if (ad == null) {
                    break;
                }
                int anchorIdx = messages.indexOf(anchor);
                if (anchorIdx >= 0) {
                    MessageObject adObj = FeedAdFactory.createAdMessageObject(currentAccount, ad);
                    adObj.stableId = host.nextStableId();
                    adByAnchor.put(anchor, adObj);
                    slotOrdinalByAnchor.put(anchor, slot.ordinal);
                    int insIdx = anchorIdx + 1;
                    messages.add(insIdx, adObj);
                    host.notifyMessageInserted(insIdx);
                    keptRevalidated = true;
                }
            }
            listChanged = keptRevalidated;
        }
        if (listChanged) {
            host.onFeedListChanged();
            host.invalidateVisiblePart();
        }
    }

    private boolean revalidateKeptAds(ArrayList<MessageObject> messages, FeedAdController controller) {
        if (adByAnchor.isEmpty()) {
            return false;
        }
        ArrayList<MessageObject> anchors = new ArrayList<>(adByAnchor.keySet());
        boolean removed = false;
        for (MessageObject anchor : anchors) {
            int idx = messages.indexOf(anchor);
            if (idx >= 0) {
                int lastMember = lastGroupMemberIndex(messages, idx);
                if (lastMember != idx) {
                    MessageObject ad = adByAnchor.remove(anchor);
                    Integer ordinal = slotOrdinalByAnchor.remove(anchor);
                    MessageObject newAnchor = messages.get(lastMember);
                    if (adByAnchor.containsKey(newAnchor)) {
                        removed |= removeAd(messages, ad);
                    } else {
                        adByAnchor.put(newAnchor, ad);
                        if (ordinal != null) {
                            slotOrdinalByAnchor.put(newAnchor, ordinal);
                        }
                    }
                }
            }
        }
        HashMap<MessageObject, Integer> postIndices = new HashMap<>();
        int count = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                postIndices.put(messages.get(i), count);
                count++;
            }
        }
        int baseEvery = controller.getBaseEvery();
        int minTrailing = controller.getMinTrailing();
        TreeMap<Integer, MessageObject> treeMap = new TreeMap<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            Integer ord = postIndices.get(anchor);
            if (ord != null) {
                treeMap.put(ord, anchor);
            }
        }
        int lastOrd = Integer.MIN_VALUE;
        for (Map.Entry<Integer, MessageObject> entry : treeMap.entrySet()) {
            int ord = entry.getKey();
            if (ord < minTrailing || (lastOrd != Integer.MIN_VALUE && ord - lastOrd < baseEvery)) {
                MessageObject anchor = entry.getValue();
                removed |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            } else {
                lastOrd = ord;
            }
        }
        return removed;
    }

    private boolean removeAd(ArrayList<MessageObject> messages, MessageObject ad) {
        if (ad == null) return false;
        int idx = messages.indexOf(ad);
        if (idx < 0) return false;
        messages.remove(idx);
        host.notifyMessageRemoved(idx);
        return true;
    }

    private ArrayList<AnchorSlot> computeNewAnchors(ArrayList<MessageObject> messages, MessageObject focusMessage, FeedAdController controller) {
        ArrayList<Integer> postPositions = new ArrayList<>();
        HashMap<MessageObject, Integer> postOrdinals = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                postOrdinals.put(messages.get(i), postPositions.size());
                postPositions.add(i);
            }
        }
        ArrayList<AnchorSlot> newAnchors = new ArrayList<>();
        int size = postPositions.size();
        if (size == 0) return newAnchors;

        int firstAfter = controller.getFirstAfter();
        int effectiveEvery = controller.getEffectiveEvery();
        int minTrailing = controller.getMinTrailing();
        int focusIdx = focusMessage != null ? messages.indexOf(focusMessage) : -1;
        int centerOrd = 0;
        if (focusIdx >= 0) {
            for (int i = 0; i < postPositions.size(); i++) {
                if (postPositions.get(i) >= focusIdx) {
                    centerOrd = i;
                    break;
                }
            }
        }

        TreeSet<Integer> targetSlots = new TreeSet<>();
        for (int i = centerOrd - firstAfter; i >= 0; i -= effectiveEvery) {
            targetSlots.add(i);
        }
        for (int i = centerOrd + firstAfter; i < size; i += effectiveEvery) {
            targetSlots.add(i);
        }

        ArrayList<Integer> existingOrdinals = new ArrayList<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            if (postOrdinals.containsKey(anchor)) {
                Integer ord = slotOrdinalByAnchor.get(anchor);
                if (ord == null) ord = postOrdinals.get(anchor);
                existingOrdinals.add(ord);
            }
        }

        for (Integer targetOrd : targetSlots) {
            int ordVal = targetOrd;
            if (ordVal >= minTrailing) {
                int msgIdx = lastGroupMemberIndex(messages, postPositions.get(ordVal));
                MessageObject anchor = messages.get(msgIdx);
                if (postOrdinals.containsKey(anchor) && !adByAnchor.containsKey(anchor)) {
                    boolean ok = true;
                    for (Integer existing : existingOrdinals) {
                        if (Math.abs(ordVal - existing) < effectiveEvery) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        newAnchors.add(new AnchorSlot(anchor, ordVal));
                        existingOrdinals.add(ordVal);
                    }
                }
            }
        }
        return newAnchors;
    }

    private static int lastGroupMemberIndex(ArrayList<MessageObject> messages, int idx) {
        long groupId = messages.get(idx).getGroupId();
        if (groupId == 0) {
            return idx;
        }
        int cur = idx;
        do {
            idx++;
            if (idx >= messages.size()) break;
            MessageObject msg = messages.get(idx);
            if (!FeedMessageUtils.isPostRow(msg)) break;
            cur = idx;
        } while (messages.get(idx).getGroupId() == groupId);
        return cur;
    }

    public static final class AnchorSlot {
        public final MessageObject anchor;
        public final int ordinal;

        public AnchorSlot(MessageObject anchor, int ordinal) {
            this.anchor = anchor;
            this.ordinal = ordinal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnchorSlot)) return false;
            AnchorSlot that = (AnchorSlot) o;
            return ordinal == that.ordinal && Objects.equals(anchor, that.anchor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(anchor, ordinal);
        }
    }
}
