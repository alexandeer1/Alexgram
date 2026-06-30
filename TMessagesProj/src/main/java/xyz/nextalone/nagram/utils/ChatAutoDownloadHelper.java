package xyz.nextalone.nagram.utils;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

public class ChatAutoDownloadHelper {

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("chat_auto_download_prefs", Context.MODE_PRIVATE);
    }

    public static boolean isCustomEnabled(long dialogId) {
        return getPrefs().getBoolean("custom_enabled_" + dialogId, false);
    }

    public static void setCustomEnabled(long dialogId, boolean enabled) {
        getPrefs().edit().putBoolean("custom_enabled_" + dialogId, enabled).apply();
    }

    public static boolean isMediaAutoDownloadEnabled(long dialogId, int type) {
        if (!isCustomEnabled(dialogId)) {
            return true;
        }
        return getPrefs().getBoolean("type_" + type + "_" + dialogId, true);
    }

    public static void setMediaAutoDownloadEnabled(long dialogId, int type, boolean enabled) {
        getPrefs().edit().putBoolean("type_" + type + "_" + dialogId, enabled).apply();
    }

    public static final int TYPE_PHOTO = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_FILE = 3;
    public static final int TYPE_VOICE = 4;
    public static final int TYPE_AUDIO = 5;

    public static int getMediaType(MessageObject message) {
        if (message == null || message.messageOwner == null) return 0;
        TLRPC.Message msg = message.messageOwner;
        if (MessageObject.isVoiceMessage(msg) || MessageObject.isRoundVideoMessage(msg)) {
            return TYPE_VOICE;
        } else if (MessageObject.isVideoMessage(msg) || MessageObject.isGifMessage(msg) || MessageObject.isGameMessage(msg)) {
            return TYPE_VIDEO;
        } else if (MessageObject.isPhoto(msg) || MessageObject.isStickerMessage(msg) || MessageObject.isAnimatedStickerMessage(msg)) {
            return TYPE_PHOTO;
        } else if (message.isMusic()) {
            return TYPE_AUDIO;
        } else if (MessageObject.getDocument(msg) != null) {
            return TYPE_FILE;
        }
        return 0;
    }
}
