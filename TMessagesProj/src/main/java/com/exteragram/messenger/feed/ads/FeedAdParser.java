package com.exteragram.messenger.feed.ads;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

public abstract class FeedAdParser {

    public static ArrayList<FeedAd> parse(TLRPC.messages_Messages messages) {
        ArrayList<FeedAd> ads = new ArrayList<>();
        if (messages != null && messages.messages != null) {
            HashSet<String> addedIds = new HashSet<>();
            for (int i = 0; i < messages.messages.size(); i++) {
                TLRPC.Message message = messages.messages.get(i);
                if (message instanceof TLRPC.TL_message && message.message != null && message.message.startsWith("feed_ad")) {
                    FeedAd manifest = parseManifest(message.message);
                    if (manifest != null && addedIds.add(manifest.id)) {
                        ads.add(manifest);
                    }
                }
            }
            if (!ads.isEmpty()) {
                for (int i = 0; i < messages.messages.size(); i++) {
                    TLRPC.Message message = messages.messages.get(i);
                    if (message instanceof TLRPC.TL_message) {
                        for (int j = 0; j < ads.size(); j++) {
                            FeedAd feedAd = ads.get(j);
                            if (feedAd.bodyMessageId != 0 && message.id == feedAd.bodyMessageId) {
                                feedAd.bodyText = message.message;
                                feedAd.entities = (message.entities == null || message.entities.isEmpty()) ? null : new ArrayList<>(message.entities);
                            }
                            if (feedAd.mediaMessageId != 0 && message.id == feedAd.mediaMessageId && message.media != null) {
                                feedAd.media = message.media;
                            }
                        }
                    }
                }
                ArrayList<FeedAd> displayable = new ArrayList<>(ads.size());
                for (int i = 0; i < ads.size(); i++) {
                    if (ads.get(i).isDisplayable()) {
                        displayable.add(ads.get(i));
                    }
                }
                return displayable;
            }
        }
        return ads;
    }

    private static FeedAd parseManifest(String manifestStr) {
        try {
            FeedAd feedAd = new FeedAd();
            for (String line : manifestStr.split("\n")) {
                int eq = line.indexOf('=');
                if (eq >= 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if (!val.isEmpty()) {
                        switch (key) {
                            case "sponsor_info":
                                feedAd.sponsorInfo = val;
                                break;
                            case "button":
                                feedAd.buttonText = val;
                                break;
                            case "locale":
                                feedAd.locales = parseLocales(val);
                                break;
                            case "weight":
                                feedAd.weight = Math.max(1, parseInt(val, 1));
                                break;
                            case "premium":
                                feedAd.premium = parseMatch(val);
                                break;
                            case "additional_info":
                                feedAd.additionalInfo = val;
                                break;
                            case "id":
                                feedAd.id = val;
                                break;
                            case "url":
                                feedAd.url = val;
                                break;
                            case "body":
                                feedAd.bodyMessageId = parseInt(val, 0);
                                break;
                            case "badge":
                                feedAd.badge = parseMatch(val);
                                break;
                            case "color":
                                feedAd.colorId = parseInt(val, -1);
                                break;
                            case "media":
                                feedAd.mediaMessageId = parseInt(val, 0);
                                break;
                            case "title":
                                feedAd.title = val;
                                break;
                            case "recommended":
                                feedAd.recommended = Boolean.parseBoolean(val);
                                break;
                        }
                    }
                }
            }
            if (feedAd.id == null || feedAd.id.isEmpty()) {
                return null;
            }
            return feedAd;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Set<String> parseLocales(String str) {
        HashSet<String> set = new HashSet<>();
        for (String item : str.split(",")) {
            String lc = item.trim().toLowerCase();
            if (!lc.isEmpty()) {
                set.add(lc);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static int parseMatch(String str) {
        if (TextUtils.equals(str, "true") || TextUtils.equals(str, "has")) {
            return 1;
        }
        return (TextUtils.equals(str, "false") || TextUtils.equals(str, "none")) ? 2 : 0;
    }

    private static int parseInt(String str, int def) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException unused) {
            return def;
        }
    }
}
