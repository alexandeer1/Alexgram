package com.exteragram.messenger.plugins.utils;

import com.exteragram.messenger.plugins.PluginsController;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

/**
 * Alexgram adaptation: NagramXF's version wires a native crash flag via AliuHook (LSPlant).
 * Alexgram hooks with Pine (top.canyie.pine), which has no such flag, so this is a safe
 * no-op that keeps the safe-mode plumbing (and its call sites) intact.
 */
public class NativeCrashHandler {
    private static final String CRASH_FLAG_FILENAME = "native_crash.flag";

    public static void init(String path) {
        // no-op without AliuHook
    }

    public static boolean isNativeLibraryLoaded() {
        try {
            top.canyie.pine.Pine.ensureInitialized();
            return top.canyie.pine.Pine.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void checkAndHandleNativeCrash() {
        File flag = new File(ApplicationLoader.getFilesDirFixed(), CRASH_FLAG_FILENAME);
        if (!flag.exists()) {
            return;
        }
        FileLog.e("Native crash detected. Enabling safe mode for plugins.");
        PluginsController.markPendingSafeModeCrash(PluginsController.SafeModeReason.NATIVE_CRASH, null);
        //noinspection ResultOfMethodCallIgnored
        flag.delete();
    }

    public static String getCrashFlagPath() {
        return new File(ApplicationLoader.getFilesDirFixed(), CRASH_FLAG_FILENAME).getAbsolutePath();
    }
}
