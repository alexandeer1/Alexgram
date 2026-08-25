package com.exteragram.messenger.feed;

import android.text.TextUtils;
import androidx.collection.LongSparseArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

final class FeedTimelineLoader {
    private final AtomicInteger channelCacheEpoch = new AtomicInteger();
    private volatile ChannelSet channelSetCache;
    private final int currentAccount;

    public static final class ChannelEnumeration {
        int cacheEpoch;
        int configGeneration;
        boolean failed;
        boolean hasChannels;
        final ArrayList<ChannelSnapshot> included = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
    }

    public static final class NewerPage {
        boolean failed;
        boolean hasMore;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final Cursor first = new Cursor();
    }

    public static final class OlderPage {
        boolean failed;
        boolean hasIncomplete;
        int lastChunkRowCount;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final ArrayList<long[]> backfillCandidates = new ArrayList<>();
        final Cursor last = new Cursor();
        final Cursor first = new Cursor();
    }

    public static final class WindowPage {
        boolean failed;
        boolean truncated;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    }

    public FeedTimelineLoader(int account) {
        this.currentAccount = account;
    }

    public static final class Cursor {
        int date;
        int mid;
        long uid;

        public boolean isEmpty() {
            return this.date == 0;
        }

        public void set(int date, long uid, int mid) {
            this.date = date;
            this.uid = uid;
            this.mid = mid;
        }
    }

    public static final class ChannelSnapshot {
        int depthDate;
        int depthMid;
        final long dialogId;
        boolean hasCached;
        boolean hasHole;
        int holeEnd;
        boolean incomplete;
        boolean localStartReached;
        final int readInboxMax;
        final int topMessage;
        final int unreadCount;

        public ChannelSnapshot(long dialogId, int readInboxMax, int unreadCount, int topMessage) {
            this.dialogId = dialogId;
            this.readInboxMax = readInboxMax;
            this.unreadCount = unreadCount;
            this.topMessage = topMessage;
        }
    }

    public static final class ChannelSet {
        final int configGen;
        boolean failed;
        boolean hasChannels;
        final int sessionGen;
        final ArrayList<long[]> includedRows = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();

        public ChannelSet(int sessionGen, int configGen) {
            this.sessionGen = sessionGen;
            this.configGen = configGen;
        }
    }

    public synchronized void invalidateChannelCache() {
        this.channelCacheEpoch.incrementAndGet();
        this.channelSetCache = null;
    }

    public ChannelEnumeration enumerateChannels(FeedConfig config, int sessionGen, boolean force) {
        ChannelSet channelSet;
        int epoch;
        boolean forceReload = force;
        int attempts = 0;
        while (true) {
            synchronized (this) {
                channelSet = this.channelSetCache;
                epoch = this.channelCacheEpoch.get();
            }
            FeedConfig.Snapshot snapshot = config.snapshot();
            int configGen = snapshot.getGeneration();
            if (forceReload || channelSet == null || channelSet.sessionGen != sessionGen || channelSet.configGen != configGen) {
                channelSet = buildChannelSet(snapshot.getIncludeArchived(), new HashSet<>(snapshot.getExcludedChannels()), sessionGen, configGen);
                if (!channelSet.failed) {
                    synchronized (this) {
                        if (epoch == this.channelCacheEpoch.get()) {
                            this.channelSetCache = channelSet;
                        }
                    }
                }
            }
            if (!channelSet.failed || attempts >= 3) {
                break;
            }
            attempts++;
            forceReload = true;
        }
        ChannelEnumeration enumeration = new ChannelEnumeration();
        enumeration.hasChannels = channelSet.hasChannels;
        enumeration.failed = channelSet.failed;
        enumeration.configGeneration = channelSet.configGen;
        enumeration.cacheEpoch = epoch;
        enumeration.channels.addAll(channelSet.channels);
        for (int i = 0; i < channelSet.includedRows.size(); i++) {
            long[] row = channelSet.includedRows.get(i);
            enumeration.included.add(new ChannelSnapshot(row[0], (int) row[1], (int) row[2], (int) row[3]));
        }
        return enumeration;
    }

    public synchronized int getChannelCacheEpoch() {
        return this.channelCacheEpoch.get();
    }

    public synchronized boolean isEnumerationCurrent(ChannelEnumeration enumeration) {
        return enumeration != null && enumeration.cacheEpoch == this.channelCacheEpoch.get();
    }

