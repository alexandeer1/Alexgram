// [Alexgram: Hidden Accounts] - Start
package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages password-protected account visibility.
 * Accounts marked as hidden are filtered out of the account switcher
 * until the user unlocks with their PIN.
 *
 * PIN is stored as a salted SHA-256 hash.
 * Hidden account indices are persisted in a dedicated SharedPreferences file.
 */
public class HiddenAccountsController {

    private static final String PREFS_FILE = "hidden_accounts_config";
    private static final String KEY_PIN_HASH   = "pin_hash";
    private static final String KEY_PIN_SALT   = "pin_salt";
    private static final String KEY_HIDDEN     = "hidden_accounts";
    private static final String KEY_AUTO_LOCK  = "auto_lock";

    private static volatile HiddenAccountsController instance;

    private final SharedPreferences prefs;
    private boolean unlocked = false;

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static HiddenAccountsController getInstance() {
        if (instance == null) {
            synchronized (HiddenAccountsController.class) {
                if (instance == null) {
                    instance = new HiddenAccountsController();
                }
            }
        }
        return instance;
    }

    private HiddenAccountsController() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    // ── PIN management ────────────────────────────────────────────────────────

    public boolean hasPin() {
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT);
    }

    public void setPin(String pin) {
        try {
            byte[] salt = new byte[16];
            Utilities.random.nextBytes(salt);
            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + pinBytes.length];
            System.arraycopy(salt, 0, bytes, 0, 16);
            System.arraycopy(pinBytes, 0, bytes, 16, pinBytes.length);
            System.arraycopy(salt, 0, bytes, pinBytes.length + 16, 16);
            String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            prefs.edit()
                    .putString(KEY_PIN_HASH, hash)
                    .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
                    .apply();
            unlocked = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean checkPin(String pin) {
        try {
            String hash = prefs.getString(KEY_PIN_HASH, "");
            String saltStr = prefs.getString(KEY_PIN_SALT, "");
            byte[] salt = saltStr.isEmpty() ? new byte[0] : Base64.decode(saltStr, Base64.DEFAULT);
            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + pinBytes.length];
            System.arraycopy(salt, 0, bytes, 0, 16);
            System.arraycopy(pinBytes, 0, bytes, 16, pinBytes.length);
            System.arraycopy(salt, 0, bytes, pinBytes.length + 16, 16);
            String computed = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            return hash.equals(computed);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public void removePin() {
        // Unhide all accounts before clearing
        Set<String> saved = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        for (String s : saved) {
            try {
                int account = Integer.parseInt(s);
                PasscodeHelper.setHideAccount(account, false);
            } catch (NumberFormatException ignored) {}
        }
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .remove(KEY_HIDDEN)
                .apply();
        unlocked = false;
        notifyChanged();
    }

    // ── Lock state ────────────────────────────────────────────────────────────

    public void unlock() {
        unlocked = true;
        notifyChanged();
    }

    public void lock() {
        unlocked = false;
        notifyChanged();
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public boolean isLocked() {
        return !unlocked;
    }

    // ── Account visibility ────────────────────────────────────────────────────

    /**
     * Returns true if the account is configured as hidden (regardless of lock state).
     * Use isVisibleInSwitcher() to determine whether to actually show in the UI.
     */
    public boolean isAccountHidden(int account) {
        Set<String> saved = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        return saved.contains(String.valueOf(account));
    }

    public void setAccountHidden(int account, boolean hidden) {
        Set<String> saved = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
        if (hidden) {
            saved.add(String.valueOf(account));
        } else {
            saved.remove(String.valueOf(account));
        }
        prefs.edit().putStringSet(KEY_HIDDEN, saved).apply();

        // Wire into the existing PasscodeHelper filtering that is already used
        // across the entire codebase (DrawerLayoutAdapter, SettingsActivity, etc.)
        PasscodeHelper.setHideAccount(account, hidden);
        notifyChanged();
    }

    public Set<Integer> getHiddenAccounts() {
        Set<String> saved = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        Set<Integer> result = new HashSet<>();
        for (String s : saved) {
            try { result.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public int getHiddenCount() {
        return prefs.getStringSet(KEY_HIDDEN, new HashSet<>()).size();
    }

    // Restore PasscodeHelper hidden state on app start (after restart, PasscodeHelper reads
    // its own prefs, but we need to re-apply our hidden flags if the account remains hidden)
    public void restoreHiddenState() {
        if (!hasPin()) return;
        Set<String> saved = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        for (String s : saved) {
            try {
                int account = Integer.parseInt(s);
                if (UserConfig.getInstance(account).isClientActivated()) {
                    // Ensure PasscodeHelper reflects our hidden flag
                    PasscodeHelper.setHideAccount(account, true);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    public boolean isAutoLockEnabled() {
        return prefs.getBoolean(KEY_AUTO_LOCK, true);
    }

    public void setAutoLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_LOCK, enabled).apply();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void notifyChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainUserInfoChanged);
        }
    }
}
// [Alexgram: Hidden Accounts] - End