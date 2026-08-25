package org.telegram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Dispatches BasePlugin.on_send_message_hook for outgoing messages.
 *
 * Hooks SendMessagesHelper.sendMessage(SendMessageParams) — the funnel every text/media send goes
 * through. In beforeHookedMethod the plugin can mutate the params in place (MODIFY, e.g. rewrite
 * params.message) or cancel the send (CANCEL). Gated by {@link #active} so there is no per-send
 * work unless a plugin registered an outgoing-message hook.
 */
public final class PluginSendMessageInterceptor {

    private static volatile boolean active = false;
    private static volatile boolean installed = false;

    private PluginSendMessageInterceptor() {
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
            Method m = SendMessagesHelper.class.getDeclaredMethod(
                    "sendMessage", SendMessagesHelper.SendMessageParams.class);
            XposedBridge.hookMethod(m, new SendHook());
            installed = true;
            FileLog.d("zasto plugins: send-message interceptor installed");
        } catch (Throwable t) {
            FileLog.e("zasto plugins: failed to install send-message interceptor", t);
        }
    }

    private static int accountOf(Object sendMessagesHelper) {
        Object v = PluginUtils.getPrivateField(sendMessagesHelper, "currentAccount");
        if (v instanceof Integer) {
            return (Integer) v;
        }
        return UserConfig.selectedAccount;
    }

    private static final class SendHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!active) {
                return;
            }
            try {
                if (param.args == null || param.args.length < 1 || param.args[0] == null) {
                    return;
                }
                Object params = param.args[0];
                int account = accountOf(param.thisObject);
                // Plugin mutates params in place for MODIFY; returns true to CANCEL the send.
                boolean cancel = PluginsController.getInstance().dispatchSendMessage(account, params);
                if (cancel) {
                    param.setResult(null); // skip the original sendMessage(...) entirely
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
