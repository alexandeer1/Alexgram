package com.exteragram.messenger.plugins;

import org.telegram.plugins.PluginInfo;

/**
 * Compatibility bridge: exposes exteraGram's {@code com.exteragram.messenger.plugins.Plugin} API
 * over ZaStoGram's own {@link org.telegram.plugins.PluginInfo}, so community library-plugins
 * (e.g. QuantaHut) that import this class resolve and run.
 */
public class Plugin {

    private final PluginInfo info;

    public Plugin(PluginInfo info) {
        this.info = info;
    }

    public String getId() {
        return info.id;
    }

    public String getName() {
        return info.displayName();
    }

    public String getVersion() {
        return info.version != null ? info.version : "1.0";
    }

    public String getAuthor() {
        return info.author;
    }

    public String getDescription() {
        return info.description;
    }

    public boolean isEnabled() {
        return info.enabled;
    }

    /** __icon__ is "StickerPackShortName/index"; this returns the pack short name (or null). */
    public String getPack() {
        String icon = info.icon;
        if (icon == null || icon.length() == 0) {
            return null;
        }
        int slash = icon.indexOf('/');
        return slash > 0 ? icon.substring(0, slash) : icon;
    }

    /** The index part of __icon__ ("pack/index"), or -1 if absent/unparseable. */
    public int getIndex() {
        String icon = info.icon;
        if (icon == null) {
            return -1;
        }
        int slash = icon.indexOf('/');
        if (slash < 0 || slash + 1 >= icon.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(icon.substring(slash + 1).trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    public PluginInfo getInfo() {
        return info;
    }
}
