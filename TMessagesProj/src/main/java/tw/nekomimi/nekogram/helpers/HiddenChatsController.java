package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class HiddenChatsController {

    private static volatile HiddenChatsController Instance;
    private final SharedPreferences preferences;
    private final Set<String> hiddenChatIds = new HashSet<>();
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
        Set<String> savedIds = preferences.getStringSet("hidden_ids", new HashSet<>());
        if (savedIds != null) {
            hiddenChatIds.addAll(savedIds);
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

    private void saveIds() {
        preferences.edit().putStringSet("hidden_ids", hiddenChatIds).apply();
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

    public void toggleHidden(long dialogId) {
        String idInfo = String.valueOf(dialogId);
        if (hiddenChatIds.contains(idInfo)) {
            hiddenChatIds.remove(idInfo);
        } else {
            hiddenChatIds.add(idInfo);
        }
        saveIds();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public boolean isHidden(long dialogId) {
        return hiddenChatIds.contains(String.valueOf(dialogId));
    }
    
    public int getHiddenCount() {
        return hiddenChatIds.size();
    }
}
