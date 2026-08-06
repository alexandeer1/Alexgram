package com.exteragram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class ExteraConfig {

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("exteraconfig", Context.MODE_PRIVATE);
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
