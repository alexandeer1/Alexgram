package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import android.util.SparseArray;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class HiddenChatsController {

    private static volatile HiddenChatsController Instance;
    private final SharedPreferences preferences;
    private final SparseArray<Set<String>> hiddenChatIds = new SparseArray<>();
    private String passcode;
    private boolean isUnlocked = false;
    private boolean biometricEnabled = false;

    public static HiddenChatsController getInstance() {
        HiddenChatsController localInstance = Instance;
        if (localInstance == null) {
            synchronized (HiddenChatsController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new HiddenChatsController();
                }
            }
        }
        return localInstance;
    }

    private HiddenChatsController() {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("hidden_chats_config", Context.MODE_PRIVATE);
        loadConfig();
    }

    private void loadConfig() {
        Set<String> savedIds = preferences.getStringSet("hidden_ids", null);
        if (savedIds != null) {
            // Migration
            hiddenChatIds.put(0, new HashSet<>(savedIds));
            preferences.edit().remove("hidden_ids").putStringSet("hidden_ids_0", savedIds).apply();
        }

        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
             if (hiddenChatIds.indexOfKey(i) >= 0) continue;

             Set<String> accountIds = preferences.getStringSet("hidden_ids_" + i, new HashSet<>());
             hiddenChatIds.put(i, new HashSet<>(accountIds));
        }

        passcode = preferences.getString("passcode", null);
        biometricEnabled = preferences.getBoolean("biometric", false);
    }

    public void reset() {
        hiddenChatIds.clear();
        passcode = null;
        isUnlocked = false;
        biometricEnabled = false;
        preferences.edit().clear().apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    private void saveIds(int currentAccount) {
        if (hiddenChatIds.indexOfKey(currentAccount) >= 0) {
            preferences.edit().putStringSet("hidden_ids_" + currentAccount, hiddenChatIds.get(currentAccount)).apply();
        }
    }

    public void setPasscode(String code) {
        passcode = code;
        preferences.edit().putString("passcode", code).apply();
        isUnlocked = true; // Unlock when setting new passcode
    }

    public boolean hasPasscode() {
        return passcode != null && !passcode.isEmpty();
    }

    public boolean checkPasscode(String code) {
        return code != null && code.equals(passcode);
    }

    public void setBiometricEnabled(boolean enabled) {
        biometricEnabled = enabled;
        preferences.edit().putBoolean("biometric", enabled).apply();
    }
    
    public boolean isBiometricEnabled() {
        return biometricEnabled;
    }

    public void unlock() {
        isUnlocked = true;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public void lock() {
        isUnlocked = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public void toggleHidden(int currentAccount, long dialogId) {
        String idInfo = String.valueOf(dialogId);
        
        Set<String> ids = hiddenChatIds.get(currentAccount);
        if (ids == null) {
            ids = new HashSet<>();
            hiddenChatIds.put(currentAccount, ids);
        }

        if (ids.contains(idInfo)) {
            ids.remove(idInfo);
        } else {
            ids.add(idInfo);
        }
        saveIds(currentAccount);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 
                org.telegram.messenger.MessagesController.UPDATE_MASK_DIALOGS);
    }

    public boolean isHidden(int currentAccount, long dialogId) {
        Set<String> ids = hiddenChatIds.get(currentAccount);
        return ids != null && ids.contains(String.valueOf(dialogId));
    }
    
    public int getHiddenCount(int currentAccount) {
        Set<String> ids = hiddenChatIds.get(currentAccount);
        return ids != null ? ids.size() : 0;
    }
}
