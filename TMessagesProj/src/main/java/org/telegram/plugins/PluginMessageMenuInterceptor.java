package org.telegram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Renders plugin MESSAGE_CONTEXT_MENU items in the message long-press menu.
 *
 * Hooks ChatActivity.fillMessageMenu(...) to append items into the (icons, items, options) arrays
 * with synthetic option ids in a high range, and ChatActivity.processSelectedOption(int) to detect
 * those ids and dispatch on_click with a context dict ({message, dialog_id, user, chat, ...}).
 * Gated by {@link #active}.
 */
public final class PluginMessageMenuInterceptor {

    private static volatile boolean active = false;
    private static volatile boolean installed = false;

    // High range so synthetic option ids never collide with real ones (which are small).
    private static final int ID_BASE = 0x7A570000;
    private static final HashMap<Integer, String[]> MAP = new HashMap<>(); // id -> {pluginId, itemId}

    private PluginMessageMenuInterceptor() {
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
            Class<?> ca = Class.forName("org.telegram.ui.ChatActivity");
            Method fill = ca.getDeclaredMethod("fillMessageMenu",
                    MessageObject.class, ArrayList.class, ArrayList.class, ArrayList.class);
            XposedBridge.hookMethod(fill, new FillHook());
            Method proc = ca.getDeclaredMethod("processSelectedOption", int.class);
            XposedBridge.hookMethod(proc, new ProcHook());
            installed = true;
            FileLog.d("zasto plugins: message-menu interceptor installed");
        } catch (Throwable t) {
            FileLog.e("zasto plugins: failed to install message-menu interceptor", t);
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private static final class FillHook extends XC_MethodHook {
        @Override
        @SuppressWarnings("unchecked")
        protected void afterHookedMethod(MethodHookParam param) {
            if (!active) {
                return;
            }
            try {
                Object[] a = param.args;
                if (a == null || a.length < 4
                        || !(a[1] instanceof ArrayList) || !(a[2] instanceof ArrayList) || !(a[3] instanceof ArrayList)) {
                    return;
                }
                ArrayList<Integer> icons = (ArrayList<Integer>) a[1];
                ArrayList<CharSequence> items = (ArrayList<CharSequence>) a[2];
                ArrayList<Integer> options = (ArrayList<Integer>) a[3];
                List<Map<String, Object>> menu = PluginsController.getInstance().getMenuItems("message_context_menu");
                synchronized (MAP) {
                    MAP.clear();
                    int k = 0;
                    for (Map<String, Object> m : menu) {
                        int id = ID_BASE + (k++);
                        icons.add(PluginUtils.resolveDrawable(asString(m.get("icon"))));
                        items.add(asString(m.get("text")));
                        options.add(id);
                        MAP.put(id, new String[]{asString(m.get("plugin_id")), asString(m.get("item_id"))});
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static final class ProcHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!active) {
                return;
            }
            try {
                if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) {
                    return;
                }
                int option = (Integer) param.args[0];
                String[] target;
                synchronized (MAP) {
                    target = MAP.get(option);
                }
                if (target == null) {
                    return; // a real (non-plugin) option — let the original handle it
                }
                Object chat = param.thisObject;
                HashMap<String, Object> ctx = new HashMap<>();
                ctx.put("fragment", chat);
                ctx.put("message", PluginUtils.getPrivateField(chat, "selectedObject"));
                Object dlg = PluginUtils.getPrivateField(chat, "dialog_id");
                if (dlg != null) {
                    ctx.put("dialog_id", dlg);
                }
                ctx.put("user", PluginUtils.getPrivateField(chat, "currentUser"));
                ctx.put("chat", PluginUtils.getPrivateField(chat, "currentChat"));
                ctx.put("account", PluginUtils.getPrivateField(chat, "currentAccount"));
                PluginsController.getInstance().onMenuItemClick(target[0], target[1], ctx);
                param.setResult(null); // synthetic option id has no original handling — skip the switch
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