    private ChannelSet buildChannelSet(boolean includeArchived, HashSet<Long> excluded, int sessionGen, int configGen) {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(this.currentAccount);
        MessagesController messagesController = MessagesController.getInstance(this.currentAccount);
        ChannelSet channelSet = new ChannelSet(sessionGen, configGen);
        ArrayList<long[]> rawList = new ArrayList<>();
        ArrayList<Long> chatIds = new ArrayList<>();
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized("SELECT d.did, d.inbox_max, d.unread_count, d.last_mid, d.folder_id FROM dialogs AS d WHERE d.did < 0", new Object[0]);
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                if (DialogObject.isChatDialog(dialogId)) {
                    rawList.add(new long[]{dialogId, cursor.intValue(1), cursor.intValue(2), cursor.intValue(3), cursor.intValue(4)});
                    chatIds.add(-dialogId);
                }
            }
            cursor.dispose();
            
            LongSparseArray<TLRPC.Chat> chatMap = new LongSparseArray<>();
            if (!chatIds.isEmpty()) {
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                try {
                    messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), chats);
                    for (int i = 0; i < chats.size(); i++) {
                        chatMap.put(chats.get(i).id, chats.get(i));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            for (int i = 0; i < rawList.size(); i++) {
                long[] row = rawList.get(i);
                long dialogId = row[0];
                TLRPC.Chat chat = chatMap.get(-dialogId);
                if (chat == null) {
                    chat = messagesController.getChat(-dialogId);
                }
                if (FeedController.isEligibleChannel(chat) && (row[4] != 1 || includeArchived)) {
                    channelSet.hasChannels = true;
                    channelSet.channels.add(chat);
                    if (!excluded.contains(dialogId)) {
                        channelSet.includedRows.add(new long[]{dialogId, row[1], row[2], row[3]});
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (channelSet.channels.isEmpty()) {
            LongSparseArray<TLRPC.Dialog> dialogsDict = messagesController.dialogs_dict;
            for (int i = 0; i < dialogsDict.size(); i++) {
                TLRPC.Dialog d = dialogsDict.valueAt(i);
                if (d != null && DialogObject.isChatDialog(d.id)) {
                    TLRPC.Chat chat = messagesController.getChat(-d.id);
                    if (FeedController.isEligibleChannel(chat) && (d.folder_id != 1 || includeArchived)) {
                        channelSet.hasChannels = true;
                        channelSet.channels.add(chat);
                        if (!excluded.contains(d.id)) {
                            channelSet.includedRows.add(new long[]{d.id, d.read_inbox_max_id, d.unread_count, d.top_message});
                        }
                    }
                }
            }
        }

        return channelSet;
    }

    public OlderPage loadOlderPage(ArrayList<ChannelSnapshot> included, Cursor cursor, HashSet<Long> exhausted) {
        OlderPage olderPage = new OlderPage();
        boolean isCursorEmpty = cursor.isEmpty();
        olderPage.last.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(included.size());
            for (int i = 0; i < included.size(); i++) {
                dialogIds.add(included.get(i).dialogId);
            }
            String dialogIdsStr = TextUtils.join(",", dialogIds);
            MessagesStorage messagesStorage = MessagesStorage.getInstance(this.currentAccount);
            HashMap<Long, Integer> holesMap = new HashMap<>();
            SQLiteCursor holesCursor = messagesStorage.getDatabase().queryFinalized("SELECT uid, MIN(start) FROM messages_holes WHERE uid IN (" + dialogIdsStr + ") GROUP BY uid", new Object[0]);
            while (holesCursor.next()) {
                holesMap.put(holesCursor.longValue(0), holesCursor.intValue(1));
            }
            holesCursor.dispose();
            for (int i = 0; i < included.size(); i++) {
                ChannelSnapshot snapshot = included.get(i);
                Integer holeMinMid = holesMap.get(snapshot.dialogId);
                boolean hasHole = holeMinMid != null;
                snapshot.hasHole = hasHole;
                snapshot.holeEnd = hasHole ? holeMinMid : 0;
            }
            loadChannelDepths(messagesStorage, included);
            int maxDepthDate = 0;
            for (int i = 0; i < included.size(); i++) {
                ChannelSnapshot snapshot = included.get(i);
                boolean incomplete = !snapshot.localStartReached && !exhausted.contains(snapshot.dialogId);
                snapshot.incomplete = incomplete;
                if (incomplete) {
                    olderPage.hasIncomplete = true;
                    if (snapshot.hasCached) {
                        maxDepthDate = Math.max(maxDepthDate, snapshot.depthDate);
                    }
                    long targetMid;
                    if (snapshot.hasCached) {
                        targetMid = snapshot.depthMid;
                    } else {
                        int topOrHole = Math.max(snapshot.holeEnd, snapshot.topMessage);
                        targetMid = topOrHole > 0 ? topOrHole + 1 : 0L;
                    }
                    olderPage.backfillCandidates.add(new long[]{snapshot.dialogId, targetMid, snapshot.depthDate});
                }
            }
            olderPage.backfillCandidates.sort((a, b) -> Long.compare(b[2], a[2]));
            if (maxDepthDate == Integer.MAX_VALUE) {
                return olderPage;
            }
            Cursor unreadBoundary = isCursorEmpty ? findUnreadBoundary(messagesStorage, included, maxDepthDate) : null;
            ArrayList<Long> userIds = new ArrayList<>();
            ArrayList<Long> chatIds = new ArrayList<>();
            int loadedCount = 0;
            do {
                int count = loadChunk(messagesStorage, dialogIdsStr, maxDepthDate, olderPage, userIds, chatIds);
                olderPage.lastChunkRowCount = count;
                loadedCount += count;
                if (count < 30 || unreadBoundary == null || loadedCount >= 200) {
                    break;
                }
            } while (compareDesc(olderPage.last, unreadBoundary) < 0);

            completeTrailingAlbum(messagesStorage, olderPage, userIds, chatIds);
            for (int i = 0; i < dialogIds.size(); i++) {
                long peerId = -dialogIds.get(i);
                if (!chatIds.contains(peerId)) {
                    chatIds.add(peerId);
                }
            }
            if (!userIds.isEmpty()) {
                messagesStorage.getUsersInternal(userIds, olderPage.users);
            }
            if (!chatIds.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), olderPage.chats);
            }
            clusterGroupedMessages(olderPage.messages);
            return olderPage;
        } catch (Exception e) {
            FileLog.e(e);
            olderPage.failed = true;
            clusterGroupedMessages(olderPage.messages);
            return olderPage;
        }
    }

