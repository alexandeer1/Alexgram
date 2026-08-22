package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import tw.nekomimi.nekogram.NekoXConfig;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AccountSessionManager {

    public interface SessionImportCallback {
        void onImportSuccess(int targetAccountIndex, String summary);
        void onImportFailed(String errorMessage);
    }

    public static class AccountSessionInfo {
        public int accountIndex;
        public long userId;
        public String firstName;
        public String lastName;
        public String username;
        public String phone;
        public int dcId;
        public boolean isPremium;
        public boolean isBot;
        public int loginTime;

        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(firstName)) {
                sb.append(firstName);
            }
            if (!TextUtils.isEmpty(lastName)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(lastName);
            }
            if (sb.length() == 0 && !TextUtils.isEmpty(username)) {
                sb.append("@").append(username);
            }
            if (sb.length() == 0 && !TextUtils.isEmpty(phone)) {
                sb.append("+").append(phone);
            }
            return sb.length() > 0 ? sb.toString() : "Account #" + (accountIndex + 1);
        }
    }

    public static class ParsedSession {
        public int dcId = 2;
        public int apiId = 0;
        public byte[] authKey;
        public long userId = 0;
        public boolean isBot = false;
        public boolean testMode = false;
        public String sourceFormat = "Pyrogram";
    }

    public static List<AccountSessionInfo> getActiveAccountSessions() {
        List<AccountSessionInfo> sessions = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config != null && config.isClientActivated()) {
                TLRPC.User currentUser = config.getCurrentUser();
                AccountSessionInfo info = new AccountSessionInfo();
                info.accountIndex = a;
                info.userId = config.clientUserId;
                info.dcId = ConnectionsManager.getInstance(a).getCurrentDatacenterId();
                if (info.dcId == 0) {
                    int[] outDc = new int[1];
                    extractAuthKeyFromTgnetDat(a, outDc);
                    if (outDc[0] != 0) {
                        info.dcId = outDc[0];
                    } else {
                        info.dcId = 2;
                    }
                }
                info.loginTime = config.loginTime;
                if (currentUser != null) {
                    info.firstName = currentUser.first_name;
                    info.lastName = currentUser.last_name;
                    info.username = currentUser.username;
                    info.phone = currentUser.phone;
                    info.isPremium = currentUser.premium;
                    info.isBot = currentUser.bot;
                }
                sessions.add(info);
            }
        }
        return sessions;
    }

    // ==================== AUTH KEY EXTRACTION ====================

    public static byte[] extractAuthKeyFromTgnetDat(int accountIndex, int[] outDcId) {
        File configDir = (accountIndex == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + accountIndex));
        File tgnetFile = new File(configDir, "tgnet.dat");
        if (!tgnetFile.exists()) {
            tgnetFile = new File(configDir, "tgnet.dat.bak");
            if (!tgnetFile.exists()) {
                return null;
            }
        }
        try (FileInputStream fis = new FileInputStream(tgnetFile);
             DataInputStream dis = new DataInputStream(fis)) {

            int b0 = dis.readUnsignedByte();
            int b1 = dis.readUnsignedByte();
            int b2 = dis.readUnsignedByte();
            int b3 = dis.readUnsignedByte();
            int size = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
            if (size <= 0 || size > 10 * 1024 * 1024) {
                return null;
            }
            byte[] payload = new byte[size];
            dis.readFully(payload);
            SerializedData data = new SerializedData(payload);

            int version = data.readInt32(false);
            boolean testBackend = data.readBool(false);
            if (version >= 3) {
                data.readBool(false); // clientBlocked
            }
            if (version >= 4) {
                data.readString(false); // lastInitSystemLangcode
            }
            boolean hasCurrentDc = data.readBool(false);
            if (!hasCurrentDc) {
                data.cleanup();
                return null;
            }
            int currentDcId = data.readInt32(false);
            if (outDcId != null && outDcId.length > 0) {
                outDcId[0] = currentDcId;
            }
            data.readInt32(false); // timeDifference
            data.readInt32(false); // lastDcUpdateTime
            data.readInt64(false); // pushSessionId
            if (version >= 2) {
                data.readBool(false); // registeredForInternalPush
            }
            if (version >= 5) {
                data.readInt32(false); // lastServerTime
            }
            int sessionsCount = data.readInt32(false);
            for (int i = 0; i < sessionsCount; i++) {
                data.readInt64(false);
            }
            int datacentersCount = data.readInt32(false);
            byte[] foundAuthKey = null;
            for (int i = 0; i < datacentersCount; i++) {
                int dcVersion = data.readInt32(false);
                int dcId = data.readInt32(false);
                if (dcVersion >= 3) {
                    data.readInt32(false); // lastInitVersion
                }
                if (dcVersion >= 10) {
                    data.readInt32(false); // lastInitMediaVersion
                }
                int addrListCount = dcVersion >= 5 ? 4 : 1;
                for (int b = 0; b < addrListCount; b++) {
                    int addrCount = data.readInt32(false);
                    for (int k = 0; k < addrCount; k++) {
                        data.readString(false); // address
                        data.readInt32(false); // port
                        if (dcVersion >= 7) {
                            data.readInt32(false); // flags
                        }
                        if (dcVersion >= 9) {
                            data.readString(false); // secret
                        }
                    }
                }
                if (dcVersion >= 6) {
                    data.readBool(false); // isCdnDatacenter
                }
                int authKeyLen = data.readInt32(false);
                byte[] authKey = null;
                if (authKeyLen > 0) {
                    authKey = data.readData(authKeyLen, false);
                }
                if (dcVersion >= 4) {
                    data.readInt64(false); // authKeyPermId
                } else {
                    int dummy = data.readInt32(false);
                    if (dummy != 0) {
                        data.readInt64(false);
                    }
                }
                if (dcVersion >= 8) {
                    int tempLen = data.readInt32(false);
                    if (tempLen > 0) {
                        data.readData(tempLen, false);
                    }
                    data.readInt64(false); // authKeyTempId
                }
                if (dcVersion >= 12) {
                    int mediaTempLen = data.readInt32(false);
                    if (mediaTempLen > 0) {
                        data.readData(mediaTempLen, false);
                    }
                    data.readInt64(false); // authKeyMediaTempId
                }
                data.readInt32(false); // authorized
                int saltsCount = data.readInt32(false);
                for (int k = 0; k < saltsCount; k++) {
                    data.readInt32(false);
                    data.readInt32(false);
                    data.readInt64(false);
                }
                if (dcVersion >= 13) {
                    int mediaSaltsCount = data.readInt32(false);
                    for (int k = 0; k < mediaSaltsCount; k++) {
                        data.readInt32(false);
                        data.readInt32(false);
                        data.readInt64(false);
                    }
                }
                if (dcId == currentDcId && authKey != null && authKey.length == 256) {
                    foundAuthKey = authKey;
                }
            }
            data.cleanup();
            return foundAuthKey;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    // ==================== TGNET.DAT CREATION ====================

    public static void writeTgnetDat(File configDir, int dcId, byte[] authKey, boolean testBackend) throws Exception {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        if (dcId <= 0 || dcId > 5) {
            dcId = 2;
        }
        if (authKey == null || authKey.length != 256) {
            throw new IllegalArgumentException("Auth key must be exactly 256 bytes");
        }

        byte[] sha1 = Utilities.computeSHA1(authKey);
        long authKeyPermId = 0;
        for (int i = 0; i < 8; i++) {
            authKeyPermId |= ((long) (sha1[12 + i] & 0xFF)) << (i * 8);
        }

        SerializedData stream = new SerializedData();
        stream.writeInt32(5); // configVersion
        stream.writeBool(testBackend);
        stream.writeBool(false); // clientBlocked
        stream.writeString(""); // lastInitSystemLangcode
        stream.writeBool(true); // hasCurrentDc
        stream.writeInt32(dcId);
        stream.writeInt32(0); // timeDifference
        int now = (int) (System.currentTimeMillis() / 1000);
        stream.writeInt32(now); // lastDcUpdateTime
        stream.writeInt64(Utilities.random.nextLong()); // pushSessionId
        stream.writeBool(false); // registeredForInternalPush
        stream.writeInt32(now); // lastServerTime
        stream.writeInt32(0); // sessionsToDestroy count
        stream.writeInt32(5); // datacenters count = 5

        String[][] dcIpv4 = testBackend ? new String[][]{
                {"149.154.175.40"},
                {"149.154.167.40"},
                {"149.154.175.117"},
                {"149.154.167.91"},
                {"149.154.171.5"}
        } : new String[][]{
                {"149.154.175.50"},
                {"149.154.167.51", "95.161.76.100"},
                {"149.154.175.100"},
                {"149.154.167.91"},
                {"149.154.171.5"}
        };

        String[] dcIpv6 = testBackend ? new String[]{
                "2001:b28:f23d:f001:0000:0000:0000:000e",
                "2001:67c:4e8:f002:0000:0000:0000:000e",
                "2001:b28:f23d:f003:0000:0000:0000:000e",
                "2001:67c:4e8:f004:0000:0000:0000:000a",
                "2001:b28:f23f:f005:0000:0000:0000:000a"
        } : new String[]{
                "2001:b28:f23d:f001:0000:0000:0000:000a",
                "2001:67c:4e8:f002:0000:0000:0000:000a",
                "2001:b28:f23d:f003:0000:0000:0000:000a",
                "2001:67c:4e8:f004:0000:0000:0000:000a",
                "2001:b28:f23f:f005:0000:0000:0000:000a"
        };

        for (int d = 1; d <= 5; d++) {
            stream.writeInt32(13); // dcVersion = 13
            stream.writeInt32(d);  // datacenterId
            stream.writeInt32(0);  // lastInitVersion
            stream.writeInt32(0);  // lastInitMediaVersion

            // 4 address lists:
            String[] ips = dcIpv4[d - 1];
            stream.writeInt32(ips.length);
            for (String ip : ips) {
                stream.writeString(ip);
                stream.writeInt32(443);
                stream.writeInt32(0);
                stream.writeString("");
            }
            stream.writeInt32(1);
            stream.writeString(dcIpv6[d - 1]);
            stream.writeInt32(443);
            stream.writeInt32(1);
            stream.writeString("");

            stream.writeInt32(0); // IPv4Download
            stream.writeInt32(0); // IPv6Download
            stream.writeBool(false); // isCdnDatacenter

            if (d == dcId) {
                stream.writeInt32(256);
                stream.writeBytes(authKey);
                stream.writeInt64(authKeyPermId);
                stream.writeInt32(0); // authKeyTemp len
                stream.writeInt64(0);
                stream.writeInt32(0); // authKeyMediaTemp len
                stream.writeInt64(0);
                stream.writeInt32(1); // authorized = 1
                stream.writeInt32(0); // serverSalts count
                stream.writeInt32(0); // mediaServerSalts count
            } else {
                stream.writeInt32(0);
                stream.writeInt64(0);
                stream.writeInt32(0);
                stream.writeInt64(0);
                stream.writeInt32(0);
                stream.writeInt64(0);
                stream.writeInt32(0); // authorized = 0
                stream.writeInt32(0);
                stream.writeInt32(0);
            }
        }

        byte[] payloadBytes = stream.toByteArray();
        stream.cleanup();

        File targetFile = new File(configDir, "tgnet.dat");
        File backupFile = new File(configDir, "tgnet.dat.bak");
        if (backupFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete();
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            int payloadLen = payloadBytes.length;
            fos.write(payloadLen & 0xFF);
            fos.write((payloadLen >> 8) & 0xFF);
            fos.write((payloadLen >> 16) & 0xFF);
            fos.write((payloadLen >> 24) & 0xFF);
            fos.write(payloadBytes);
            fos.flush();
        }
    }

    // ==================== PYROGRAM EXPORT ====================

    /**
     * Exports Pyrogram v2 Session String (>BI?256sQ?)
     * Format: URL-safe Base64 encoded 271 bytes
     */
    public static String exportPyrogramString(int accountIndex) {
        int[] outDc = new int[1];
        byte[] authKey = extractAuthKeyFromTgnetDat(accountIndex, outDc);
        if (authKey == null || authKey.length != 256) {
            return null;
        }
        int dcId = outDc[0] > 0 ? outDc[0] : ConnectionsManager.getInstance(accountIndex).getCurrentDatacenterId();
        if (dcId <= 0 || dcId > 5) dcId = 2;

        UserConfig config = UserConfig.getInstance(accountIndex);
        long userId = config != null ? config.getClientUserId() : 0;
        boolean isBot = config != null && config.getCurrentUser() != null && config.getCurrentUser().bot;

        byte[] buffer = new byte[271];
        buffer[0] = (byte) (dcId & 0xFF);

        int apiId = BuildVars.APP_ID > 0 ? BuildVars.APP_ID : 2040;
        buffer[1] = (byte) ((apiId >> 24) & 0xFF);
        buffer[2] = (byte) ((apiId >> 16) & 0xFF);
        buffer[3] = (byte) ((apiId >> 8) & 0xFF);
        buffer[4] = (byte) (apiId & 0xFF);

        buffer[5] = 0; // test_mode
        System.arraycopy(authKey, 0, buffer, 6, 256);

        for (int i = 0; i < 8; i++) {
            buffer[262 + i] = (byte) ((userId >> ((7 - i) * 8)) & 0xFF);
        }
        buffer[270] = (byte) (isBot ? 1 : 0);

        return Base64.encodeToString(buffer, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Exports Telethon String Session (1 + Base64(>B4sH256s))
     */
    public static String exportTelethonString(int accountIndex) {
        int[] outDc = new int[1];
        byte[] authKey = extractAuthKeyFromTgnetDat(accountIndex, outDc);
        if (authKey == null || authKey.length != 256) {
            return null;
        }
        int dcId = outDc[0] > 0 ? outDc[0] : ConnectionsManager.getInstance(accountIndex).getCurrentDatacenterId();
        if (dcId <= 0 || dcId > 5) dcId = 2;

        String[] ips = {"149.154.175.50", "149.154.167.51", "149.154.175.100", "149.154.167.91", "149.154.171.5"};
        String ipStr = (dcId >= 1 && dcId <= 5) ? ips[dcId - 1] : ips[1];
        byte[] ipBytes;
        try {
            ipBytes = InetAddress.getByName(ipStr).getAddress();
        } catch (Exception e) {
            ipBytes = new byte[]{(byte) 149, (byte) 154, (byte) 167, (byte) 51};
        }

        byte[] buffer = new byte[1 + ipBytes.length + 2 + 256];
        buffer[0] = (byte) (dcId & 0xFF);
        System.arraycopy(ipBytes, 0, buffer, 1, ipBytes.length);
        int port = 443;
        buffer[1 + ipBytes.length] = (byte) ((port >> 8) & 0xFF);
        buffer[1 + ipBytes.length + 1] = (byte) (port & 0xFF);
        System.arraycopy(authKey, 0, buffer, 1 + ipBytes.length + 2, 256);

        return "1" + Base64.encodeToString(buffer, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Exports standard Pyrogram SQLite (.session) file
     */
    public static File exportPyrogramSqliteFile(Context context, int accountIndex) {
        int[] outDc = new int[1];
        byte[] authKey = extractAuthKeyFromTgnetDat(accountIndex, outDc);
        if (authKey == null || authKey.length != 256) {
            return null;
        }
        int dcId = outDc[0] > 0 ? outDc[0] : ConnectionsManager.getInstance(accountIndex).getCurrentDatacenterId();
        if (dcId <= 0 || dcId > 5) dcId = 2;

        UserConfig config = UserConfig.getInstance(accountIndex);
        long userId = config != null ? config.getClientUserId() : 0;
        boolean isBot = config != null && config.getCurrentUser() != null && config.getCurrentUser().bot;
        String name = config != null && config.getCurrentUser() != null ? config.getCurrentUser().username : null;
        if (TextUtils.isEmpty(name)) {
            name = "account_" + userId;
        }

        File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
        if (!exportDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            exportDir.mkdirs();
        }

        String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dbFile = new File(exportDir, safeName + "_" + timestamp + ".session");
        if (dbFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.delete();
        }

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            db.execSQL("CREATE TABLE version (number INTEGER PRIMARY KEY);");
            db.execSQL("INSERT INTO version VALUES (3);");
            db.execSQL("CREATE TABLE sessions (dc_id INTEGER PRIMARY KEY, test_mode INTEGER, auth_key BLOB, date INTEGER, user_id INTEGER, is_bot INTEGER);");
            SQLiteStatement stmt = db.compileStatement("INSERT INTO sessions (dc_id, test_mode, auth_key, date, user_id, is_bot) VALUES (?, ?, ?, ?, ?, ?)");
            stmt.bindLong(1, dcId);
            stmt.bindLong(2, 0);
            stmt.bindBlob(3, authKey);
            stmt.bindLong(4, System.currentTimeMillis() / 1000);
            stmt.bindLong(5, userId);
            stmt.bindLong(6, isBot ? 1 : 0);
            stmt.executeInsert();
            stmt.close();
            return dbFile;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignore) {}
            }
        }
    }

    // ==================== FULL ALEXGRAM BACKUP EXPORT ====================

    public static void exportSessions(Context context, List<Integer> targetAccountIndices, boolean encrypt, String password, Runnable onComplete) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("app", "Alexgram");
                root.put("type", "session_backup");
                root.put("version", 3);
                root.put("exported_at", System.currentTimeMillis());
                root.put("formatted_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

                JSONArray accountsArray = new JSONArray();
                List<AccountSessionInfo> activeSessions = getActiveAccountSessions();

                for (AccountSessionInfo session : activeSessions) {
                    int a = session.accountIndex;
                    if (targetAccountIndices != null && !targetAccountIndices.contains(a)) {
                        continue;
                    }
                    JSONObject accObj = new JSONObject();
                    accObj.put("account_index", a);
                    accObj.put("user_id", session.userId);
                    accObj.put("first_name", session.firstName != null ? session.firstName : "");
                    accObj.put("last_name", session.lastName != null ? session.lastName : "");
                    accObj.put("username", session.username != null ? session.username : "");
                    accObj.put("phone", session.phone != null ? session.phone : "");
                    accObj.put("dc_id", session.dcId);
                    accObj.put("is_premium", session.isPremium);
                    accObj.put("is_bot", session.isBot);
                    accObj.put("login_time", session.loginTime);

                    // Pyrogram string representation
                    String pyroString = exportPyrogramString(a);
                    if (pyroString != null) {
                        accObj.put("pyrogram_session", pyroString);
                    }

                    // Preferences
                    SharedPreferences pref = UserConfig.getInstance(a).getPreferences();
                    Map<String, ?> allEntries = pref.getAll();
                    JSONObject prefJson = new JSONObject();
                    for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                        Object value = entry.getValue();
                        if (value == null) continue;
                        JSONObject itemObj = new JSONObject();
                        if (value instanceof Boolean) {
                            itemObj.put("t", "b");
                            itemObj.put("v", value);
                        } else if (value instanceof Integer) {
                            itemObj.put("t", "i");
                            itemObj.put("v", value);
                        } else if (value instanceof Long) {
                            itemObj.put("t", "l");
                            itemObj.put("v", value);
                        } else if (value instanceof Float) {
                            itemObj.put("t", "f");
                            itemObj.put("v", ((Float) value).doubleValue());
                        } else if (value instanceof String) {
                            itemObj.put("t", "s");
                            itemObj.put("v", value);
                        }
                        prefJson.put(entry.getKey(), itemObj);
                    }
                    accObj.put("preferences", prefJson);

                    // Session data files
                    File configDir = (a == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + a));
                    JSONObject filesJson = new JSONObject();
                    if (configDir.exists() && configDir.isDirectory()) {
                        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".dat") || name.endsWith(".json") || name.endsWith(".key") || name.endsWith(".bin") || name.endsWith(".data"));
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && f.length() < 10 * 1024 * 1024) {
                                    byte[] bytes = readFileToBytes(f);
                                    if (bytes != null) {
                                        filesJson.put(f.getName(), Base64.encodeToString(bytes, Base64.NO_WRAP));
                                    }
                                }
                            }
                        }
                    }
                    accObj.put("session_files", filesJson);
                    accountsArray.put(accObj);
                }

                root.put("accounts_count", accountsArray.length());
                root.put("accounts", accountsArray);

                String jsonString = root.toString(2);
                byte[] dataToSave;

                if (encrypt && !TextUtils.isEmpty(password)) {
                    dataToSave = encryptData(jsonString.getBytes(StandardCharsets.UTF_8), password);
                    root = new JSONObject();
                    root.put("app", "Alexgram");
                    root.put("encrypted", true);
                    root.put("payload", Base64.encodeToString(dataToSave, Base64.NO_WRAP));
                    jsonString = root.toString(2);
                }

                File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
                if (!exportDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    exportDir.mkdirs();
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "Alexgram_Backup_" + timestamp + ".session";
                File outputFile = new File(exportDir, fileName);

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(jsonString.getBytes(StandardCharsets.UTF_8));
                }

                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, LocaleController.getString(R.string.SessionExportSuccess), Toast.LENGTH_LONG).show();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, LocaleController.getString(R.string.SessionExportError), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==================== PARSERS & IMPORTERS ====================

    /**
     * Parses Pyrogram v1/v2 or Telethon session string
     */
    public static ParsedSession parseStringSession(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String clean = raw.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.startsWith("'") && clean.endsWith("'") && clean.length() > 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }

        // Telethon String Session: '1' + Base64(>B4sH256s) or Base64(>B16sH256s)
        if (clean.startsWith("1") && clean.length() > 300) {
            try {
                String sub = clean.substring(1);
                byte[] decoded = Base64.decode(sub, Base64.DEFAULT);
                if (decoded != null && (decoded.length == 263 || decoded.length == 275)) {
                    ParsedSession s = new ParsedSession();
                    s.dcId = decoded[0] & 0xFF;
                    s.authKey = Arrays.copyOfRange(decoded, decoded.length - 256, decoded.length);
                    s.sourceFormat = "Telethon String";
                    return s;
                }
            } catch (Exception ignore) {}
        }

        // Standard or URL-safe Base64 (Pyrogram v1, v2, Hydrogram, Pyrofork)
        byte[] decoded = null;
        try {
            decoded = Base64.decode(clean, Base64.URL_SAFE);
        } catch (Exception ignore) {}
        if (decoded == null || decoded.length < 260) {
            try {
                decoded = Base64.decode(clean, Base64.DEFAULT);
            } catch (Exception ignore) {}
        }
        if (decoded == null) {
            return null;
        }

        // Pyrogram v2 (>BI?256sQ?) - 271 bytes
        if (decoded.length == 271) {
            ParsedSession s = new ParsedSession();
            s.dcId = decoded[0] & 0xFF;
            s.apiId = ((decoded[1] & 0xFF) << 24) | ((decoded[2] & 0xFF) << 16) | ((decoded[3] & 0xFF) << 8) | (decoded[4] & 0xFF);
            s.testMode = decoded[5] != 0;
            s.authKey = Arrays.copyOfRange(decoded, 6, 262);
            ByteBuffer buf = ByteBuffer.wrap(decoded, 262, 8);
            s.userId = buf.getLong();
            s.isBot = decoded[270] != 0;
            s.sourceFormat = "Pyrogram v2 (Latest)";
            return s;
        }

        // Pyrogram v1 64-bit (>B?256sQ?) - 267 bytes
        if (decoded.length == 267) {
            ParsedSession s = new ParsedSession();
            s.dcId = decoded[0] & 0xFF;
            s.testMode = decoded[1] != 0;
            s.authKey = Arrays.copyOfRange(decoded, 2, 258);
            ByteBuffer buf = ByteBuffer.wrap(decoded, 258, 8);
            s.userId = buf.getLong();
            s.isBot = decoded[266] != 0;
            s.sourceFormat = "Pyrogram v1 (64-bit)";
            return s;
        }

        // Pyrogram v1 32-bit (>B?256sI?) - 263 bytes
        if (decoded.length == 263) {
            ParsedSession s = new ParsedSession();
            s.dcId = decoded[0] & 0xFF;
            s.testMode = decoded[1] != 0;
            s.authKey = Arrays.copyOfRange(decoded, 2, 258);
            ByteBuffer buf = ByteBuffer.wrap(decoded, 258, 4);
            s.userId = buf.getInt() & 0xFFFFFFFFL;
            s.isBot = decoded[262] != 0;
            s.sourceFormat = "Pyrogram v1 (32-bit)";
            return s;
        }

        // Telethon fallback without '1' prefix
        if (decoded.length == 275) {
            ParsedSession s = new ParsedSession();
            s.dcId = decoded[0] & 0xFF;
            s.authKey = Arrays.copyOfRange(decoded, decoded.length - 256, decoded.length);
            s.sourceFormat = "Telethon String";
            return s;
        }

        return null;
    }

    /**
     * Parses Pyrogram SQLite .session database file
     */
    public static ParsedSession parseSqliteSessionFile(File file) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = db.rawQuery("SELECT dc_id, test_mode, auth_key, user_id, is_bot FROM sessions LIMIT 1", null);
            if (cursor != null && cursor.moveToFirst()) {
                ParsedSession session = new ParsedSession();
                session.dcId = cursor.getInt(0);
                session.testMode = cursor.getInt(1) != 0;
                session.authKey = cursor.getBlob(2);
                session.userId = cursor.getLong(3);
                session.isBot = cursor.getInt(4) != 0;
                session.sourceFormat = "Pyrogram SQLite (.session)";
                cursor.close();
                if (session.authKey != null && session.authKey.length == 256) {
                    return session;
                }
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (db != null) {
                try { db.close(); } catch (Exception ignore) {}
            }
        }
        return null;
    }

    /**
     * Imports a session directly from pasted Pyrogram or Telethon string
     */
    public static void importSessionFromString(Context context, String sessionString, SessionImportCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ParsedSession parsed = parseStringSession(sessionString);
                if (parsed == null || parsed.authKey == null || parsed.authKey.length != 256) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("InvalidSessionString", R.string.SessionImportError)));
                    }
                    return;
                }

                int targetAccount = findTargetAccountIndex();
                if (targetAccount < 0) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("MaxAccountsLimitReached", R.string.MaxAccountsLimitReached)));
                    }
                    return;
                }

                applyParsedSessionToAccount(context, parsed, targetAccount);

                String summary = "• " + parsed.sourceFormat + "\n  User ID: " + (parsed.userId != 0 ? parsed.userId : "Auto") + "\n  Datacenter: DC" + parsed.dcId;

                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportSuccess(targetAccount, summary));
                }

            } catch (Exception e) {
                FileLog.e(e);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString(R.string.SessionImportError) + ": " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Alias for importSessionFromFile (used by LoginActivity)
     */
     public static void importSessionFromUri(Context context, Uri uri, String password, SessionImportCallback callback) {
         importSessionFromFile(context, uri, password, callback);
     }

    /**
     * Imports session from picked file (.session SQLite, text string file, or Alexgram JSON backup)
     */
    public static void importSessionFromFile(Context context, Uri uri, String password, SessionImportCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            File tempFile = null;
            try {
                InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("SessionStreamError", R.string.SessionStreamError)));
                    }
                    return;
                }

                // Copy to temp file to test SQLite or JSON or String
                tempFile = new File(ApplicationLoader.applicationContext.getCacheDir(), "import_temp_" + System.currentTimeMillis() + ".session");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = inputStream.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                    }
                }
                inputStream.close();

                // 1. Try parsing as SQLite .session file
                ParsedSession sqliteSession = parseSqliteSessionFile(tempFile);
                if (sqliteSession != null) {
                    int targetAccount = findTargetAccountIndex();
                    if (targetAccount < 0) {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("MaxAccountsLimitReached", R.string.MaxAccountsLimitReached)));
                        }
                        return;
                    }
                    applyParsedSessionToAccount(context, sqliteSession, targetAccount);
                    String summary = "• Pyrogram SQLite Session\n  User ID: " + (sqliteSession.userId != 0 ? sqliteSession.userId : "Auto") + "\n  Datacenter: DC" + sqliteSession.dcId;
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportSuccess(targetAccount, summary));
                    }
                    return;
                }

                // 2. Try parsing as text / string session or JSON
                byte[] fileBytes = readFileToBytes(tempFile);
                if (fileBytes == null || fileBytes.length == 0) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("SessionFileEmpty", R.string.SessionFileEmpty)));
                    }
                    return;
                }

                String textContent = new String(fileBytes, StandardCharsets.UTF_8).trim();

                // Check if text content is Pyrogram / Telethon string
                ParsedSession textSession = parseStringSession(textContent);
                if (textSession != null) {
                    int targetAccount = findTargetAccountIndex();
                    if (targetAccount < 0) {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("MaxAccountsLimitReached", R.string.MaxAccountsLimitReached)));
                        }
                        return;
                    }
                    applyParsedSessionToAccount(context, textSession, targetAccount);
                    String summary = "• " + textSession.sourceFormat + "\n  User ID: " + (textSession.userId != 0 ? textSession.userId : "Auto") + "\n  Datacenter: DC" + textSession.dcId;
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportSuccess(targetAccount, summary));
                    }
                    return;
                }

                // 3. Try parsing as Alexgram JSON backup
                if (textContent.startsWith("{")) {
                    JSONObject root = new JSONObject(textContent);
                    if (root.optBoolean("encrypted", false)) {
                        if (TextUtils.isEmpty(password)) {
                            if (callback != null) {
                                AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("SessionPasswordRequired", R.string.SessionPasswordRequired)));
                            }
                            return;
                        }
                        String base64Payload = root.optString("payload");
                        byte[] encryptedBytes = Base64.decode(base64Payload, Base64.DEFAULT);
                        byte[] decryptedBytes = decryptData(encryptedBytes, password);
                        String decryptedJson = new String(decryptedBytes, StandardCharsets.UTF_8);
                        root = new JSONObject(decryptedJson);
                    }

                    JSONArray accountsArray = root.optJSONArray("accounts");
                    if (accountsArray == null || accountsArray.length() == 0) {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString("SessionNoAccountsInBackup", R.string.SessionNoAccountsInBackup)));
                        }
                        return;
                    }

                    int importedAccountIndex = -1;
                    StringBuilder summary = new StringBuilder();

                    for (int i = 0; i < accountsArray.length(); i++) {
                        JSONObject accObj = accountsArray.getJSONObject(i);
                        String name = (accObj.optString("first_name") + " " + accObj.optString("last_name")).trim();
                        long uId = accObj.optLong("user_id");
                        int dc = accObj.optInt("dc_id", 2);

                        int targetAccount = findTargetAccountIndex();
                        if (targetAccount < 0) break;
                        if (importedAccountIndex < 0) importedAccountIndex = targetAccount;

                        // Check if pyrogram_session is embedded
                        String pyroStr = accObj.optString("pyrogram_session", null);
                        if (!TextUtils.isEmpty(pyroStr)) {
                            ParsedSession pSession = parseStringSession(pyroStr);
                            if (pSession != null) {
                                applyParsedSessionToAccount(context, pSession, targetAccount);
                            }
                        }

                        // Restore preferences
                        JSONObject prefJson = accObj.optJSONObject("preferences");
                        if (prefJson != null) {
                            SharedPreferences.Editor editor = UserConfig.getInstance(targetAccount).getPreferences().edit();
                            editor.clear();
                            Iterator<String> keys = prefJson.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                Object val = prefJson.get(k);
                                if (val instanceof JSONObject) {
                                    JSONObject itemObj = (JSONObject) val;
                                    String type = itemObj.optString("t");
                                    if ("b".equals(type)) {
                                        editor.putBoolean(k, itemObj.getBoolean("v"));
                                    } else if ("i".equals(type)) {
                                        editor.putInt(k, itemObj.getInt("v"));
                                    } else if ("l".equals(type)) {
                                        editor.putLong(k, itemObj.getLong("v"));
                                    } else if ("f".equals(type)) {
                                        editor.putFloat(k, (float) itemObj.getDouble("v"));
                                    } else if ("s".equals(type)) {
                                        editor.putString(k, itemObj.getString("v"));
                                    }
                                } else {
                                    if (val instanceof Boolean) {
                                        editor.putBoolean(k, (Boolean) val);
                                    } else if (val instanceof Integer) {
                                        editor.putInt(k, (Integer) val);
                                    } else if (val instanceof Long) {
                                        editor.putLong(k, (Long) val);
                                    } else if (val instanceof Float || val instanceof Double) {
                                        editor.putFloat(k, ((Number) val).floatValue());
                                    } else if (val instanceof String) {
                                        editor.putString(k, (String) val);
                                    }
                                }
                            }
                            editor.commit();
                        }

                        // Restore native files
                        File targetDir = (targetAccount == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + targetAccount));
                        if (!targetDir.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            targetDir.mkdirs();
                        }
                        JSONObject filesJson = accObj.optJSONObject("session_files");
                        if (filesJson != null) {
                            Iterator<String> fKeys = filesJson.keys();
                            while (fKeys.hasNext()) {
                                String fileName = fKeys.next();
                                String b64 = filesJson.getString(fileName);
                                byte[] destBytes = Base64.decode(b64, Base64.DEFAULT);
                                File destFile = new File(targetDir, fileName);
                                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                    fos.write(destBytes);
                                }
                            }
                        }

                        UserConfig.getInstance(targetAccount).reloadConfig();
                        if (UserConfig.getInstance(targetAccount).isClientActivated()) {
                            UserConfig.getInstance(targetAccount).saveConfig(false);
                            try {
                                ConnectionsManager.getInstance(targetAccount).setUserId(UserConfig.getInstance(targetAccount).getClientUserId());
                            } catch (Exception ignore) {}
                        }

                        summary.append("• ").append(!TextUtils.isEmpty(name) ? name : "Account #" + (targetAccount + 1))
                                .append(" (ID: ").append(uId).append(", DC").append(dc).append(")\n");
                    }

                    final int finalTargetAccount = importedAccountIndex;
                    final String finalSummary = summary.toString().trim();

                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportSuccess(finalTargetAccount, finalSummary));
                    }
                    return;
                }

                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString(R.string.SessionImportError)));
                }

            } catch (Exception e) {
                FileLog.e(e);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString(R.string.SessionImportError) + ": " + e.getMessage()));
                }
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    /**
     * Applies a parsed session (dcId, authKey, userId, isBot) into target account index
     */
    public static void applyParsedSessionToAccount(Context context, ParsedSession session, int targetAccount) throws Exception {
        File targetDir = (targetAccount == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + targetAccount));
        if (!targetDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            targetDir.mkdirs();
        }

        // 1. Write clean tgnet.dat
        writeTgnetDat(targetDir, session.dcId, session.authKey, session.testMode);

        // 2. Create User object
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = session.userId > 0 ? session.userId : (100000000L + targetAccount);
        user.first_name = "Session User";
        user.self = true;
        user.bot = session.isBot;

        SerializedData uData = new SerializedData();
        user.serializeToStream(uData);
        byte[] uBytes = uData.toByteArray();
        uData.cleanup();
        String userBase64 = Base64.encodeToString(uBytes, Base64.DEFAULT);

        // 3. Write UserConfig SharedPreferences
        SharedPreferences.Editor editor = UserConfig.getInstance(targetAccount).getPreferences().edit();
        editor.clear();
        editor.putString("user", userBase64);
        if (targetAccount == 0) {
            editor.putInt("selectedAccount", targetAccount);
        }
        editor.putInt("loginTime", (int) (System.currentTimeMillis() / 1000));
        editor.putBoolean("syncContacts", true);
        editor.putBoolean("suggestContacts", true);
        editor.putBoolean("unreadDialogsLoaded", true);
        editor.putBoolean("draftsLoaded", true);
        editor.commit();

        SharedPreferences.Editor editor0 = UserConfig.getInstance(0).getPreferences().edit();
        editor0.putInt("selectedAccount", targetAccount);
        editor0.commit();

        // 4. Initialize UserConfig and controllers
        UserConfig.selectedAccount = targetAccount;
        UserConfig.getInstance(targetAccount).clearConfig();
        UserConfig.getInstance(targetAccount).setCurrentUser(user);
        UserConfig.getInstance(targetAccount).saveConfig(true);
        UserConfig.getInstance(0).saveConfig(true);

        try {
            MessagesStorage.getInstance(targetAccount).cleanup(true);
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(user);
            MessagesStorage.getInstance(targetAccount).putUsersAndChats(users, null, true, true);
        } catch (Exception ignore) {}

        try {
            AccountInstance accountInstance = AccountInstance.getInstance(targetAccount);
            accountInstance.getUserConfig().loadConfig();

            String deviceModel;
            String systemLangCode;
            String langCode;
            String appVersion;
            String systemVersion;
            try {
                systemLangCode = LocaleController.getSystemLocaleStringIso639().toLowerCase();
                langCode = LocaleController.getLocaleStringIso639().toLowerCase();
                deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
                appVersion = BuildConfig.VERSION_NAME.split("-")[0];
                systemVersion = "SDK " + Build.VERSION.SDK_INT;
            } catch (Exception ignore) {
                systemLangCode = "en";
                langCode = "en";
                deviceModel = "Android";
                appVersion = "12.9.2";
                systemVersion = "SDK 34";
            }
            int timezoneOffset = (TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings()) / 1000;

            accountInstance.getConnectionsManager().init(
                    SharedConfig.buildVersion(),
                    TLRPC.LAYER,
                    NekoXConfig.currentAppId(),
                    deviceModel,
                    systemVersion,
                    appVersion,
                    langCode,
                    systemLangCode,
                    targetDir.toString(),
                    FileLog.getNetworkLogPath(),
                    SharedConfig.pushString != null ? SharedConfig.pushString : "",
                    AndroidUtilities.getCertificateSHA256Fingerprint(),
                    timezoneOffset,
                    user.id,
                    false,
                    true
            );
            accountInstance.getConnectionsManager().resumeNetworkMaybe();

            // Request full self user details from server
            TLRPC.TL_users_getUsers reqUsers = new TLRPC.TL_users_getUsers();
            TLRPC.TL_inputUserSelf selfInput = new TLRPC.TL_inputUserSelf();
            reqUsers.id.add(selfInput);
            accountInstance.getConnectionsManager().sendRequest(reqUsers, (response, error) -> {
                if (response instanceof Vector) {
                    Vector<?> v = (Vector<?>) response;
                    if (!v.objects.isEmpty() && v.objects.get(0) instanceof TLRPC.User) {
                        TLRPC.User fullSelf = (TLRPC.User) v.objects.get(0);
                        fullSelf.self = true;
                        AndroidUtilities.runOnUIThread(() -> {
                            accountInstance.getUserConfig().setCurrentUser(fullSelf);
                            accountInstance.getUserConfig().saveConfig(true);
                            accountInstance.getMessagesController().putUser(fullSelf, false);
                            ArrayList<TLRPC.User> uList = new ArrayList<>();
                            uList.add(fullSelf);
                            accountInstance.getMessagesStorage().putUsersAndChats(uList, null, true, true);
                            accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                        });
                    }
                }
            });

            // Load dialogs from server
            AndroidUtilities.runOnUIThread(() -> {
                accountInstance.getMessagesController().loadDialogs(0, 0, 100, false);
                accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            });

        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static int findTargetAccountIndex() {
        int current = UserConfig.selectedAccount;
        if (!UserConfig.getInstance(current).isClientActivated()) {
            return current;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    private static byte[] readFileToBytes(File file) {
        try (InputStream is = new FileInputStream(file); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] encryptData(byte[] input, String password) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = new byte[16];
        System.arraycopy(keyBytes, 0, iv, 0, 16);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(input);
    }

    private static byte[] decryptData(byte[] encrypted, String password) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = new byte[16];
        System.arraycopy(keyBytes, 0, iv, 0, 16);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(encrypted);
    }
}
