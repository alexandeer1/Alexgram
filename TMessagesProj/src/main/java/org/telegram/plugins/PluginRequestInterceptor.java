package org.telegram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.QuickAckDelegate;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.RequestDelegateTimestamp;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.WriteToSocketDelegate;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Lets plugins observe and rewrite MTProto responses via BasePlugin.post_request_hook.
 *
 * Strategy: hook ConnectionsManager.sendRequestInternal (the single funnel that both the async
 * sendRequest and the sync sendRequestSync paths reach) and, in beforeHookedMethod, wrap the onComplete delegate (args[1])
 * so plugins run just before the response is delivered. Gated by {@link #active} so there is no
 * per-request work unless a plugin actually registers a request hook.
 */
public final class PluginRequestInterceptor {

    public static final String CANCEL_SENTINEL = "__zasto_cancel__";

    private static volatile boolean active = false;
    private static volatile boolean installed = false;

    private PluginRequestInterceptor() {
    }

    /** Enable/disable dispatch; installs the underlying hook lazily on first activation. */
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
            // sendRequestInternal is the single funnel for BOTH the async sendRequest path and the
            // sync sendRequestSync path (file downloads), so hooking it catches every request.
            // (private; Pine/Xposed hooks private members fine. Trailing int = requestToken.)
            Method m = ConnectionsManager.class.getDeclaredMethod(
                    "sendRequestInternal",
                    TLObject.class, RequestDelegate.class, RequestDelegateTimestamp.class,
                    QuickAckDelegate.class, WriteToSocketDelegate.class,
                    int.class, int.class, int.class, boolean.class, int.class);
            XposedBridge.hookMethod(m, new SendRequestHook());
            installed = true;
            FileLog.d("zasto plugins: request interceptor installed");
        } catch (Throwable t) {
            FileLog.e("zasto plugins: failed to install request interceptor", t);
        }
    }

    private static int accountOf(Object connectionsManager) {
        Object v = PluginUtils.getPrivateField(connectionsManager, "currentAccount");
        if (v instanceof Integer) {
            return (Integer) v;
        }
        return UserConfig.selectedAccount;
    }

    private static final class SendRequestHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!active) {
                return;
            }
            try {
                final Object[] args = param.args;
                if (args == null || args.length < 2 || !(args[0] instanceof TLObject)) {
                    return;
                }
                TLObject request = (TLObject) args[0];
                String name = request.getClass().getSimpleName();
                final int account = accountOf(param.thisObject);

                // pre_request_hook: inspect / modify / cancel the outgoing request before it is sent.
                Object pre = PluginsController.getInstance().dispatchPreRequest(name, account, request);
                if (CANCEL_SENTINEL.equals(pre)) {
                    param.setResult(null); // skip sending entirely
                    return;
                }
                if (pre instanceof TLObject && pre != request) {
                    args[0] = pre;
                    request = (TLObject) pre;
                    name = request.getClass().getSimpleName();
                }

                // post_request_hook: wrap the onComplete delegate (args[1]) when present.
                if (args[1] instanceof RequestDelegate) {
                    final RequestDelegate orig = (RequestDelegate) args[1];
                    final String reqName = name;
                    args[1] = (RequestDelegate) (response, error) -> {
                        TLObject deliver = response;
                        try {
                            Object out = PluginsController.getInstance()
                                    .dispatchPostRequest(reqName, account, response, error);
                            if (CANCEL_SENTINEL.equals(out)) {
                                return; // HookStrategy.CANCEL — swallow the response
                            }
                            if (out instanceof TLObject) {
                                deliver = (TLObject) out;
                            }
                            // else out == null: pass-through — deliver the original response (may be null on error)
                        } catch (Throwable t) {
                            FileLog.e(t);
                        }
                        orig.run(deliver, error);
                    };
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
