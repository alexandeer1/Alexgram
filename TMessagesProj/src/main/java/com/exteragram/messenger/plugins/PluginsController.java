package com.exteragram.messenger.plugins;

import org.telegram.plugins.PluginInfo;

import java.util.LinkedHashMap;

/**
 * Compatibility bridge: exposes exteraGram's {@code com.exteragram.messenger.plugins.PluginsController}
 * (notably {@code getInstance().plugins}) over ZaStoGram's own engine. {@link #plugins} is refreshed
 * from the live engine on every getInstance()/getPlugins() call so it always reflects current state.
 */
public class PluginsController {

    private static final PluginsController INSTANCE = new PluginsController();

    /** id -> Plugin, mirroring exteraGram's public field. Kept fresh by refresh(). */
    public final LinkedHashMap<String, Plugin> plugins = new LinkedHashMap<>();

    public static PluginsController getInstance() {
        INSTANCE.refresh();
        return INSTANCE;
    }

    private synchronized void refresh() {
        plugins.clear();
        try {
            for (PluginInfo info : org.telegram.plugins.PluginsController.getInstance().getPlugins()) {
                if (info != null && info.id != null) {
                    plugins.put(info.id, new Plugin(info));
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public LinkedHashMap<String, Plugin> getPlugins() {
        refresh();
        return plugins;
    }

    public Plugin getPlugin(String id) {
        refresh();
        return plugins.get(id);
    }
}
