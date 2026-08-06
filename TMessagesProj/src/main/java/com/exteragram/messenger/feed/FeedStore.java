package com.exteragram.messenger.feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import org.telegram.messenger.MessageObject;

public final class FeedStore {
    private int count;
    private boolean endReached;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final FeedMessageIdentityMap identityMap = new FeedMessageIdentityMap();
    private final HashSet<Long> hiddenDialogIds = new HashSet<>();
    private final FeedTimelineLoader.Cursor oldestCursor = new FeedTimelineLoader.Cursor();
    private final FeedTimelineLoader.Cursor newestCursor = new FeedTimelineLoader.Cursor();

    public ArrayList<MessageObject> getMessages() {
        return messages;
    }

    public ArrayList<MessageObject> getVisibleMessages() {
        if (hiddenDialogIds.isEmpty()) {
            return new ArrayList<>(messages);
        }
        ArrayList<MessageObject> visible = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && !hiddenDialogIds.contains(msg.getDialogId())) {
                visible.add(msg);
            }
        }
        return visible;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int getVisibleCount() {
        if (hiddenDialogIds.isEmpty()) {
            return messages.size();
        }
        int visible = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && !hiddenDialogIds.contains(msg.getDialogId())) {
                visible++;
            }
        }
        return visible;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && msg.getDialogId() == dialogId) {
                return true;
            }
        }
        return false;
    }

    public HashSet<Long> getLoadedDialogIds() {
        HashSet<Long> ids = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null) {
                ids.add(msg.getDialogId());
            }
        }
        return ids;
    }

    public HashSet<Long> getHiddenSnapshot() {
        return new HashSet<>(hiddenDialogIds);
    }

    public boolean setHidden(long dialogId, boolean hidden) {
        boolean changed = hidden ? hiddenDialogIds.add(dialogId) : hiddenDialogIds.remove(dialogId);
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public boolean applyIncludedDialogs(HashSet<Long> includedDialogs) {
        HashSet<Long> loaded = getLoadedDialogIds();
        boolean changed = false;
        for (Long id : loaded) {
            if (!includedDialogs.contains(id)) {
                changed |= hiddenDialogIds.add(id);
            }
        }
        Iterator<Long> it = hiddenDialogIds.iterator();
        while (it.hasNext()) {
            Long next = it.next();
            if (includedDialogs.contains(next) || !loaded.contains(next)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public FeedTimelineLoader.Cursor getOldestCursor() {
        return oldestCursor;
    }

    public FeedTimelineLoader.Cursor getNewestCursor() {
        return newestCursor;
    }

    public boolean isEndReached() {
        return endReached;
    }

    public void setEndReached(boolean endReached) {
        this.endReached = endReached;
        updateCount();
    }

    public int getCount() {
        return count;
    }

    private void updateCount() {
        int visibleCount = 0;
        if (!messages.isEmpty()) {
            visibleCount = (endReached ? 0 : 3) + getVisibleCount();
        }
        count = visibleCount;
    }

    public void clear() {
        messages.clear();
        identityMap.clear();
        hiddenDialogIds.clear();
        endReached = false;
        count = 0;
        oldestCursor.set(0, 0L, 0);
        newestCursor.set(0, 0L, 0);
    }

    public ArrayList<MessageObject> appendMessages(ArrayList<MessageObject> list, boolean prepend) {
        ArrayList<MessageObject> added = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            MessageObject msg = list.get(i);
            if (identityMap.register(msg)) {
                added.add(msg);
            }
        }
        if (prepend) {
            ArrayList<MessageObject> reversed = new ArrayList<>(added);
            Collections.reverse(reversed);
            messages.addAll(0, reversed);
        } else {
            messages.addAll(added);
        }
        updateCount();
        return added;
    }

    public ArrayList<MessageObject> mergeRows(ArrayList<MessageObject> list) {
        ArrayList<MessageObject> added = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            MessageObject msg = list.get(i);
            if (identityMap.register(msg)) {
                added.add(msg);
            }
        }
        int mergeIdx = 0;
        int i = 0;
        while (i < added.size()) {
            MessageObject msg = added.get(i);
            int endGroup = i + 1;
            long groupId = msg.getGroupId();
            while (groupId != 0 && endGroup < added.size() && added.get(endGroup).getGroupId() == groupId && added.get(endGroup).getDialogId() == msg.getDialogId()) {
                endGroup++;
            }
            mergeIdx = findMergeIndex(msg, mergeIdx);
            while (i < endGroup) {
                messages.add(mergeIdx, added.get(i));
                i++;
                mergeIdx++;
            }
        }
        updateCount();
        return added;
    }

    private int findMergeIndex(MessageObject msg, int startIndex) {
        int i = startIndex;
        while (i < messages.size()) {
            MessageObject current = messages.get(i);
            if (current != null && compareTimeline(current.messageOwner.date, current.getDialogId(), current.getRealId(), msg.messageOwner.date, msg.getDialogId(), msg.getRealId()) < 0) {
                break;
            }
            i++;
        }
        while (i > 0 && i < messages.size()) {
            MessageObject prev = messages.get(i - 1);
            MessageObject curr = messages.get(i);
            if (prev == null || curr == null || prev.getGroupId() == 0 || prev.getGroupId() != curr.getGroupId() || prev.getDialogId() != curr.getDialogId()) {
                break;
            }
            i++;
        }
        return i;
    }

    public void replaceMessage(MessageObject oldMsg, MessageObject newMsg) {
        if (oldMsg == null || newMsg == null) {
            return;
        }
        int idx = messages.indexOf(oldMsg);
        if (idx >= 0) {
            messages.set(idx, newMsg);
        }
        identityMap.replace(newMsg);
    }

    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> ids, boolean[] outRemoved) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        if (ids == null) {
            outRemoved[0] = false;
            return removedIds;
        }
        HashSet<Integer> idSet = new HashSet<>(ids);
        HashSet<Integer> purgedSet = new HashSet<>();
        boolean removed = false;
        for (int i = 0; i < ids.size(); i++) {
            MessageObject msg = identityMap.getByRealId(dialogId, ids.get(i));
            if (msg != null) {
                removed |= messages.remove(msg);
                purgeRow(msg, removedIds, purgedSet);
            }
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg != null && msg.getDialogId() == dialogId && idSet.contains(msg.getRealId())) {
                messages.remove(i);
                purgeRow(msg, removedIds, purgedSet);
                removed = true;
            }
        }
        if (removed) {
            onRowsRemoved();
        }
        outRemoved[0] = removed;
        return removedIds;
    }

    public ArrayList<Integer> deleteHistory(long dialogId, int maxId, boolean[] outRemoved) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        HashSet<Integer> purgedSet = new HashSet<>();
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg != null && msg.getDialogId() == dialogId && msg.getRealId() > 0 && msg.getRealId() <= maxId) {
                messages.remove(i);
                purgeRow(msg, removedIds, purgedSet);
                removed = true;
            }
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        outRemoved[0] = removed;
        return removedIds;
    }

    public boolean trim(int maxMessages) {
        if (messages.size() <= maxMessages) {
            return false;
        }
        MessageObject pivot = messages.get(maxMessages - 1);
        int pivotDate = pivot.messageOwner.date;
        long pivotDialogId = pivot.getDialogId();
        int pivotRealId = pivot.getRealId();
        boolean trimmed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg != null && compareTimeline(msg.messageOwner.date, msg.getDialogId(), msg.getRealId(), pivotDate, pivotDialogId, pivotRealId) < 0) {
                messages.remove(i);
                identityMap.releaseRow(msg);
                trimmed = true;
            }
        }
        if (!trimmed) {
            return false;
        }
        if (messages.isEmpty()) {
            oldestCursor.set(0, 0L, 0);
        } else {
            int oldestDate = 0;
            int oldestRealId = 0;
            long oldestDialogId = 0;
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                if (msg != null && (oldestDate == 0 || compareTimeline(msg.messageOwner.date, msg.getDialogId(), msg.getRealId(), oldestDate, oldestDialogId, oldestRealId) < 0)) {
                    oldestDate = msg.messageOwner.date;
                    oldestDialogId = msg.getDialogId();
                    oldestRealId = msg.getRealId();
                }
            }
            oldestCursor.set(oldestDate, oldestDialogId, oldestRealId);
        }
        endReached = false;
        updateCount();
        return true;
    }

    private void onRowsRemoved() {
        if (!rebuildPagingCursorsFromLoadedRows()) {
            endReached = false;
        }
        updateCount();
    }

    private boolean rebuildPagingCursorsFromLoadedRows() {
        boolean wasOldestEmpty = oldestCursor.isEmpty();
        int newestDate = 0, oldestDate = 0;
        int newestRealId = 0, oldestRealId = 0;
        long newestDialogId = 0, oldestDialogId = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (isPagingRow(msg)) {
                int date = msg.messageOwner.date;
                long dialogId = msg.getDialogId();
                int realId = msg.getRealId();
                if (newestDate == 0 || compareTimeline(date, dialogId, realId, newestDate, newestDialogId, newestRealId) > 0) {
                    newestDate = date;
                    newestDialogId = dialogId;
                    newestRealId = realId;
                }
                if (oldestDate == 0 || compareTimeline(date, dialogId, realId, oldestDate, oldestDialogId, oldestRealId) < 0) {
                    oldestDate = date;
                    oldestDialogId = dialogId;
                    oldestRealId = realId;
                }
            }
        }
        if (newestDate == 0) {
            oldestCursor.set(0, 0L, 0);
            newestCursor.set(0, 0L, 0);
            return false;
        }
        if (!wasOldestEmpty) {
            if (compareTimeline(oldestDate, oldestDialogId, oldestRealId, oldestCursor.date, oldestCursor.uid, oldestCursor.mid) > 0) {
                endReached = false;
            }
        }
        newestCursor.set(newestDate, newestDialogId, newestRealId);
        oldestCursor.set(oldestDate, oldestDialogId, oldestRealId);
        return true;
    }

    private static boolean isPagingRow(MessageObject msg) {
        return msg != null && !msg.isDateObject && msg.messageOwner != null && msg.getRealId() > 0;
    }

    public static int compareTimeline(int d1, long u1, int m1, int d2, long u2, int m2) {
        if (d1 != d2) {
            return Integer.compare(d1, d2);
        }
        if (u1 != u2) {
            return Long.compare(u1, u2);
        }
        return Integer.compare(m1, m2);
    }

    private void purgeRow(MessageObject msg, ArrayList<Integer> ids, HashSet<Integer> purgedSet) {
        identityMap.purge(msg);
        if (purgedSet.add(msg.getId())) {
            ids.add(msg.getId());
        }
    }

    public boolean hasNoSyntheticIds() {
        return identityMap.isEmpty();
    }

    public MessageObject getMessage(long dialogId, int messageId) {
        return identityMap.getByAnyId(dialogId, messageId);
    }

    public int resolveRealMessageId(long dialogId, int generatedId) {
        return identityMap.resolveRealMessageId(dialogId, generatedId);
    }

    public long resolveRealDialogId(int generatedId) {
        return identityMap.resolveRealDialogId(generatedId);
    }
}
