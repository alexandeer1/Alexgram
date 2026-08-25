package tw.nekomimi.nekogram.helpers.remote;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class UpdateHelper extends BaseRemoteHelper {

    public static final int UPDATE_OFF = 0;
    public static final int UPDATE_CHANNEL_RELEASE = 1;
    public static final int UPDATE_CHANNEL_BETA = 2;
    private boolean updateAlways = false;

    private static class ChannelApk {
        TLRPC.Document document;
        String versionName = "";
        int versionCode = 0;
        String abi = "universal";
        String caption;
        ArrayList<TLRPC.MessageEntity> entities;
    }

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    public static void cleanAppUpdate() {
        if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.document != null) {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            if (path != null && path.exists()) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (!path.delete()) path.deleteOnExit();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        SharedConfig.pendingAppUpdate = null;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        delegate.onTLResponse(null, text);
    }

    @Override
    public String getChannelMetadataName() {
        if ("nightly".equalsIgnoreCase(BuildConfig.APP_TIER)) {
            return "Alexgram_Nightly";
        }
        if ("beta".equalsIgnoreCase(BuildConfig.APP_TIER)) {
            return "Alexgram_beta";
        }
        return "Alexgram_updates";
    }

    @Override
    protected String getTag() {
        if (BuildConfig.DEBUG) return "updateDebug";
        if ("nightly".equalsIgnoreCase(BuildConfig.APP_TIER)) return "updateNightly";
        if ("beta".equalsIgnoreCase(BuildConfig.APP_TIER)) return "updateBeta";
        return "updateRelease";
    }

    @SuppressWarnings("ConstantConditions")
    private int getPreferredAbiFile(Map<String, Integer> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        for (String abi : Build.SUPPORTED_ABIS) {
            Integer id = files.get(abi);
            if (id != null && id != 0) {
                return id;
            }
        }
        if (files.containsKey("universal")) {
            Integer id = files.get("universal");
            if (id != null && id != 0) {
                return id;
            }
        }
        if (files.containsKey("arm64-v8a")) {
            Integer id = files.get("arm64-v8a");
            if (id != null && id != 0) {
                return id;
            }
        }
        for (Integer id : files.values()) {
            if (id != null && id != 0) {
                return id;
            }
        }
        return 0;
    }

    private Map<String, Integer> jsonToMap(JSONObject obj) {
        Map<String, Integer> map = new HashMap<>();
        if (obj == null) return map;
        try {
            var keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int id = obj.optInt(key, 0);
                if (id != 0) {
                    map.put(key, id);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private Update getShouldUpdateVersion(List<JSONObject> responses) {
        int currentVersion = BuildConfig.VERSION_CODE;
        long buildTimestamp = BuildConfig.BUILD_TIMESTAMP;
        Update ref = null;
        for (var string : responses) {
            try {
                int remoteVersion = string.optInt("version_code", 0);
                long remoteBuildTimestamp = string.optLong("build_timestamp", 0L);
                boolean shouldUpdate;
                if (remoteVersion == 0) {
                    // No version_code: compare the version NAME string against what's installed.
                    // Shows update if version string differs from installed → stops after update.
                    String remoteVersionName = string.optString("version", "");
                    boolean alreadyInstalled = !TextUtils.isEmpty(remoteVersionName)
                            && remoteVersionName.trim().equals(BuildConfig.VERSION_NAME.trim());
                    shouldUpdate = !alreadyInstalled;
                } else if (remoteVersion > currentVersion) {
                    shouldUpdate = true;
                } else {
                    shouldUpdate = (remoteVersion == currentVersion && remoteBuildTimestamp > buildTimestamp);
                }
                if (shouldUpdate || updateAlways) {
                    if (updateAlways) {
                        updateAlways = false;
                    }
                    JSONObject docObj = string.optJSONObject("document");
                    ref = new Update(
                            string.optBoolean("can_not_skip", false),
                            string.optString("version", ""),
                            remoteVersion,
                            string.optInt("sticker", 0),
                            string.optInt("message", 0),
                            (docObj != null && docObj.length() > 0) ? jsonToMap(docObj) : null,
                            string.optString("url", null),
                            string.optString("changelog", null)
                    );
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        return ref;
    }

    private void getNewVersionMessagesCallback(Delegate delegate, Update json, HashMap<String, Integer> ids, TLObject response) {
        var update = new TLRPC.TL_help_appUpdate();
        update.version = json.version;
        update.can_not_skip = json.canNotSkip;
        if (json.url != null) {
            update.url = json.url;
            update.flags |= 4;
        }
        if (NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_OFF && !update.can_not_skip) {
            delegate.onTLResponse(null, null);
            return;
        }
        if (response != null) {
            var res = (TLRPC.messages_Messages) response;
            long chId = getChannelMetadataId();
            if (chId != 0) {
                getMessagesController().removeDeletedMessagesFromArray(chId, res.messages);
            }
            var messages = new HashMap<Integer, TLRPC.Message>();
            for (var message : res.messages) {
                messages.put(message.id, message);
            }
            if (ids.containsKey("sticker")) {
                var sticker = messages.get(ids.get("sticker"));
                if (sticker != null && sticker.media != null) {
                    update.sticker = sticker.media.document;
                    update.flags |= 8;
                }
            }
            if (ids.containsKey("message")) {
                var message = messages.get(ids.get("message"));
                if (message != null) {
                    update.text = message.message;
                    update.entities = message.entities;
                }
            }
            if (ids.containsKey("document")) {
                var file = messages.get(ids.get("document"));
                if (file != null && file.media != null) {
                    update.document = file.media.document;
                    update.flags |= 2;
                }
            }
        }
        // Use inline changelog text if no channel message was fetched
        if (TextUtils.isEmpty(update.text) && !TextUtils.isEmpty(json.changelog)) {
            update.text = json.changelog;
        }
        delegate.onTLResponse(update, null);
    }

    private ChannelApk parseApkMessage(TLRPC.Message message) {
        if (message == null || !(message.media instanceof TLRPC.TL_messageMediaDocument) || message.media.document == null) {
            return null;
        }
        String fileName = FileLoader.getDocumentFileName(message.media.document);
        if (TextUtils.isEmpty(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return null;
        }
        ChannelApk apk = new ChannelApk();
        apk.document = message.media.document;
        apk.caption = message.message;
        apk.entities = message.entities;

        // Extract versionCode from (7031) or (1249)
        Pattern codePattern = Pattern.compile("\\((\\d{3,7})\\)");
        Matcher codeMatcher = codePattern.matcher(fileName);
        if (codeMatcher.find()) {
            try {
                apk.versionCode = Integer.parseInt(codeMatcher.group(1));
            } catch (Exception ignored) {
            }
        }

        // Extract versionName from -v12.10.0- or v12.10.0 or 12.10.0
        Pattern verPattern = Pattern.compile("-v?(\\d+\\.\\d+(?:\\.\\d+)*(?:-[A-Za-z0-9.]+)*?)");
        Matcher verMatcher = verPattern.matcher(fileName);
        if (verMatcher.find()) {
            apk.versionName = verMatcher.group(1).split("-")[0];
        } else {
            Pattern simpleVer = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
            Matcher sm = simpleVer.matcher(fileName);
            if (sm.find()) {
                apk.versionName = sm.group(1);
            }
        }

        // Extract ABI
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("arm64-v8a")) {
            apk.abi = "arm64-v8a";
        } else if (lower.contains("armeabi-v7a")) {
            apk.abi = "armeabi-v7a";
        } else if (lower.contains("x86_64")) {
            apk.abi = "x86_64";
        } else if (lower.contains("x86")) {
            apk.abi = "x86";
        } else {
            apk.abi = "universal";
        }

        return apk;
    }

    @Override
    protected void onGetMessageSuccess(TLObject response, Delegate delegate) {
        if (!(response instanceof TLRPC.messages_Messages)) {
            delegate.onTLResponse(null, null);
            return;
        }
        final var res = (TLRPC.messages_Messages) response;
        long chId = getChannelMetadataId();
        if (chId != 0) {
            getMessagesController().removeDeletedMessagesFromArray(chId, res.messages);
        }

        // 1. Try to find JSON metadata posts first (with or without #tag)
        ArrayList<JSONObject> jsonResponses = new ArrayList<>();
        var tag = "#" + getTag();
        for (var message : res.messages) {
            if (TextUtils.isEmpty(message.message)) continue;
            String text = message.message.trim();
            if (text.startsWith(tag)) {
                try {
                    jsonResponses.add(new JSONObject(text.substring(tag.length()).trim()));
                } catch (Exception ignored) {}
            } else if (text.startsWith("#update")) {
                int brace = text.indexOf('{');
                if (brace != -1) {
                    try {
                        jsonResponses.add(new JSONObject(text.substring(brace).trim()));
                    } catch (Exception ignored) {}
                }
            } else if (text.startsWith("{") && text.endsWith("}")) {
                try {
                    JSONObject obj = new JSONObject(text);
                    if (obj.has("version") || obj.has("version_code") || obj.has("document")) {
                        jsonResponses.add(obj);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!jsonResponses.isEmpty()) {
            var update = getShouldUpdateVersion(jsonResponses);
            if (update != null) {
                onLoadSuccess(jsonResponses, delegate);
                return;
            }
        }

        // 2. Auto-detect directly from uploaded APK document messages in channel!
        List<ChannelApk> apks = new ArrayList<>();
        for (var message : res.messages) {
            ChannelApk apk = parseApkMessage(message);
            if (apk != null) {
                apks.add(apk);
            }
        }

        if (!apks.isEmpty()) {
            // Find highest version code or latest version name
            int maxCode = 0;
            String maxVersion = "";
            for (ChannelApk apk : apks) {
                if (apk.versionCode > maxCode) {
                    maxCode = apk.versionCode;
                    maxVersion = apk.versionName;
                }
            }
            if (maxCode == 0 && !apks.isEmpty()) {
                maxVersion = apks.get(0).versionName;
            }

            // Collect APKs matching the newest version
            Map<String, ChannelApk> abiMap = new HashMap<>();
            for (ChannelApk apk : apks) {
                boolean matches = (maxCode > 0 && apk.versionCode == maxCode)
                        || (maxCode == 0 && !TextUtils.isEmpty(apk.versionName) && apk.versionName.equals(maxVersion));
                if (matches && !abiMap.containsKey(apk.abi)) {
                    abiMap.put(apk.abi, apk);
                }
            }

            // Match best ABI for user device
            ChannelApk bestApk = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abiMap.containsKey(abi)) {
                    bestApk = abiMap.get(abi);
                    break;
                }
            }
            if (bestApk == null && abiMap.containsKey("universal")) {
                bestApk = abiMap.get("universal");
            }
            if (bestApk == null && abiMap.containsKey("arm64-v8a")) {
                bestApk = abiMap.get("arm64-v8a");
            }
            if (bestApk == null && !abiMap.isEmpty()) {
                bestApk = abiMap.values().iterator().next();
            }

            if (bestApk != null) {
                boolean shouldUpdate = false;
                if (bestApk.versionCode > 0) {
                    shouldUpdate = bestApk.versionCode > BuildConfig.VERSION_CODE;
                } else if (!TextUtils.isEmpty(bestApk.versionName)) {
                    shouldUpdate = !bestApk.versionName.trim().equalsIgnoreCase(BuildConfig.VERSION_NAME.trim());
                }

                if (shouldUpdate || updateAlways) {
                    if (updateAlways) updateAlways = false;
                    var appUpdate = new TLRPC.TL_help_appUpdate();
                    appUpdate.version = bestApk.versionName;
                    appUpdate.can_not_skip = false;
                    appUpdate.document = bestApk.document;
                    appUpdate.flags |= 2;
                    appUpdate.text = bestApk.caption;
                    appUpdate.entities = bestApk.entities;

                    if (NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_OFF && !appUpdate.can_not_skip) {
                        delegate.onTLResponse(null, null);
                        return;
                    }

                    delegate.onTLResponse(appUpdate, null);
                    return;
                }
            }
        }

        delegate.onTLResponse(null, null);
    }

    @Override
    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate) {
        var update = getShouldUpdateVersion(responses);
        if (update == null) {
            delegate.onTLResponse(null, null);
            return;
        }
        var ids = new HashMap<String, Integer>();
        if (update.sticker != null && update.sticker != 0) {
            ids.put("sticker", update.sticker);
        }
        if (update.message != null && update.message != 0) {
            ids.put("message", update.message);
        }
        if (update.document != null) {
            int fileId = getPreferredAbiFile(update.document);
            if (fileId != 0) {
                ids.put("document", fileId);
            }
        }
        if (ids.isEmpty()) {
            getNewVersionMessagesCallback(delegate, update, null, null);
        } else {
            var req = new TLRPC.TL_channels_getMessages();
            long chId = getChannelMetadataId();
            TLRPC.InputChannel inputChannel = getMessagesController().getInputChannel(chId);
            if (inputChannel == null || inputChannel.access_hash == 0) {
                TLRPC.InputPeer inputPeer = getMessagesController().getInputPeer(-chId);
                if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                    inputChannel = new TLRPC.TL_inputChannel();
                    inputChannel.channel_id = inputPeer.channel_id;
                    inputChannel.access_hash = inputPeer.access_hash;
                }
            }
            req.channel = inputChannel;
            req.id = new ArrayList<>(ids.values());
            getConnectionsManager().sendRequest(req, (response1, error1) -> {
                if (error1 == null) {
                    getNewVersionMessagesCallback(delegate, update, ids, response1);
                } else {
                    delegate.onTLResponse(null, error1.text);
                }
            });
        }
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        this.updateAlways = updateAlways;
        load(delegate);
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }

    public static class Update {
        public Boolean canNotSkip;
        public String version;
        public Integer versionCode;
        public Integer sticker;
        public Integer message;
        public Map<String, Integer> document;
        public String url;
        public String changelog;

        public Update(Boolean canNotSkip, String version, int versionCode, int sticker, int message, Map<String, Integer> document, String url, String changelog) {
            this.canNotSkip = canNotSkip;
            this.version = version;
            this.versionCode = versionCode;
            this.sticker = sticker;
            this.message = message;
            this.document = document;
            this.url = url;
            this.changelog = changelog;
        }
    }
}
