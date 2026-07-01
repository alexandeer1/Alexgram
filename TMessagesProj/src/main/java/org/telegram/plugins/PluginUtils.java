package org.telegram.plugins;

import android.content.Context;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Static helpers exposed to plugins through the Python facade modules
 * (client_utils / hook_utils / android_utils). Everything here is best-effort and
 * must never throw into the host — plugins run untrusted code.
 */
public final class PluginUtils {

    private PluginUtils() {
    }

    // ---- client_utils ----

    public static int getCurrentAccount() {
        return UserConfig.selectedAccount;
    }

    public static UserConfig getUserConfig() {
        return UserConfig.getInstance(UserConfig.selectedAccount);
    }

    /** Top-most fragment currently on screen, or null. */
    public static BaseFragment getLastFragment() {
        try {
            LaunchActivity la = LaunchActivity.instance;
            if (la == null || la.actionBarLayout == null) {
                return null;
            }
            List<BaseFragment> stack = la.actionBarLayout.getFragmentStack();
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return stack.get(stack.size() - 1);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    /** Best-effort context: visible activity if any, else the application context. */
    public static Context getContext() {
        try {
            BaseFragment f = getLastFragment();
            if (f != null && f.getParentActivity() != null) {
                return f.getParentActivity();
            }
        } catch (Throwable ignore) {
        }
        return ApplicationLoader.applicationContext;
    }

    /** Run on the global background queue (matches exteraGram's run_on_queue). */
    public static void runOnQueue(Runnable r) {
        if (r == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(r);
    }

    public static void runOnUiThread(Runnable r) {
        if (r == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(r);
    }

    public static void runOnUiThread(Runnable r, long delayMs) {
        if (r == null) {
            return;
        }
        if (delayMs <= 0) {
            AndroidUtilities.runOnUIThread(r);
        } else {
            AndroidUtilities.runOnUIThread(r, delayMs);
        }
    }

    /** Resolve an app drawable id by name (e.g. "msg_info"); 0 if missing. */
    public static int resolveDrawable(String name) {
        if (name == null || name.length() == 0) {
            return 0;
        }
        try {
            Context c = ApplicationLoader.applicationContext;
            return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Send a local file as a document to a dialog. Mirrors exteraGram's send_document().
     * Reply/quote objects are passed straight through (may be null).
     */
    public static void sendDocument(long dialogId, String path, String caption,
                                    Object replyToMsg, Object replyToTopMsg, Object replyQuote) {
        if (path == null || dialogId == 0) {
            return;
        }
        final String cap = caption == null ? "" : caption;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                SendMessagesHelper.prepareSendingDocument(
                        AccountInstance.getInstance(account),
                        path, path, null, cap, null, dialogId,
                        (MessageObject) replyToMsg,
                        (MessageObject) replyToTopMsg,
                        null,
                        (ChatActivity.ReplyQuote) replyQuote,
                        null, true, 0, null, null, 0, false);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    // ---- hook_utils ----

    public static Class<?> findClass(String name) {
        try {
            ClassLoader cl = ApplicationLoader.applicationContext.getClassLoader();
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Read a (possibly private) field by name, walking up the superclass chain. */
    public static Object getPrivateField(Object obj, String name) {
        if (obj == null || name == null) {
            return null;
        }
        Class<?> c = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
        Object target = (obj instanceof Class) ? null : obj;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Write a (possibly private) field by name. */
    public static boolean setPrivateField(Object obj, String name, Object value) {
        if (obj == null || name == null) {
            return false;
        }
        Class<?> c = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
        Object target = (obj instanceof Class) ? null : obj;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
}
