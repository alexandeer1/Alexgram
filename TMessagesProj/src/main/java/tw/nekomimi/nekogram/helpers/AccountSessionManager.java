package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                info.loginTime = config.loginTime;
                if (currentUser != null) {
                    info.firstName = currentUser.first_name;
                    info.lastName = currentUser.last_name;
                    info.username = currentUser.username;
                    info.phone = currentUser.phone;
                    info.isPremium = currentUser.premium;
                }
                sessions.add(info);
            }
        }
        return sessions;
    }

    public static void exportSessions(Context context, boolean encrypt, String password, Runnable onComplete) {
        exportSessions(context, null, encrypt, password, onComplete);
    }

    public static void exportSessions(Context context, List<Integer> targetAccountIndices, boolean encrypt, String password, Runnable onComplete) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("app", "Alexgram");
                root.put("type", "session_backup");
                root.put("version", 2);
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
                    accObj.put("login_time", session.loginTime);

                    // Export userconfig SharedPreferences with type metadata
                    SharedPreferences pref = context.getSharedPreferences(a == 0 ? "userconfig" : "userconfig" + a, Context.MODE_PRIVATE);
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

                    // Export native session data files (.dat files)
                    File configDir = (a == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + a));
                    JSONObject filesJson = new JSONObject();
                    if (configDir.exists() && configDir.isDirectory()) {
                        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".dat") || name.endsWith(".json") || name.endsWith(".key") || name.endsWith(".bin") || name.endsWith(".data"));
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && f.length() < 10 * 1024 * 1024) {
                                    byte[] bytes = readFileToBytes(f);
                                    if (bytes != null) {
                                        filesJson.put(f.getName(), android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
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
                    root.put("payload", android.util.Base64.encodeToString(dataToSave, android.util.Base64.NO_WRAP));
                    jsonString = root.toString(2);
                }

                File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
                if (!exportDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    exportDir.mkdirs();
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "Alexgram_Session_" + timestamp + ".session";
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

    public static void importSessionFromUri(Context context, Uri uri, String password, SessionImportCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed("Could not read file stream."));
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                inputStream.close();

                String fileContent = stringBuilder.toString();
                JSONObject root = new JSONObject(fileContent);

                if (root.optBoolean("encrypted", false)) {
                    if (TextUtils.isEmpty(password)) {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onImportFailed("Password required for encrypted session backup."));
                        }
                        return;
                    }
                    String base64Payload = root.optString("payload");
                    byte[] encryptedBytes = android.util.Base64.decode(base64Payload, android.util.Base64.DEFAULT);
                    byte[] decryptedBytes = decryptData(encryptedBytes, password);
                    String decryptedJson = new String(decryptedBytes, StandardCharsets.UTF_8);
                    root = new JSONObject(decryptedJson);
                }

                JSONArray accountsArray = root.optJSONArray("accounts");
                if (accountsArray == null || accountsArray.length() == 0) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onImportFailed("No valid account sessions found in session file."));
                    }
                    return;
                }

                int importedAccountIndex = -1;
                StringBuilder summary = new StringBuilder();

                for (int i = 0; i < accountsArray.length(); i++) {
                    JSONObject accObj = accountsArray.getJSONObject(i);
                    String name = accObj.optString("first_name") + " " + accObj.optString("last_name");
                    long uId = accObj.optLong("user_id");
                    int dc = accObj.optInt("dc_id");

                    // Find target account index
                    int targetAccount = findTargetAccountIndex();
                    if (targetAccount < 0) break;
                    if (importedAccountIndex < 0) importedAccountIndex = targetAccount;

                    // Restore preferences
                    JSONObject prefJson = accObj.optJSONObject("preferences");
                    if (prefJson != null) {
                        SharedPreferences.Editor editor = context.getSharedPreferences(targetAccount == 0 ? "userconfig" : "userconfig" + targetAccount, Context.MODE_PRIVATE).edit();
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

                    // Restore native session dat files
                    File targetDir = (targetAccount == 0 ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + targetAccount));
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }
                    JSONObject filesJson = accObj.optJSONObject("session_files");
                    if (filesJson != null) {
                        Iterator<String> fKeys = filesJson.keys();
                        while (fKeys.hasNext()) {
                            String fileName = fKeys.next();
                            String b64 = filesJson.getString(fileName);
                            byte[] fileBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                            File destFile = new File(targetDir, fileName);
                            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                fos.write(fileBytes);
                            }
                        }
                    }

                    // Force reload UserConfig from updated preferences
                    UserConfig.getInstance(targetAccount).reloadConfig();
                    if (UserConfig.getInstance(targetAccount).isClientActivated()) {
                        UserConfig.getInstance(targetAccount).saveConfig(false);
                        try {
                            org.telegram.tgnet.ConnectionsManager.getInstance(targetAccount).setUserId(UserConfig.getInstance(targetAccount).getClientUserId());
                        } catch (Exception ignore) {}
                    }

                    summary.append("• ").append(name.trim()).append(" (ID: ").append(uId).append(", DC").append(dc).append(")\n");
                }

                final int finalTargetAccount = importedAccountIndex;
                final String finalSummary = summary.toString().trim();

                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportSuccess(finalTargetAccount, finalSummary));
                }

            } catch (Exception e) {
                FileLog.e(e);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onImportFailed(LocaleController.getString(R.string.SessionImportError)));
                }
            }
        });
    }

    private static int findTargetAccountIndex() {
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
