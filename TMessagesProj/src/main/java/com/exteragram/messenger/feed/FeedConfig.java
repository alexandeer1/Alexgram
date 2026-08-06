package com.exteragram.messenger.feed;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FeedConfig {

    private static final FeedConfig[] instances = new FeedConfig[16];
    private static final Object[] lockObjects = new Object[16];

    static {
        for (int i = 0; i < 16; i++) {
            lockObjects[i] = new Object();
        }
    }

    private final SharedPreferences preferences;
    private volatile Set<Long> excludedChannels;
    private volatile boolean includeArchived;
    private volatile int generation;

    public static FeedConfig getInstance(int num) {
        if (num < 0 || num >= 16) {
            num = org.telegram.messenger.UserConfig.selectedAccount;
            if (num < 0 || num >= 16) {
                num = 0;
            }
        }
        FeedConfig instance = instances[num];
        if (instance != null) {
            return instance;
        }
        synchronized (lockObjects[num]) {
            instance = instances[num];
            if (instance == null) {
                instance = new FeedConfig(num);
                instances[num] = instance;
            }
        }
        return instance;
    }

    private FeedConfig(int num) {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("feedconfig" + num, Context.MODE_PRIVATE);
        includeArchived = preferences.getBoolean("includeArchived", false);
        Set<String> savedExcluded = preferences.getStringSet("excludedChannels", null);
        Set<Long> set = new HashSet<>();
        if (savedExcluded != null) {
            for (String str : savedExcluded) {
                try {
                    set.add(Long.parseLong(str));
                } catch (Exception ignored) {
                }
            }
        }
        excludedChannels = set;
    }

    public boolean getIncludeArchived() {
        return includeArchived;
    }

    public synchronized void setIncludeArchived(boolean value) {
        if (this.includeArchived == value) {
            return;
        }
        this.includeArchived = value;
        this.generation++;
        preferences.edit().putBoolean("includeArchived", value).apply();
    }

    public boolean isExcluded(long dialogId) {
        return excludedChannels.contains(dialogId);
    }

    public synchronized void setExcluded(long dialogId, boolean excluded) {
        HashSet<Long> hashSet = new HashSet<>(excludedChannels);
        boolean changed = excluded ? hashSet.add(dialogId) : hashSet.remove(dialogId);
        if (changed) {
            applyExcluded(hashSet);
        }
    }

    public Set<Long> getExcludedSnapshot() {
        return excludedChannels;
    }

    public synchronized void removeExcluded(Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        HashSet<Long> hashSet = new HashSet<>(excludedChannels);
        if (hashSet.removeAll(ids)) {
            applyExcluded(hashSet);
        }
    }

    public synchronized void clearExcluded() {
        if (excludedChannels.isEmpty()) {
            return;
        }
        applyExcluded(Collections.emptySet());
    }

    public synchronized void excludeAll(Collection<Long> ids) {
        HashSet<Long> hashSet = new HashSet<>(excludedChannels);
        if (hashSet.addAll(ids)) {
            applyExcluded(hashSet);
        }
    }

    private void applyExcluded(Set<Long> updated) {
        excludedChannels = updated;
        generation++;
        HashSet<String> stringSet = new HashSet<>();
        for (Long id : updated) {
            stringSet.add(String.valueOf(id));
        }
        preferences.edit().putStringSet("excludedChannels", stringSet).apply();
    }

    public int getGeneration() {
        return generation;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(includeArchived, excludedChannels, generation);
    }

    public static final class Snapshot {
        private final boolean includeArchived;
        private final Set<Long> excludedChannels;
        private final int generation;

        public Snapshot(boolean includeArchived, Set<Long> excludedChannels, int generation) {
            this.includeArchived = includeArchived;
            this.excludedChannels = excludedChannels;
            this.generation = generation;
        }

        public boolean getIncludeArchived() {
            return includeArchived;
        }

        public Set<Long> getExcludedChannels() {
            return excludedChannels;
        }

        public int getGeneration() {
            return generation;
        }
    }
}
