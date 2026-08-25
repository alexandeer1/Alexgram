package tw.nekomimi.nekogram.helpers.remote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public abstract class BaseRemoteHelper {
    public static final long CHANNEL_METADATA_ID = 3637588965L;
    public static final String CHANNEL_METADATA_NAME = "Alexgram_updates";

    public String getChannelMetadataName() {
        return CHANNEL_METADATA_NAME;
    }

    public long getChannelMetadataId() {
        return preferences.getLong(getChannelMetadataName() + "_id", CHANNEL_METADATA_ID);
    }

    public void setChannelMetadataId(long id) {
        preferences.edit().putLong(getChannelMetadataName() + "_id", id).apply();
    }

    protected static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekoremoteconfig", Activity.MODE_PRIVATE);

    protected MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    protected ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    protected MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    protected FileLoader getFileLoader() {
        return FileLoader.getInstance(UserConfig.selectedAccount);
    }

    abstract protected void onError(String text, Delegate delegate);

    abstract protected String getTag();

    protected JSONObject getJSON() {
        var tag = getTag();
        var json = preferences.getString(tag, "");
        if (TextUtils.isEmpty(json)) {
            load();
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            FileLog.e(e);
            load();
            return null;
        }
    }

    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate) {
        var tag = getTag();
        var json = responses.size() > 0 ? responses.get(0) : null;
        if (json == null) {
            preferences.edit()
                    .remove(tag + "_update_time")
                    .remove(tag)
                    .apply();
        } else {
            preferences.edit()
                    .putLong(tag + "_update_time", System.currentTimeMillis())
                    .putString(tag, json.toString())
                    .apply();
        }
    }

    protected void onGetMessageSuccess(TLObject response, Delegate delegate) {
        var tag = "#" + getTag();
        final var res = (TLRPC.messages_Messages) response;
        long chId = getChannelMetadataId();
        if (chId != 0) {
            getMessagesController().removeDeletedMessagesFromArray(chId, res.messages);
        }
        ArrayList<JSONObject> responses = new ArrayList<>();
        for (var message : res.messages) {
            if (TextUtils.isEmpty(message.message) || !message.message.startsWith(tag)) {
                continue;
            }
            try {
                responses.add(new JSONObject(message.message.substring(tag.length()).trim()));
            } catch (JSONException e) {
                FileLog.e(e);
            }
        }
        onLoadSuccess(responses, delegate);
    }

    public void load() {
        load(false, null);
    }

    public void load(Delegate delegate) {
        load(false, delegate);
    }

    private void load(boolean forceRefreshAccessHash, Delegate delegate) {
        var tag = "#" + getTag();
        long chId = getChannelMetadataId();
        TLRPC.InputPeer currentPeer = chId != 0 ? getMessagesController().getInputPeer(-chId) : null;
        if (currentPeer == null || currentPeer.access_hash == 0 || forceRefreshAccessHash) {
            TLRPC.TL_contacts_resolveUsername req1 = new TLRPC.TL_contacts_resolveUsername();
            req1.username = getChannelMetadataName();
            getConnectionsManager().sendRequest(req1, (response1, error1) -> {
                if (error1 != null) {
                    onError(error1.text, delegate);
                    return;
                }
                if (!(response1 instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    return;
                }
                TLRPC.TL_contacts_resolvedPeer resolvedPeer = (TLRPC.TL_contacts_resolvedPeer) response1;
                getMessagesController().putUsers(resolvedPeer.users, false);
                getMessagesController().putChats(resolvedPeer.chats, false);
                getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, false, true);
                if ((resolvedPeer.chats == null || resolvedPeer.chats.size() == 0)) {
                    return;
                }
                long resolvedChatId = resolvedPeer.chats.get(0).id;
                setChannelMetadataId(resolvedChatId);
                TLRPC.TL_inputPeerChannel peerChannel = new TLRPC.TL_inputPeerChannel();
                peerChannel.channel_id = resolvedChatId;
                peerChannel.access_hash = resolvedPeer.chats.get(0).access_hash;
                sendFetchRequest(peerChannel, tag, delegate);
            });
        } else {
            sendFetchRequest(currentPeer, tag, delegate);
        }
    }

    private void sendFetchRequest(TLRPC.InputPeer peer, String tag, Delegate delegate) {
        TLObject req;
        if (this instanceof UpdateHelper) {
            TLRPC.TL_messages_getHistory gh = new TLRPC.TL_messages_getHistory();
            gh.peer = peer;
            gh.limit = 30;
            gh.offset_id = 0;
            req = gh;
        } else {
            TLRPC.TL_messages_search s = new TLRPC.TL_messages_search();
            s.limit = 10;
            s.offset_id = 0;
            s.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            s.q = tag;
            s.peer = peer;
            req = s;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                onGetMessageSuccess(response, delegate);
            } else {
                onError(error.text, delegate);
            }
        });
    }

    public interface Delegate {
        void onTLResponse(TLRPC.TL_help_appUpdate res, String error);
    }
}
