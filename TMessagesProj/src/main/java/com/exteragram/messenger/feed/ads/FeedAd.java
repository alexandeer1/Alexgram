package com.exteragram.messenger.feed.ads;

import java.util.ArrayList;
import java.util.Set;
import org.telegram.tgnet.TLRPC;

public final class FeedAd {
    public String additionalInfo;
    int bodyMessageId;
    public CharSequence bodyText;
    public String buttonText;
    public ArrayList<TLRPC.MessageEntity> entities;
    public String id;
    public Set<String> locales;
    public TLRPC.MessageMedia media;
    int mediaMessageId;
    public boolean recommended;
    public String sponsorInfo;
    public String title;
    public String url;
    public int weight = 1;
    public int colorId = -1;
    public int premium = 0;
    public int badge = 0;

    public boolean isDisplayable() {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return bodyText != null || title != null || media != null;
    }
}