    private int loadChunk(MessagesStorage messagesStorage, String dialogIdsStr, int minDate, OlderPage olderPage, ArrayList<Long> userIds, ArrayList<Long> chatIds) {
        StringBuilder sb = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
        sb.append(dialogIdsStr);
        sb.append(")");
        if (minDate > 0) {
            sb.append(" AND date <= ");
            sb.append(minDate);
        }
        int count = 0;
        if (!olderPage.last.isEmpty()) {
            appendCursorBound(sb, olderPage.last, true, false);
        }
        sb.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT 30");
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(sb.toString(), new Object[0]);
            try {
                while (cursor.next()) {
                    count++;
                    olderPage.last.set(cursor.intValue(2), cursor.longValue(3), cursor.intValue(1));
                    if (olderPage.first.isEmpty()) {
                        olderPage.first.set(olderPage.last.date, olderPage.last.uid, olderPage.last.mid);
                    }
                    TLRPC.Message message = readMessage(cursor);
                    if (message != null) {
                        olderPage.messages.add(message);
                        MessagesStorage.addUsersAndChatsFromMessage(message, userIds, chatIds, null);
                    }
                }
            } finally {
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return count;
    }

    private Cursor findUnreadBoundary(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> included, int minDate) {
        StringBuilder sb = new StringBuilder();
        Cursor boundary = null;
        int batchCount = 0;
        for (int i = 0; i < included.size(); i++) {
            ChannelSnapshot snapshot = included.get(i);
            if (snapshot.topMessage > snapshot.readInboxMax || snapshot.unreadCount > 0) {
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                sb.append("(uid = ");
                sb.append(snapshot.dialogId);
                sb.append(" AND mid > ");
                sb.append(snapshot.readInboxMax);
                sb.append(")");
                batchCount++;
            }
            if (batchCount > 0 && (batchCount == 64 || i == included.size() - 1)) {
                Cursor cur = queryUnreadBoundary(messagesStorage, sb, minDate);
                if (cur != null && (boundary == null || compareDesc(cur, boundary) > 0)) {
                    boundary = cur;
                }
                sb.setLength(0);
                batchCount = 0;
            }
        }
        return boundary;
    }

    private Cursor queryUnreadBoundary(MessagesStorage messagesStorage, StringBuilder conditions, int minDate) {
        StringBuilder sb = new StringBuilder("SELECT date, uid, mid FROM messages_v2 WHERE (");
        sb.append(conditions);
        sb.append(")");
        if (minDate > 0) {
            sb.append(" AND date <= ");
            sb.append(minDate);
        }
        sb.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT 1");
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(sb.toString(), new Object[0]);
            try {
                if (!cursor.next()) {
                    return null;
                }
                Cursor result = new Cursor();
                result.set(cursor.intValue(0), cursor.longValue(1), cursor.intValue(2));
                return result;
            } finally {
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void appendCursorBound(StringBuilder sb, Cursor cursor, boolean descending, boolean inclusive) {
        String op = descending ? "<" : ">";
        String incOp = inclusive ? (descending ? "<=" : ">=") : op;
        sb.append(" AND (date ");
        sb.append(op);
        sb.append(" ");
        sb.append(cursor.date);
        sb.append(" OR (date = ");
        sb.append(cursor.date);
        sb.append(" AND (uid ");
        sb.append(op);
        sb.append(" ");
        sb.append(cursor.uid);
        sb.append(" OR (uid = ");
        sb.append(cursor.uid);
        sb.append(" AND mid ");
        sb.append(incOp);
        sb.append(" ");
        sb.append(cursor.mid);
        sb.append("))))");
    }

    private static int compareDesc(Cursor c1, Cursor c2) {
        if (c1.date != c2.date) {
            return c1.date > c2.date ? -1 : 1;
        }
        if (c1.uid != c2.uid) {
            return c1.uid > c2.uid ? -1 : 1;
        }
        return -Integer.compare(c1.mid, c2.mid);
    }

    public NewerPage loadNewerPage(ArrayList<ChannelSnapshot> included, Cursor cursor) {
        NewerPage page = new NewerPage();
        page.first.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(included.size());
            for (int i = 0; i < included.size(); i++) {
                dialogIds.add(included.get(i).dialogId);
            }
            MessagesStorage messagesStorage = MessagesStorage.getInstance(this.currentAccount);
            ArrayList<Long> userIds = new ArrayList<>();
            ArrayList<Long> chatIds = new ArrayList<>();
            StringBuilder sb = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            sb.append(TextUtils.join(",", dialogIds));
            sb.append(") AND ");
            appendCursorBound(sb, cursor, false, false);
            sb.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT 50");
            SQLiteCursor dbCursor = messagesStorage.getDatabase().queryFinalized(sb.toString(), new Object[0]);
            int count = 0;
            while (dbCursor.next()) {
                count++;
                page.first.set(dbCursor.intValue(2), dbCursor.longValue(3), dbCursor.intValue(1));
                TLRPC.Message message = readMessage(dbCursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, userIds, chatIds, null);
                }
            }
            dbCursor.dispose();
            page.hasMore = count == 50;
            if (!userIds.isEmpty()) {
                messagesStorage.getUsersInternal(userIds, page.users);
            }
            if (!chatIds.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    public WindowPage loadChannelWindow(ArrayList<Long> dialogIds, Cursor c1, Cursor c2) {
        WindowPage page = new WindowPage();
        if (!dialogIds.isEmpty() && !c1.isEmpty() && !c2.isEmpty()) {
            try {
                MessagesStorage messagesStorage = MessagesStorage.getInstance(this.currentAccount);
                ArrayList<Long> userIds = new ArrayList<>();
                ArrayList<Long> chatIds = new ArrayList<>();
                StringBuilder sb = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
                sb.append(TextUtils.join(",", dialogIds));
                sb.append(") AND ");
                appendCursorBound(sb, c1, true, true);
                appendCursorBound(sb, c2, false, true);
                sb.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT 501");
                SQLiteCursor dbCursor = messagesStorage.getDatabase().queryFinalized(sb.toString(), new Object[0]);
                int count = 0;
                while (dbCursor.next()) {
                    count++;
                    if (count > 500) {
                        page.truncated = true;
                        break;
                    }
                    TLRPC.Message message = readMessage(dbCursor);
                    if (message != null) {
                        page.messages.add(message);
                        MessagesStorage.addUsersAndChatsFromMessage(message, userIds, chatIds, null);
                    }
                }
                dbCursor.dispose();
                if (!userIds.isEmpty()) {
                    messagesStorage.getUsersInternal(userIds, page.users);
                }
                if (!chatIds.isEmpty()) {
                    messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), page.chats);
                }
            } catch (Exception e) {
                FileLog.e(e);
                page.failed = true;
                page.messages.clear();
                page.users.clear();
                page.chats.clear();
            }
            clusterGroupedMessages(page.messages);
        }
        return page;
    }

    private void completeTrailingAlbum(MessagesStorage messagesStorage, OlderPage olderPage, ArrayList<Long> userIds, ArrayList<Long> chatIds) {
        if (olderPage.messages.isEmpty()) {
            return;
        }
        TLRPC.Message lastMsg = olderPage.messages.get(olderPage.messages.size() - 1);
        if (lastMsg.grouped_id == 0) {
            return;
        }
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized("SELECT data, mid, date, uid FROM messages_v2 WHERE uid = " + lastMsg.dialog_id + " AND mid < " + lastMsg.id + " ORDER BY mid DESC LIMIT 10", new Object[0]);
            try {
                while (cursor.next()) {
                    TLRPC.Message msg = readMessage(cursor);
                    if (msg == null || msg.grouped_id != lastMsg.grouped_id) {
                        break;
                    }
                    olderPage.messages.add(msg);
                    MessagesStorage.addUsersAndChatsFromMessage(msg, userIds, chatIds, null);
                }
            } finally {
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private TLRPC.Message readMessage(SQLiteCursor cursor) {
        try {
            NativeByteBuffer byteBuffer = cursor.byteBufferValue(0);
            if (byteBuffer == null) {
                return null;
            }
            TLRPC.Message message = TLRPC.Message.TLdeserialize(byteBuffer, byteBuffer.readInt32(false), false);
            if (message == null) {
                byteBuffer.reuse();
                return null;
            }
            message.readAttachPath(byteBuffer, UserConfig.getInstance(this.currentAccount).clientUserId);
            byteBuffer.reuse();
            if (message instanceof TLRPC.TL_messageEmpty || message.action != null) {
                return null;
            }
            message.id = cursor.intValue(1);
            message.date = cursor.intValue(2);
            message.dialog_id = cursor.longValue(3);
            return message;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void loadChannelDepths(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> included) {
        LongSparseArray<ChannelSnapshot> map = new LongSparseArray<>(included.size());
        for (int i = 0; i < included.size(); i++) {
            ChannelSnapshot snapshot = included.get(i);
            snapshot.depthMid = 0;
            snapshot.depthDate = Integer.MAX_VALUE;
            snapshot.hasCached = false;
            snapshot.localStartReached = false;
            map.put(snapshot.dialogId, snapshot);
        }
        int i = 0;
        while (i < included.size()) {
            int end = Math.min(i + 64, included.size());
            StringBuilder sb = new StringBuilder();
            for (int k = i; k < end; k++) {
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                ChannelSnapshot snapshot = included.get(k);
                int minMid = Math.max(snapshot.holeEnd, 1);
                sb.append("(uid = ");
                sb.append(snapshot.dialogId);
                sb.append(" AND mid >= ");
                sb.append(minMid);
                sb.append(")");
            }
            try {
                SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized("SELECT uid, MIN(mid), MIN(date) FROM messages_v2 WHERE (" + sb + ") GROUP BY uid", new Object[0]);
                try {
                    while (cursor.next()) {
                        ChannelSnapshot snapshot = map.get(cursor.longValue(0));
                        if (snapshot != null) {
                            snapshot.depthMid = cursor.intValue(1);
                            snapshot.depthDate = cursor.intValue(2);
                            snapshot.hasCached = true;
                        }
                    }
                } finally {
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            i = end;
        }
        for (int k = 0; k < included.size(); k++) {
            ChannelSnapshot snapshot = included.get(k);
            snapshot.localStartReached = !snapshot.hasHole && snapshot.hasCached && snapshot.depthMid <= 1;
        }
    }

    private static void clusterGroupedMessages(ArrayList<TLRPC.Message> messages) {
        if (messages.size() < 3) {
            return;
        }
        HashMap<Long, ArrayList<TLRPC.Message>> map = new HashMap<>();
        boolean hasMultiple = false;
        for (int i = 0; i < messages.size(); i++) {
            long groupId = messages.get(i).grouped_id;
            if (groupId != 0) {
                ArrayList<TLRPC.Message> list = map.computeIfAbsent(groupId, k -> new ArrayList<>());
                if (list.size() > 0) {
                    hasMultiple = true;
                }
                list.add(messages.get(i));
            }
        }
        if (hasMultiple) {
            ArrayList<TLRPC.Message> clustered = new ArrayList<>(messages.size());
            HashSet<Long> addedGroups = new HashSet<>();
            for (int i = 0; i < messages.size(); i++) {
                TLRPC.Message msg = messages.get(i);
                long groupId = msg.grouped_id;
                if (groupId == 0) {
                    clustered.add(msg);
                } else if (addedGroups.add(groupId)) {
                    clustered.addAll(map.get(groupId));
                }
            }
            messages.clear();
            messages.addAll(clustered);
        }
    }
}
