package com.exteragram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.plugins.PluginsConstants;
import com.exteragram.messenger.plugins.PluginsController;

import org.telegram.messenger.ApplicationLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Merged Alexgram config: keeps the original feed/header getters and adds the
 * NagramXF plugin-engine settings that the ported plugin engine reads/writes.
 * Unlike NagramXF, {@link #pluginsEngine} defaults to ON so plugins work out of the box.
 */
public class ExteraConfig {

    private static final Object sync = new Object();
    private static boolean configLoaded;
    private static boolean initialized;

    public static SharedPreferences preferences;
    public static SharedPreferences.Editor editor;
    public static boolean pluginsEngine;
    public static boolean pluginsSafeMode;
    public static boolean pluginsDevMode;
    public static boolean pluginsCompactView;
    public static boolean pluginsDisableArtOpts;
    public static boolean pluginsPySdkAutoUpdate;
    public static boolean pluginsPySdkBetaVersions;
    public static long sdkUpdateScheduleTimestamp;
    public static Set<String> pinnedPlugins = Collections.emptySet();

    public enum DrawerItem {
        PLUGINS(102);

        public final int id;

        DrawerItem(int id) {
            this.id = id;
        }

        public static DrawerItem getById(int id) {
            for (DrawerItem item : values()) {
                if (item.id == id) {
                    return item;
                }
            }
            return null;
        }
    }

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("exteraconfig", Context.MODE_PRIVATE);
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            preferences = context.getSharedPreferences("exteraconfig", Context.MODE_PRIVATE);
            editor = preferences.edit();
            // Always-on engine in Alexgram: default true (NagramXF defaults to false behind a toggle).
            pluginsEngine = PluginsController.isPluginEngineSupported() && preferences.getBoolean("pluginsEngine", true);
            pluginsSafeMode = preferences.getBoolean("pluginsSafeMode", false);
            pluginsDevMode = preferences.getBoolean("pluginsDevMode", false);
            pluginsCompactView = preferences.getBoolean("pluginsCompactView", false);
            pluginsDisableArtOpts = preferences.getBoolean("pluginsDisableArtOpts", false);
            pluginsPySdkAutoUpdate = preferences.getBoolean("pluginsPySdkAutoUpdate", false);
            pluginsPySdkBetaVersions = preferences.getBoolean("pluginsPySdkBetaVersions", false);
            sdkUpdateScheduleTimestamp = preferences.getLong("sdkUpdateScheduleTimestamp", 0L);
            pinnedPlugins = new HashSet<>(preferences.getStringSet("pinnedPlugins", Collections.emptySet()));
            configLoaded = true;
        }
    }

    public static void reloadConfig() {
        synchronized (sync) {
            configLoaded = false;
        }
        loadConfig();
    }

    public static void init() {
        synchronized (sync) {
            if (initialized) {
                return;
            }
            initialized = true;
        }
        loadConfig();
        BadgesController.INSTANCE.init();
        PluginsController.getInstance().init(() -> PluginsController.getInstance().executeOnAppEvent(PluginsConstants.APP_START));
    }

    public static boolean getShowFeedTab() {
        return getPreferences().getBoolean("showFeedTab", false);
    }

    public static void setShowFeedTab(boolean value) {
        getPreferences().edit().putBoolean("showFeedTab", value).apply();
    }

    public static boolean getFeedReplaceContactsTab() {
        return getPreferences().getBoolean("feedReplaceContactsTab", false);
    }

    public static void setFeedReplaceContactsTab(boolean value) {
        getPreferences().edit().putBoolean("feedReplaceContactsTab", value).apply();
    }

    public static boolean getShowFeedUnreadCounter() {
        return getPreferences().getBoolean("showFeedUnreadCounter", true);
    }

    public static void setShowFeedUnreadCounter(boolean value) {
        getPreferences().edit().putBoolean("showFeedUnreadCounter", value).apply();
    }

    public static boolean getNewChatHeaderStyle() {
        return getPreferences().getBoolean("newChatHeaderStyle", false);
    }
}
