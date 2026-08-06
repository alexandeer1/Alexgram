package com.exteragram.messenger.feed;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

final class FeedMessageIdentityMap {
    private final HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();
    private final ConcurrentHashMap<Integer, MessageCompositeID> realIdsByGeneratedId = new ConcurrentHashMap<>();
    private final HashMap<MessageCompositeID, MessageObject> messagesByRealId = new HashMap<>();
    private final HashMap<GroupKey, MessageObject> primaryByGroup = new HashMap<>();
    private int lastGeneratedId = 2147483637;

    public boolean register(MessageObject messageObject) {
        messageObject.reactionsLastCheckTime = Long.MAX_VALUE;
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.messageOwner);
        int realId = messageObject.messageOwner.id;
        Integer generatedId = generatedIds.get(compositeId);
        if (generatedId == null) {
            int newId = lastGeneratedId--;
            generatedId = newId;
            generatedIds.put(compositeId, generatedId);
        }
        realIdsByGeneratedId.put(generatedId, compositeId);
        boolean added;
        if (messagesByRealId.containsKey(compositeId)) {
            added = false;
        } else {
            updatePrimaryGroupFlag(messageObject, compositeId.dialog_id, realId);
            messagesByRealId.put(compositeId, messageObject);
            added = true;
        }
        TLRPC.Message message = messageObject.messageOwner;
        message.realId = realId;
        message.id = generatedId;
        return added;
    }

    public void replace(MessageObject messageObject) {
        messageObject.reactionsLastCheckTime = Long.MAX_VALUE;
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.put(compositeId, messageObject.getId());
        realIdsByGeneratedId.put(messageObject.getId(), compositeId);
        MessageObject old = messagesByRealId.put(compositeId, messageObject);
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(compositeId.dialog_id, messageObject.messageOwner.grouped_id);
            if (old != null && primaryByGroup.get(groupKey) == old) {
                primaryByGroup.put(groupKey, messageObject);
            }
        }
    }

    public void releaseRow(MessageObject messageObject) {
        messagesByRealId.remove(new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId()));
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(messageObject.getDialogId(), messageObject.messageOwner.grouped_id);
            if (primaryByGroup.get(groupKey) == messageObject) {
                primaryByGroup.remove(groupKey);
            }
        }
    }

    public void purge(MessageObject messageObject) {
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.remove(compositeId);
        messagesByRealId.remove(compositeId);
        realIdsByGeneratedId.remove(messageObject.getId());
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(compositeId.dialog_id, messageObject.messageOwner.grouped_id);
            if (primaryByGroup.get(groupKey) == messageObject) {
                primaryByGroup.remove(groupKey);
            }
        }
    }

    public MessageObject getByRealId(long dialogId, int messageId) {
        return messagesByRealId.get(new MessageCompositeID(dialogId, messageId));
    }

    public MessageObject getByAnyId(long dialogId, int messageId) {
        MessageObject messageObject = messagesByRealId.get(new MessageCompositeID(dialogId, messageId));
        if (messageObject != null) {
            return messageObject;
        }
        int realId = resolveRealMessageId(dialogId, messageId);
        if (realId != messageId) {
            return messagesByRealId.get(new MessageCompositeID(dialogId, realId));
        }
        return null;
    }

    public int resolveRealMessageId(long dialogId, int generatedId) {
        MessageCompositeID compositeId = realIdsByGeneratedId.get(generatedId);
        return (compositeId == null || compositeId.dialog_id != dialogId) ? generatedId : compositeId.id;
    }

    public long resolveRealDialogId(int generatedId) {
        MessageCompositeID compositeId = realIdsByGeneratedId.get(generatedId);
        return compositeId != null ? compositeId.dialog_id : 0L;
    }

    public boolean isEmpty() {
        return realIdsByGeneratedId.isEmpty();
    }

    public void clear() {
        generatedIds.clear();
        realIdsByGeneratedId.clear();
        messagesByRealId.clear();
        primaryByGroup.clear();
        lastGeneratedId = 2147483637;
    }

    private void updatePrimaryGroupFlag(MessageObject messageObject, long dialogId, int realId) {
        if (!messageObject.hasValidGroupId()) {
            messageObject.isPrimaryGroupMessage = false;
            return;
        }
        GroupKey groupKey = new GroupKey(dialogId, messageObject.messageOwner.grouped_id);
        MessageObject currentPrimary = primaryByGroup.get(groupKey);
        if (currentPrimary == null || realId > currentPrimary.getRealId()) {
            messageObject.isPrimaryGroupMessage = true;
            if (currentPrimary != null) {
                currentPrimary.isPrimaryGroupMessage = false;
            }
            primaryByGroup.put(groupKey, messageObject);
        } else {
            messageObject.isPrimaryGroupMessage = false;
        }
    }

    public static final class GroupKey {
        final long dialog_id;
        final long groupedId;

        public GroupKey(long dialog_id, long groupedId) {
            this.dialog_id = dialog_id;
            this.groupedId = groupedId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GroupKey groupKey = (GroupKey) obj;
            return dialog_id == groupKey.dialog_id && groupedId == groupKey.groupedId;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(dialog_id) * 31) + Long.hashCode(groupedId);
        }
    }

    public static final class MessageCompositeID {
        final long dialog_id;
        final int id;

        public MessageCompositeID(TLRPC.Message message) {
            this.dialog_id = MessageObject.getDialogId(message);
            this.id = message.id;
        }

        public MessageCompositeID(long dialog_id, int id) {
            this.dialog_id = dialog_id;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MessageCompositeID compositeId = (MessageCompositeID) obj;
            return dialog_id == compositeId.dialog_id && id == compositeId.id;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(dialog_id) * 31) + id;
        }
    }
}
