package org.telegram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Dispatches BasePlugin.on_update(s)_hook for incoming updates.
 *
 * Hooks MessagesController.processUpdates(TLRPC.Updates, boolean). In beforeHookedMethod plugins
 * observe/modify the container (and per-update dispatch happens Python-side); returning CANCEL
 * skips processing the whole container. Gated by {@link #active}.
 */
public final class PluginUpdatesInterceptor {

    private static volatile boolean active = false;
    private static volatile boolean installed = false;

    private PluginUpdatesInterceptor() {
    }

    public static synchronized void setActive(boolean value) {
        active = value;
        if (value && !installed) {
            install();
        }
    }

    private static void install() {
        if (installed) {
            return;
        }
        try {
            Method m = MessagesController.class.getDeclaredMethod(
                    "processUpdates", TLRPC.Updates.class, boolean.class);
            XposedBridge.hookMethod(m, new UpdatesHook());
            installed = true;
            FileLog.d("zasto plugins: updates interceptor installed");
        } catch (Throwable t) {
            FileLog.e("zasto plugins: failed to install updates interceptor", t);
        }
    }

    private static int accountOf(Object messagesController) {
        Object v = PluginUtils.getPrivateField(messagesController, "currentAccount");
        if (v instanceof Integer) {
            return (Integer) v;
        }
        return UserConfig.selectedAccount;
    }

    private static final class UpdatesHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!active) {
                return;
            }
            try {
                if (param.args == null || param.args.length < 1 || param.args[0] == null) {
                    return;
                }
                Object updates = param.args[0];
                String name = updates.getClass().getSimpleName();
                int account = accountOf(param.thisObject);
                boolean cancel = PluginsController.getInstance().dispatchUpdates(name, account, updates);
                if (cancel) {
                    param.setResult(null); // skip processing this updates container
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
