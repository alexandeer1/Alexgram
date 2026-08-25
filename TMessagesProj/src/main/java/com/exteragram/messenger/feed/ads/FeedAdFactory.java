package com.exteragram.messenger.feed.ads;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public abstract class FeedAdFactory {
    private static final AtomicInteger nextId = new AtomicInteger(-20000000);

    public static MessageObject createAdMessageObject(int account, FeedAd feedAd) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = nextId.getAndDecrement();
        message.date = ConnectionsManager.getInstance(account).getCurrentTime();
        CharSequence bodyText = feedAd.bodyText;
        message.message = bodyText != null ? bodyText.toString() : "";
        message.peer_id = new TLRPC.TL_peerChannel();
        message.flags |= 256;
        if (feedAd.entities != null && !feedAd.entities.isEmpty()) {
            message.entities = feedAd.entities;
            message.flags |= 128;
        }
        if (feedAd.media != null) {
            message.media = feedAd.media;
            message.flags |= 512;
        }
        MessageObject messageObject = new MessageObject(account, message, new HashMap<>(), new HashMap<>(), true, true);
        messageObject.searchType = 4;
        if (feedAd.id != null) {
            messageObject.sponsoredId = feedAd.id.getBytes(StandardCharsets.UTF_8);
        }
        messageObject.sponsoredTitle = feedAd.title;
        messageObject.sponsoredUrl = feedAd.url;
        messageObject.sponsoredButtonText = feedAd.buttonText;
        messageObject.sponsoredInfo = feedAd.sponsorInfo;
        messageObject.sponsoredAdditionalInfo = feedAd.additionalInfo;
        messageObject.sponsoredRecommended = feedAd.recommended;
        messageObject.sponsoredCanReport = false;
        messageObject.sponsoredMedia = feedAd.media;
        messageObject.sponsoredColor = buildColor(feedAd.colorId);
        messageObject.setType();
        messageObject.textLayoutBlocks = new ArrayList<>();
        messageObject.generateThumbs(true);
        return messageObject;
    }

    private static TLRPC.PeerColor buildColor(int colorId) {
        if (colorId < 0) {
            return null;
        }
        TLRPC.TL_peerColor peerColor = new TLRPC.TL_peerColor();
        peerColor.flags |= 1;
        peerColor.color = colorId;
        return peerColor;
    }
}
