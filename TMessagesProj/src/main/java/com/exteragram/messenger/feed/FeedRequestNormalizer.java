package com.exteragram.messenger.feed;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public abstract class FeedRequestNormalizer {
    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final ClassMetadata EMPTY_METADATA = new ClassMetadata(null, null, null, null, EMPTY_FIELDS);
    private static final ConcurrentHashMap<Class<?>, ClassMetadata> metadataCache = new ConcurrentHashMap<>();

    private static long mergeResolvedDialogIds(long d1, long d2) {
        if (d1 == 0) return d2;
        if (d2 == 0 || d1 == d2) return d1;
        return 0L;
    }

    public static TLObject normalize(int account, TLObject req) {
        if (req != null) {
            FeedController controller = FeedController.peekInstance(account);
            if (controller != null && !controller.hasNoSyntheticIds() && req.getClass().getName().startsWith("org.telegram.tgnet.")) {
                ClassMetadata metadata = getMetadata(req);
                if (metadata.messageIdFields.length != 0 || metadata.invoiceField != null) {
                    normalizeMessageIds(account, controller, req, metadata);
                    normalizeInvoice(account, controller, getFieldValue(metadata.invoiceField, req));
                }
            }
        }
        return req;
    }

    private static ClassMetadata getMetadata(Object obj) {
        if (obj == null) {
            return EMPTY_METADATA;
        }
        return metadataCache.computeIfAbsent(obj.getClass(), FeedRequestNormalizer::buildMetadata);
    }

    private static ClassMetadata buildMetadata(Class<?> cls) {
        Field[] fields;
        try {
            fields = cls.getFields();
        } catch (Exception unused) {
            fields = EMPTY_FIELDS;
        }
        Field reqPeer = null;
        ArrayList<Field> msgIdFields = null;
        Field peer = null;
        Field channel = null;
        Field invoice = null;
        for (Field field : fields) {
            String name = field.getName();
            if ("from_peer".equals(name) && reqPeer == null) {
                reqPeer = field;
            } else if ("peer".equals(name) && peer == null) {
                peer = field;
            } else if ("channel".equals(name) && channel == null) {
                channel = field;
            } else if ("invoice".equals(name) && invoice == null) {
                invoice = field;
            }
            if (isMessageIdField(field)) {
                if (msgIdFields == null) {
                    msgIdFields = new ArrayList<>();
                }
                msgIdFields.add(field);
            }
        }
        return new ClassMetadata(reqPeer != null ? reqPeer : peer, peer, channel, invoice, msgIdFields != null ? msgIdFields.toArray(new Field[0]) : EMPTY_FIELDS);
    }

    private static void normalizeMessageIds(int account, FeedController controller, Object obj) {
        normalizeMessageIds(account, controller, obj, getMetadata(obj));
    }

    private static void normalizeMessageIds(int account, FeedController controller, Object obj, ClassMetadata metadata) {
        Field field = metadata.requestPeerField;
        long dialogId = getDialogId(field, obj);
        if (dialogId == 0) {
            dialogId = getDialogId(metadata.peerField, obj);
        }
        if (dialogId == 0) {
            dialogId = getChannelDialogId(metadata.channelField, obj);
        }
        long resolvedDialogId = normalizeMessageIdFields(controller, obj, metadata);
        if (resolvedDialogId == 0 || resolvedDialogId == dialogId) {
            return;
        }
        if (field != null) {
            setInputPeer(account, field, obj, resolvedDialogId);
        } else if (metadata.channelField != null) {
            setInputChannel(account, metadata.channelField, obj, resolvedDialogId);
        }
    }

    private static long normalizeMessageIdFields(FeedController controller, Object obj, ClassMetadata metadata) {
        long dialogId = 0;
        if (obj == null) {
            return 0L;
        }
        for (Field field : metadata.messageIdFields) {
            dialogId = mergeResolvedDialogIds(dialogId, normalizeMessageIdField(controller, obj, field));
        }
        return dialogId;
    }

    private static boolean isMessageIdField(Field field) {
        if (field == null || Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        String name = field.getName();
        return "id".equals(name) || "msg_id".equals(name) || name.endsWith("_msg_id");
    }

    private static void normalizeInvoice(int account, FeedController controller, Object obj) {
        if (obj instanceof TLRPC.TL_inputInvoiceMessage) {
            normalizeMessageIds(account, controller, obj);
        }
    }

    private static long normalizeMessageIdField(FeedController controller, Object obj, Field field) {
        try {
            Object value = field.get(obj);
            if (value instanceof Integer) {
                Integer num = (Integer) value;
                long realDialogId = controller.resolveRealDialogId(num);
                if (realDialogId == 0) {
                    return 0L;
                }
                field.setInt(obj, controller.resolveRealMessageId(realDialogId, num));
                return realDialogId;
            }
            if (!(value instanceof ArrayList)) {
                return 0L;
            }
            ArrayList<?> list = (ArrayList<?>) value;
            long dialogId = 0;
            for (int i = 0; i < list.size(); i++) {
                try {
                    Object item = list.get(i);
                    if (item instanceof Integer) {
                        Integer num = (Integer) item;
                        long realDialogId = controller.resolveRealDialogId(num);
                        if (realDialogId != 0) {
                            setListInteger((ArrayList<Integer>) list, i, controller.resolveRealMessageId(realDialogId, num));
                            dialogId = mergeResolvedDialogIds(dialogId, realDialogId);
                        }
                    }
                } catch (Exception unused) {
                    return dialogId;
                }
            }
            return dialogId;
        } catch (Exception unused) {
            return 0L;
        }
    }

    private static void setListInteger(ArrayList<Integer> list, int index, int value) {
        list.set(index, value);
    }

    private static void setInputPeer(int account, Field field, Object obj, long dialogId) {
        if (account < 0) {
            return;
        }
        try {
            TLRPC.InputPeer inputPeer = MessagesController.getInstance(account).getInputPeer(dialogId);
            if (inputPeer != null) {
                field.set(obj, inputPeer);
            }
        } catch (Exception unused) {
        }
    }

    private static void setInputChannel(int account, Field field, Object obj, long dialogId) {
        if (account < 0 || dialogId >= 0) {
            return;
        }
        try {
            TLRPC.InputChannel inputChannel = MessagesController.getInstance(account).getInputChannel(-dialogId);
            if (inputChannel != null) {
                field.set(obj, inputChannel);
            }
        } catch (Exception unused) {
        }
    }

    private static long getDialogId(Field field, Object obj) {
        if (field == null) return 0L;
        try {
            Object val = field.get(obj);
            if (val instanceof TLRPC.InputPeer) {
                return DialogObject.getPeerDialogId((TLRPC.InputPeer) val);
            }
        } catch (Exception unused) {
        }
        return 0L;
    }

    private static long getChannelDialogId(Field field, Object obj) {
        Object val = getFieldValue(field, obj);
        if (val instanceof TLRPC.InputChannel) {
            return getInputChannelDialogId((TLRPC.InputChannel) val);
        }
        return 0L;
    }

    private static long getInputChannelDialogId(TLRPC.InputChannel inputChannel) {
        if (inputChannel == null || inputChannel.channel_id == 0) {
            return 0L;
        }
        return -inputChannel.channel_id;
    }

    private static Object getFieldValue(Field field, Object obj) {
        if (field == null) return null;
        try {
            return field.get(obj);
        } catch (Exception unused) {
            return null;
        }
    }

    public static final class ClassMetadata {
        final Field requestPeerField;
        final Field peerField;
        final Field channelField;
        final Field invoiceField;
        final Field[] messageIdFields;

        public ClassMetadata(Field requestPeerField, Field peerField, Field channelField, Field invoiceField, Field[] messageIdFields) {
            this.requestPeerField = requestPeerField;
            this.peerField = peerField;
            this.channelField = channelField;
            this.invoiceField = invoiceField;
            this.messageIdFields = messageIdFields;
        }
    }
}
